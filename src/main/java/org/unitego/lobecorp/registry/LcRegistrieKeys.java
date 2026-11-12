package org.unitego.lobecorp.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.world.encounter.EncounterDefinition;
import org.unitego.lobecorp.world.entity.skill.EntitySkillGroup;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;

public interface LcRegistrieKeys {
	ResourceKey<Registry<IEntitySkill<?>>> ENTITY_SKILL = ResourceKey.createRegistryKey(Lobecorp.id("entity_skill"));
	ResourceKey<Registry<EntitySkillGroup>> ENTITY_SKILL_GROUP = ResourceKey.createRegistryKey(Lobecorp.id("entity_skill_group"));
	ResourceKey<Registry<EncounterDefinition<?>>> ENCOUNTER = ResourceKey.createRegistryKey(Lobecorp.id("encounter"));
}
