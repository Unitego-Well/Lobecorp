package org.unitego.lobecorp.registry.world.item;

import net.minecraft.world.item.Item;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import org.unitego.lobecorp.world.item.WarpAnchorItem;
import org.unitego.lobecorp.world.item.component.WarpAnchor;
import org.unitego.lobecorp.world.item.CannedEnkephalinItem;
import org.unitego.lobecorp.registry.translation.WarpTranslationKeys;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.world.LcLevels;
import org.unitego.lobecorp.registry.translation.LangHandler;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public interface LcItems {
	DeferredRegister.Items REGISTER = DeferredRegister.createItems(Lobecorp.NAMESPACE);

	DeferredItem<CannedEnkephalinItem> CANNED_ENKEPHALIN = register(REGISTER, "canned_enkephalin", "Canned Enkephalin", "罐装脑啡肽", CannedEnkephalinItem::new);
	DeferredItem<WarpAnchorItem> WARP_ANCHOR = registerWarpAnchor(
			"warp_anchor", "WARP Jump Anchor", "WARP迁跃锚", WarpAnchor.EMPTY);
	DeferredItem<WarpAnchorItem> WARP_ANCHOR_BACKSTREETS_MIDNIGHT = registerWarpAnchor(
			"warp_anchor_backstreets_midnight", "WARP Jump Anchor - Backstreets at Midnight", "WARP迁跃锚-后巷深宵",
			WarpAnchor.EMPTY.withAnchor(WarpAnchor.Destination.surface(LcLevels.MIDNIGHT_CLEANUP)).withAnchorEditable(false));

	static DeferredItem<WarpAnchorItem> registerWarpAnchor(String name, String enUs, String zhCn, WarpAnchor data) {
		return register(REGISTER, name, enUs, zhCn, WarpAnchorItem::new,
				properties -> properties.stacksTo(1).component(LcDataComponents.WARP_ANCHOR.get(), data)
						.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable(WarpTranslationKeys.DESCRIPTION)))));
	}

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
