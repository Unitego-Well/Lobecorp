package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill;

import net.minecraft.server.level.ServerLevel;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.skill.EntitySkill;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.util.entity.skill.QueenSkillUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.world.hitbox.geometry.CylinderSize;
import org.unitego.lobecorp.world.hitbox.HitboxHitPolicy;
import org.unitego.lobecorp.world.hitbox.HitboxInstance;
import org.unitego.lobecorp.world.hitbox.HitboxManager;
import org.unitego.lobecorp.world.hitbox.HitboxTemplate;
import org.unitego.lobecorp.particle.QueenMagicCircleParticleOptions;
import org.unitego.lobecorp.util.TypedDataKey;

import java.util.List;

/// 前摇最后五刻锁定落点，随后对柱体内全部敌人造成伤害。
public class PillarOfLightSkill extends EntitySkill<TheQueenOfHatred> {
	/// 实体或位置目标的最大施法距离，单位为格。
	public static final double RANGE = 20.0;
	/// 命中所需水平半径和垂直半径，单位为格。
	public static final double RADIUS = 1.5;
	/// 相对女皇攻击属性的伤害倍率。
	private static final double DAMAGE_MULTIPLIER = 3.0;
	/// 前摇结束前停止追踪的时间，单位为 tick。
	private static final int LOCK_BEFORE_ACTIVATION_TICKS = 5;
	/// 光柱闪光持续时长，单位为 tick。
	private static final int FLASH_TICKS = 10;
	/// 落空或取消后的冷却，单位为 tick。
	public static final int SHORT_COOLDOWN_TICKS = 60;
	/// 本次施放开始时选择的目标，和前摇锁定的落点。
	private static final TypedDataKey<List<LivingEntity>> TARGETS = TypedDataKey.create();
	private static final TypedDataKey<List<Vec3>> POSITIONS = TypedDataKey.create();
	private static final HitboxTemplate HITBOX = new HitboxTemplate(new CylinderSize(RADIUS, RADIUS * 2.0),
			target -> target instanceof LivingEntity living && living.isAlive(), context -> {
		if (!(context.source() instanceof TheQueenOfHatred queen)
				|| !(context.target() instanceof LivingEntity target) || !queen.isHatedTarget(target))
			return false;
		Vec3 offset = target.position().subtract(context.instance().position());
		if (offset.horizontalDistanceSqr() > RADIUS * RADIUS || Math.abs(offset.y) > RADIUS)
			return false;
		boolean hurt = target.hurtServer(context.level(), queen.damageSources().mobAttack(queen),
				(float) (queen.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_MULTIPLIER));
		if (hurt && context.instance().skillRuntime() != null)
			context.instance().skillRuntime().markSuccessful();
		return hurt;
	});

	public PillarOfLightSkill(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canUse(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return QueenSkillUtil.canTarget(queen, runtime, RANGE);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		QueenSkillUtil.startSpell(queen);
		runtime.setCooldownTicks(SHORT_COOLDOWN_TICKS);
		if (queen.level() instanceof ServerLevel)
			runtime.setData(TARGETS, QueenSkillUtil.targets(queen, runtime, RANGE));
	}

	@Override
	public void onWindupTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
		if (runtime.elapsedTicks() < windupTicks() - LOCK_BEFORE_ACTIVATION_TICKS || runtime.hasData(POSITIONS))
			return;
		List<LivingEntity> targets = runtime.getData(TARGETS);
		if (targets != null) {
			List<Vec3> positions = targets.isEmpty() ? QueenSkillUtil.targetPositions(queen, runtime, RANGE)
					: targets.stream().filter(target -> QueenSkillUtil.validTarget(queen, target, RANGE))
					.map(LivingEntity::position).toList();
			runtime.setData(POSITIONS, positions);
			if (queen.level() instanceof ServerLevel level) {
				for (Vec3 position : positions)
					QueenMagicCircleParticleOptions.send(level, position, RADIUS,
							LOCK_BEFORE_ACTIVATION_TICKS, false);
			}
		}
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(queen.level() instanceof ServerLevel level))
			return;
		List<Vec3> positions = runtime.getData(POSITIONS);
		if (positions == null)
			return;
		for (Vec3 position : positions) {
			HitboxInstance hitbox = HitboxManager.create(HITBOX, level, position, HitboxManager.CURRENT_TICK_DURATION, runtime);
			hitbox.setSource(queen);
			hitbox.setHitPolicy(HitboxHitPolicy.ONCE_PER_TARGET);
			hitbox.activate();
			QueenMagicCircleParticleOptions.send(level, position, RADIUS, FLASH_TICKS, true);
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
		runtime.setCooldownTicks(runtime.isSuccessful() ? cooldownTicks() : SHORT_COOLDOWN_TICKS);
		queen.stopActionAnimation();
	}

	@Override
	public void onCancel(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		runtime.setCooldownTicks(SHORT_COOLDOWN_TICKS);
		queen.stopActionAnimation();
	}
}
