package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredAnim;
import org.unitego.lobecorp.world.entity.skill.EntitySkill;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.world.hitbox.geometry.CylinderSize;
import org.unitego.lobecorp.world.hitbox.HitboxHitPolicy;
import org.unitego.lobecorp.world.hitbox.HitboxInstance;
import org.unitego.lobecorp.world.hitbox.HitboxLineOfSightMode;
import org.unitego.lobecorp.world.hitbox.HitboxManager;
import org.unitego.lobecorp.world.hitbox.HitboxTemplate;
import org.unitego.lobecorp.util.entity.EntityFacingUtil;
import org.unitego.lobecorp.util.entity.skill.QueenSkillUtil;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;
import org.unitego.lobecorp.util.TypedDataKey;

/// 女皇远程瞄准后发射瞬时星束，方块截断而敌人不截断光束。
public class StarBeamSkill extends EntitySkill<TheQueenOfHatred> {
	/// 星束的最大射程，单位为格。
	public static final double RANGE = 15.0;
	/// 星束完整宽度，单位为格。
	public static final double WIDTH = 0.5;
	/// 发射时锁定的伤害，供当前 tick 的命中框结算。
	private static final TypedDataKey<Float> DAMAGE = TypedDataKey.create();
	/// 星束沿圆柱轴穿透所有有效敌人，每个目标只结算一次。
	private static final HitboxTemplate HITBOX = new HitboxTemplate(new CylinderSize(WIDTH / 2.0, RANGE),
			target -> target instanceof LivingEntity living && living.isAlive(),
			context -> {
				if (!(context.source() instanceof TheQueenOfHatred queen)
						|| !(context.target() instanceof LivingEntity target) || !queen.isHatedTarget(target))
					return false;
				Float damage = context.instance().getData(DAMAGE);
				if (damage == null || !target.hurtServer(context.level(), queen.damageSources().mobAttack(queen), damage))
					return false;
				EntitySkillRuntime<?> runtime = context.instance().skillRuntime();
				if (runtime != null)
					runtime.markSuccessful();
				return true;
			});

	public StarBeamSkill(Properties properties) {
		super(properties);
	}

	/// 服务端命中框、HUD 和一次性粒子共用法杖起点，不依赖客户端动画数据。
	public static Vec3 origin(Mob mob) {
		return QueenSkillUtil.staffOrigin(mob);
	}

	/// 返回被方块截断后的终点，供实际伤害和指挥家预览共用。
	public static Vec3 endpoint(Mob mob, Vec3 position) {
		return QueenSkillUtil.beamEndpoint(mob, position, RANGE);
	}

	@Override
	public boolean canUse(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target == null && runtime.targetPosition() == null) {
			target = queen.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
			if (target != null)
				runtime.setTarget(target);
		}
		return target != null ? target.isAlive() && queen.isHatedTarget(target)
				&& queen.hasLineOfSight(target) && queen.distanceToSqr(target) <= RANGE * RANGE
				: runtime.targetPosition() != null;
	}

	private static void aim(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		EntityFacingUtil.aim(queen, EntityFacingUtil.targetPosition(queen, runtime, true), origin(queen), true);
		queen.lockSkillFacing();
	}

	@Override
	public boolean prepareAim(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return EntityFacingUtil.aim(queen, EntityFacingUtil.targetPosition(queen, runtime, true), origin(queen), true);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		aim(queen, runtime);
		queen.playActionAnimation(queen.isPhaseTwo() ? TheQueenOfHatredAnim.AIM_2 : TheQueenOfHatredAnim.AIM);
	}

	@Override
	public void onWindupTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		aim(queen, runtime);
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(queen.level() instanceof ServerLevel level))
			return;
		aim(queen, runtime);
		Vec3 start = origin(queen);
		Vec3 end = endpoint(queen, start.add(queen.getLookAngle()));
		float damage = (float) queen.getAttributeValue(Attributes.ATTACK_DAMAGE);
		Vec3 beam = end.subtract(start);
		if (beam.lengthSqr() > 0.0) {
			HitboxInstance hitbox = HitboxManager.create(HITBOX, level, start.add(beam.scale(0.5)),
					HitboxManager.CURRENT_TICK_DURATION, runtime);
			hitbox.setSource(queen);
			hitbox.setSize(new CylinderSize(WIDTH / 2.0, beam.length()));
			hitbox.setRotation(new Vec3(queen.getXRot() + 90.0, -queen.getYRot(), 0.0));
			hitbox.setHitPolicy(HitboxHitPolicy.ONCE_PER_TARGET);
			hitbox.setLineOfSightMode(HitboxLineOfSightMode.UNRESTRICTED);
			hitbox.setData(DAMAGE, damage);
			hitbox.activate();
		}
		level.sendParticles(LcParticleTypes.QUEEN_LASER.get(), start.x, start.y, start.z,
				0, beam.x, beam.y, beam.z, 1.0);
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
