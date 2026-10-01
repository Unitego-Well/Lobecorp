package org.unitego.lobecorp.conductor.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.conductor.control.ConductorController;
import org.unitego.lobecorp.conductor.control.ConductorWork;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.projectile.MagicStarProjectile;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity_skill.EntitySkillAccess;
import org.unitego.lobecorp.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity_skill.MultiStageSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.LaserSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.StarBeamSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.DashSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.DamageReductionSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.PurificationSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.SlownessSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.MarkSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.StarfallSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.PillarOfLightSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.BlinkSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.TeleportSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.RefractionSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.ConvergentSkill;
import org.unitego.lobecorp.entity_skill.skill.sweeper.AttackSkill;
import org.unitego.lobecorp.entity_skill.skill.sweeper.LeapSkill;
import org.unitego.lobecorp.entity_skill.skill.sweeper.ReassembleSkill;
import org.unitego.lobecorp.entity_skill.skill.sweeper.SweepSkill;
import org.unitego.lobecorp.util.EntitySkillUtil;
import org.unitego.lobecorp.util.QueenSkillUtil;

import java.util.List;

import static org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.SweepSkill.MELEE_RANGE;

/// 将实体技能注册项适配为指挥家可用能力。
public class EntitySkillConductorAbility implements ConductorAbility {
	protected final IEntitySkill<?> skill;

	public EntitySkillConductorAbility(IEntitySkill<?> skill) {
		this.skill = skill;
	}

	@Override
	public Identifier id() {
		return skill.id();
	}

	@Override
	public boolean supports(Mob mob) {
		EntitySkillAccess access = EntitySkillAccess.get(mob);
		return access != null && access.supports(skill);
	}

	@Override
	public TargetKind targetKind() {
		return skill instanceof AttackSkill ? TargetKind.POSITION : TargetKind.SELF;
	}

	@Override
	public boolean directional() {
		return skill instanceof AttackSkill;
	}

	@Override
	public int cooldownTicks(Mob mob) {
		EntitySkillAccess access = EntitySkillAccess.get(mob);
		return access == null ? EntitySkillUtil.cooldownTicks(mob, skill) : access.cooldownTicks(skill);
	}

	public boolean isCasting(Mob mob) {
		return EntitySkillUtil.isCasting(mob, skill);
	}

	/// HUD 与服务端共享当前施放阶段的接招资格，不以“存在运行实例”直接禁止。
	public boolean canBeginCast(Mob mob) {
		EntitySkillAccess access = EntitySkillAccess.get(mob);
		return access != null && access.canBeginCast(skill);
	}

	@Override
	public int totalCooldownTicks(Mob mob) {
		return skill instanceof MultiStageSkill<?> multiStage ? multiStage.sequenceCooldownTicks()
				: skill.cooldownTicks();
	}

	@Override
	public Display display(Mob mob) {
		boolean support = skill instanceof SweepSkill || skill instanceof ReassembleSkill
				|| skill instanceof DamageReductionSkill || skill instanceof PurificationSkill;
		boolean noDamage = support || skill instanceof SlownessSkill || skill instanceof MarkSkill;
		return new Display(noDamage ? DamageKind.NONE : DamageKind.DYNAMIC, 0.0D,
				support ? AbilityKind.SUPPORT : skill.isBasicAttack() ? AbilityKind.BASIC : AbilityKind.ATTACK);
	}

	@Override
	public boolean isAvailable(Mob mob) {
		return !(skill instanceof PurificationSkill)
				|| mob instanceof TheQueenOfHatred queen && PurificationSkill.needsPurification(queen);
	}

	@Override
	public double displayRange(Mob mob) {
		return skill instanceof AttackSkill ? AttackSkill.conductorRange() : ConductorAbility.super.displayRange(mob);
	}

