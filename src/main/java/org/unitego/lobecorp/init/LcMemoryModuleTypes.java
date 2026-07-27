package org.unitego.lobecorp.init;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;

import java.util.Optional;
import java.util.UUID;

public class LcMemoryModuleTypes {
    public static final DeferredRegister<MemoryModuleType<?>> REGISTER = Lobecorp.register(BuiltInRegistries.MEMORY_MODULE_TYPE);

    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<EntityCorpse<?>>> NEAREST_CORPSE = register("nearest_corpse");
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<NearestVisibleLivingEntities>> NEAREST_VISIBLE_CORPSES = register("visible_nearest_corpse");

    private static <U> DeferredHolder<MemoryModuleType<?>, MemoryModuleType<U>> register(String name, Codec<U> codec) {
        return REGISTER.register(name, () -> new MemoryModuleType<>(Optional.of(codec)));
    }

    private static <U> DeferredHolder<MemoryModuleType<?>, MemoryModuleType<U>> register(String name) {
        return REGISTER.register(name, () -> new MemoryModuleType<>(Optional.empty()));
    }

    private static DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Boolean>> registerBoolean(String name) {
        return register(name, Codec.BOOL);
    }

    private static DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> registerInt(String name) {
        return register(name, Codec.INT);
    }

    private static DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Long>> registerLong(String name) {
        return register(name, Codec.LONG);
    }

    private static DeferredHolder<MemoryModuleType<?>, MemoryModuleType<UUID>> registerUUID(String name) {
        return register(name, UUIDUtil.CODEC);
    }
}
