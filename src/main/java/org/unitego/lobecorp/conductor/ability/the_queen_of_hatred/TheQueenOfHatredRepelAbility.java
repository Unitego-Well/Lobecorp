package org.unitego.lobecorp.conductor.ability.the_queen_of_hatred;

import net.minecraft.world.entity.Mob;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.RepelSkill;

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
