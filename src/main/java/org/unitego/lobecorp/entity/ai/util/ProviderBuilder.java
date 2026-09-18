package org.unitego.lobecorp.entity.ai.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProviderBuilder<E extends LivingEntity> {
	private final List<MemoryModuleType<?>> memoryTypes = new ArrayList<>();
	private final List<SensorType<? extends Sensor<? super E>>> sensorTypes = new ArrayList<>();
	private final Brain.ActivitySupplier<E> activities;

	public ProviderBuilder(Brain.ActivitySupplier<E> activities) {
		this.activities = activities;
	}

	public ProviderBuilder<E> addMemoryTypes(MemoryModuleType<?>... memoryTypes) {
		this.memoryTypes.addAll(Arrays.asList(memoryTypes));
		return this;
	}

	@SafeVarargs
	public final ProviderBuilder<E> addSensorTypes(SensorType<? extends Sensor<? super E>>... sensorTypes) {
		this.sensorTypes.addAll(Arrays.asList(sensorTypes));
		return this;
	}

	public Brain.Provider<E> build() {
		return Brain.provider(this.memoryTypes, this.sensorTypes, this.activities);
	}
}