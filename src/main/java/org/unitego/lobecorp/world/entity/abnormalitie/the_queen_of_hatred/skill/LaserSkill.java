package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredAnim;
import org.unitego.lobecorp.world.entity.skill.EntitySkill;
import org.unitego.lobecorp.world.entity.skill.EntitySkillAccess;
import org.unitego.lobecorp.world.entity.skill.EntitySkillManualControl;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.world.hitbox.geometry.CylinderSize;
import org.unitego.lobecorp.world.hitbox.HitboxEffectContext;
import org.unitego.lobecorp.world.hitbox.HitboxHitMode;
import org.unitego.lobecorp.world.hitbox.HitboxHitPolicy;
import org.unitego.lobecorp.world.hitbox.HitboxInstance;
import org.unitego.lobecorp.world.hitbox.HitboxLineOfSightMode;
import org.unitego.lobecorp.world.hitbox.HitboxManager;
import org.unitego.lobecorp.world.hitbox.HitboxTemplate;
import org.unitego.lobecorp.particle.QueenChannelLaserParticleOptions;
import org.unitego.lobecorp.util.entity.EntityFacingUtil;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.util.entity.skill.QueenSkillUtil;
import org.unitego.lobecorp.util.TypedDataKey;

/// 绑定女皇法杖的持续激光；强化只改变转速，接触敌人的累计 tick 与实际伤害成功分开记录。
public class LaserSkill extends EntitySkill<TheQueenOfHatred> {
	/// 开始施放时所选目标的最小距离，单位为格；不限制光束沿途伤害。
	public static final double MINIMUM_RANGE = 7.0;
	/// 激光射程和施放目标最大距离，单位为格。
	public static final double RANGE = 20.0;
	/// 激光完整直径，单位为格，强化后不改变。
	public static final double WIDTH = 1.5;
	/// 每次伤害相对攻击属性的倍率，强化后不改变。
	private static final double DAMAGE_MULTIPLIER = 0.5;
	/// 发射后首次接触敌人的最长等待时间，单位为 tick。
	private static final int FIRST_HIT_TIMEOUT_TICKS = 60;
	/// 触发强化所需累计接触 tick；间断只暂停累计，不清零。
	private static final int ENHANCEMENT_CONTACT_TICKS = 20;
	/// 未强化时采用默认头身和俯仰转速的比例。
	private static final float INITIAL_TURN_RATIO = 0.8F;
	/// 强化后采用默认转速的比例。
	private static final float ENHANCED_TURN_RATIO = 0.5F;
	/// 激光允许的最大上下瞄准角度，单位为度。
	private static final float MAXIMUM_PITCH = 45.0F;
	/// 取消或首次命中超时后的冷却，单位为 tick。
	public static final int SHORT_COOLDOWN_TICKS = 60;
	/// 当前持续命中框编号及累计接触时间，均属于本次施放。
	private static final TypedDataKey<Integer> HITBOX_ID = TypedDataKey.create();
	private static final TypedDataKey<Integer> CONTACT_TICKS = TypedDataKey.create();
	private static final TypedDataKey<Long> LAST_CONTACT_TIME = TypedDataKey.create();
	private static final HitboxHitPolicy HIT_POLICY = new HitboxHitPolicy(HitboxHitMode.EVERY_TICK, 0, -1, -1);
	private static final HitboxTemplate HITBOX = new HitboxTemplate(new CylinderSize(WIDTH / 2.0, RANGE),
			target -> target instanceof LivingEntity living && living.isAlive(), LaserSkill::hit);

	public LaserSkill(Properties properties) {
		super(properties);
	}

	public static Vec3 origin(Mob mob) {
		return QueenSkillUtil.staffOrigin(mob);
	}

	public static Vec3 endpoint(Mob mob, Vec3 position) {
		return QueenSkillUtil.beamEndpoint(mob, position, RANGE);
	}

	/// 位置目标和同地面高度的实体默认平射；高低处实体目标限制为上下 45 度。
	public static Vec3 aimPosition(Mob mob, @Nullable LivingEntity target, Vec3 position) {
		Vec3 start = origin(mob);
		Vec3 offset = position.subtract(start);
		float pitch = target != null && !Mth.equal(target.getY(), mob.getY())
				? Mth.clamp((float) (-Mth.atan2(offset.y, offset.horizontalDistance()) * Mth.RAD_TO_DEG),
				-MAXIMUM_PITCH, MAXIMUM_PITCH) : 0.0F;
		return start.add(Vec3.directionFromRotation(pitch, EntityFacingUtil.yaw(offset, mob.getYRot())).scale(RANGE));
	}

	public static void enableManualControl(EntitySkillRuntime<?> runtime) {
		EntitySkillManualControl.prepare(runtime);
	}

	/// 更新已有手动施放的目标，不重新施放、不推进生命周期，也不改变冷却。
	public static void updateManualAim(Mob mob, @Nullable LivingEntity target, Vec3 position, boolean following) {
		EntitySkillAccess access = EntitySkillAccess.get(mob);
		if (access == null)
			return;
		for (EntitySkillRuntime<?> runtime : access.activeSkills()) {
			EntitySkillManualControl control = EntitySkillManualControl.get(runtime);
			if (runtime.skill() instanceof LaserSkill && control != null
					&& runtime.state() != EntitySkillRuntime.SkillState.RECOVERY) {
				control.update(target, position, following);
			}
		}
	}

	public static void releaseManualControl(Mob mob) {
		EntitySkillAccess access = EntitySkillAccess.get(mob);
		if (access == null)
			return;
		for (EntitySkillRuntime<?> runtime : access.activeSkills()) {
			EntitySkillManualControl control = EntitySkillManualControl.get(runtime);
			if (runtime.skill() instanceof LaserSkill && control != null)
				control.release();
		}
	}

