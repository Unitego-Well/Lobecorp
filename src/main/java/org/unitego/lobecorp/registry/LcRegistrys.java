package org.unitego.lobecorp.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.ai.skill.IEntitySkill;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public interface LcRegistrys {
    ResourceKey<Registry<IEntitySkill>> ENTITY_SKILL_KEY = ResourceKey.createRegistryKey(Lobecorp.id("entity_skill"));
    Registry<IEntitySkill> ENTITY_SKILL = new RegistryBuilder<>(ENTITY_SKILL_KEY).sync(true).create();

    @SubscribeEvent
    static void registerRegistry(NewRegistryEvent event) {
        event.register(ENTITY_SKILL);
    }
}
