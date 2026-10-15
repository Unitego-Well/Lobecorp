package org.unitego.lobecorp.util.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.world.entity.control.EntityGrabState;

import java.util.UUID;

/// 独立的临时抓取工具；只约束移动，不关闭 AI、攻击、使用物品或转向。
/// 世界由游戏生命周期持有，工具借用世界访问实体，不负责关闭世界。
@SuppressWarnings("resource")
public class EntityGrabUtil {
	/// 默认偏移为零，直接固定在抓取者脚部坐标。
	public static boolean grab(LivingEntity grabber, LivingEntity target, double escapeDistance) {
		return grab(grabber, target, Vec3.ZERO, escapeDistance);
	}

	/// 将目标固定到抓取者脚部加世界轴偏移；一个目标同时只由一个实体持有。
	public static boolean grab(LivingEntity grabber, LivingEntity target, Vec3 offset, double escapeDistance) {
		if (!(grabber.level() instanceof ServerLevel) || target.level() != grabber.level()
				|| grabber == target || !grabber.isAlive() || !target.isAlive()
				|| grabber.isRemoved() || target.isRemoved() || !offset.isFinite()
				|| !Double.isFinite(escapeDistance) || escapeDistance <= 0.0
				|| relation(target) != null || createsCycle(grabber, target)) {
			return false;
		}

		Vec3 position = grabber.position().add(offset);
		EntityGrabState state = target.getData(LcAttachmentTypes.ENTITY_GRAB);
		state.setRelation(new EntityGrabState.Relation(grabber.getUUID(), offset,
				escapeDistance * escapeDistance, position));
		grabber.getData(LcAttachmentTypes.ENTITY_GRAB).addHeldTarget(target.getUUID());
		target.setData(LcAttachmentTypes.ENTITY_GRAB, state);
		fixPosition(target, position);
		return true;
	}

	/// 只释放指定抓取者持有的目标，避免结束旧技能时清除其他抓取关系。
	public static void release(LivingEntity grabber, LivingEntity target) {
		EntityGrabState.Relation relation = relation(target);

		if (relation != null && relation.grabberId().equals(grabber.getUUID())) {
			release(target);
		}
	}

	/// 释放该实体当前持有的全部目标；落地、取消和卸载均可重复调用。
	public static void releaseAll(LivingEntity grabber) {
		EntityGrabState state = grabber.getExistingDataOrNull(LcAttachmentTypes.ENTITY_GRAB);

		if (state == null || !(grabber.level() instanceof ServerLevel level)) {
			return;
		}

		for (UUID id : state.heldTargets()) {
			if (level.getEntity(id) instanceof LivingEntity target) {
				release(grabber, target);
			}

			state.removeHeldTarget(id);
		}
	}

	/// 实体离开世界时清除两端关系，状态不会写入存档或转移到新实体。
	public static void clear(LivingEntity entity) {
		releaseAll(entity);
		release(entity);
	}

	/// 在实体 Post tick 更新自身及所持目标，避免实体 tick 顺序导致锚点落后一 tick。
	public static void tick(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		if (!entity.isAlive() || entity.isRemoved()) {
			clear(entity);
			return;
		}

		updateHeld(entity);
		EntityGrabState state = entity.getExistingDataOrNull(LcAttachmentTypes.ENTITY_GRAB);

		if (state == null) {
			return;
		}

		for (UUID id : state.heldTargets()) {
			if (level.getEntity(id) instanceof LivingEntity target) {
				updateHeld(target);
			} else {
				state.removeHeldTarget(id);
			}
		}
	}

	/// 同时限制客户端和服务端的普通移动；直接瞬移仍可改变位置并触发脱离。
	public static Vec3 restrictMovement(Entity entity, Vec3 movement) {
		if (!(entity instanceof LivingEntity living)) {
			return movement;
		}

		EntityGrabState.Relation relation = relation(living);

		if (relation == null) {
			return movement;
		}

		if (!living.level().isClientSide() && escaped(living, relation)) {
			release(living);
			return movement;
		}

		living.setDeltaMovement(Vec3.ZERO);
		return Vec3.ZERO;
	}

	private static void updateHeld(LivingEntity target) {
		EntityGrabState.Relation relation = relation(target);

		if (relation == null) {
			return;
		}

		Entity source = target.level().getEntity(relation.grabberId());

		if (!(source instanceof LivingEntity grabber) || !grabber.isAlive() || grabber.isRemoved()
				|| !target.isAlive() || target.isRemoved() || escaped(target, relation)) {
			release(target);
			return;
		}

		Vec3 position = grabber.position().add(relation.offset());
		target.getData(LcAttachmentTypes.ENTITY_GRAB).setRelation(relation.at(position));
		fixPosition(target, position);
	}

	private static boolean escaped(LivingEntity target, EntityGrabState.Relation relation) {
		return target.position().distanceToSqr(relation.fixedPosition()) >= relation.escapeDistanceSquared();
	}

	private static void fixPosition(LivingEntity target, Vec3 position) {
		target.setDeltaMovement(Vec3.ZERO);
		target.resetFallDistance();

		if (target.position().equals(position)) {
			return;
		}

		if (target instanceof ServerPlayer player) {
			player.teleportTo(position.x, position.y, position.z);
		} else {
			target.setPos(position);
		}
	}

	private static void release(LivingEntity target) {
		EntityGrabState state = target.getExistingDataOrNull(LcAttachmentTypes.ENTITY_GRAB);
		EntityGrabState.Relation relation = state == null ? null : state.relation();

		if (relation == null || target.level().isClientSide()) {
			return;
		}

		if (target.level().getEntity(relation.grabberId()) instanceof LivingEntity grabber) {
			EntityGrabState sourceState = grabber.getExistingDataOrNull(LcAttachmentTypes.ENTITY_GRAB);

			if (sourceState != null) {
				sourceState.removeHeldTarget(target.getUUID());
			}
		}

		state.setRelation(null);
		target.setData(LcAttachmentTypes.ENTITY_GRAB, state);
	}

	private static EntityGrabState.@Nullable Relation relation(LivingEntity entity) {
		EntityGrabState state = entity.getExistingDataOrNull(LcAttachmentTypes.ENTITY_GRAB);
		return state == null ? null : state.relation();
	}

	private static boolean createsCycle(LivingEntity grabber, LivingEntity target) {
		EntityGrabState.Relation relation = relation(grabber);

		while (relation != null) {
			if (relation.grabberId().equals(target.getUUID())) {
				return true;
			}

			Entity source = grabber.level().getEntity(relation.grabberId());
			relation = source instanceof LivingEntity living ? relation(living) : null;
		}

		return false;
	}
}
