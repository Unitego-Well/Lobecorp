package org.unitego.lobecorp.registry.item;

import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public interface LcItems {
	DeferredRegister.Items REGISTER = DeferredRegister.createItems(Lobecorp.NAMESPACE);

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
		SpawnEggItems.init(iEventBus);
	}

	static <I extends Item> DeferredItem<I> register(
			DeferredRegister.Items register,
			String name, String enUs, String zhCn,
			Function<Item.Properties, ? extends I> func, UnaryOperator<Item.Properties> properties
	) {
		DeferredItem<I> holder = register.registerItem(name, func, properties);
		LangHandler.creates(register.getNamespace(), enUs, zhCn,
				(langSet, txt) -> langSet.itemText(holder, txt));
		return holder;
	}

	static <I extends Item> DeferredItem<I> register(
			DeferredRegister.Items register,
			String name, String enUs, String zhCn,
			Function<Item.Properties, ? extends I> func
	) {
		return register(register, name, enUs, zhCn, func, UnaryOperator.identity());
	}
}
