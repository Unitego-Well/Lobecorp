package org.unitego.lobecorp.animation.pose;

import com.geckolib.animation.state.EasingState;
import com.geckolib.cache.animation.BoneAnimation;
import com.geckolib.cache.animation.Keyframe;
import com.geckolib.cache.animation.KeyframeStack;
import com.geckolib.loading.definition.animation.ActorAnimations;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.unitego.lobecorp.animation.adapter.geckolib.LcMolangParser;
import org.unitego.lobecorp.attachment.LcTransform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// 不依赖渲染控制器的资源动画；关键帧保持私有，实例变量只在采样时传入。
public class LcAnimationClip {
	// Bedrock 模型与动画的位移通道使用像素，公共姿态以格为单位。
	private static final double MODEL_UNITS_PER_BLOCK = 16;
	private static final String MISSING_ANIMATION = "资源中不存在动画：";
	private static final String INVALID_DURATION = "动画时长必须是有限的非负秒数：";
	private static final String INVALID_SAMPLE_TIME = "动画采样时间必须是有限秒数";

	private final double durationSeconds;
	private final boolean looping;
	private final List<BoneAnimation> bones;
	private final LcMolangParser expressions;

	private LcAnimationClip(double durationSeconds, boolean looping, List<BoneAnimation> bones, LcMolangParser expressions) {
		this.durationSeconds = durationSeconds;
		this.looping = looping;
		this.bones = List.copyOf(bones);
		this.expressions = expressions;
	}

	@SuppressWarnings("UnstableApiUsage")
	public static LcAnimationClip parse(String animationJson, String animationName) {
		// 版本适配点：仅使用 GeckoLib 5.5.2 的资源解析与关键帧烘焙。
		var definitions = ActorAnimations.GSON.fromJson(animationJson, ActorAnimations.class);
		var animation = Objects.requireNonNull(definitions.animations().get(animationName),
				MISSING_ANIMATION + animationName);
		var parser = new LcMolangParser();
		var bakedBones = new ArrayList<BoneAnimation>();
		var inferredLength = 0.0;

		if (animation.boneAnimations() != null) {
			for (var entry : animation.boneAnimations().entrySet()) {
				bakedBones.add(entry.getValue().bake(entry.getKey(), parser));
				inferredLength = Math.max(inferredLength, entry.getValue().getAnimationLength());
			}
		}

		var duration = animation.animLength() == null
				? (inferredLength == 0 ? Double.MAX_VALUE : inferredLength) : animation.animLength().doubleValue();

		if (!Double.isFinite(duration) || duration < 0) {
			throw new IllegalArgumentException(INVALID_DURATION + animationName);
		}

		var loop = animation.loop() != null && animation.loop().map(value -> value, "loop"::equals);
		return new LcAnimationClip(duration, loop, bakedBones, parser);
	}

	public double durationSeconds() {
		return durationSeconds;
	}

	public boolean looping() {
		return looping;
	}

	public LcSkeletalPose sample(double seconds, ILcAnimationQueries queries) {
		if (!Double.isFinite(seconds)) {
			throw new IllegalArgumentException(INVALID_SAMPLE_TIME);
		}

		var sampleTime = Math.clamp(seconds, 0, durationSeconds);
		var context = new SampleQueries(Objects.requireNonNull(queries), sampleTime);
		return expressions.evaluate(context, () -> sampleBones(sampleTime));
	}

	private LcSkeletalPose sampleBones(double seconds) {
		var transforms = new LinkedHashMap<String, LcTransform>();
		var rotations = new LinkedHashMap<String, Vec3>();

		for (var bone : bones) {
			var nativePosition = sampleVector(bone.positionKeyFrames(), seconds, 0);
			var nativeRotation = sampleVector(bone.rotationKeyFrames(), seconds, 0);
			var scale = sampleVector(bone.scaleKeyFrames(), seconds, 1);
			var position = new Vec3(nativePosition.x, nativePosition.y, -nativePosition.z)
					.scale(1 / MODEL_UNITS_PER_BLOCK);
			// Gecko 烘焙后的 Euler 已包含源轴符号，Y 轴半圈共轭将其转换到公共坐标。
			var rotation = new Vec3(-nativeRotation.x, nativeRotation.y, -nativeRotation.z);
			transforms.put(bone.boneName(), new LcTransform(position,
					new Quaternionf().rotationZYX((float) rotation.z, (float) rotation.y, (float) rotation.x), scale));
			rotations.put(bone.boneName(), rotation);
		}

		return new LcSkeletalPose(transforms, rotations);
	}

	private static Vec3 sampleVector(KeyframeStack stack, double seconds, double fallback) {
		return new Vec3(sampleAxis(stack.xKeyframes(), seconds, fallback),
				sampleAxis(stack.yKeyframes(), seconds, fallback),
				sampleAxis(stack.zKeyframes(), seconds, fallback));
	}

	@SuppressWarnings("DataFlowIssue")
	private static double sampleAxis(Keyframe[] frames, double seconds, double fallback) {
		if (frames.length == 0) {
			return fallback;
		}

		var frameIndex = findFrameIndex(frames, seconds);
		var current = frames[Math.clamp(frameIndex, 0, frames.length - 1)];
		var next = frames[Math.clamp(frameIndex + 1, 0, frames.length - 1)];
		var delta = next.length() == 0 ? 0 : (seconds - current.startTime()) / next.length();
		var easing = new EasingState(next.easingType(), next.easingArgs(), delta,
				current.endValue().get(null), next.endValue().get(null));
		// 与 AnimationProcessor 一致，首尾也执行缓动；pre/post 已由资源烘焙为独立关键帧。
		// 内置缓动只访问数值和采样上下文，不构造客户端 ControllerState。
		return (float) easing.interpolate(null);
	}

	private static int findFrameIndex(Keyframe[] frames, double seconds) {
		if (frames[0].startTime() > seconds) {
			return -1;
		}

		for (var index = 0; index < frames.length - 1; index++) {
			if (frames[index + 1].startTime() >= seconds) {
				return index;
			}
		}

		return frames.length - 1;
	}

	private static class SampleQueries implements ILcAnimationQueries {
		private final ILcAnimationQueries instance;
		private final double seconds;
		private final Map<String, Double> temporary = new HashMap<>();

		private SampleQueries(ILcAnimationQueries instance, double seconds) {
			this.instance = instance;
			this.seconds = seconds;
		}

		@Override
		public double get(String name) {
			if (name.equals("query.anim_time")) {
				return seconds;
			}

			if (name.startsWith("temp.")) {
				return temporary.getOrDefault(name, 0.0);
			}

			return instance.get(name);
		}

		@Override
		public void set(String name, double value) {
			if (name.startsWith("temp.")) {
				temporary.put(name, value);
				return;
			}

			instance.set(name, value);
		}
	}
}
