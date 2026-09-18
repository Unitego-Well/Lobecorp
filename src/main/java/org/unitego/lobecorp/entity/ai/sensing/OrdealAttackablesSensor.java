package org.unitego.lobecorp.entity.ai.sensing;

import com.google.common.collect.Sets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestVisibleLivingEntitySensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.unitego.lobecorp.entity.ordeal.IOrdeal;

import java.util.Set;

public class OrdealAttackablesSensor extends NearestVisibleLivingEntitySensor {

	@Override
	protected boolean isMatchingEntity(ServerLevel level, LivingEntity body, LivingEntity mob) {
		if (body instanceof IOrdeal iOrdeal) {
			return iOrdeal.isValidTarget(mob) && Sensor.isEntityAttackable(level, body, mob);
		}
		return Sensor.isEntityAttackable(level, body, mob);
	}

	@Override
	protected MemoryModuleType<LivingEntity> getMemoryToSet() {
		return MemoryModuleType.NEAREST_ATTACKABLE;
	}

	@Override
	public Set<MemoryModuleType<?>> requires() {
		return Sets.union(super.requires(), Set.of(MemoryModuleType.HAS_HUNTING_COOLDOWN));
	}
}