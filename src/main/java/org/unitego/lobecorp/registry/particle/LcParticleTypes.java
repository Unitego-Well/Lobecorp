package org.unitego.lobecorp.registry.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.particle.ParticleOptionsParticleType;
import org.unitego.lobecorp.particle.QueenConvergentParticleOptions;
import org.unitego.lobecorp.particle.QueenChannelLaserParticleOptions;
import org.unitego.lobecorp.particle.QueenMagicCircleParticleOptions;

import java.util.function.Function;

public interface LcParticleTypes {
	DeferredRegister<ParticleType<?>> REGISTER = Lobecorp.register(BuiltInRegistries.PARTICLE_TYPE);

	DeferredHolder<ParticleType<?>, SimpleParticleType> SIMPLE_SHORT_SLASH = register("simple_short_slash", true);
	DeferredHolder<ParticleType<?>, SimpleParticleType> SIMPLE_LONG_SLASH = register("simple_long_slash", true);
	DeferredHolder<ParticleType<?>, SimpleParticleType> SIMPLE_DOUBLE_SLASH = register("simple_double_slash", true);
	DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD = register("blood", true);
	DeferredHolder<ParticleType<?>, SimpleParticleType> SHORT_SMOKE = register("short_smoke", true);
	DeferredHolder<ParticleType<?>, SimpleParticleType> QUEEN_REPEL_WAVE = register("queen_repel_wave", true);
	DeferredHolder<ParticleType<?>, SimpleParticleType> QUEEN_LASER = register("queen_laser", true);
	DeferredHolder<ParticleType<?>, ParticleType<QueenChannelLaserParticleOptions>> QUEEN_CHANNEL_LASER = register(
			"queen_channel_laser", true, type -> QueenChannelLaserParticleOptions.CODEC, type -> QueenChannelLaserParticleOptions.STREAM_CODEC);
	DeferredHolder<ParticleType<?>, ParticleType<QueenMagicCircleParticleOptions>> QUEEN_MAGIC_CIRCLE = register(
			"queen_magic_circle", true, type -> QueenMagicCircleParticleOptions.CODEC, type -> QueenMagicCircleParticleOptions.STREAM_CODEC);
	DeferredHolder<ParticleType<?>, ParticleType<QueenConvergentParticleOptions>> QUEEN_CONVERGENT = register(
			"queen_convergent", true, type -> QueenConvergentParticleOptions.CODEC, type -> QueenConvergentParticleOptions.STREAM_CODEC);

	private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name, boolean overrideLimiter) {
		return REGISTER.register(name, () -> new SimpleParticleType(overrideLimiter));
	}

	private static <T extends ParticleOptions> DeferredHolder<ParticleType<?>, ParticleType<T>> register(
			String name,
			boolean overrideLimiter,
			Function<ParticleType<T>, MapCodec<T>> codec,
			Function<ParticleType<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>> streamCodec
	) {
		return REGISTER.register(name, () -> new ParticleOptionsParticleType<>(overrideLimiter, codec, streamCodec));
	}

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}
}
