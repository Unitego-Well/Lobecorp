package org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity_skill.EntitySkill;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.hitbox.HitboxEffectContext;
import org.unitego.lobecorp.hitbox.HitboxHitMode;
import org.unitego.lobecorp.hitbox.HitboxHitPolicy;
import org.unitego.lobecorp.hitbox.HitboxInstance;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.hitbox.HitboxPurpose;
import org.unitego.lobecorp.hitbox.HitboxTemplate;
import org.unitego.lobecorp.hitbox.SphereSize;
import org.unitego.lobecorp.particle.QueenConvergentParticleOptions;
import org.unitego.lobecorp.util.TypedDataKey;

/// 聚爆在持续阶段吸引敌人，六次强吸引结束后在女皇位置结算爆炸。
public class ConvergentSkill extends EntitySkill<TheQueenOfHatred> {
	/// 吸引范围半径，单位为格。
	public static final double PULL_RADIUS = 10.0;
	/// 最终爆炸的球形半径，单位为格。
	public static final double EXPLOSION_RADIUS = 4.0;
	/// 持续阶段均匀分布的强吸引次数。
	private static final int PULL_COUNT = 6;
	/// 每 tick 小吸引增加的水平速度，单位为格/tick。
	private static final double WEAK_PULL_SPEED = 0.05;
	/// 强吸引增加的三维速度，单位为格/tick。
	private static final double STRONG_PULL_SPEED = 0.5;
	/// 两阶段均采用原一阶段爆炸相对攻击属性的伤害倍率。
	private static final double DAMAGE_MULTIPLIER = 2.0;
	private static final TypedDataKey<Integer> PULL_HITBOX_ID = TypedDataKey.create();
	private static final HitboxHitPolicy CONTINUOUS_PULL_POLICY = new HitboxHitPolicy(HitboxHitMode.EVERY_TICK, 0, -1, -1);
	private static final HitboxTemplate WEAK_PULL = new HitboxTemplate(new SphereSize(PULL_RADIUS),
			target -> target instanceof LivingEntity, ConvergentSkill::applyWeakPull, HitboxPurpose.DETECTION);
	private static final HitboxTemplate STRONG_PULL = new HitboxTemplate(new SphereSize(PULL_RADIUS),
			target -> target instanceof LivingEntity, ConvergentSkill::applyStrongPull, HitboxPurpose.DETECTION);
	private static final HitboxTemplate EXPLOSION = new HitboxTemplate(new SphereSize(EXPLOSION_RADIUS),
			target -> target instanceof LivingEntity, ConvergentSkill::applyExplosion);

	public ConvergentSkill(Properties properties) {
		super(properties);
	}

	private static boolean applyWeakPull(HitboxEffectContext context) {
		return applyPull(context, WEAK_PULL_SPEED, false);
	}

	private static boolean applyStrongPull(HitboxEffectContext context) {
		return applyPull(context, STRONG_PULL_SPEED, true);
	}

	private static boolean applyPull(HitboxEffectContext context, double speed, boolean vertical) {
		if (!(context.source() instanceof TheQueenOfHatred queen)
				|| !(context.target() instanceof LivingEntity target) || !target.isAlive() || !queen.isHatedTarget(target)) return false;
		Vec3 direction = queen.getBoundingBox().getCenter().subtract(target.getBoundingBox().getCenter());
		if (!vertical) direction = direction.multiply(1.0, 0.0, 1.0);
		if (direction.lengthSqr() == 0.0) return false;
		target.setDeltaMovement(target.getDeltaMovement().add(direction.normalize().scale(speed)));
		target.hurtMarked = true;
		return true;
	}

	private static boolean applyExplosion(HitboxEffectContext context) {
		if (!(context.source() instanceof TheQueenOfHatred queen)
				|| !(context.target() instanceof LivingEntity target) || !target.isAlive() || !queen.isHatedTarget(target)) return false;
		return target.hurtServer(context.level(), queen.damageSources().mobAttack(queen),
				(float) (queen.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_MULTIPLIER));
	}

	private static HitboxInstance createHitbox(HitboxTemplate template, ServerLevel level,
	                                          TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime, int ticks) {
		HitboxInstance hitbox = HitboxManager.create(template, level, queen.position(), ticks, runtime);
		hitbox.follow(queen, new Vec3(0.0, queen.getBbHeight() / 2.0, 0.0), false);
		hitbox.activate();
		return hitbox;
	}

	private void sendVisual(ServerLevel level, TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime, boolean star) {
		level.sendParticles(new QueenConvergentParticleOptions(queen.getId(), runtime.id(),
				level.getGameTime(), durationTicks(), durationTicks() / PULL_COUNT, star),
				queen.getX(), queen.getY(), queen.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.playActionAnimation(TheQueenOfHatredAnim.SPELL);
		queen.lockSkillFacing();
	}

	@Override
	public void onWindupTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(queen.level() instanceof ServerLevel level)) return;
		sendVisual(level, queen, runtime, true);
		sendVisual(level, queen, runtime, false);
	}

	@Override
	public void onTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
		if (!(queen.level() instanceof ServerLevel level)) return;
		if (runtime.activeTicks() == 1) {
			HitboxInstance hitbox = createHitbox(WEAK_PULL, level, queen, runtime, durationTicks());
			hitbox.setHitPolicy(CONTINUOUS_PULL_POLICY);
			runtime.setData(PULL_HITBOX_ID, hitbox.id());
		}
		if (runtime.activeTicks() % (durationTicks() / PULL_COUNT) == 0) {
			createHitbox(STRONG_PULL, level, queen, runtime, HitboxManager.CURRENT_TICK_DURATION);
			if (runtime.activeTicks() < durationTicks()) sendVisual(level, queen, runtime, false);
		}
	}

	@Override
	public void onEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
		if (!(queen.level() instanceof ServerLevel level)) return;
		Integer id = runtime.removeData(PULL_HITBOX_ID);
		HitboxInstance hitbox = id == null ? null : HitboxManager.get(level, id);
		if (hitbox != null) hitbox.setRemainingTicks(HitboxManager.CURRENT_TICK_DURATION);
		createHitbox(EXPLOSION, level, queen, runtime, HitboxManager.CURRENT_TICK_DURATION);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, queen.getX(), queen.getY(), queen.getZ(),
				1, 0.0, 0.0, 0.0, 0.0);
		runtime.markSuccessful();
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
