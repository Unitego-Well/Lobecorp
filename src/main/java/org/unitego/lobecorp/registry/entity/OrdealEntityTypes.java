package org.unitego.lobecorp.registry.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;

public interface OrdealEntityTypes {
	DeferredRegister.Entities REGISTER = DeferredRegister.createEntities(Lobecorp.NAMESPACE);

	//region 紫罗兰

	DeferredHolder<EntityType<?>, EntityType<Sweeper>> SWEEPER = LcEntityTypes.register(REGISTER,
			"sweeper", "Sweeper", "清道夫", Sweeper::new, MobCategory.MONSTER, sweeperBuilder -> sweeperBuilder
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F));

	//endregion

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}
}
