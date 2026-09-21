package org.unitego.lobecorp.entity.ai.control;

import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAttackPlanner;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredAttackMode;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredSkill;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.entity_skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.registry.tag.LcEntitySkillTags;

import java.util.Comparator;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 统一仲裁憎恶皇后的威胁响应、近战反击、位移和普通技能计划。
public class TheQueenOfHatredCombatController {
	/// 二阶段每轮进入规避回合的概率。
	private static final float SECOND_PHASE_DODGE_CHANCE = 0.1F;
	/// 常规攻击规划的刷新间隔，单位为 tick。
	private static final int NORMAL_PLAN_INTERVAL_TICKS = 5;
	/// 接近敌对生物威胁的扫描间隔，单位为 tick。
	private static final int HOSTILE_THREAT_SCAN_INTERVAL_TICKS = 5;
	/// 接近投射物威胁的扫描间隔，单位为 tick。
	private static final int PROJECTILE_THREAT_SCAN_INTERVAL_TICKS = 2;
	/// 战斗目标重新评估间隔，单位为 tick。
	private static final int TARGET_REFRESH_INTERVAL_TICKS = 20;
	/// AI 超出预算时各类扫描间隔的倍率。
	private static final int DEGRADED_INTERVAL_MULTIPLIER = 2;
	/// 没有有效命中时结束攻击回合所需的完整技能释放次数。
	private static final int ATTACK_ROUND_REQUIRED_RELEASES = 2;
	/// 接近敌对生物威胁的搜索半径，单位为格。
	private static final double HOSTILE_THREAT_SEARCH_RANGE = 12.0;
	/// 接近投射物威胁的搜索半径，单位为格。
	private static final double PROJECTILE_THREAT_SEARCH_RANGE = 8.0;
	/// 生物被视为快速逼近威胁所需的最低相对速度。
	private static final double THREAT_MINIMUM_APPROACH_SPEED = 0.35;
	/// 威胁碰撞预测的最大提前量，单位为 tick。
	private static final int THREAT_PREDICTION_TICKS = TICKS_PER_SECOND / 2;
	/// 连续瞬步达到该次数后升级为传送规避。
	private static final int THREAT_BLINK_LIMIT = 3;
	/// 持续安全达到该时间后重置连续规避状态，单位为 tick。
	private static final int THREAT_SAFE_RESET_TICKS = TICKS_PER_SECOND;
	/// 规避意图提交失败后的重试间隔，单位为 tick。
	private static final int THREAT_REPOSITION_RETRY_TICKS = 5;
	/// 允许优先近战反击的最大距离，单位为格。
	private static final double THREAT_MELEE_COUNTERATTACK_RANGE = 4.0;
	/// 威胁预测对女皇碰撞体增加的安全余量，单位为格。
	private static final double THREAT_COLLISION_PADDING = 0.75;
	/// 单次 AI 步骤进入降级判定的平均耗时预算，单位为纳秒。
	private static final long AI_COST_BUDGET_NANOS = 500_000L;
	/// 降级状态解除所需低于的平均耗时，单位为纳秒。
	private static final long AI_COST_RECOVERY_NANOS = 250_000L;
	/// 连续超出预算达到该采样数后进入降级状态。
	private static final int AI_COST_OVER_BUDGET_SAMPLES = 20;
	/// 连续低于恢复预算达到该采样数后退出降级状态。
	private static final int AI_COST_RECOVERY_SAMPLES = 100;
	/// AI 耗时指数移动平均使用的右移位数。
	private static final int AI_COST_AVERAGE_SHIFT = 3;
	/// 接近敌对目标扫描的性能分析区段。
	private static final String PROFILER_HOSTILE_THREAT_SCAN = "queenHostileThreatScan";
	/// 接近投射物扫描的性能分析区段。
	private static final String PROFILER_PROJECTILE_THREAT_SCAN = "queenProjectileThreatScan";
	/// 紧急威胁响应的性能分析区段。
	private static final String PROFILER_THREAT_RESPONSE = "queenThreatResponse";
	/// 常规攻击规划的性能分析区段。
	private static final String PROFILER_ATTACK_PLANNING = "queenAttackPlanning";

	private final TheQueenOfHatred queen;
	private final TheQueenOfHatredAttackPlanner attackPlanner;
	@Nullable
	private LivingEntity hostileThreat;
	@Nullable
	private Projectile projectileThreat;
	private boolean pendingThreatDodge;
	private int consecutiveThreatBlinks;
	private int threatSafeTicks;
	private int threatRepositionRetryTicks;
	private int normalPlanTicks;
	private int hostileThreatScanTicks;
	private int projectileThreatScanTicks;
	private int targetRefreshTicks;
	private long averageAiCostNanos;
	private int overBudgetSamples;
	private int recoverySamples;
	private boolean degraded;
	private CombatRound combatRound = CombatRound.WAITING;
	@Nullable
	private TheQueenOfHatredSkill pendingAttackSkill;
	private int completedAttackReleases;

