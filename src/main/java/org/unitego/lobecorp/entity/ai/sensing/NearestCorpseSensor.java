package org.unitego.lobecorp.entity.ai.sensing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestVisibleLivingEntitySensor;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.init.LcMemoryModuleTypes;

public class NearestCorpseSensor extends NearestVisibleLivingEntitySensor {
    @Override
    protected boolean isMatchingEntity(final ServerLevel level, LivingEntity body, LivingEntity mob) {
        return mob instanceof EntityCorpse;
    }

    @Override
    protected MemoryModuleType<LivingEntity> getMemoryToSet() {
        return (MemoryModuleType<LivingEntity>) (MemoryModuleType<?>) LcMemoryModuleTypes.NEAREST_CORPSE.get();
    }
}
