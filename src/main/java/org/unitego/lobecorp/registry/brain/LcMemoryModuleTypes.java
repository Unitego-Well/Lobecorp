package org.unitego.lobecorp.registry.brain;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.registry.LcCodecs;

import java.util.*;
import java.util.function.Function;

/// 实体 brain 系统的存储器
public interface LcMemoryModuleTypes {
	DeferredRegister<MemoryModuleType<?>> REGISTER = Lobecorp.register(BuiltInRegistries.MEMORY_MODULE_TYPE);

	/// 最近的尸体。
	DeferredHolder<MemoryModuleType<?>, MemoryModuleType<EntityCorpse<?>>> NEAREST_CORPSE = register("nearest_corpse");
	/// 最近的清理目标。
	DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Entity>> NEAREST_CLEANUP_TARGET = register("nearest_cleanup_target");

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}

	/// 注册非持久化记忆模块。
	private static <U> DeferredHolder<MemoryModuleType<?>, MemoryModuleType<U>> register(String name) {
		return REGISTER.register(name, () -> new MemoryModuleType<>(Optional.empty()));
	}

	/// 注册持久化记忆模块。
	private static <U> DeferredHolder<MemoryModuleType<?>, MemoryModuleType<U>> register(String name, Codec<U> codec) {
		return REGISTER.register(name, () -> new MemoryModuleType<>(Optional.of(codec)));
	}

	private static <T> DeferredHolder<MemoryModuleType<?>, MemoryModuleType<List<T>>> registerList(String name, Codec<T> codec) {
		return register(name, Codec.list(codec));
	}

	private static <T> DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Set<T>>> registerSet(String name, Codec<T> codec) {
		return register(name, LcCodecs.set(codec));
	}

	private static <K, V> DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Map<K, V>>> registerMap(String name, Codec<K> codecK, Function<K, Codec<? extends V>> valueCodecFunction) {
		return register(name, Codec.dispatchedMap(codecK, valueCodecFunction));
	}

	private static <K, V> DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Map<K, V>>> registerMap(String name, Codec<K> codecK, Codec<V> codecV) {
		return registerMap(name, codecK, v -> codecV);
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

	private static DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Identifier>> registerIdentifier(String name) {
		return register(name, Identifier.CODEC);
	}
}
