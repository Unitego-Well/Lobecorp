package org.unitego.lobecorp.registry.entity;

import net.minecraft.world.entity.EntityType;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.ability.WardenSonicBoomAbility;
import org.unitego.lobecorp.conductor.ability.the_queen_of_hatred.TheQueenOfHatredRepelAbility;
import org.unitego.lobecorp.conductor.ability.the_queen_of_hatred.TheQueenOfHatredSweepAbility;
import org.unitego.lobecorp.event.RegisterConductorAbilitiesEvent;
import org.unitego.lobecorp.registry.entity_skill.SweeperSkills;
import org.unitego.lobecorp.registry.entity_skill.TheQueenOfHatredSkills;

public class RegisterConductorAbilities {
	public static void register(RegisterConductorAbilitiesEvent event) {
		event.register(OrdealEntityTypes.SWEEPER.get(), SweeperSkills.ATTACK.getId(),
				() -> new EntitySkillConductorAbility(SweeperSkills.ATTACK.get()));
		event.register(OrdealEntityTypes.SWEEPER.get(), SweeperSkills.LEAP.getId(),
				() -> new EntitySkillConductorAbility.Leap(SweeperSkills.LEAP.get()));
		event.register(OrdealEntityTypes.SWEEPER.get(), SweeperSkills.SWEEP.getId(),
				() -> new EntitySkillConductorAbility(SweeperSkills.SWEEP.get()));
		event.register(OrdealEntityTypes.SWEEPER.get(), SweeperSkills.REASSEMBLE.getId(),
				() -> new EntitySkillConductorAbility(SweeperSkills.REASSEMBLE.get()));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.REPEL.getId(),
				() -> new TheQueenOfHatredRepelAbility(TheQueenOfHatredSkills.REPEL.get()));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.SWEEP.getId(),
				() -> new TheQueenOfHatredSweepAbility(TheQueenOfHatredSkills.SWEEP.get()));
		event.register(EntityType.WARDEN, WardenSonicBoomAbility.ID, WardenSonicBoomAbility::new);
	}
}
