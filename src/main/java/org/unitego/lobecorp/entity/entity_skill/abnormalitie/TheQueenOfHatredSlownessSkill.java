package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredMagicCircle;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.hitbox.CylinderSize;
import org.unitego.lobecorp.hitbox.HitboxInstance;
import org.unitego.lobecorp.hitbox.HitboxLineOfSightMode;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.hitbox.HitboxTemplate;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.util.TypedDataKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;
import static org.unitego.lobecorp.hitbox.HitboxManager.CURRENT_TICK_DURATION;

/// 迟缓：在锁定位置展开法阵并对范围内敌对目标施加缓慢。
public class TheQueenOfHatredSlownessSkill extends TheQueenOfHatredSpellSkill {
	/// AI 选择迟缓法术的概率。
	public static final float AI_CAST_CHANCE = 0.25F;
	/// 能够锁定攻击目标的最大距离。
	private static final double USE_RANGE = 10.0;
	/// 使用距离的平方缓存。
	private static final double USE_RANGE_SQUARED = USE_RANGE * USE_RANGE;
	/// 法阵圆柱判定半径。
	private static final double EFFECT_RADIUS = 5.0;
	/// 法阵圆柱判定总高度。
	private static final double EFFECT_HEIGHT = 1.0;
	/// 二级缓慢使用的原版增幅值。
	private static final int SLOWNESS_AMPLIFIER = 1;
	/// 缓慢效果持续时间。
	private static final int SLOWNESS_DURATION_TICKS = 5 * TICKS_PER_SECOND;
	/// 迟缓法术的独立冷却时间。
	public static final int COOLDOWN_TICKS = 20 * TICKS_PER_SECOND;
	/// 本次施法创建的判断框编号。
	private static final TypedDataKey<List<Integer>> HITBOX_IDS = TypedDataKey.create();
	/// 本次施法创建的法阵实体编号。
	private static final TypedDataKey<List<Integer>> MAGIC_CIRCLE_IDS = TypedDataKey.create();
	private static final TypedDataKey<List<LivingEntity>> TARGETS = TypedDataKey.create();
	/// 迟缓法阵判断框模板。
	private static final HitboxTemplate HITBOX_TEMPLATE = new HitboxTemplate(
			new CylinderSize(EFFECT_RADIUS, EFFECT_HEIGHT),
			target -> target instanceof LivingEntity,
			context -> {
				if (!(context.source() instanceof TheQueenOfHatred entity)) {
					return false;
				}
				if (!(context.target() instanceof LivingEntity target) || !entity.isValidTarget(target)) {
					return false;
				}
				return target.addEffect(new MobEffectInstance(
						MobEffects.SLOWNESS, SLOWNESS_DURATION_TICKS, SLOWNESS_AMPLIFIER), entity);
			}
	);

	/// @param properties 迟缓法术的基础技能配置
	public TheQueenOfHatredSlownessSkill(Properties properties) {
		super(properties);
	}

	@Override
	public float planningChance() {
		return AI_CAST_CHANCE;
	}

	@Override
	public TheQueenOfHatredAttackMode attackMode() {
		return TheQueenOfHatredAttackMode.LONG_RANGE_CAST;
	}

	@Override
	public double minimumPlanningRange() {
		return 5.0;
	}

	@Override
	public double maximumPlanningRange() {
		return USE_RANGE;
	}

	@Override
	public boolean prefersDenseTarget() {
		return true;
	}

	@Override
	public boolean canUse(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = entity.getAttackTarget();
		if (target == null || !entity.isValidTarget(target)) {
			return false;
		}
		if (entity.distanceToSqr(target) > USE_RANGE_SQUARED) {
			return false;
		}
		runtime.setTarget(target);
		runtime.setData(TARGETS, selectPhaseTargets(entity, target, USE_RANGE, false));
		return true;
	}

	@Override
	public void onWindupStart(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onWindupStart(entity, runtime);
		List<LivingEntity> targets = runtime.getData(TARGETS);
		if (targets == null || targets.isEmpty()) {
			return;
		}
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		List<Integer> hitboxIds = new ArrayList<>();
		List<Integer> circleIds = new ArrayList<>();
		for (LivingEntity target : targets) {
			Vec3 circlePosition = target.position();
			Vec3 hitboxPosition = circlePosition.add(0.0, EFFECT_HEIGHT / 2.0, 0.0);
			int lifetime = windupTicks() + CURRENT_TICK_DURATION;
			HitboxInstance hitbox = HitboxManager.create(HITBOX_TEMPLATE, level, hitboxPosition, lifetime);
			hitbox.setSource(entity);
			hitbox.appendTargetFilter(entity::isValidTarget);
			hitbox.setLineOfSightMode(HitboxLineOfSightMode.CENTER_TO_ENTITY);
			hitboxIds.add(hitbox.id());
			TheQueenOfHatredMagicCircle circle = new TheQueenOfHatredMagicCircle(
					AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED_MAGIC_CIRCLE.get(), level);
			circle.setPos(circlePosition);
			level.addFreshEntity(circle);
			circleIds.add(circle.getId());
		}
		runtime.setData(HITBOX_IDS, List.copyOf(hitboxIds));
		runtime.setData(MAGIC_CIRCLE_IDS, List.copyOf(circleIds));
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onActivate(entity, runtime);
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		List<LivingEntity> locked = runtime.getData(TARGETS);
		List<LivingEntity> targets = refreshPhaseTargets(entity, locked == null ? List.of() : locked,
				USE_RANGE, false);
		List<Integer> circleIds = runtime.getData(MAGIC_CIRCLE_IDS);
		List<HitboxInstance> hitboxes = getHitboxes(level, runtime);
		for (int index = 0; index < targets.size() && index < hitboxes.size(); index++) {
			Vec3 circlePosition = targets.get(index).position();
			hitboxes.get(index).setPosition(circlePosition.add(0.0, EFFECT_HEIGHT / 2.0, 0.0));
			if (circleIds != null && index < circleIds.size()
					&& level.getEntity(circleIds.get(index)) instanceof TheQueenOfHatredMagicCircle circle) {
				circle.setPos(circlePosition);
			}
		}
		for (HitboxInstance hitbox : getHitboxes(level, runtime)) {
			hitbox.activate();
			hitbox.setRemainingTicks(CURRENT_TICK_DURATION);
		}
	}

	@Override
	public void onTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onEnd(entity, runtime);
		removeMagicCircle(entity, runtime);
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onRecoveryEnd(entity, runtime);
		removeHitbox(entity, runtime);
		removeMagicCircle(entity, runtime);
	}

	@Override
	public void onCancel(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onCancel(entity, runtime);
		removeHitbox(entity, runtime);
		removeMagicCircle(entity, runtime);
	}

	private List<HitboxInstance> getHitboxes(ServerLevel level, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		List<Integer> ids = runtime.getData(HITBOX_IDS);
		if (ids == null) {
			return List.of();
		}
		return ids.stream().map(id -> HitboxManager.get(level, id)).filter(Objects::nonNull).toList();
	}

	private void removeHitbox(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		List<Integer> ids = runtime.removeData(HITBOX_IDS);
		if (ids == null) {
			return;
		}
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ids.forEach(id -> HitboxManager.remove(level, id));
	}

	private void removeMagicCircle(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		List<Integer> ids = runtime.removeData(MAGIC_CIRCLE_IDS);
		if (ids == null) {
			return;
		}
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		for (Integer id : ids) {
			if (level.getEntity(id) instanceof TheQueenOfHatredMagicCircle circle) {
				circle.discard();
			}
		}
	}
}
