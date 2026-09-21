package org.unitego.lobecorp.registry.brain;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.entity.ai.sensing.NearestEntitySensor;
import org.unitego.lobecorp.entity.ai.sensing.OrdealAttackablesSensor;
import org.unitego.lobecorp.entity.ai.sensing.TheQueenOfHatredAttackablesSensor;
import org.unitego.lobecorp.entity.entity_skill.sweeper.SweeperReassembleSkill;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;

import java.util.function.Supplier;

/// 实体 brain 系统的传感器
public interface LcSensorTypes {
    int CLEANUP_TARGET_SCAN_RATE = 10;
	DeferredRegister<SensorType<?>> REGISTER = Lobecorp.register(BuiltInRegistries.SENSOR_TYPE);
	/// 考验最近目标
	DeferredHolder<SensorType<?>, SensorType<OrdealAttackablesSensor>> ORDEAL_ATTACKABLES = register("ordeal_attackables", OrdealAttackablesSensor::new);
	/// 憎恶皇后最近的有效敌对目标。
	DeferredHolder<SensorType<?>, SensorType<TheQueenOfHatredAttackablesSensor>> THE_QUEEN_OF_HATRED_ATTACKABLES =
			register("the_queen_of_hatred_attackables", TheQueenOfHatredAttackablesSensor::new);

	/// 最近尸体（单个）
	DeferredHolder<SensorType<?>, SensorType<NearestEntitySensor<EntityCorpse<?>>>> NEAREST_CORPSE = register("nearest_corpse", () ->
			NearestEntitySensor.create(
					entity -> entity instanceof EntityCorpse && entity.isAlive(), 32, 16,
					LcMemoryModuleTypes.NEAREST_CORPSE.get()));

	/// 最近的清理目标
	DeferredHolder<SensorType<?>, SensorType<NearestEntitySensor<Entity>>> NEAREST_CLEANUP_TARGET = register("nearest_cleanup_target", () ->
			NearestEntitySensor.create(
					(body, entity) -> entity.isAlive() && (entity instanceof EntityCorpse<?> corpse
							&& (!(corpse.getOwnerEntity() instanceof Sweeper)
							|| body instanceof Sweeper sweeper && SweeperReassembleSkill.canReassemble(sweeper, corpse))
							|| entity instanceof ItemEntity itemEntity && !itemEntity.getItem().isEmpty()), 32, 16,
					LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get(), CLEANUP_TARGET_SCAN_RATE));

	private static <U extends Sensor<?>> DeferredHolder<SensorType<?>, SensorType<U>> register(String name, Supplier<U> factory) {
		return REGISTER.register(name, () -> new SensorType<>(factory));
	}

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}
}
