package org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity_skill.EntitySkill;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime;

/// 女皇传送到已选择的安全位置，路径可以越过方块，落点必须容纳实体。
public class TeleportSkill extends EntitySkill<TheQueenOfHatred> {
	/// 传送最大三维距离，单位为格。
	public static final double MAXIMUM_DISTANCE = 30.0;
	/// 检查脚下支撑的向下偏移，单位为格。
	private static final double GROUND_CHECK_OFFSET = 0.05;

	public TeleportSkill(Properties properties) {
		super(properties);
	}

	/// AI、HUD 和激活阶段共用安全检查，不加载未加载的区块。
	public static boolean isSafeDestination(Mob mob, Vec3 position) {
		Level level = mob.level();
		AABB bounds = mob.getBoundingBox().move(position.subtract(mob.position()));
		return mob.position().distanceToSqr(position) <= MAXIMUM_DISTANCE * MAXIMUM_DISTANCE
				&& level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(position.x), SectionPos.blockToSectionCoord(position.z))
				&& level.getWorldBorder().isWithinBounds(bounds)
				&& level.noCollision(mob, bounds) && !level.containsAnyLiquid(bounds)
				&& !level.noCollision(mob, bounds.move(0.0, -GROUND_CHECK_OFFSET, 0.0));
	}

	@Override
	public boolean canUse(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return !queen.isPassenger() && runtime.targetPosition() != null
				&& isSafeDestination(queen, runtime.targetPosition());
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.playActionAnimation(TheQueenOfHatredAnim.TELEPORT);
		queen.lockSkillFacing();
	}

	@Override
	public void onWindupTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		Vec3 destination = runtime.targetPosition();
		if (!(queen.level() instanceof ServerLevel) || destination == null || !isSafeDestination(queen, destination)) return;
		queen.getNavigation().stop();
		queen.setDeltaMovement(Vec3.ZERO);
		queen.teleportTo(destination.x, destination.y, destination.z);
		queen.resetFallDistance();
		runtime.markSuccessful();
	}

	@Override
	public void onTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
	}

	@Override
	public void onCancel(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
	}
}
