package org.unitego.lobecorp.init;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.ordeal.indigo.Sweeper;

public class LcEntityTypes {
    public static final DeferredRegister.Entities REGISTER = DeferredRegister.createEntities(Lobecorp.NAMESPACE);

    public static final DeferredHolder<EntityType<?>, EntityType<Sweeper>> SWEEPER = REGISTER.registerEntityType(
            "sweeper", Sweeper::new, MobCategory.MONSTER
    );
}
