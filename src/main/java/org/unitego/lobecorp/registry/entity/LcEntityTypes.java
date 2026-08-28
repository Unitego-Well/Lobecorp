package org.unitego.lobecorp.registry.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.ApiStatus;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.generator.lang.EnUsLangGenerator;
import org.unitego.lobecorp.generator.lang.LangHandler;
import org.unitego.lobecorp.generator.lang.ZhCnLangGenerator;

import java.util.function.UnaryOperator;

public interface LcEntityTypes {
    DeferredRegister.Entities REGISTER = DeferredRegister.createEntities(Lobecorp.NAMESPACE);

    DeferredHolder<EntityType<?>, EntityType<EntityCorpse<?>>> ENTITY_CORPSE = LcEntityTypes.register(REGISTER,
            "entity_corpse", "Entity Corpse", "实体尸体", EntityCorpse::new, MobCategory.MISC);

    static void init(IEventBus iEventBus) {
        REGISTER.register(iEventBus);
        OrdealEntityTypes.init(iEventBus);
    }

    static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(
            DeferredRegister.Entities register,
            String name, String enUs, String zhCn,
            EntityType.EntityFactory<T> factory,
            MobCategory category, UnaryOperator<EntityType.Builder<T>> builder
    ) {
        DeferredHolder<EntityType<?>, EntityType<T>> holder = register.registerEntityType(name, factory, category, builder);
        LangHandler.addLangEnUsAndZhCnTxt(register.getNamespace(), enUs, zhCn,
                (langSet, txt) -> langSet.entityTypeText(holder, txt));
        return holder;
    }

    static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(
            DeferredRegister.Entities register,
            String name, String enUs, String zhCn,
            EntityType.EntityFactory<T> factory, MobCategory category
    ) {
        return register(register, name, enUs, zhCn, factory, category, UnaryOperator.identity());
    }
}
