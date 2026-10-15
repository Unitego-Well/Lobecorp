package org.unitego.lobecorp.animation.player;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.animation.LcAnimationTransitionSettings;
import org.unitego.lobecorp.animation.LcControllerBlendType;
import org.unitego.lobecorp.animation.LcRotationTransitionMode;
import org.unitego.lobecorp.animation.LcTransitionMode;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// 玩家视觉接管策略；空来源表示完全覆盖，空骨骼或通道集合分别表示全部骨骼或通道。
/// 来源权重只作用于已登记的阶段，不从最终模型姿态推断动作来源。
public record LcPlayerAnimationPolicy(Map<Identifier, Double> sources, Set<String> bones, Set<Channel> channels,
                                      double weight,
                                      int priority, LcAnimationTransitionSettings transitions,
                                      boolean firstPersonArms) {

	private static final String INVALID_WEIGHT = "Player animation weight must be finite and between zero and one";
	private static final String INVALID_TRANSITION = "Player animation transition duration must be non-negative";
	private static final LcAnimationTransitionSettings IMMEDIATE = new LcAnimationTransitionSettings(0, 0,
			LcTransitionMode.OVERLAP, LcTransitionMode.OVERLAP, LcRotationTransitionMode.NORMAL,
			LcRotationTransitionMode.NORMAL, LcTransitionMode.OVERLAP, LcRotationTransitionMode.NORMAL,
			LcControllerBlendType.OVERRIDE);

	public LcPlayerAnimationPolicy {
		sources = Map.copyOf(sources);
		bones = Set.copyOf(bones);
		channels = Set.copyOf(channels);
		Objects.requireNonNull(transitions);
		requireWeight(weight);
		sources.values().forEach(LcPlayerAnimationPolicy::requireWeight);

		if (transitions.fadeInTicks() < 0 || transitions.fadeOutTicks() < 0) {
			throw new IllegalArgumentException(INVALID_TRANSITION);
		}
	}

	public static LcPlayerAnimationPolicy fullOverride() {
		return new LcPlayerAnimationPolicy(Map.of(), Set.of(), Set.of(), 1, 0, IMMEDIATE, false);
	}

	public LcPlayerAnimationPolicy withSources(Map<Identifier, Double> values) {
		return new LcPlayerAnimationPolicy(values, bones, channels, weight, priority, transitions, firstPersonArms);
	}

	public LcPlayerAnimationPolicy withBones(Set<String> values) {
		return new LcPlayerAnimationPolicy(sources, values, channels, weight, priority, transitions, firstPersonArms);
	}

	public LcPlayerAnimationPolicy withChannels(Set<Channel> values) {
		return new LcPlayerAnimationPolicy(sources, bones, values, weight, priority, transitions, firstPersonArms);
	}

	public LcPlayerAnimationPolicy withWeight(double value) {
		return new LcPlayerAnimationPolicy(sources, bones, channels, value, priority, transitions, firstPersonArms);
	}

	public LcPlayerAnimationPolicy withPriority(int value) {
		return new LcPlayerAnimationPolicy(sources, bones, channels, weight, value, transitions, firstPersonArms);
	}

	public LcPlayerAnimationPolicy withTransitions(LcAnimationTransitionSettings value) {
		return new LcPlayerAnimationPolicy(sources, bones, channels, weight, priority, value, firstPersonArms);
	}

	/// 显式接管本次播放的第一人称手臂；默认关闭，视觉淡出结束后恢复原版路径。
	public LcPlayerAnimationPolicy withFirstPersonArms(boolean value) {
		return new LcPlayerAnimationPolicy(sources, bones, channels, weight, priority, transitions, value);
	}

	public double sourceWeight(Identifier source) {
		return sources.getOrDefault(source, 0.0);
	}

	public boolean includesBone(String bone) {
		return bones.isEmpty() || bones.contains(bone);
	}

	public boolean includesChannel(Channel channel) {
		return channels.isEmpty() || channels.contains(channel);
	}

	public enum Channel {
		TRANSLATION,
		ROTATION,
		SCALE
	}

	private static void requireWeight(double value) {
		if (!Double.isFinite(value) || value < 0 || value > 1) {
			throw new IllegalArgumentException(INVALID_WEIGHT);
		}
	}
}
