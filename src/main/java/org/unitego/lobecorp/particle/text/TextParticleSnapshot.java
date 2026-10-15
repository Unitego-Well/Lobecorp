package org.unitego.lobecorp.particle.text;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// 服务端文本实例的完整传输快照；起始 tick 供客户端定位逐字和单字动画进度。
///
/// 单字字号以世界空间格为单位，整体旋转为度；描边为 ARGB，零值关闭描边。
public record TextParticleSnapshot(UUID id, long revision, long startedTick, Component text, Vec3 position,
                                   Vec3 rotation, float size, TextRevealMode revealMode, int characterIntervalTicks,
                                   TextGlyphAnimation animation, Map<Integer, TextGlyphAnimation> characterAnimations,
                                   int outlineColor) {
	private static final String INVALID_SIZE = "文本单字字号必须是有限正数";
	private static final String INVALID_INTERVAL = "文本逐字间隔至少为一 tick";
	private static final String INVALID_VECTOR = "文本位置和旋转必须为有限数值";
	private static final String INVALID_CHARACTER_INDEX = "文本单字动画索引必须是非负整数";
	private static final Codec<Float> SIZE_CODEC = Codec.FLOAT.validate(TextParticleSnapshot::validateSize);
	private static final Codec<Vec3> VECTOR_CODEC = Vec3.CODEC.validate(TextParticleSnapshot::validateVector);
	private static final Codec<Integer> CHARACTER_INDEX_CODEC =
			Codec.STRING.comapFlatMap(TextParticleSnapshot::parseCharacterIndex, Object::toString);
	public static final Codec<TextParticleSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(TextParticleSnapshot::id),
			Codec.LONG.fieldOf("revision").forGetter(TextParticleSnapshot::revision),
			Codec.LONG.fieldOf("started_tick").forGetter(TextParticleSnapshot::startedTick),
			ComponentSerialization.CODEC.fieldOf("text").forGetter(TextParticleSnapshot::text),
			VECTOR_CODEC.fieldOf("position").forGetter(TextParticleSnapshot::position),
			VECTOR_CODEC.fieldOf("rotation").forGetter(TextParticleSnapshot::rotation),
			SIZE_CODEC.fieldOf("size").forGetter(TextParticleSnapshot::size),
			TextRevealMode.CODEC.fieldOf("reveal_mode").forGetter(TextParticleSnapshot::revealMode),
			Codec.intRange(1, Integer.MAX_VALUE).fieldOf("character_interval_ticks")
					.forGetter(TextParticleSnapshot::characterIntervalTicks),
			TextGlyphAnimation.CODEC.fieldOf("animation").forGetter(TextParticleSnapshot::animation),
			Codec.unboundedMap(CHARACTER_INDEX_CODEC, TextGlyphAnimation.CODEC).fieldOf("character_animations")
					.forGetter(TextParticleSnapshot::characterAnimations),
			Codec.INT.fieldOf("outline_color").forGetter(TextParticleSnapshot::outlineColor)
	).apply(instance, TextParticleSnapshot::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, TextParticleSnapshot> STREAM_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public TextParticleSnapshot {
		Objects.requireNonNull(id);
		Objects.requireNonNull(text);
		Objects.requireNonNull(position);
		Objects.requireNonNull(rotation);
		Objects.requireNonNull(revealMode);
		Objects.requireNonNull(animation);
		characterAnimations = Map.copyOf(characterAnimations);
		validateSize(size).getOrThrow(IllegalArgumentException::new);
		validateVector(position).getOrThrow(IllegalArgumentException::new);
		validateVector(rotation).getOrThrow(IllegalArgumentException::new);

		if (characterIntervalTicks < 1) {
			throw new IllegalArgumentException(INVALID_INTERVAL);
		}

		for (var index : characterAnimations.keySet()) {
			if (index < 0) {
				throw new IllegalArgumentException(INVALID_CHARACTER_INDEX);
			}
		}
	}

	private static DataResult<Float> validateSize(float size) {
		return Float.isFinite(size) && size > 0 ? DataResult.success(size) : DataResult.error(() -> INVALID_SIZE);
	}

	private static DataResult<Vec3> validateVector(Vec3 vector) {
		return vector.isFinite() ? DataResult.success(vector) : DataResult.error(() -> INVALID_VECTOR);
	}

	private static DataResult<Integer> parseCharacterIndex(String value) {
		try {
			var index = Integer.parseInt(value);
			return index >= 0 ? DataResult.success(index) : DataResult.error(() -> INVALID_CHARACTER_INDEX);
		} catch (NumberFormatException _) {
			return DataResult.error(() -> INVALID_CHARACTER_INDEX);
		}
	}
}
