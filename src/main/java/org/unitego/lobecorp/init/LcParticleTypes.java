package org.unitego.lobecorp.init;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;

import java.util.function.Function;

public class LcParticleTypes {
    public static final DeferredRegister<ParticleType<?>> REGISTER = Lobecorp.register(BuiltInRegistries.PARTICLE_TYPE);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SIMPLE_SHORT_SLASH = register("simple_short_slash", true);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SIMPLE_LONG_SLASH = register("simple_long_slash", true);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SIMPLE_DOUBLE_SLASH = register("simple_double_slash", true);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD = register("blood", true);

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

    private static class ParticleOptionsParticleType<T extends ParticleOptions> extends ParticleType<T> {
        private final Function<ParticleType<T>, MapCodec<T>> codec;
        private final Function<ParticleType<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>> streamCodec;

        public ParticleOptionsParticleType(boolean overrideLimiter, Function<ParticleType<T>, MapCodec<T>> codec, Function<ParticleType<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>> streamCodec) {
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
}
