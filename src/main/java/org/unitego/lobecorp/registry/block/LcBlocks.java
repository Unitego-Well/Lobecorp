package org.unitego.lobecorp.registry.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.generator.lang.LangHandler;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public interface LcBlocks {
    static void init(IEventBus iEventBus) {
    }

    static <B extends Block> DeferredBlock<B> register(
            DeferredRegister.Blocks register,
            String name, String enUs, String zhCn,
            Function<BlockBehaviour.Properties, ? extends B> func,
            UnaryOperator<BlockBehaviour.Properties> properties
    ) {
        DeferredBlock<B> holder = register.registerBlock(name, func, properties);
        LangHandler.addLangEnUsAndZhCnTxt(register.getNamespace(), enUs, zhCn,
                (langSet, txt) -> langSet.blockText(holder, txt));
        return holder;
    }

    static <B extends Block> DeferredBlock<B> register(
            DeferredRegister.Blocks register,
            String name, String enUs, String zhCn,
            Function<BlockBehaviour.Properties, ? extends B> func
    ) {
        return register(register, name, enUs, zhCn, func, UnaryOperator.identity());
    }
}
