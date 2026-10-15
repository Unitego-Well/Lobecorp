package org.unitego.lobecorp.particle.text;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/// 单字相对显露时刻的线性关键帧动画；Euler 通道保留完整转圈，不使用最短路径旋转。
public record TextGlyphAnimation(List<TextGlyphKeyframe> keyframes, boolean loop) {
	private static final String EMPTY_KEYFRAMES = "单字动画至少需要一个关键帧";
	private static final String INVALID_FIRST_TICK = "单字动画首帧必须从零 tick 开始";
	private static final String UNORDERED_KEYFRAMES = "单字动画关键帧时间必须严格递增";
	private static final String INVALID_SAMPLE_TIME = "单字动画采样时间必须是有限 tick 数";
	public static final Codec<TextGlyphAnimation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			TextGlyphKeyframe.CODEC.listOf().validate(TextGlyphAnimation::validateKeyframes)
					.fieldOf("keyframes").forGetter(TextGlyphAnimation::keyframes),
			Codec.BOOL.fieldOf("loop").forGetter(TextGlyphAnimation::loop)
	).apply(instance, TextGlyphAnimation::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, TextGlyphAnimation> STREAM_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(CODEC);
	private static final Transform IDENTITY = new Transform(Vec3.ZERO, new Vec3(1, 1, 1));
	/// 无单字动画时保留原始字号和朝向。
	public static final TextGlyphAnimation NONE = new TextGlyphAnimation(
			List.of(new TextGlyphKeyframe(0, IDENTITY.rotation(), IDENTITY.scale())), false);

	public TextGlyphAnimation {
		keyframes = List.copyOf(keyframes);
		validateKeyframes(keyframes).getOrThrow(IllegalArgumentException::new);
	}

	/// 已显露时间以客户端 tick 为单位；非循环动画保持末帧，循环动画以末帧时间为周期。
	public Transform sample(double elapsedTicks) {
		if (!Double.isFinite(elapsedTicks)) {
			throw new IllegalArgumentException(INVALID_SAMPLE_TIME);
		}

		if (equals(NONE)) {
			return IDENTITY;
		}

		var last = keyframes.getLast();
		var sampleTick = Math.max(0, elapsedTicks);
		if (keyframes.size() == 1 || !loop && sampleTick >= last.tick()) {
			return new Transform(last.rotation(), last.scale());
		}

		if (loop) {
			sampleTick %= last.tick();
		}

		var lower = 1;
		var upper = keyframes.size() - 1;
		while (lower < upper) {
			var middle = lower + (upper - lower) / 2;
			if (keyframes.get(middle).tick() >= sampleTick) {
				upper = middle;
			} else {
				lower = middle + 1;
			}
		}

		var previous = keyframes.get(lower - 1);
		var next = keyframes.get(lower);
		var weight = (sampleTick - previous.tick()) / (next.tick() - previous.tick());
		return new Transform(interpolate(previous.rotation(), next.rotation(), weight),
				interpolate(previous.scale(), next.scale(), weight));
	}

	/// 线性缩放不会越过端点；排版时取此上界可一次覆盖整个动画和任意旋转。
	public double maxScale() {
		var maximum = 0.0;
		for (var keyframe : keyframes) {
			var scale = keyframe.scale();
			maximum = Math.max(maximum, Math.max(Math.abs(scale.x), Math.max(Math.abs(scale.y), Math.abs(scale.z))));
		}

		return maximum;
	}

	private static Vec3 interpolate(Vec3 previous, Vec3 next, double weight) {
		var remaining = 1 - weight;
		return new Vec3(previous.x * remaining + next.x * weight,
				previous.y * remaining + next.y * weight, previous.z * remaining + next.z * weight);
	}

	private static DataResult<List<TextGlyphKeyframe>> validateKeyframes(List<TextGlyphKeyframe> keyframes) {
		if (keyframes.isEmpty()) {
			return DataResult.error(() -> EMPTY_KEYFRAMES);
		}

		if (keyframes.getFirst().tick() != 0) {
			return DataResult.error(() -> INVALID_FIRST_TICK);
		}

		var previousTick = 0;
		for (var index = 1; index < keyframes.size(); index++) {
			var tick = keyframes.get(index).tick();
			if (tick <= previousTick) {
				return DataResult.error(() -> UNORDERED_KEYFRAMES);
			}

			previousTick = tick;
		}

		return DataResult.success(keyframes);
	}

	/// 客户端采样结果只包含单字局部旋转和缩放。
	public record Transform(Vec3 rotation, Vec3 scale) {
	}
}
