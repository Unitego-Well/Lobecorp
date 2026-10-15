package org.unitego.lobecorp.particle.text;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/// 单字局部动画关键帧；旋转为度，缩放允许零值和镜像，不包含位移通道。
public record TextGlyphKeyframe(int tick, Vec3 rotation, Vec3 scale) {
	private static final String INVALID_TICK = "单字关键帧时间不能为负数";
	private static final String INVALID_VECTOR = "单字旋转与缩放必须为有限数值";
	private static final Codec<Vec3> VECTOR_CODEC = Vec3.CODEC.validate(TextGlyphKeyframe::validateVector);
	public static final Codec<TextGlyphKeyframe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.intRange(0, Integer.MAX_VALUE).fieldOf("tick").forGetter(TextGlyphKeyframe::tick),
			VECTOR_CODEC.fieldOf("rotation").forGetter(TextGlyphKeyframe::rotation),
			VECTOR_CODEC.fieldOf("scale").forGetter(TextGlyphKeyframe::scale)
	).apply(instance, TextGlyphKeyframe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, TextGlyphKeyframe> STREAM_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public TextGlyphKeyframe {
		if (tick < 0) {
			throw new IllegalArgumentException(INVALID_TICK);
		}

		if (!rotation.isFinite() || !scale.isFinite()) {
			throw new IllegalArgumentException(INVALID_VECTOR);
		}
	}

	private static DataResult<Vec3> validateVector(Vec3 vector) {
		return vector.isFinite() ? DataResult.success(vector) : DataResult.error(() -> INVALID_VECTOR);
	}
}
