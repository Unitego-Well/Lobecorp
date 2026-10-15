package org.unitego.lobecorp.particle.damage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 一次同步伤害调用内的命中上下文；退出作用域后恢复外层上下文，不跨 tick 保存。
public class DamageParticleHitContext implements AutoCloseable {
	/// 作用域必须按进入顺序的逆序释放，防止嵌套伤害串用命中位置。
	private static final String INVALID_CLOSE_ORDER = "Damage particle hit contexts must close in reverse order";
	/// 显式命中点必须是有限世界坐标。
	private static final String INVALID_POSITION = "Damage particle hit position must be finite";
	private static final ThreadLocal<DamageParticleHitContext> CURRENT = new ThreadLocal<>();

	@Nullable
	private final DamageParticleHitContext previous;
	@Nullable
	private final Entity target;
	@Nullable
	private final DamageSource source;
	@Nullable
	private final Projectile projectile;
	@Nullable
	private final Vec3 position;
	private final boolean melee;

	private DamageParticleHitContext(@Nullable Entity target, @Nullable DamageSource source,
	                                 @Nullable Projectile projectile,
	                                 @Nullable Vec3 position, boolean melee) {
		this.previous = CURRENT.get();
		this.target = target;
		this.source = source;
		this.projectile = projectile;
		this.position = position;
		this.melee = melee;
		CURRENT.set(this);
	}

	/// 为调用方已知的世界命中点建立作用域，只匹配相同目标与 DamageSource 实例。
	public static DamageParticleHitContext at(Entity target, DamageSource source, Vec3 position, boolean melee) {
		Objects.requireNonNull(target);
		Objects.requireNonNull(source);
		Objects.requireNonNull(position);
		if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
			throw new IllegalArgumentException(INVALID_POSITION);
		}

		return new DamageParticleHitContext(target, source, null, position, melee);
	}

	/// 保留本次投射物几何碰撞点，仅用于直接来源和受击目标均匹配的伤害。
	public static DamageParticleHitContext projectile(Projectile projectile, EntityHitResult hit) {
		return new DamageParticleHitContext(hit.getEntity(), null, projectile, hit.getLocation(), false);
	}

	/// 包围玩家的一次直接伤害调用，绑定目标和来源实例，避免附魔追加伤害继承近战标记。
	public static DamageParticleHitContext melee(ServerPlayer player, Entity target, DamageSource source) {
		return at(target, source, estimateMeleePosition(target, player), isDirectPlayerAttack(source, player));
	}

	/// 优先使用明确作用域；没有命中数据时使用身体中心，玩家近战仅估算视线与 AABB 交点。
	public static Hit resolve(LivingEntity target, DamageSource source) {
		for (DamageParticleHitContext context = CURRENT.get(); context != null; context = context.previous) {
			if (context.target == target && context.source == source && context.position != null) {
				return new Hit(context.position, context.melee);
			}

			if (context.target == target && context.projectile != null
					&& source.getDirectEntity() == context.projectile && context.position != null) {
				return new Hit(context.position, false);
			}
		}

		if (source.getEntity() instanceof ServerPlayer player && isDirectPlayerAttack(source, player)
				&& (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.SPEAR)
				|| source.is(DamageTypes.MACE_SMASH))) {
			return new Hit(estimateMeleePosition(target, player), true);
		}

		return new Hit(target.getBoundingBox().getCenter(), false);
	}

	private static boolean isDirectPlayerAttack(DamageSource source, ServerPlayer player) {
		return source.getEntity() == player && source.getDirectEntity() == player
				&& !source.is(DamageTypes.THORNS) && !source.is(DamageTypeTags.IS_PROJECTILE)
				&& !source.is(DamageTypeTags.IS_EXPLOSION);
	}

	private static Vec3 estimateMeleePosition(Entity target, ServerPlayer player) {
		var bounds = target.getBoundingBox();
		Vec3 center = bounds.getCenter();
		Vec3 eye = player.getEyePosition();
		double rayLength = eye.distanceTo(center) + bounds.getSize();
		Vec3 end = eye.add(player.getLookAngle().scale(rayLength));
		return bounds.clip(eye, end).orElse(center);
	}

	@Override
	public void close() {
		if (CURRENT.get() != this) {
			throw new IllegalStateException(INVALID_CLOSE_ORDER);
		}

		if (previous == null) {
			CURRENT.remove();
		} else {
			CURRENT.set(previous);
		}
	}

	/// 已冻结的世界位置与近战语义；估算点和中心回退不表示模型表面碰撞。
	public record Hit(Vec3 position, boolean melee) {
	}
}
