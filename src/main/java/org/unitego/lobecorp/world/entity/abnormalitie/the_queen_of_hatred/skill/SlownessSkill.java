package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill;

import net.minecraft.server.level.ServerLevel;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.skill.EntitySkill;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.util.entity.skill.QueenSkillUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.world.hitbox.HitboxHitPolicy;
import org.unitego.lobecorp.world.hitbox.HitboxInstance;
import org.unitego.lobecorp.world.hitbox.HitboxManager;
import org.unitego.lobecorp.world.hitbox.HitboxTemplate;
import org.unitego.lobecorp.world.hitbox.geometry.SphereSize;
import org.unitego.lobecorp.particle.QueenMagicCircleParticleOptions;

/// 在锁定位置展开法阵，对圈内全部敌人施加缓慢。
public class SlownessSkill extends EntitySkill<TheQueenOfHatred> {
	/// 实体或位置目标的最大施法距离，单位为格。
	public static final double RANGE = 20.0;
	/// 每个法阵的效果半径，单位为格。
	public static final double RADIUS = 5.0;
	/// 缓慢持续五秒，单位为游戏 tick。
	private static final int EFFECT_TICKS = 100;
	/// 原版放大器从零计数，1 对应缓慢 II。
	private static final int AMPLIFIER = 1;
	private static final HitboxTemplate HITBOX = new HitboxTemplate(new SphereSize(RADIUS),
			target -> target instanceof LivingEntity living && living.isAlive(), context -> {
		if (!(context.source() instanceof TheQueenOfHatred queen)
				|| !(context.target() instanceof LivingEntity target) || !queen.isHatedTarget(target))
			return false;
		boolean applied = target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EFFECT_TICKS, AMPLIFIER));
		if (applied && context.instance().skillRuntime() != null)
			context.instance().skillRuntime().markSuccessful();
		return applied;
	});

	public SlownessSkill(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canUse(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return QueenSkillUtil.canTarget(queen, runtime, RANGE);
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(queen.level() instanceof ServerLevel level))
			return;
		for (Vec3 position : QueenSkillUtil.targetPositions(queen, runtime, RANGE)) {
			HitboxInstance hitbox = HitboxManager.create(HITBOX, level, position, HitboxManager.CURRENT_TICK_DURATION, runtime);
			hitbox.setSource(queen);
			hitbox.setHitPolicy(HitboxHitPolicy.ONCE_PER_TARGET);
			hitbox.activate();
			QueenMagicCircleParticleOptions.send(level, position, RADIUS, EFFECT_TICKS, false);
		}
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		QueenSkillUtil.startSpell(queen);
	}

	@Override
	public void onWindupTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
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
