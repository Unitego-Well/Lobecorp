package org.unitego.lobecorp.init.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.api.LcStreamCodecs;

import java.util.Optional;
import java.util.function.Supplier;

public class LcEntityDataSerializers {
    public static final DeferredRegister<EntityDataSerializer<?>> REGISTER = Lobecorp.register(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS);

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<CompoundTag>> COMPOUND_TAG =
            register("compound_tag", () -> EntityDataSerializer.forValueType(LcStreamCodecs.COMPOUND_TAG));

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<Identifier>> IDENTIFIER =
            register("identifier", () -> EntityDataSerializer.forValueType(Identifier.STREAM_CODEC));

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<EntityType<?>>> ENTITY_TYPE =
            register("entity_type", () -> EntityDataSerializer.forValueType(EntityType.STREAM_CODEC));

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<Optional<EntityType<?>>>> OPTIONAL_ENTITY_TYPE =
            register("optional_entity_type", () -> EntityDataSerializer.forValueType(LcStreamCodecs.OPTIONAL_ENTITY_TYPE));

    private static <T> DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<T>> register(String id, Supplier<EntityDataSerializer<T>> supplier) {
        return REGISTER.register(id, supplier);
    }
}
