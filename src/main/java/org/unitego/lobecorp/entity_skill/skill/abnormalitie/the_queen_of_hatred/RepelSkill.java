package org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity_skill.effect.EntitySkillEffect;
import org.unitego.lobecorp.entity_skill.effect.EntitySkillEffectDebugInfo;
import org.unitego.lobecorp.entity_skill.effect.EntitySkillEffectManager;
import org.unitego.lobecorp.hitbox.*;
import org.unitego.lobecorp.registry.effect.LcMobEffects;
import org.unitego.lobecorp.util.TypedDataKey;

/// 憎恶皇后的环形退散冲击波。
public class RepelSkill extends TheQueenOfHatredSkill {
	/// 技能运行结束后再次施放前的等待时间，单位为游戏刻。
	private static final int REUSE_DELAY_TICKS = 20;

	public RepelSkill(Properties properties) {
		super(properties);
	}

	public static int conductorReuseDelayTicks() {
		return REUSE_DELAY_TICKS;
	}

	public static double conductorPreviewRadius() {
		return RepelWaveEffect.MAXIMUM_WAVE_RADIUS;
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.playActionAnimation(TheQueenOfHatredAnim.DISPEL);
		queen.lockSkillFacing();
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (queen.level() instanceof ServerLevel level) {
			EntitySkillEffectManager.add(new RepelWaveEffect(queen, level, runtime));
		}
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
		queen.delayNextSkillCast(REUSE_DELAY_TICKS);
	}

	@Override
	public void onCancel(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
	}

	/// 憎恶皇后退散技能释放的脱手环形冲击波。
	public static class RepelWaveEffect extends EntitySkillEffect {
		private static final int WAVE_DURATION_TICKS = 20;
		private static final double INITIAL_WAVE_RADIUS = 1.0;
		private static final double MAXIMUM_WAVE_RADIUS = 10.0;
		private static final double WAVE_HEIGHT = 3.0;
		private static final double WAVE_RING_THICKNESS = 2.0;
		private static final double MINIMUM_WAVE_ATTENUATION = 0.1;
		private static final double WAVE_RADIUS_PER_TICK =
				(MAXIMUM_WAVE_RADIUS - INITIAL_WAVE_RADIUS) / (WAVE_DURATION_TICKS - 1);
		private static final TypedDataKey<RepelWaveEffect> WAVE_EFFECT = TypedDataKey.create();
		private static final HitboxTemplate HITBOX_TEMPLATE = new HitboxTemplate(
				new RingCylinderSize(INITIAL_WAVE_RADIUS, WAVE_HEIGHT, WAVE_RING_THICKNESS),
				target -> target instanceof LivingEntity,
				context -> {
					if (!(context.source() instanceof TheQueenOfHatred queen)
							|| !(context.target() instanceof LivingEntity target)) {
						return false;
					}
					RepelWaveEffect effect = context.instance().getData(WAVE_EFFECT);
					if (effect == null || !queen.isHatedTarget(target) || !effect.intersectsCurrentWave(target)) {
						return false;
					}
					effect.applyHit(queen, context.level(), target);
					return true;
				}
		);
		private final HitboxInstance hitbox;
		private double radius = INITIAL_WAVE_RADIUS;

		public RepelWaveEffect(TheQueenOfHatred queen, ServerLevel level,
		                       EntitySkillRuntime<TheQueenOfHatred> runtime) {
			super(level, queen.position(), WAVE_DURATION_TICKS, queen, false, null, false);
			hitbox = HitboxManager.create(HITBOX_TEMPLATE, level, position(), WAVE_DURATION_TICKS, runtime);
			hitbox.setSource(queen);
			hitbox.setEntitySkillEffectDebugInfo(new EntitySkillEffectDebugInfo(getClass().getSimpleName(),
					queen.getDisplayName().getString(), runtime.skill().id().toString(), WAVE_DURATION_TICKS));
			hitbox.setHitPolicy(HitboxHitPolicy.ONCE_PER_TARGET);
			hitbox.setLineOfSightMode(HitboxLineOfSightMode.UNRESTRICTED);
			hitbox.setData(WAVE_EFFECT, this);
		}

		@Override
		protected void tickEffect() {
			radius = Math.min(INITIAL_WAVE_RADIUS + (ageTicks() - 1) * WAVE_RADIUS_PER_TICK,
					MAXIMUM_WAVE_RADIUS);
			hitbox.setPosition(position());
			hitbox.setSize(new RingCylinderSize(radius, WAVE_HEIGHT, WAVE_RING_THICKNESS));
			hitbox.activate();
		}

		@Override
		protected void onRemoved() {
			HitboxManager.remove(level(), hitbox.id());
		}

		private boolean intersectsCurrentWave(LivingEntity target) {
			double distance = target.position().subtract(position()).horizontalDistance();
			double targetRadius = target.getBbWidth() * 0.5;
			double previousRadius = ageTicks() == 1 ? 0.0 : radius - WAVE_RADIUS_PER_TICK;
			return distance + targetRadius >= previousRadius && distance - targetRadius <= radius;
		}

		private void applyHit(TheQueenOfHatred queen, ServerLevel level, LivingEntity target) {
			Vec3 offset = target.position().subtract(position());
			double distance = offset.horizontalDistance();
			double attenuation = Math.max(MINIMUM_WAVE_ATTENUATION,
					1.0 - (1.0 - MINIMUM_WAVE_ATTENUATION) * distance / MAXIMUM_WAVE_RADIUS);
			float damage = (float) (queen.getAttributeValue(Attributes.ATTACK_DAMAGE)
					* 0.5 * attenuation);
			if (damage > 0.0F) {
				boolean isHurt = target.hurtServer(level, queen.damageSources().mobAttack(queen), damage);
				if (isHurt) {
					target.addEffect(new MobEffectInstance(LcMobEffects.STUN, 50, 2));
				}
			}
			Vec3 horizontalDirection = distance == 0.0
					? Vec3.directionFromRotation(0.0F, queen.getYRot())
					: new Vec3(offset.x, 0.0, offset.z).normalize();
			Vec3 currentMovement = target.getDeltaMovement();
			double v = 10.0; // 水平冲击倍率
			double v1 = 0.5; // 垂直击飞倍率
			target.setDeltaMovement(currentMovement.add(
					horizontalDirection.x * v * attenuation,
					v1 * attenuation,
					horizontalDirection.z * v * attenuation));
			target.hurtMarked = true;
		}
	}
}
