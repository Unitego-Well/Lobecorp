package org.unitego.lobecorp.init;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.ai.sensing.NearestCorpseSensor;
import org.unitego.lobecorp.entity.ai.sensing.OrdealAttackablesSensor;

import java.util.function.Supplier;

public class LcSensorTypes {
    public static final DeferredRegister<SensorType<?>> REGISTER = Lobecorp.register(BuiltInRegistries.SENSOR_TYPE);
    /// 考验最近目标
    public static final DeferredHolder<SensorType<?>, SensorType<OrdealAttackablesSensor>> ORDEAL_ATTACKABLES = register("ordeal_attackables", OrdealAttackablesSensor::new);
    /// 最近尸体
    public static final DeferredHolder<SensorType<?>, SensorType<NearestCorpseSensor>> NEAREST_CORPSE = register("nearest_corpse", NearestCorpseSensor::new);

    private static <U extends Sensor<?>> DeferredHolder<SensorType<?>, SensorType<U>> register(String name, Supplier<U> factory) {
        return REGISTER.register(name, () -> new SensorType<>(factory));
    }
}