	@Override
	public PreviewGeometry previewGeometry(Mob mob, Vec3 requestedPosition, Vec3 effectivePosition) {
		if (skill instanceof AttackSkill) return new PreviewGeometry(List.of(), List.of(), List.of(
				new PreviewSector(mob.position(), AttackSkill.direction(mob, requestedPosition),
						AttackSkill.conductorRange(), AttackSkill.conductorAngle())));
		return ConductorAbility.super.previewGeometry(mob, requestedPosition, effectivePosition);
	}

	@Override
	public boolean cast(Mob mob, @Nullable Entity target, @Nullable Vec3 position) {
		if (mob instanceof Sweeper sweeper && (skill instanceof SweepSkill || skill instanceof ReassembleSkill)) {
			return ConductorWork.start(sweeper, skill instanceof ReassembleSkill);
		}
		return ConductorController.cast(mob, skill, target, position);
	}

	/// 瞬步按 HUD 所选方向冲刺，属于无伤害的辅助技能。
	public static class Blink extends EntitySkillConductorAbility {
		public Blink(IEntitySkill<?> skill) {
			super(skill);
		}

		@Override
		public TargetKind targetKind() {
			return TargetKind.POSITION;
		}

		@Override
		public boolean directional() {
			return true;
		}

		@Override
		public RangeMetric rangeMetric() {
			return RangeMetric.HORIZONTAL;
		}

		@Override
		public double maximumRange(Mob mob) {
			return BlinkSkill.MAXIMUM_DISTANCE;
		}

		@Override
		public Display display(Mob mob) {
			return new Display(DamageKind.NONE, 0.0D, AbilityKind.SUPPORT);
		}

		@Override
		public PreviewGeometry previewGeometry(Mob mob, Vec3 requestedPosition, Vec3 effectivePosition) {
			Vec3 endpoint = new Vec3(effectivePosition.x, mob.getY(), effectivePosition.z);
			return new PreviewGeometry(List.of(new PreviewCircle(endpoint, mob.getBbWidth() / 2.0)),
					List.of(new PreviewBeam(mob.position(), endpoint, mob.getBbWidth() / 2.0)));
		}
	}

	/// 传送只选择安全落点，HUD 与技能激活阶段共用落点检查。
	public static class Teleport extends EntitySkillConductorAbility {
		public Teleport(IEntitySkill<?> skill) {
			super(skill);
		}

		@Override
		public TargetKind targetKind() {
			return TargetKind.POSITION;
		}

		@Override
		public RangeMetric rangeMetric() {
			return RangeMetric.DISTANCE;
		}

		@Override
		public double maximumRange(Mob mob) {
			return TeleportSkill.MAXIMUM_DISTANCE;
		}

		@Override
		public Display display(Mob mob) {
			return new Display(DamageKind.NONE, 0.0D, AbilityKind.SUPPORT);
		}

		@Override
		public boolean canTargetPosition(Mob mob, Vec3 position) {
			return TeleportSkill.isSafeDestination(mob, position);
		}

		@Override
		public PreviewGeometry previewGeometry(Mob mob, Vec3 requestedPosition, Vec3 effectivePosition) {
			return new PreviewGeometry(List.of(new PreviewCircle(effectivePosition, mob.getBbWidth() / 2.0)), List.of());
		}
	}

	/// 冲刺复用瞬步的距离和方向预览，仅二阶段可用。
	public static class Dash extends Blink {
		public Dash(IEntitySkill<?> skill) {
			super(skill);
		}

		@Override
		public double maximumRange(Mob mob) {
			return DashSkill.MAXIMUM_DISTANCE;
		}

		@Override
		public boolean isAvailable(Mob mob) {
			return mob instanceof TheQueenOfHatred queen && queen.isPhaseTwo();
		}

		@Override
		public Display display(Mob mob) {
			return new Display(DamageKind.DYNAMIC, 0.0D, AbilityKind.ATTACK);
		}
	}