	public TheQueenOfHatredCombatController(TheQueenOfHatred queen) {
		this.queen = queen;
		attackPlanner = new TheQueenOfHatredAttackPlanner(queen);
	}

	public void updateThreatSnapshot() {
		ProfilerFiller profiler = Profiler.get();
		if (hostileThreatScanTicks-- <= 0) {
			hostileThreatScanTicks = scaledInterval(HOSTILE_THREAT_SCAN_INTERVAL_TICKS) - 1;
			profiler.push(PROFILER_HOSTILE_THREAT_SCAN);
			hostileThreat = approachingHostileThreat();
			profiler.pop();
		}
		if (projectileThreatScanTicks-- <= 0) {
			projectileThreatScanTicks = scaledInterval(PROJECTILE_THREAT_SCAN_INTERVAL_TICKS) - 1;
			profiler.push(PROFILER_PROJECTILE_THREAT_SCAN);
			projectileThreat = approachingProjectileThreat();
			profiler.pop();
		}
		if (hostileThreat != null || projectileThreat != null) {
			threatSafeTicks = 0;
			return;
		}
		if (++threatSafeTicks >= THREAT_SAFE_RESET_TICKS) {
			consecutiveThreatBlinks = 0;
			pendingThreatDodge = false;
			threatRepositionRetryTicks = 0;
		}
	}

	public boolean tick() {
		if (combatRound == CombatRound.ATTACK) {
			return tickAttackRound();
		}
		if (combatRound == CombatRound.DODGE) {
			return tickDodgeRoundProfiled();
		}
		if (hostileThreat != null || projectileThreat != null || pendingThreatDodge) {
			if (queen.isSecondPhase() && queen.getRandom().nextFloat() >= SECOND_PHASE_DODGE_CHANCE) {
				completeThreatDodge();
				return tickAttackRound();
			}
			combatRound = CombatRound.DODGE;
			return tickDodgeRoundProfiled();
		}
		if (EntitySkillManager.hasActiveSkills(queen)) {
			return false;
		}
		if (normalPlanTicks > 0) {
			normalPlanTicks--;
			return false;
		}
		normalPlanTicks = NORMAL_PLAN_INTERVAL_TICKS - 1;
		ProfilerFiller profiler = Profiler.get();
		profiler.push(PROFILER_ATTACK_PLANNING);
		TheQueenOfHatredCombatIntent intent = attackPlanner.plan();
		boolean handled = execute(intent);
		profiler.pop();
		if (handled && intent.type() == TheQueenOfHatredCombatIntent.Type.CAST) {
			beginAttackRound(intent.skill());
		}
		return handled;
	}

	private boolean tickDodgeRoundProfiled() {
		ProfilerFiller profiler = Profiler.get();
		profiler.push(PROFILER_THREAT_RESPONSE);
		boolean handled = tickDodgeRound();
		profiler.pop();
		return handled;
	}

	private boolean tickDodgeRound() {
		if (EntitySkillManager.hasActiveSkills(queen)) {
			return false;
		}
		if (hostileThreat == null && projectileThreat == null && !pendingThreatDodge) {
			beginAttackRound(null);
			return tickAttackRound();
		}
		return tickThreatResponse();
	}

	private boolean tickAttackRound() {
		if (completedAttackReleases >= ATTACK_ROUND_REQUIRED_RELEASES) {
			finishAttackRound();
			return false;
		}
		if (EntitySkillManager.hasActiveSkills(queen)) {
			return false;
		}
		if (queen.getAttackTarget() == null) {
			finishAttackRound();
			return false;
		}
		ProfilerFiller profiler = Profiler.get();
		profiler.push(PROFILER_ATTACK_PLANNING);
		TheQueenOfHatredCombatIntent intent = attackPlanner.planAttackAfterDodge();
		boolean handled = execute(intent);
		profiler.pop();
		if (handled) {
			if (intent.type() == TheQueenOfHatredCombatIntent.Type.CAST) {
				trackAttackSkill(intent.skill());
			}
			return true;
		}
		finishAttackRound();
		return false;
	}

	public TheQueenOfHatredAttackMode mode() {
		return attackPlanner.mode();
	}

	public double minimumRange() {
		return attackPlanner.minimumRange();
	}

