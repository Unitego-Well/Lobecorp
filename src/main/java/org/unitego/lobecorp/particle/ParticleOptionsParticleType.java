package org.unitego.lobecorp.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.Function;

public class ParticleOptionsParticleType<T extends ParticleOptions> extends ParticleType<T> {
	private final Function<ParticleType<T>, MapCodec<T>> codec;
	private final Function<ParticleType<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>> streamCodec;

	public ParticleOptionsParticleType(
			boolean overrideLimiter, Function<ParticleType<T>, MapCodec<T>> codec,
			Function<ParticleType<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>> streamCodec
	) {
		super(overrideLimiter);
		this.codec = codec;
		this.streamCodec = streamCodec;
	}

	@Override
	public MapCodec<T> codec() {
		return codec.apply(this);
	}

	@Override
	public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
		return streamCodec.apply(this);
	}
}