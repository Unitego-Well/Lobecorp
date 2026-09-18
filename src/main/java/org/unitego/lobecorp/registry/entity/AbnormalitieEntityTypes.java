package org.unitego.lobecorp.registry.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;

public interface AbnormalitieEntityTypes {
	DeferredRegister.Entities REGISTER = DeferredRegister.createEntities(Lobecorp.NAMESPACE);

	//region ZAYIN

	//endregion

	//region TETH

	//endregion

	//region HE

	//endregion

	//region WAW

	DeferredHolder<EntityType<?>, EntityType<Sweeper>> THE_QUEEN_OF_HATRED = LcEntityTypes.register(REGISTER,
			"the_queen_of_hatred", "The Queen Of Hatred", "憎恶皇后", Sweeper::new, MobCategory.MONSTER, sweeperBuilder -> sweeperBuilder
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F));

	//endregion

	//region ALEPH

	//endregion


	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}
}
