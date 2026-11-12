package org.unitego.lobecorp.registry;

import net.minecraft.core.Registry;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.unitego.lobecorp.world.encounter.EncounterDefinition;
import org.unitego.lobecorp.world.entity.skill.EntitySkillGroup;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;

public interface LcRegistries {
	Registry<IEntitySkill<?>> ENTITY_SKILL = new RegistryBuilder<>(LcRegistrieKeys.ENTITY_SKILL)
			.sync(true).create();
	Registry<EntitySkillGroup> ENTITY_SKILL_GROUP = new RegistryBuilder<>(LcRegistrieKeys.ENTITY_SKILL_GROUP)
			.sync(true).create();
	Registry<EncounterDefinition<?>> ENCOUNTER = new RegistryBuilder<>(LcRegistrieKeys.ENCOUNTER)
			.sync(true).create();

	static void registerRegistry(NewRegistryEvent event) {
		event.register(ENTITY_SKILL_GROUP);
		event.register(ENTITY_SKILL);
		event.register(ENCOUNTER);
	}
}
