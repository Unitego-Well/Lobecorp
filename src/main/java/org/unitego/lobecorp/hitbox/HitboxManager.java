package org.unitego.lobecorp.hitbox;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.network.HitboxCreatePayload;
import org.unitego.lobecorp.network.HitboxRemovePayload;
import org.unitego.lobecorp.network.HitboxUpdatePayload;
import org.unitego.lobecorp.registry.LcAttachmentTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// Level 判断框实例的创建、命中处理、同步和移除入口。
public final class HitboxManager {
	/// 单次当前 tick 生命周期。
	public static final int CURRENT_TICK_DURATION = 1;
	/// 无来源固定判断框的客户端同步半径。
	private static final double FIXED_HITBOX_SYNC_RANGE = 64.0;

	private HitboxManager() {
	}

	/// 创建并立即同步一个非激活实例。
	///
	/// @param template 共享模板
	/// @param level 所在服务端维度
	/// @param position 初始中心
	/// @param durationTicks 包括预览在内的总寿命
	/// @return 已加入 Level 管理器的实例
	public static HitboxInstance create(HitboxTemplate template, ServerLevel level, Vec3 position,
			int durationTicks) {
		HitboxInstance instance = new HitboxInstance(template, level, position, durationTicks);
		level.getData(LcAttachmentTypes.HITBOX_LEVEL_DATA).add(instance);
		send(instance, new HitboxCreatePayload(HitboxSnapshot.of(instance)));
		return instance;
	}

	/// 按编号读取服务端 Level 中的实例。
	///
	/// @param level 所在服务端维度
	/// @param id Level 内实例编号
	/// @return 当前实例；不存在时返回 {@code null}
	@Nullable
	public static HitboxInstance get(ServerLevel level, int id) {
		return level.getData(LcAttachmentTypes.HITBOX_LEVEL_DATA).get(id);
	}

	/// 立即移除实例并向当前观察者发送移除载荷。
	///
	/// @param level 所在服务端维度
	/// @param id Level 内实例编号
	/// @return 是否确实移除了实例
	public static boolean remove(ServerLevel level, int id) {
		HitboxInstance instance = level.getData(LcAttachmentTypes.HITBOX_LEVEL_DATA).remove(id);
		if (instance == null) {
			return false;
		}
		instance.markRemoved();
		send(instance, new HitboxRemovePayload(id));
		return true;
	}

	/// 在 Level Tick Post 更新该维度的全部服务端实例。
	///
	/// @param level 当前服务端维度
	public static void tick(ServerLevel level) {
		HitboxLevelData data = level.getData(LcAttachmentTypes.HITBOX_LEVEL_DATA);
		List<HitboxInstance> instances = new ArrayList<>(data.instances());
		for (HitboxInstance instance : instances) {
			if (instance.isExpired()) {
				remove(level, instance.id());
				continue;
			}
			if (instance.hasInvalidSource()) {
				remove(level, instance.id());
				continue;
			}
			instance.updateFollowTransform();
			if (instance.isActive() && !instance.isExhausted()) {
				processHits(instance);
			}
			if (instance.isRemoved()) {
				continue;
			}
			send(instance, new HitboxUpdatePayload(HitboxSnapshot.of(instance)));
			instance.advanceLifetime();
		}
	}

	private static void processHits(HitboxInstance instance) {
		if (instance.reachedTotalMaximum()) {
			handleExhausted(instance);
			return;
		}
		ServerLevel level = instance.level();
		AABB broadBounds = HitboxGeometry.boundingBox(instance);
		List<Entity> candidates = level.getEntities(instance.source(), broadBounds, instance::accepts);
		candidates.sort(Comparator.comparingDouble(target -> target.getBoundingBox().getCenter()
				.distanceToSqr(instance.position())));

		for (Entity target : candidates) {
			if (!instance.canAttempt(target, level.getGameTime())) {
				continue;
			}
			if (!HitboxGeometry.intersects(instance, target.getBoundingBox())) {
				continue;
			}
			if (!hasLineOfSight(instance, target)) {
				continue;
			}
			boolean successful = instance.template().effect()
					.apply(new HitboxEffectContext(level, instance, instance.source(), target));
			if (!successful) {
				continue;
			}
			instance.recordSuccess(target, level.getGameTime());
			if (instance.reachedTotalMaximum()) {
				handleExhausted(instance);
				return;
			}
		}
	}

	private static void handleExhausted(HitboxInstance instance) {
		if (instance.source() == null) {
			remove(instance.level(), instance.id());
			return;
		}
		instance.exhaust();
	}

	private static boolean hasLineOfSight(HitboxInstance instance, Entity target) {
		if (instance.lineOfSightMode() == HitboxLineOfSightMode.UNRESTRICTED) {
			return true;
		}
		List<Vec3> starts;
		Entity source = instance.source();
		if (instance.lineOfSightMode() == HitboxLineOfSightMode.ENTITY_TO_ENTITY && source != null) {
			starts = samplePoints(source.getBoundingBox());
		} else {
			starts = List.of(instance.position());
		}
		List<Vec3> ends = samplePoints(target.getBoundingBox());
		for (Vec3 start : starts) {
			for (Vec3 end : ends) {
				HitResult result = instance.level().clip(new ClipContext(start, end,
						ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
				if (result.getType() == HitResult.Type.MISS) {
					return true;
				}
			}
		}
		return false;
	}

	private static List<Vec3> samplePoints(AABB bounds) {
		Vec3 center = bounds.getCenter();
		return List.of(
				center,
				new Vec3(bounds.minX, center.y, center.z),
				new Vec3(bounds.maxX, center.y, center.z),
				new Vec3(center.x, bounds.minY, center.z),
				new Vec3(center.x, bounds.maxY, center.z),
				new Vec3(center.x, center.y, bounds.minZ),
				new Vec3(center.x, center.y, bounds.maxZ)
		);
	}

	private static void send(HitboxInstance instance,
			net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
		Entity source = instance.source();
		if (source != null) {
			PacketDistributor.sendToPlayersTrackingEntity(source, payload);
			return;
		}
		Vec3 position = instance.position();
		PacketDistributor.sendToPlayersNear(instance.level(), null, position.x, position.y, position.z,
				FIXED_HITBOX_SYNC_RANGE, payload);
	}
}
