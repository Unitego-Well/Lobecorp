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
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity_skill.EntitySkillAccess;
import org.unitego.lobecorp.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.RepelSkill;
import org.unitego.lobecorp.entity_skill.skill.sweeper.AttackSkill;
import org.unitego.lobecorp.entity_skill.skill.sweeper.LeapSkill;
import org.unitego.lobecorp.entity_skill.skill.sweeper.ReassembleSkill;
import org.unitego.lobecorp.entity_skill.skill.sweeper.SweepSkill;
import org.unitego.lobecorp.util.EntitySkillUtil;

import java.util.List;

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
		int skillRemaining = access == null ? EntitySkillUtil.cooldownTicks(mob, skill) : access.cooldownTicks(skill);
		return mob instanceof TheQueenOfHatred queen
				? Math.max(skillRemaining, queen.conductorSkillCooldownTicks()) : skillRemaining;
	}

	public boolean isCasting(Mob mob) {
		return EntitySkillUtil.isCasting(mob, skill);
	}

	@Override
	public int totalCooldownTicks(Mob mob) {
		return skill instanceof AttackSkill ? AttackSkill.conductorComboCooldownTicks()
				: skill instanceof RepelSkill ? Math.max(skill.cooldownTicks(), RepelSkill.conductorReuseDelayTicks())
				: skill.cooldownTicks();
	}

	@Override
	public Display display(Mob mob) {
		boolean support = skill instanceof SweepSkill || skill instanceof ReassembleSkill;
		return new Display(support ? DamageKind.NONE : DamageKind.DYNAMIC, 0.0D,
				support ? AbilityKind.SUPPORT : skill.isBasicAttack() ? AbilityKind.BASIC : AbilityKind.ATTACK);
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
