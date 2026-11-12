package org.unitego.lobecorp.generator;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.data.ParticleDescriptionProvider;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;

public class ParticleGenerator extends ParticleDescriptionProvider {
	public ParticleGenerator(PackOutput output) {
		super(output);
	}

	@Override
	protected void addDescriptions() {
		spriteSet(LcParticleTypes.SIMPLE_SHORT_SLASH.get(), Lobecorp.id("simple/short_slash"), 6, false);
		spriteSet(LcParticleTypes.SIMPLE_LONG_SLASH.get(), Lobecorp.id("simple/long_slash"), 6, false);
		spriteSet(LcParticleTypes.SIMPLE_DOUBLE_SLASH.get(), Lobecorp.id("simple/double_slash"), 6, false);
		spriteSet(LcParticleTypes.BLOOD.get(), Lobecorp.id("blood"));
		spriteSet(LcParticleTypes.SHORT_SMOKE.get(), Identifier.withDefaultNamespace("big_smoke"), 12, false);
		spriteSet(LcParticleTypes.QUEEN_REPEL_WAVE.get(), Lobecorp.id("the_queen_of_hatred/shock_wave"));
		spriteSet(LcParticleTypes.QUEEN_LASER.get(), Lobecorp.id("the_queen_of_hatred/laser_tail"));
		spriteSet(LcParticleTypes.QUEEN_CHANNEL_LASER.get(), Lobecorp.id("the_queen_of_hatred/laser_tail"));
		spriteSet(LcParticleTypes.QUEEN_MAGIC_CIRCLE.get(), Lobecorp.id("the_queen_of_hatred/magic_circle"));
		spriteSet(LcParticleTypes.QUEEN_CONVERGENT.get(), Lobecorp.id("queen_convergent_star"), Lobecorp.id("the_queen_of_hatred/shock_wave"));
	}

	protected void spriteSet(DeferredHolder<ParticleType<?>, ParticleType<?>> type, int numOfTextures, boolean reverse) {
		spriteSet(type.get(), type.getId(), numOfTextures, reverse);
	}

	protected void spriteSet(DeferredHolder<ParticleType<?>, ParticleType<?>> type) {
		spriteSet(type.get(), type.getId());
	}
}
