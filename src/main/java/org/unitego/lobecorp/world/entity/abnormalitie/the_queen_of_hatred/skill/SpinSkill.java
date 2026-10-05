package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredAnim;
import org.unitego.lobecorp.world.entity.projectile.MagicStarProjectile;
import org.unitego.lobecorp.world.entity.skill.EntitySkill;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.world.hitbox.HitboxInstance;
import org.unitego.lobecorp.world.hitbox.geometry.CylinderSize;
import org.unitego.lobecorp.world.hitbox.HitboxLineOfSightMode;
import org.unitego.lobecorp.world.hitbox.HitboxManager;
import org.unitego.lobecorp.world.hitbox.HitboxTemplate;
import org.unitego.lobecorp.world.hitbox.geometry.SectorCylinderSize;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;

/// 女皇回旋：结算一圈横扫近战，并向四周发射中型追踪星星。
public class SpinSkill extends EntitySkill<TheQueenOfHatred> {
	/// 回旋覆盖完整一周，单位为度。
	private static final double FULL_CIRCLE_DEGREES = 360.0;
	/// 一阶段向四周均匀发射的星星数量。
	private static final int PHASE_ONE_STAR_COUNT = 8;
	/// 二阶段向四周均匀发射的星星数量。
	private static final int PHASE_TWO_STAR_COUNT = 12;
	/// 星星出生位置相对女皇的水平偏移，单位为格。
	private static final double STAR_SPAWN_OFFSET = 0.8;
	/// 星星出生位置相对女皇脚部的高度，单位为格。
	private static final double STAR_SPAWN_HEIGHT = 1.2;
	/// 仅扩大横扫模板的角度，保留其高度、伤害、击退和目标判定。
	private static final HitboxTemplate HITBOX = new HitboxTemplate(
			new CylinderSize(SweepSkill.MELEE_RANGE,
					((SectorCylinderSize) SweepSkill.meleeHitboxTemplate().initialSize()).height()),
			SweepSkill.meleeHitboxTemplate().targetFilter(), SweepSkill.meleeHitboxTemplate().effect());

	public SpinSkill(Properties properties) {
		super(properties);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.lockSkillFacing();
		queen.playActionAnimation(TheQueenOfHatredAnim.SPIN);
	}

	@Override
	public void onWindupTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(queen.level() instanceof ServerLevel level))
			return;
		HitboxInstance hitbox = HitboxManager.create(HITBOX, level, queen.position(),
				HitboxManager.CURRENT_TICK_DURATION, runtime);
		hitbox.follow(queen, new Vec3(0.0, queen.getBbHeight() / 2.0, 0.0), false);
		hitbox.setLineOfSightMode(HitboxLineOfSightMode.UNRESTRICTED);
		hitbox.activate();
		int count = queen.isPhaseTwo() ? PHASE_TWO_STAR_COUNT : PHASE_ONE_STAR_COUNT;
		Vec3 forward = Vec3.directionFromRotation(0.0F, queen.getYRot());
		for (int index = 0; index < count; index++) {
			Vec3 direction = forward.yRot((float) Math.toRadians(FULL_CIRCLE_DEGREES * index / count));
			Vec3 origin = queen.position().add(0.0, STAR_SPAWN_HEIGHT, 0.0)
					.add(direction.scale(STAR_SPAWN_OFFSET));
			MagicStarProjectile star = new MagicStarProjectile(AbnormalitieEntityTypes.MAGIC_HOMING_STAR.get(), level);
			star.configure(queen, MagicStarProjectile.StarSize.MEDIUM, MagicStarProjectile.StarVariant.A, origin, direction);
			level.addFreshEntity(star);
		}
	}

	@Override
	public void onTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
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
