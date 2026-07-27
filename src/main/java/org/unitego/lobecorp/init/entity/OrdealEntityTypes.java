package org.unitego.lobecorp.init.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.ApiStatus;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;

public class OrdealEntityTypes {
    public static final DeferredRegister.Entities REGISTER = DeferredRegister.createEntities(Lobecorp.NAMESPACE);

    public static final DeferredHolder<EntityType<?>, EntityType<Sweeper>> SWEEPER = LcEntityTypes.register(REGISTER,
            "sweeper", "Sweeper", "清道夫", Sweeper::new, MobCategory.MONSTER, sweeperBuilder -> sweeperBuilder
                    .sized(0.625f, 2.938f)
                    .eyeHeight(2.4375f));

    @ApiStatus.Internal
    static void init(IEventBus iEventBus) {
        REGISTER.register(iEventBus);
    }
}
