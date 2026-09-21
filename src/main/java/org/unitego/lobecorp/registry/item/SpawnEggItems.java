package org.unitego.lobecorp.registry.item;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.item.LcSpawnEggItem;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface SpawnEggItems {
	/// 生物生成蛋延迟注册器。
	DeferredRegister.Items REGISTER = DeferredRegister.createItems(Lobecorp.NAMESPACE);

	/// 憎恶皇后生成蛋。
	DeferredItem<LcSpawnEggItem> THE_QUEEN_OF_HATRED = register("the_queen_of_hatred", "The Queen Of Hatred", "憎恶皇后",
			AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED);

	/// 清道夫生成蛋。
	DeferredItem<LcSpawnEggItem> SWEEPER = register("sweeper", "Sweeper", "清道夫", OrdealEntityTypes.SWEEPER);

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}

	static DeferredItem<LcSpawnEggItem> register(String name, String enUs, String zhCn,
			Supplier<? extends EntityType<?>> entityType) {
		return register(name, enUs, zhCn, UnaryOperator.identity(), entityType);
	}

	static DeferredItem<LcSpawnEggItem> register(String name, String enUs, String zhCn,
			UnaryOperator<Item.Properties> properties, Supplier<? extends EntityType<?>> entityType) {
		return LcItems.register(REGISTER, name + "_spawn_egg", enUs + " Spawn Egg",
				zhCn + "刷怪蛋", p -> new LcSpawnEggItem(entityType, p), properties);
	}
}
