package org.unitego.lobecorp.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;

/// 固定落点法阵和光柱的显示参数；不参与命中判断。
@NullMarked
public record QueenMagicCircleParticleOptions(double radius, int durationTicks, boolean pillar) implements ParticleOptions {
	public static final MapCodec<QueenMagicCircleParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("radius").forGetter(QueenMagicCircleParticleOptions::radius),
			Codec.intRange(1, Integer.MAX_VALUE).fieldOf("duration").forGetter(QueenMagicCircleParticleOptions::durationTicks),
			Codec.BOOL.fieldOf("pillar").forGetter(QueenMagicCircleParticleOptions::pillar)
	).apply(instance, QueenMagicCircleParticleOptions::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, QueenMagicCircleParticleOptions> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, QueenMagicCircleParticleOptions::radius,
			ByteBufCodecs.VAR_INT, QueenMagicCircleParticleOptions::durationTicks,
			ByteBufCodecs.BOOL, QueenMagicCircleParticleOptions::pillar,
			QueenMagicCircleParticleOptions::new);

	public static void send(ServerLevel level, Vec3 position, double radius, int ticks, boolean pillar) {
		level.sendParticles(new QueenMagicCircleParticleOptions(radius, ticks, pillar),
				position.x, position.y, position.z, 1, 0.0, 0.0, 0.0, 0.0);
	}

	@Override
	public ParticleType<QueenMagicCircleParticleOptions> getType() {
		return LcParticleTypes.QUEEN_MAGIC_CIRCLE.get();
	}
}

