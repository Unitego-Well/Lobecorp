package org.unitego.lobecorp.util;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime;

/// 技能共用的目标方向、受限转向和瞄准判定；只更新当前角度，保留插值旧值。
public class EntityFacingUtil {
	/// 浮点角度比较的误差容限，单位为度。
	private static final float ANGLE_EPSILON = 1.0E-3F;

	@Nullable
	public static Vec3 targetPosition(Mob mob, EntitySkillRuntime<?> runtime, boolean center) {
		LivingEntity target = runtime.target(LivingEntity.class);
		if (center && target != null) return target.getBoundingBox().getCenter();
		if (runtime.targetPosition() != null) return runtime.targetPosition();
		if (target == null) target = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		return target == null ? null : center ? target.getBoundingBox().getCenter() : target.position();
	}

	public static Vec3 horizontalDirection(Mob mob, Vec3 position) {
		Vec3 offset = position.subtract(mob.position()).multiply(1.0, 0.0, 1.0);
		return offset.horizontalDistanceSqr() > 0.0 ? offset.normalize() : Vec3.directionFromRotation(0.0F, mob.getYRot());
	}

	public static float yaw(Vec3 offset, float fallback) {
		return offset.horizontalDistanceSqr() > 0.0
				? (float) (Mth.atan2(offset.z, offset.x) * Mth.RAD_TO_DEG) - 90.0F : fallback;
	}

	/// 使用实体的头部转速和俯仰限制；旧角度只作为每 tick 转向预算的基准，不写回旧值。
	public static void turn(Mob mob, float yaw, float headYaw, float bodyYaw) {
		turn(mob, yaw, headYaw, bodyYaw, 1.0F);
	}

	/// 按默认转速的比例转动头、身体和实体朝向；比例只影响本 tick 的转向预算。
	public static void turn(Mob mob, float yaw, float headYaw, float bodyYaw, float speedRatio) {
		float speed = mob.getHeadRotSpeed() * speedRatio;
		mob.setYRot(Mth.approachDegrees(mob.yRotO, yaw, speed));
		mob.setYBodyRot(Mth.approachDegrees(mob.yBodyRotO, bodyYaw, speed));
		float head = Mth.approachDegrees(mob.yHeadRotO, headYaw, speed);
		mob.setYHeadRot(mob.yBodyRot + Mth.clamp(Mth.wrapDegrees(head - mob.yBodyRot),
				-mob.getMaxHeadYRot(), mob.getMaxHeadYRot()));
	}

	public static void turn(Mob mob, float yaw) {
		turn(mob, yaw, yaw, yaw);
	}

	/// 逐 tick 转向目标；身体与头部均到位后返回 true，可用于 AI 和 HUD 的施放门槛。
	public static boolean aim(Mob mob, @Nullable Vec3 position, boolean pitch) {
		return aim(mob, position, pitch ? mob.getEyePosition() : mob.position(), pitch);
	}

	/// 从指定发射起点瞄准目标，沿用头身转速和俯仰限制。
	public static boolean aim(Mob mob, @Nullable Vec3 position, Vec3 origin, boolean pitch) {
		return aim(mob, position, origin, pitch, 1.0F);
	}

	/// 持续瞄准可缩放默认转速，仍保留头身角度限制和旧值插值。
	public static boolean aim(Mob mob, @Nullable Vec3 position, Vec3 origin, boolean pitch, float speedRatio) {
		if (position == null) return true;
		Vec3 offset = position.subtract(origin);
		float yaw = yaw(offset, mob.getYRot());
		turn(mob, yaw, yaw, yaw, speedRatio);
		float targetPitch = pitch && offset.lengthSqr() > 0.0
				? (float) (-Mth.atan2(offset.y, offset.horizontalDistance()) * Mth.RAD_TO_DEG) : mob.getXRot();
		if (pitch) mob.setXRot(Mth.approachDegrees(mob.xRotO, targetPitch, mob.getMaxHeadXRot() * speedRatio));
		// LookControl 随后的 tick 会归零俯仰；沿用本 tick 已获准的角度，避免再次转向或丢失瞄准进度。
		mob.getLookControl().setLookAt(position.x, pitch ? position.y : mob.getEyeY(), position.z,
				0.0F, pitch ? Math.abs(mob.getXRot()) : 0.0F);
		return aligned(mob.getYRot(), yaw) && aligned(mob.getYHeadRot(), yaw)
				&& aligned(mob.yBodyRot, yaw) && (!pitch || aligned(mob.getXRot(), targetPitch));
	}

	/// 将以脚部为基准的局部位置转为世界坐标；X 向左、Y 向上、Z 向前，单位为格。
	/// 随实体缩放和身体朝向变换，俯仰绕眼部高度旋转，不读取模型或动画。
	public static Vec3 localPosition(LivingEntity entity, Vec3 position) {
		Vec3 offset = position.scale(entity.getScale()).subtract(0.0, entity.getEyeHeight(), 0.0);
		return entity.getEyePosition().add(offset.xRot(entity.getXRot() * Mth.DEG_TO_RAD)
				.yRot(-entity.yBodyRot * Mth.DEG_TO_RAD));
	}

	private static boolean aligned(float current, float target) {
		return Math.abs(Mth.wrapDegrees(current - target)) <= ANGLE_EPSILON;
	}
}
