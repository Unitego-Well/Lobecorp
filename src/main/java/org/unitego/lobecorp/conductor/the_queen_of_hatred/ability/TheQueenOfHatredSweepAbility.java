package org.unitego.lobecorp.conductor.the_queen_of_hatred.ability;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;

import java.util.List;

import static org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.SweepSkill.MELEE_RANGE;
import static org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.SweepSkill.conductorAngle;

public class TheQueenOfHatredSweepAbility extends EntitySkillConductorAbility {
	public TheQueenOfHatredSweepAbility(IEntitySkill<?> skill) {
		super(skill);
	}

	@Override
	public boolean directional() {
		return true;
	}

	@Override
	public double displayRange(Mob mob) {
		return MELEE_RANGE;
	}

	@Override
	public PreviewGeometry previewGeometry(Mob mob, Vec3 requestedPosition, Vec3 effectivePosition) {
		Vec3 offset = requestedPosition.subtract(mob.position()).multiply(1.0, 0.0, 1.0);
		Vec3 forward = offset.lengthSqr() > ConductorRules.FORMATION_DIRECTION_EPSILON
				? offset.normalize() : Vec3.directionFromRotation(0.0F, mob.getYRot());
		return new PreviewGeometry(List.of(), List.of(), List.of(
				new PreviewSector(mob.position(), forward, MELEE_RANGE, conductorAngle())));
	}

	@Override
	public TargetKind targetKind() {
		return TargetKind.POSITION;
	}
}
