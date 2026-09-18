package org.unitego.lobecorp.registry.item;

import com.mojang.serialization.ListBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;

import java.util.List;
import java.util.function.UnaryOperator;

public interface LcCreativeModeTabs {
	DeferredRegister<CreativeModeTab> REGISTER = Lobecorp.register(BuiltInRegistries.CREATIVE_MODE_TAB);

	DeferredHolder<CreativeModeTab, CreativeModeTab> DEFAULT = register(REGISTER, "lobecorp","Lobecorp", "脑叶公司", b -> b, (parameters, output) -> {
		output.acceptAll(LcItems.REGISTER.getEntries().stream().map(DeferredHolder::get).map(Item::getDefaultInstance).toList());
		output.acceptAll(SpawnEggItems.REGISTER.getEntries().stream().map(DeferredHolder::get).map(Item::getDefaultInstance).toList());
	});

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}

	static DeferredHolder<CreativeModeTab, CreativeModeTab> register(
			DeferredRegister<CreativeModeTab> registry,
			String name, String enUs, String zhCn,
			UnaryOperator<CreativeModeTab.Builder> builder,
			CreativeModeTab.DisplayItemsGenerator displayItemsGenerator
	) {
		DeferredHolder<CreativeModeTab, CreativeModeTab> register = registry.register(name, () -> builder.apply(CreativeModeTab.builder()
				.title(Component.translatable("itemGroup." + registry.getNamespace() + "." + name))
				.displayItems(displayItemsGenerator)).build());
		String namespace = registry.getNamespace();
		String key = "itemGroup." + namespace + "." + name;
		LangHandler.creates(namespace, enUs, zhCn,
				(langSet, txt) -> langSet.add(key, txt));
		return register;
	}

	static void addRegistryItem(DeferredRegister.Items registry, CreativeModeTab.Output output) {
		registry.getEntries().forEach(entry -> output.accept(entry.get()));
	}
}
