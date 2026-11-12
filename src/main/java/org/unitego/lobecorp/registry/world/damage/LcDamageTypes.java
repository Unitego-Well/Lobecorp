package org.unitego.lobecorp.registry.world.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import org.unitego.lobecorp.Lobecorp;

public interface LcDamageTypes {
	ResourceKey<DamageType> MAGIC_STAR = ResourceKey.create(Registries.DAMAGE_TYPE, Lobecorp.id("magic_star"));

	static void bootstrap(BootstrapContext<DamageType> context) {
		context.register(MAGIC_STAR, new DamageType("magic", DamageScaling.NEVER, 0.0F));
	}
}
