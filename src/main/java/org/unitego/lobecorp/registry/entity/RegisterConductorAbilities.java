package org.unitego.lobecorp.registry.entity;

import net.minecraft.world.entity.EntityType;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.ability.WardenSonicBoomAbility;
import org.unitego.lobecorp.conductor.the_queen_of_hatred.ability.TheQueenOfHatredRepelAbility;
import org.unitego.lobecorp.conductor.the_queen_of_hatred.ability.TheQueenOfHatredSweepAbility;
import org.unitego.lobecorp.event.RegisterConductorAbilitiesEvent;
import org.unitego.lobecorp.registry.entity.skill.SweeperSkills;
import org.unitego.lobecorp.registry.entity.skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.SlownessSkill;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.MarkSkill;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.StarfallSkill;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.PillarOfLightSkill;

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
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.ATTACK.getId(),
				() -> new TheQueenOfHatredSweepAbility(TheQueenOfHatredSkills.ATTACK.get()));
		event.register(EntityType.WARDEN, WardenSonicBoomAbility.ID, WardenSonicBoomAbility::new);
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.SPIN.getId(),
				() -> new EntitySkillConductorAbility.Spin(TheQueenOfHatredSkills.SPIN.get()));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.LASER.getId(),
				() -> new EntitySkillConductorAbility.Laser(TheQueenOfHatredSkills.LASER.get()));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.STAR_BEAM.getId(),
				() -> new EntitySkillConductorAbility.StarBeam(TheQueenOfHatredSkills.STAR_BEAM.get()));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.DAMAGE_REDUCTION.getId(),
				() -> new EntitySkillConductorAbility(TheQueenOfHatredSkills.DAMAGE_REDUCTION.get()));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.PURIFICATION.getId(),
				() -> new EntitySkillConductorAbility(TheQueenOfHatredSkills.PURIFICATION.get()));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.SLOWNESS.getId(),
				() -> new EntitySkillConductorAbility.QueenTargeted(TheQueenOfHatredSkills.SLOWNESS.get(), SlownessSkill.RANGE, SlownessSkill.RADIUS));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.MARK.getId(),
				() -> new EntitySkillConductorAbility.QueenTargeted(TheQueenOfHatredSkills.MARK.get(), MarkSkill.RANGE, 0.0));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.STARFALL.getId(),
				() -> new EntitySkillConductorAbility.QueenTargeted(TheQueenOfHatredSkills.STARFALL.get(), StarfallSkill.RANGE, StarfallSkill.RADIUS));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.PILLAR_OF_LIGHT.getId(),
				() -> new EntitySkillConductorAbility.QueenTargeted(TheQueenOfHatredSkills.PILLAR_OF_LIGHT.get(), PillarOfLightSkill.RANGE, PillarOfLightSkill.RADIUS));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.BLINK.getId(),
				() -> new EntitySkillConductorAbility.Blink(TheQueenOfHatredSkills.BLINK.get()));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.TELEPORT.getId(),
				() -> new EntitySkillConductorAbility.Teleport(TheQueenOfHatredSkills.TELEPORT.get()));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.DASH.getId(),
				() -> new EntitySkillConductorAbility.Dash(TheQueenOfHatredSkills.DASH.get()));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.REFRACTION.getId(),
				() -> new EntitySkillConductorAbility.Refraction(TheQueenOfHatredSkills.REFRACTION.get()));
		event.register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredSkills.CONVERGENT.getId(),
				() -> new EntitySkillConductorAbility.Convergent(TheQueenOfHatredSkills.CONVERGENT.get()));
	}
}
