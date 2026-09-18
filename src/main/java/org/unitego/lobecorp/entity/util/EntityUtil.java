package org.unitego.lobecorp.entity.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class EntityUtil {
	/// 从攻击者眼睛位置沿视线方向，计算射线与目标 AABB 的交点。
	public static Optional<Vec3> getHitPosOnAABB(LivingEntity attacker, Entity target) {
		Vec3 eyePos = attacker.getEyePosition();
		Vec3 lookVec = attacker.getLookAngle();
		AABB bb = target.getBoundingBox();
		double dist = eyePos.distanceTo(target.position()) + bb.getXsize() + bb.getYsize() + bb.getZsize();
		return bb.clip(eyePos, eyePos.add(lookVec.scale(dist)));
	}
}
