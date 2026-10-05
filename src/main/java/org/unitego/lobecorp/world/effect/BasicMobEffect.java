package org.unitego.lobecorp.world.effect;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.function.Function;

public class BasicMobEffect extends MobEffect {
	public BasicMobEffect(MobEffectCategory category, int color) {
		super(category, color);
	}

	public BasicMobEffect(MobEffectCategory category, int color, Function<MobEffectInstance, ParticleOptions> particleFactory) {
		super(category, color, particleFactory);
	}

	public BasicMobEffect(MobEffectCategory category, int color, ParticleOptions particleOptions) {
		super(category, color, particleOptions);
	}
}
