package org.unitego.lobecorp.registry.entity.ai;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.world.entity.EntityCorpse;

import java.util.*;

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
}
