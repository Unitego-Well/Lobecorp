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

public class OrdealEntityTypes {
    public static final DeferredRegister.Entities REGISTER = DeferredRegister.createEntities(Lobecorp.NAMESPACE);

    public static final DeferredHolder<EntityType<?>, EntityType<Sweeper>> SWEEPER = LcEntityTypes.register(LcEntityTypes.REGISTER,
            "sweeper", "清道夫", "Sweeper", Sweeper::new, MobCategory.MONSTER
    );

    @ApiStatus.Internal
    static void init(IEventBus iEventBus) {
        REGISTER.register(iEventBus);
    }
}
