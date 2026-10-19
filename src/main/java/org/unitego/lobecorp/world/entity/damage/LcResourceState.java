package org.unitego.lobecorp.world.entity.damage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/// 记录当前值及上次结算的上限，使卸载、存档往返和上限变化保持资源百分比。
public record LcResourceState(float rationality, float theSoul, float maxRationality, float maxTheSoul) {
	public static final Codec<LcResourceState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.fieldOf("rationality").forGetter(LcResourceState::rationality),
			Codec.FLOAT.fieldOf("the_soul").forGetter(LcResourceState::theSoul),
			Codec.FLOAT.fieldOf("max_rationality").forGetter(LcResourceState::maxRationality),
			Codec.FLOAT.fieldOf("max_the_soul").forGetter(LcResourceState::maxTheSoul)
	).apply(instance, LcResourceState::new));

	public static final StreamCodec<ByteBuf, LcResourceState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, LcResourceState::rationality,
			ByteBufCodecs.FLOAT, LcResourceState::theSoul,
			ByteBufCodecs.FLOAT, LcResourceState::maxRationality,
			ByteBufCodecs.FLOAT, LcResourceState::maxTheSoul,
			LcResourceState::new);
}
