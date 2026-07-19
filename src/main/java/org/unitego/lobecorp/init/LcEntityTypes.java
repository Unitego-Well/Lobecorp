package org.unitego.lobecorp.init;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.ApiStatus;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.resource.generator.lang.EnUsLang;
import org.unitego.lobecorp.resource.generator.lang.ZhCnLang;

import java.util.function.UnaryOperator;

public class LcEntityTypes {
    public static final DeferredRegister.Entities REGISTER = DeferredRegister.createEntities(Lobecorp.NAMESPACE);

    @ApiStatus.Internal
    public static void init(IEventBus iEventBus) {
        REGISTER.register(iEventBus);
        OrdealEntityTypes.init(iEventBus);
    }

    @ApiStatus.Internal
    public static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(
            DeferredRegister.Entities register,
            String name, String zhCn, String enUs, EntityType.EntityFactory<T> factory,
            MobCategory category, UnaryOperator<EntityType.Builder<T>> builder
    ) {
        var deferredHolder = register.registerEntityType(name, factory, category, builder);
        ZhCnLang.addI18nEntityTypeText(deferredHolder, zhCn);
        EnUsLang.addI18nEntityTypeText(deferredHolder, enUs);
        return deferredHolder;
    }

    @ApiStatus.Internal
    public static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(
            DeferredRegister.Entities register,
            String name, String zhCn, String enUs, EntityType.EntityFactory<T> factory,
            MobCategory category
    ) {
        return register(register, name, zhCn, enUs, factory, category, UnaryOperator.identity());
    }
}
