package org.unitego.lobecorp.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.generator.lang.LangHandler;

import java.util.function.UnaryOperator;

public interface LcCreativeModeTabs {
    static void init(IEventBus iEventBus) {
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
        LangHandler.addLangEnUsAndZhCnTxt(namespace, enUs, zhCn,
                (langSet, txt) -> langSet.add(key, txt));
        return register;
    }

    static void addRegistryItem(DeferredRegister.Items registry, CreativeModeTab.Output output) {
        registry.getEntries().forEach(entry -> output.accept(entry.get()));
    }
}
