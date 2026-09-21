package org.unitego.lobecorp.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillGroup;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public interface LcRegistrys {
	ResourceKey<Registry<IEntitySkill<?>>> ENTITY_SKILL_KEY = ResourceKey.createRegistryKey(Lobecorp.id("entity_skill"));
	Registry<IEntitySkill<?>> ENTITY_SKILL = new RegistryBuilder<>(ENTITY_SKILL_KEY).sync(true).create();
	ResourceKey<Registry<EntitySkillGroup>> ENTITY_SKILL_GROUP_KEY = ResourceKey.createRegistryKey(Lobecorp.id("entity_skill_group"));
	Registry<EntitySkillGroup> ENTITY_SKILL_GROUP = new RegistryBuilder<>(ENTITY_SKILL_GROUP_KEY).sync(true).create();

	@SubscribeEvent
	static void registerRegistry(NewRegistryEvent event) {
		event.register(ENTITY_SKILL_GROUP);
		event.register(ENTITY_SKILL);
	}
}
