package org.unitego.lobecorp.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;

/// 持续激光绑定拥有者和施放实例；起点、方向和方块截断在渲染时读取实体。
@NullMarked
public record QueenChannelLaserParticleOptions(int entityId, long runtimeId, long startGameTime, int durationTicks) implements ParticleOptions {
	public static final MapCodec<QueenChannelLaserParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.INT.fieldOf("entity_id").forGetter(QueenChannelLaserParticleOptions::entityId),
			Codec.LONG.fieldOf("runtime_id").forGetter(QueenChannelLaserParticleOptions::runtimeId),
			Codec.LONG.fieldOf("start_time").forGetter(QueenChannelLaserParticleOptions::startGameTime),
			Codec.intRange(1, Integer.MAX_VALUE).fieldOf("duration").forGetter(QueenChannelLaserParticleOptions::durationTicks)
	).apply(instance, QueenChannelLaserParticleOptions::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, QueenChannelLaserParticleOptions> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, QueenChannelLaserParticleOptions::entityId,
			ByteBufCodecs.VAR_LONG, QueenChannelLaserParticleOptions::runtimeId,
			ByteBufCodecs.VAR_LONG, QueenChannelLaserParticleOptions::startGameTime,
			ByteBufCodecs.VAR_INT, QueenChannelLaserParticleOptions::durationTicks,
			QueenChannelLaserParticleOptions::new);

	@Override
	public ParticleType<QueenChannelLaserParticleOptions> getType() {
		return LcParticleTypes.QUEEN_CHANNEL_LASER.get();
	}
}

