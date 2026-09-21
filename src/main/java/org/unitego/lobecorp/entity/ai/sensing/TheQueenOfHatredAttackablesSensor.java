package org.unitego.lobecorp.entity.ai.sensing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestVisibleLivingEntitySensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;

/// 为憎恶皇后筛选临时敌对目标的最近可见实体传感器。
public class TheQueenOfHatredAttackablesSensor extends NearestVisibleLivingEntitySensor {
	@Override
	protected boolean isMatchingEntity(ServerLevel level, LivingEntity body, LivingEntity candidate) {
		return body instanceof TheQueenOfHatred queen
				&& queen.isValidTarget(candidate)
				&& Sensor.isEntityAttackable(level, body, candidate);
	}

	@Override
	protected MemoryModuleType<LivingEntity> getMemoryToSet() {
		return MemoryModuleType.NEAREST_ATTACKABLE;
	}
}
