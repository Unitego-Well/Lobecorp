package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity.entity_skill.EntitySkill;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.util.TypedDataKey;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 憎恶皇后的环形退散冲击波。
public final class TheQueenOfHatredRepelSkill extends EntitySkill<TheQueenOfHatred> {
	/// 技能前摇，单位为游戏刻。
	public static final int WINDUP_TICKS = 44;
	/// 技能后摇，单位为游戏刻。
	public static final int RECOVERY_TICKS = 10;
	/// 技能运行结束后再次施放前的等待时间，单位为游戏刻。
	private static final int REUSE_DELAY_TICKS = TICKS_PER_SECOND;
	/// 冲击波最大半径，单位为格。
	private static final double MAXIMUM_WAVE_RADIUS = 10.0;
	/// 冲击波每游戏刻扩张的距离，单位为格。
	private static final double WAVE_RADIUS_PER_TICK = 0.2;
	/// 冲击波持续时间，单位为游戏刻。
	public static final int WAVE_DURATION_TICKS = (int) Math.ceil(MAXIMUM_WAVE_RADIUS / WAVE_RADIUS_PER_TICK);
	/// 基础伤害占女皇攻击伤害属性的比例。
	private static final double BASE_DAMAGE_RATIO = 0.5;
	/// 最大击退速度。
	private static final double MAXIMUM_KNOCKBACK = 1.0;
	/// 最大击飞速度。
	private static final double MAXIMUM_KNOCKUP = 0.3;
	/// 冲击波运行期间使用的中心与已命中目标集合。
	private static final TypedDataKey<WaveState> WAVE_STATE = TypedDataKey.create();

	public TheQueenOfHatredRepelSkill(Properties properties) {
		super(properties);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.lockSkillFacing();
		queen.triggerActionAnimation(TheQueenOfHatredAnim.DISPEL);
		if (queen.level() instanceof ServerLevel) {
			runtime.setData(WAVE_STATE, new WaveState(queen.position(), new HashSet<>()));
		}
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(queen.level() instanceof ServerLevel level)) {
			return;
		}
		WaveState state = runtime.getData(WAVE_STATE);
		if (state == null) {
			return;
		}
		double radius = Math.min(runtime.activeTicks() * WAVE_RADIUS_PER_TICK, MAXIMUM_WAVE_RADIUS);
		double previousRadius = Math.max(0.0, radius - WAVE_RADIUS_PER_TICK);
		AABB searchArea = new AABB(state.center(), state.center()).inflate(MAXIMUM_WAVE_RADIUS);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, searchArea,
				candidate -> candidate != queen && candidate.isAlive() && queen.isHatedTarget(candidate)
						&& !state.hitTargets().contains(candidate.getUUID()))) {
			Vec3 offset = target.position().subtract(state.center());
			double distance = offset.horizontalDistance();
			double targetRadius = target.getBbWidth() * 0.5;
			if (distance + targetRadius < previousRadius || distance - targetRadius > radius) {
				continue;
			}
			state.hitTargets().add(target.getUUID());
			applyHit(queen, level, target, offset, distance);
		}
	}

	@Override
	public void onEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		runtime.removeData(WAVE_STATE);
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopTriggeredActionAnimation();
		queen.delayNextSkillCast(REUSE_DELAY_TICKS);
	}

	@Override
	public void onCancel(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopTriggeredActionAnimation();
		runtime.removeData(WAVE_STATE);
	}

	private static void applyHit(TheQueenOfHatred queen, ServerLevel level, LivingEntity target,
			Vec3 offset, double distance) {
		double attenuation = Math.max(0.0, 1.0 - distance / MAXIMUM_WAVE_RADIUS);
		float damage = (float) (queen.getAttributeValue(Attributes.ATTACK_DAMAGE)
				* BASE_DAMAGE_RATIO * attenuation);
		if (damage > 0.0F) {
			target.hurtServer(level, queen.damageSources().mobAttack(queen), damage);
		}
		Vec3 horizontalDirection = new Vec3(offset.x, 0.0, offset.z).normalize();
		Vec3 currentMovement = target.getDeltaMovement();
		target.setDeltaMovement(currentMovement.add(
			horizontalDirection.x * MAXIMUM_KNOCKBACK * attenuation,
			MAXIMUM_KNOCKUP * attenuation,
			horizontalDirection.z * MAXIMUM_KNOCKBACK * attenuation));
		target.hurtMarked = true;
	}

	private record WaveState(Vec3 center, Set<UUID> hitTargets) {
	}
}
