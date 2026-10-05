package org.unitego.lobecorp.util.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.unitego.lobecorp.world.entity.ai.memory.ProviderBuilder;

public class BrainUtil {
	public static <E extends LivingEntity> ProviderBuilder<E> provider(Brain.ActivitySupplier<E> activities) {
		return new ProviderBuilder<>(activities);
	}
}
