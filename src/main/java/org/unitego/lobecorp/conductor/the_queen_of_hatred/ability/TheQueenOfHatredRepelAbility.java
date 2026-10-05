package org.unitego.lobecorp.conductor.the_queen_of_hatred.ability;

import net.minecraft.world.entity.Mob;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.RepelSkill;

public class TheQueenOfHatredRepelAbility extends EntitySkillConductorAbility {
	public TheQueenOfHatredRepelAbility(IEntitySkill<?> skill) {
		super(skill);
	}

	@Override
	public PreviewShape previewShape() {
		return PreviewShape.AREA;
	}

	@Override
	public double previewRadius(Mob mob) {
		return RepelSkill.conductorPreviewRadius();
	}
}
