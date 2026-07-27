package org.unitego.lobecorp.entity.ai.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;

public class BrainUtil {
    public static <E extends LivingEntity> ProviderBuilder<E> provider(Brain.ActivitySupplier<E> activities) {
        return new ProviderBuilder<>(activities);
    }
}
