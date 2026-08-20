package org.unitego.lobecorp.registry.brain;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.entity.ai.memory.NearestVisibleEntities;
import org.unitego.lobecorp.entity.ai.skill.SkillRuntime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/// 实体 brain 系统的存储器
public class LcMemoryModuleTypes {
    public static final DeferredRegister<MemoryModuleType<?>> REGISTER = Lobecorp.register(BuiltInRegistries.MEMORY_MODULE_TYPE);

    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<EntityCorpse<?>>> NEAREST_CORPSE = register("nearest_corpse");
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<List<EntityCorpse<?>>>> NEAREST_CORPSES = register("nearest_corpses");
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<NearestVisibleEntities<EntityCorpse<?>>>> NEAREST_VISIBLE_CORPSES = register("nearest_visible_corpses");
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> DISPOSE_CORPSE_WIND_UP_TICKS = register("dispose_corpse_wind_up_ticks");
    // 技能系统
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<SkillRuntime>> SKILL_ACTIVE = register("skill_active");
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Map<String, Long>>> SKILL_COOLDOWNS = register("skill_cooldowns");

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
