package org.unitego.lobecorp.registry.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.abnormalitie.*;

public interface AbnormalitieEntityTypes {
	DeferredRegister.Entities REGISTER = DeferredRegister.createEntities(Lobecorp.NAMESPACE);

	//region ZAYIN

	//endregion

	//region TETH

	//endregion

	//region HE

	//endregion

	//region WAW

	DeferredHolder<EntityType<?>, EntityType<TheQueenOfHatred>> THE_QUEEN_OF_HATRED = LcEntityTypes.register(REGISTER,
			"the_queen_of_hatred", "The Queen Of Hatred", "憎恶皇后",
			TheQueenOfHatred::new, MobCategory.CREATURE, b -> b
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F));

	DeferredHolder<EntityType<?>, EntityType<TheQueenOfHatredMagicStar>> THE_QUEEN_OF_HATRED_MAGIC_STAR = LcEntityTypes.register(REGISTER,
			"the_queen_of_hatred_magic_star", "Magic Star", "魔法星星",
			TheQueenOfHatredMagicStar::new, MobCategory.MISC, b -> b
					.sized(0.5F, 0.5F));

	DeferredHolder<EntityType<?>, EntityType<TheQueenOfHatredTrackingMagicStar>> THE_QUEEN_OF_HATRED_TRACKING_MAGIC_STAR = LcEntityTypes.register(REGISTER,
			"the_queen_of_hatred_tracking_magic_star", "Tracking Magic Star", "追踪魔法星星",
			TheQueenOfHatredTrackingMagicStar::new, MobCategory.MISC, b -> b
					.sized(1.0F, 1.0F));

	DeferredHolder<EntityType<?>, EntityType<TheQueenOfHatredLaser>> THE_QUEEN_OF_HATRED_LASER = LcEntityTypes.register(REGISTER,
			"the_queen_of_hatred_laser", "Laser", "激光",
			TheQueenOfHatredLaser::new, MobCategory.MISC, b -> b
					.sized(0.1F, 0.1F));

	DeferredHolder<EntityType<?>, EntityType<TheQueenOfHatredMagicCircle>> THE_QUEEN_OF_HATRED_MAGIC_CIRCLE = LcEntityTypes.register(REGISTER,
			"the_queen_of_hatred_magic_circle", "Magic Circle", "法阵",
			TheQueenOfHatredMagicCircle::new, MobCategory.MISC, b -> b
					.sized(0.1F, 0.1F));

	DeferredHolder<EntityType<?>, EntityType<TheQueenOfHatredBurstMagicStar>> THE_QUEEN_OF_HATRED_BURST_MAGIC_STAR = LcEntityTypes.register(REGISTER,
			"the_queen_of_hatred_burst_magic_star", "Burst Magic Star", "魔法爆裂星星",
			TheQueenOfHatredBurstMagicStar::new, MobCategory.MISC, b -> b
					.sized(2.0F, 2.0F));

	DeferredHolder<EntityType<?>, EntityType<TheQueenOfHatredPillarOfLight>> THE_QUEEN_OF_HATRED_PILLAR_OF_LIGHT = LcEntityTypes.register(REGISTER,
			"the_queen_of_hatred_pillar_of_light", "Pillar of Light", "光柱",
			TheQueenOfHatredPillarOfLight::new, MobCategory.MISC, b -> b
					.sized(0.1F, 0.1F));

	//endregion

	//region ALEPH

	//endregion

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}
}