	/// 折射复用远程目标规则，预览小型星星初次发射的直线路径。
	public static class Refraction extends StarBeam {
		public Refraction(IEntitySkill<?> skill) {
			super(skill);
		}

		@Override
		public double maximumRange(Mob mob) {
			return RefractionSkill.range();
		}

		@Override
		public PreviewGeometry previewGeometry(Mob mob, Vec3 requestedPosition, Vec3 effectivePosition) {
			Vec3 direction = requestedPosition.subtract(mob.getEyePosition()).normalize();
			Vec3 origin = mob.getEyePosition().add(direction.scale(mob.getBbWidth()));
			return new PreviewGeometry(List.of(), List.of(new PreviewBeam(origin,
					origin.add(direction.scale(maximumRange(mob))), MagicStarProjectile.StarSize.SMALL.width() / 2.0)));
		}
	}

	/// 聚爆同时预览吸引范围与最终爆炸范围。
	public static class Convergent extends EntitySkillConductorAbility {
		public Convergent(IEntitySkill<?> skill) {
			super(skill);
		}

		@Override
		public PreviewShape previewShape() {
			return PreviewShape.AREA;
		}

		@Override
		public double previewRadius(Mob mob) {
			return ConvergentSkill.PULL_RADIUS;
		}

		@Override
		public PreviewGeometry previewGeometry(Mob mob, Vec3 requestedPosition, Vec3 effectivePosition) {
			return new PreviewGeometry(List.of(new PreviewCircle(mob.position(), ConvergentSkill.PULL_RADIUS),
					new PreviewCircle(mob.position(), ConvergentSkill.EXPLOSION_RADIUS)), List.of());
		}
	}

	/// 星束接受实体目标或方向位置，预览和实际施放共用方块截断终点。
	public static class StarBeam extends EntitySkillConductorAbility {
		public StarBeam(IEntitySkill<?> skill) {
			super(skill);
		}

		@Override
		public TargetKind targetKind() {
			return TargetKind.EITHER;
		}

		@Override
		public boolean directional() {
			return true;
		}

		@Override
		public RangeMetric rangeMetric() {
			return RangeMetric.DISTANCE;
		}

		@Override
		public double maximumRange(Mob mob) {
			return StarBeamSkill.RANGE;
		}

		@Override
		public boolean canTarget(Mob mob, LivingEntity target) {
			return mob instanceof TheQueenOfHatred queen && target.isAlive() && queen.isHatedTarget(target);
		}

		@Override
		public PreviewGeometry previewGeometry(Mob mob, Vec3 requestedPosition, Vec3 effectivePosition) {
			return new PreviewGeometry(List.of(), List.of(new PreviewBeam(
					StarBeamSkill.origin(mob), StarBeamSkill.endpoint(mob, requestedPosition), StarBeamSkill.WIDTH / 2.0)));
		}
	}

	/// 女皇法术共用选择范围、敌我判断和落点预览；标记仍须选择实体。
	public static class QueenTargeted extends EntitySkillConductorAbility {
		private final double range;
		private final double radius;

		public QueenTargeted(IEntitySkill<?> skill, double range, double radius) {
			super(skill);
			this.range = range;
			this.radius = radius;
		}

		@Override
		public TargetKind targetKind() {
			return skill instanceof MarkSkill ? TargetKind.ENTITY : TargetKind.EITHER;
		}

		@Override
		public RangeMetric rangeMetric() {
			return RangeMetric.DISTANCE;
		}

		@Override
		public double maximumRange(Mob mob) {
			return range;
		}

		@Override
		public double previewRadius(Mob mob) {
			return skill instanceof SlownessSkill || skill instanceof StarfallSkill || skill instanceof PillarOfLightSkill
					? range : radius;
		}

		@Override
		public boolean canTarget(Mob mob, LivingEntity target) {
			return mob instanceof TheQueenOfHatred queen && QueenSkillUtil.validTarget(queen, target, range);
		}

