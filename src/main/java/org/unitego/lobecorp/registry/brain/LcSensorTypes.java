package org.unitego.lobecorp.registry.brain;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.entity.ai.sensing.NearbyEntitiesSensor;
import org.unitego.lobecorp.entity.ai.sensing.NearestEntitySensor;
import org.unitego.lobecorp.entity.ai.sensing.OrdealAttackablesSensor;

import java.util.function.Supplier;

/// 实体 brain 系统的传感器
public interface LcSensorTypes {
    DeferredRegister<SensorType<?>> REGISTER = Lobecorp.register(BuiltInRegistries.SENSOR_TYPE);
    /// 考验最近目标
    DeferredHolder<SensorType<?>, SensorType<OrdealAttackablesSensor>> ORDEAL_ATTACKABLES = register("ordeal_attackables", OrdealAttackablesSensor::new);

    /// 最近尸体（单个）
    DeferredHolder<SensorType<?>, SensorType<NearestEntitySensor<EntityCorpse<?>>>> NEAREST_CORPSE = register("nearest_corpse", () ->
            NearestEntitySensor.create(
                    entity -> entity instanceof EntityCorpse && entity.isAlive(), 32, 16,
                    LcMemoryModuleTypes.NEAREST_CORPSE.get()));

    /// 附近尸体（列表 + 可见包装器）
    DeferredHolder<SensorType<?>, SensorType<NearbyEntitiesSensor<EntityCorpse<?>>>> NEARBY_CORPSES = register("nearby_corpses", () ->
            NearbyEntitiesSensor.create(
                    entity -> entity instanceof EntityCorpse && entity.isAlive(), 32,
                    LcMemoryModuleTypes.NEAREST_CORPSES.get(),
                    LcMemoryModuleTypes.NEAREST_VISIBLE_CORPSES.get()));

    private static <U extends Sensor<?>> DeferredHolder<SensorType<?>, SensorType<U>> register(String name, Supplier<U> factory) {
        return REGISTER.register(name, () -> new SensorType<>(factory));
    }
}
