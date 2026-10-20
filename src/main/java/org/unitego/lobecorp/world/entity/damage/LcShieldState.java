package org.unitego.lobecorp.world.entity.damage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.unitego.lobecorp.api.LcDamageType;

/// 四种固定护盾的当前量和授予时容量；容量同步到客户端，不读取客户端的服务端配置。
public record LcShieldState(Pool physics, Pool spirit, Pool erosion, Pool soul) {
	public static final LcShieldState EMPTY = new LcShieldState(Pool.EMPTY, Pool.EMPTY, Pool.EMPTY, Pool.EMPTY);
	public static final Codec<LcShieldState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Pool.CODEC.fieldOf("physics").forGetter(LcShieldState::physics),
			Pool.CODEC.fieldOf("spirit").forGetter(LcShieldState::spirit),
			Pool.CODEC.fieldOf("erosion").forGetter(LcShieldState::erosion),
			Pool.CODEC.fieldOf("soul").forGetter(LcShieldState::soul)
	).apply(instance, LcShieldState::new));
	public static final StreamCodec<ByteBuf, LcShieldState> STREAM_CODEC = StreamCodec.composite(
			Pool.STREAM_CODEC, LcShieldState::physics, Pool.STREAM_CODEC, LcShieldState::spirit,
			Pool.STREAM_CODEC, LcShieldState::erosion, Pool.STREAM_CODEC, LcShieldState::soul, LcShieldState::new);

	public Pool get(LcDamageType type) {
		return switch (type) {
			case PHYSICS -> physics;
			case SPIRIT -> spirit;
			case EROSION -> erosion;
			case THE_SOUL -> soul;
		};
	}

	public LcShieldState with(LcDamageType type, Pool pool) {
		return new LcShieldState(type == LcDamageType.PHYSICS ? pool : physics,
				type == LcDamageType.SPIRIT ? pool : spirit, type == LcDamageType.EROSION ? pool : erosion,
				type == LcDamageType.THE_SOUL ? pool : soul);
	}

	public record Pool(float amount, float maximum) {
		public static final Pool EMPTY = new Pool(0, 0);
		public static final Codec<Pool> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.FLOAT.fieldOf("amount").forGetter(Pool::amount),
				Codec.FLOAT.fieldOf("maximum").forGetter(Pool::maximum)
		).apply(instance, Pool::new));
		public static final StreamCodec<ByteBuf, Pool> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, Pool::amount, ByteBufCodecs.FLOAT, Pool::maximum, Pool::new);

		public Pool {
			maximum = Float.isFinite(maximum) ? Math.max(0, maximum) : 0;
			amount = Float.isFinite(amount) ? Math.clamp(amount, 0, maximum) : 0;
		}
	}
}