	public double maximumRange() {
		return attackPlanner.maximumRange();
	}

	public void requestEmergencyDodge(@Nullable LivingEntity threat) {
		if (threat != null && queen.isValidTarget(threat)) {
			hostileThreat = threat;
		}
		pendingThreatDodge = true;
		threatSafeTicks = 0;
	}

	public void recordAttackSkillCompleted(EntitySkillRuntime<?> runtime) {
		if (combatRound == CombatRound.ATTACK && runtime.skill() == pendingAttackSkill) {
			completedAttackReleases++;
			pendingAttackSkill = null;
		}
	}

	private boolean tickThreatResponse() {
		if (pendingThreatDodge && EntitySkillManager.hasActiveSkill(queen, LcEntitySkillTags.MELEE)) {
			return false;
		}
		if (threatRepositionRetryTicks > 0) {
			threatRepositionRetryTicks--;
			return false;
		}
		if (hostileThreat != null && !pendingThreatDodge && !EntitySkillManager.hasActiveSkills(queen)
				&& queen.distanceToSqr(hostileThreat) <= Mth.square(THREAT_MELEE_COUNTERATTACK_RANGE)) {
			TheQueenOfHatredCombatIntent counterattack = attackPlanner.planMeleeCounterattack(hostileThreat);
			if (execute(counterattack)) {
				pendingThreatDodge = true;
				return true;
			}
		}
		pendingThreatDodge = true;
		LivingEntity target = hostileThreat != null ? hostileThreat : queen.getAttackTarget();
		Vec3 projectileVelocity = projectileThreat == null ? null : projectileThreat.getDeltaMovement();
		if (consecutiveThreatBlinks >= THREAT_BLINK_LIMIT) {
			boolean cast = execute(attackPlanner.planThreatReposition(target, projectileVelocity, true));
			if (cast) {
				completeThreatDodge();
			} else {
				threatRepositionRetryTicks = THREAT_REPOSITION_RETRY_TICKS;
			}
			return cast;
		}
		if (EntitySkillManager.isOnCooldown(queen, TheQueenOfHatredSkills.BLINK.get())) {
			return false;
		}
		queen.requestThreatBlinkCooldown();
		if (execute(attackPlanner.planThreatReposition(target, projectileVelocity, false))) {
			consecutiveThreatBlinks++;
			completeThreatDodge();
			return true;
		}
		queen.clearThreatBlinkCooldownRequest();
		boolean cast = execute(attackPlanner.planThreatReposition(target, projectileVelocity, true));
		if (cast) {
			completeThreatDodge();
		} else {
			threatRepositionRetryTicks = THREAT_REPOSITION_RETRY_TICKS;
		}
		return cast;
	}

	private void completeThreatDodge() {
		pendingThreatDodge = false;
		threatRepositionRetryTicks = 0;
		beginAttackRound(null);
	}

	private void beginAttackRound(@Nullable TheQueenOfHatredSkill initialSkill) {
		combatRound = CombatRound.ATTACK;
		pendingAttackSkill = null;
		completedAttackReleases = 0;
		normalPlanTicks = 0;
		trackAttackSkill(initialSkill);
	}

	private void trackAttackSkill(@Nullable TheQueenOfHatredSkill skill) {
		pendingAttackSkill = skill;
	}

	private void finishAttackRound() {
		combatRound = CombatRound.WAITING;
		pendingAttackSkill = null;
		completedAttackReleases = 0;
		normalPlanTicks = 0;
	}