		@Override
		public Vec3 targetPosition(Mob mob, LivingEntity target, Vec3 position) {
			return target == null ? position : target.position();
		}

		@Override
		public PreviewGeometry previewGeometry(Mob mob, Vec3 requestedPosition, Vec3 effectivePosition) {
			return radius > 0.0 ? new PreviewGeometry(List.of(new PreviewCircle(effectivePosition, radius)), List.of())
					: PreviewGeometry.EMPTY;
		}
	}

	/// 持续激光接受 7～20 格的实体或位置目标，预览实际直径和被方块截断的路径。
	public static class Laser extends QueenTargeted {
		public Laser(IEntitySkill<?> skill) {
			super(skill, LaserSkill.RANGE, LaserSkill.WIDTH / 2.0);
		}

		@Override
		public double previewRadius(Mob mob) {
			return LaserSkill.RANGE;
		}

		public void updateAim(Mob mob, @Nullable LivingEntity target, Vec3 position, boolean following) {
			ConductorController.updateLaserAim(mob, skill, target, position, following);
		}

		@Override
		public boolean directional() {
			return true;
		}

		@Override
		public boolean canTarget(Mob mob, LivingEntity target) {
			return super.canTarget(mob, target) && mob.distanceToSqr(target) >= LaserSkill.MINIMUM_RANGE * LaserSkill.MINIMUM_RANGE;
		}

		@Override
		public boolean canTargetPosition(Mob mob, Vec3 position) {
			return mob.position().distanceToSqr(position) >= LaserSkill.MINIMUM_RANGE * LaserSkill.MINIMUM_RANGE;
		}

		@Override
		public Vec3 targetPosition(Mob mob, LivingEntity target, Vec3 position) {
			return target == null ? position : target.getBoundingBox().getCenter();
		}

		@Override
		public PreviewGeometry previewGeometry(Mob mob, Vec3 requestedPosition, Vec3 effectivePosition) {
			return new PreviewGeometry(List.of(new PreviewCircle(mob.position(), LaserSkill.MINIMUM_RANGE)), List.of(new PreviewBeam(
					LaserSkill.origin(mob), LaserSkill.endpoint(mob, effectivePosition), LaserSkill.WIDTH / 2.0)));
		}
	}

	/// 回旋以施法者为中心显示完整近战圆形，不需要选择方向或目标。
	public static class Spin extends EntitySkillConductorAbility {
		public Spin(IEntitySkill<?> skill) {
			super(skill);
		}

		@Override
		public PreviewShape previewShape() {
			return PreviewShape.AREA;
		}

		@Override
		public double previewRadius(Mob mob) {
			return MELEE_RANGE;
		}
	}

	public static class Leap extends EntitySkillConductorAbility {
		public Leap(IEntitySkill<?> skill) {
			super(skill);
		}

		@Override
		public TargetKind targetKind() {
			return TargetKind.EITHER;
		}

		@Override
		public RangeMetric rangeMetric() {
			return RangeMetric.HORIZONTAL;
		}

		@Override
		public double maximumRange(Mob mob) {
			return LeapSkill.conductorMaximumRange();
		}

		@Override
		public Vec3 targetPosition(Mob mob, LivingEntity target, Vec3 position) {
			return target.position();
		}

		@Override
		public PreviewGeometry previewGeometry(Mob mob, Vec3 requestedPosition, Vec3 effectivePosition) {
			return new PreviewGeometry(List.of(new PreviewCircle(effectivePosition,
					LeapSkill.conductorDamageRadius())), List.of());
		}

		@Override
		public boolean isAvailable(Mob mob) {
			return mob instanceof Sweeper sweeper && sweeper.onGround();
		}

		@Override
		public boolean canTarget(Mob mob, LivingEntity target) {
			return mob instanceof Sweeper sweeper && target.isAlive() && sweeper.isValidTarget(target)
					&& !sweeper.isWithinMeleeAttackRange(target);
		}
	}
}