	private static boolean aim(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime, float ratio) {
		EntitySkillManualControl control = EntitySkillManualControl.get(runtime);
		Vec3 direction = control == null ? null : control.direction();
		if (direction != null)
			return EntityFacingUtil.aim(queen, origin(queen).add(direction), origin(queen), true, ratio);
		Vec3 position = EntityFacingUtil.targetPosition(queen, runtime, true);
		if (position == null)
			return true;
		boolean aligned = EntityFacingUtil.aim(queen, aimPosition(queen, runtime.target(LivingEntity.class), position),
				origin(queen), true, ratio);
		queen.setXRot(Mth.clamp(queen.getXRot(), -MAXIMUM_PITCH, MAXIMUM_PITCH));
		return aligned;
	}

	private static boolean hit(HitboxEffectContext context) {
		if (!(context.source() instanceof TheQueenOfHatred queen)
				|| !(context.target() instanceof LivingEntity target) || !queen.isHatedTarget(target))
			return false;
		EntitySkillRuntime<?> runtime = context.instance().skillRuntime();
		if (runtime == null || runtime.state() != EntitySkillRuntime.SkillState.ACTIVE)
			return false;
		long time = context.level().getGameTime();
		Long previous = runtime.getData(LAST_CONTACT_TIME);
		if (previous == null || previous != time) {
			Integer ticks = runtime.getData(CONTACT_TICKS);
			runtime.setData(CONTACT_TICKS, ticks == null ? 1 : ticks + 1);
			runtime.setData(LAST_CONTACT_TIME, time);
		}
		boolean hurt = target.hurtServer(context.level(), queen.damageSources().mobAttack(queen),
				(float) (queen.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_MULTIPLIER));
		if (hurt)
			runtime.markSuccessful();
		return hurt;
	}

	@Override
	public boolean canUse(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = QueenSkillUtil.primaryTarget(queen, runtime, RANGE);
		if (target != null)
			return queen.distanceToSqr(target) >= MINIMUM_RANGE * MINIMUM_RANGE;
		Vec3 position = runtime.targetPosition();
		return QueenSkillUtil.validTargetPosition(queen, runtime, RANGE) && position != null
				&& queen.position().distanceToSqr(position) >= MINIMUM_RANGE * MINIMUM_RANGE;
	}

	private static boolean aim(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		Integer ticks = runtime.getData(CONTACT_TICKS);
		float ratio = ticks != null && ticks >= ENHANCEMENT_CONTACT_TICKS ? ENHANCED_TURN_RATIO : INITIAL_TURN_RATIO;
		boolean aligned = aim(queen, runtime, ratio);
		queen.lockSkillFacing();
		return aligned;
	}

	@Override
	public boolean prepareAim(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return aim(queen, runtime, INITIAL_TURN_RATIO);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		runtime.setCooldownTicks(cooldownTicks());
		queen.playActionAnimation(queen.isPhaseTwo() ? TheQueenOfHatredAnim.AIM_2 : TheQueenOfHatredAnim.AIM);
		aim(queen, runtime);
	}

	@Override
	public void onWindupTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		aim(queen, runtime);
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(queen.level() instanceof ServerLevel level))
			return;
		HitboxInstance hitbox = HitboxManager.create(HITBOX, level, origin(queen), durationTicks(), runtime);
		hitbox.setSource(queen);
		hitbox.setHitPolicy(HIT_POLICY);
		hitbox.setLineOfSightMode(HitboxLineOfSightMode.UNRESTRICTED);
		runtime.setData(HITBOX_ID, hitbox.id());
		updateBeam(queen, hitbox);
		Vec3 start = origin(queen);
		level.sendParticles(new QueenChannelLaserParticleOptions(queen.getId(), runtime.id(), level.getGameTime(), durationTicks()),
				start.x, start.y, start.z, 1, 0.0, 0.0, 0.0, 0.0);
	}

	private static void updateBeam(TheQueenOfHatred queen, HitboxInstance hitbox) {
		Vec3 start = origin(queen);
		Vec3 beam = endpoint(queen, start.add(queen.getLookAngle())).subtract(start);
		if (beam.lengthSqr() == 0.0) {
			hitbox.deactivate();
			return;
		}
		hitbox.setPosition(start.add(beam.scale(0.5)));
		hitbox.setSize(new CylinderSize(WIDTH / 2.0, beam.length()));
		hitbox.setRotation(new Vec3(queen.getXRot() + 90.0, -queen.getYRot(), 0.0));
		hitbox.activate();
	}

	@Override
	public void onTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!runtime.hasData(CONTACT_TICKS) && runtime.activeTicks() >= FIRST_HIT_TIMEOUT_TICKS) {
			runtime.setCooldownTicks(SHORT_COOLDOWN_TICKS);
			EntitySkillUtil.endSkill(queen, this);
			return;
		}
		aim(queen, runtime);
		Integer id = runtime.getData(HITBOX_ID);
		if (queen.level() instanceof ServerLevel level && id != null) {
			HitboxInstance hitbox = HitboxManager.get(level, id);
			if (hitbox != null)
				updateBeam(queen, hitbox);
		}
	}

	@Override
	public void onEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		Integer id = runtime.removeData(HITBOX_ID);
		if (queen.level() instanceof ServerLevel level && id != null)
			HitboxManager.remove(level, id);
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
	}

	@Override
	public void onCancel(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		runtime.setCooldownTicks(SHORT_COOLDOWN_TICKS);
		queen.stopActionAnimation();
	}
}
