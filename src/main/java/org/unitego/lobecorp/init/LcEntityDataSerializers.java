package org.unitego.lobecorp.init;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.api.LcStreamCodecs;

import java.util.function.Supplier;

public class LcEntityDataSerializers {
    public static final DeferredRegister<EntityDataSerializer<?>> REGISTER = DeferredRegister.create(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, Lobecorp.NAMESPACE);

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<CompoundTag>> COMPOUND_TAG =
            register("compound_tag", () -> EntityDataSerializer.forValueType(LcStreamCodecs.COMPOUND_TAG_STREAM_CODEC));

    private static <T> DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<T>> register(String id, Supplier<EntityDataSerializer<T>> supplier) {
        return REGISTER.register(id, supplier);
    }
}