	private boolean execute(@Nullable TheQueenOfHatredCombatIntent intent) {
		if (intent == null || intent.skill() == null) {
			return false;
		}
		if (intent.target() != null && queen.isValidTarget(intent.target())) {
			queen.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, intent.target());
		}
		if (intent.type() == TheQueenOfHatredCombatIntent.Type.REPOSITION && intent.destination() != null) {
			queen.requestRepositionDestination(intent.destination());
			if (intent.skill() == TheQueenOfHatredSkills.BLINK.get()) {
				if (intent.retreat()) {
					queen.requestRetreatBlink();
				}
			} else if (intent.skill() == TheQueenOfHatredSkills.TELEPORT.get()) {
				queen.requestTeleport(intent.retreat());
			}
		}
		if (EntitySkillManager.cast(queen, intent.skill())) {
			return true;
		}
		if (intent.type() == TheQueenOfHatredCombatIntent.Type.REPOSITION) {
			queen.clearRepositionDestination();
			queen.clearTeleportRequest();
			queen.cancelRetreatBlinkRequest();
		}
		return false;
	}

	@Nullable
	private LivingEntity approachingHostileThreat() {
		AABB bounds = queen.getBoundingBox().inflate(HOSTILE_THREAT_SEARCH_RANGE);
		return queen.level().getEntitiesOfClass(LivingEntity.class, bounds, queen::isValidTarget).stream()
				.filter(target -> isPredictedThreat(target.position(), target.getDeltaMovement(),
						THREAT_MINIMUM_APPROACH_SPEED))
				.max(Comparator.comparingDouble(target ->
						approachSpeed(target.position(), target.getDeltaMovement())))
				.orElse(null);
	}

	@Nullable
	private Projectile approachingProjectileThreat() {
		AABB bounds = queen.getBoundingBox().inflate(PROJECTILE_THREAT_SEARCH_RANGE);
		return queen.level().getEntitiesOfClass(Projectile.class, bounds, projectile -> {
			Entity owner = projectile.getOwner();
			if (owner == queen || owner instanceof LivingEntity living && !queen.isValidTarget(living)) {
				return false;
			}
			return isPredictedThreat(projectile.position(), projectile.getDeltaMovement(), 0.0);
		}).stream().min(Comparator.comparingDouble(projectile ->
				predictedInterceptTicks(projectile.position(), projectile.getDeltaMovement()))).orElse(null);
	}

	private boolean isPredictedThreat(Vec3 threatPosition, Vec3 threatVelocity, double minimumSpeed) {
		Vec3 relativePosition = threatPosition.subtract(queen.getBoundingBox().getCenter());
		Vec3 relativeVelocity = threatVelocity.subtract(queen.getDeltaMovement());
		double speedSquared = relativeVelocity.lengthSqr();
		if (speedSquared < minimumSpeed * minimumSpeed || relativePosition.dot(relativeVelocity) >= 0.0) {
			return false;
		}
		double interceptTicks = predictedInterceptTicks(threatPosition, threatVelocity);
		Vec3 closest = relativePosition.add(relativeVelocity.scale(interceptTicks));
		double collisionRadius = Math.max(queen.getBbWidth(), queen.getBbHeight()) / 2.0
				+ THREAT_COLLISION_PADDING;
		return closest.lengthSqr() <= collisionRadius * collisionRadius;
	}

	private double predictedInterceptTicks(Vec3 threatPosition, Vec3 threatVelocity) {
		Vec3 relativePosition = threatPosition.subtract(queen.getBoundingBox().getCenter());
		Vec3 relativeVelocity = threatVelocity.subtract(queen.getDeltaMovement());
		double speedSquared = relativeVelocity.lengthSqr();
		return speedSquared <= Mth.square(Mth.EPSILON) ? THREAT_PREDICTION_TICKS
				: Mth.clamp(-relativePosition.dot(relativeVelocity) / speedSquared,
				0.0, THREAT_PREDICTION_TICKS);
	}

	private double approachSpeed(Vec3 threatPosition, Vec3 threatVelocity) {
		Vec3 relativePosition = threatPosition.subtract(queen.getBoundingBox().getCenter());
		if (relativePosition.lengthSqr() <= Mth.square(Mth.EPSILON)) {
			return Double.MAX_VALUE;
		}
		return -relativePosition.normalize().dot(threatVelocity.subtract(queen.getDeltaMovement()));
	}

	public boolean shouldRefreshTarget() {
		if (targetRefreshTicks-- > 0) {
			return false;
		}
		targetRefreshTicks = scaledInterval(TARGET_REFRESH_INTERVAL_TICKS) - 1;
		return true;
	}

	public int enemyAnalysisIntervalTicks() {
		return scaledInterval(TICKS_PER_SECOND);
	}

	public void recordAiStepCost(long elapsedNanos) {
		averageAiCostNanos += (elapsedNanos - averageAiCostNanos) >> AI_COST_AVERAGE_SHIFT;
		if (!degraded) {
			overBudgetSamples = averageAiCostNanos > AI_COST_BUDGET_NANOS ? overBudgetSamples + 1 : 0;
			if (overBudgetSamples >= AI_COST_OVER_BUDGET_SAMPLES) {
				degraded = true;
				recoverySamples = 0;
			}
			return;
		}
		recoverySamples = averageAiCostNanos < AI_COST_RECOVERY_NANOS ? recoverySamples + 1 : 0;
		if (recoverySamples >= AI_COST_RECOVERY_SAMPLES) {
			degraded = false;
			overBudgetSamples = 0;
		}
	}

	private int scaledInterval(int interval) {
		return degraded ? interval * DEGRADED_INTERVAL_MULTIPLIER : interval;
	}

	private enum CombatRound {
		WAITING,
		DODGE,
		ATTACK
	}
}
