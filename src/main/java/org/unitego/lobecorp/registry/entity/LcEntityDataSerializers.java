package org.unitego.lobecorp.registry.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.entity_state.EntityState;
import org.unitego.lobecorp.registry.LcStreamCodecs;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public interface LcEntityDataSerializers {
	DeferredRegister<EntityDataSerializer<?>> REGISTER = Lobecorp.register(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS);

	DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<CompoundTag>> COMPOUND_TAG =
			register("compound_tag", () -> EntityDataSerializer.forValueType(LcStreamCodecs.COMPOUND_TAG));
	DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<Identifier>> IDENTIFIER =
			register("identifier", () -> EntityDataSerializer.forValueType(Identifier.STREAM_CODEC));
	DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<EntityType<?>>> ENTITY_TYPE =
			register("entity_type", () -> EntityDataSerializer.forValueType(EntityType.STREAM_CODEC));
	DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<Optional<EntityType<?>>>> OPTIONAL_ENTITY_TYPE =
			register("optional_entity_type", () -> EntityDataSerializer.forValueType(LcStreamCodecs.OPTIONAL_ENTITY_TYPE));
	DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<List<EntityState>>> ENTITY_STATES =
			register("entity_states", () -> EntityDataSerializer.forValueType(LcStreamCodecs.ENTITY_STATES));

	private static <T> DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<T>> register(
			String id, Supplier<EntityDataSerializer<T>> supplier
	) {
		return REGISTER.register(id, supplier);
	}

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}
}
