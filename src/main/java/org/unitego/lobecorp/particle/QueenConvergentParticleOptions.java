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

/// 聚爆表现通过实体与运行实例编号绑定；时间单位均为游戏 tick。
@NullMarked
public record QueenConvergentParticleOptions(int entityId, long runtimeId, long startGameTime,
                                             int durationTicks, int pulseTicks,
                                             boolean star) implements ParticleOptions {
	public static final MapCodec<QueenConvergentParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.INT.fieldOf("entity_id").forGetter(QueenConvergentParticleOptions::entityId),
			Codec.LONG.fieldOf("runtime_id").forGetter(QueenConvergentParticleOptions::runtimeId),
			Codec.LONG.fieldOf("start_time").forGetter(QueenConvergentParticleOptions::startGameTime),
			Codec.intRange(1, Integer.MAX_VALUE).fieldOf("duration").forGetter(QueenConvergentParticleOptions::durationTicks),
			Codec.intRange(1, Integer.MAX_VALUE).fieldOf("pulse").forGetter(QueenConvergentParticleOptions::pulseTicks),
			Codec.BOOL.fieldOf("star").forGetter(QueenConvergentParticleOptions::star)
	).apply(instance, QueenConvergentParticleOptions::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, QueenConvergentParticleOptions> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, QueenConvergentParticleOptions::entityId,
			ByteBufCodecs.VAR_LONG, QueenConvergentParticleOptions::runtimeId,
			ByteBufCodecs.VAR_LONG, QueenConvergentParticleOptions::startGameTime,
			ByteBufCodecs.VAR_INT, QueenConvergentParticleOptions::durationTicks,
			ByteBufCodecs.VAR_INT, QueenConvergentParticleOptions::pulseTicks,
			ByteBufCodecs.BOOL, QueenConvergentParticleOptions::star,
			QueenConvergentParticleOptions::new);

	@Override
	public ParticleType<QueenConvergentParticleOptions> getType() {
		return LcParticleTypes.QUEEN_CONVERGENT.get();
	}
}
