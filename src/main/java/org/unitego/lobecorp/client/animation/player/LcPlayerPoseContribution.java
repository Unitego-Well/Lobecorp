package org.unitego.lobecorp.client.animation.player;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.LcControllerBlendType;
import org.unitego.lobecorp.animation.LcRotationTransitionMode;
import org.unitego.lobecorp.animation.LcTransitionMode;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy.Channel;

import java.util.LinkedHashMap;
import java.util.Map;

/// 一层自身的不可变姿态操作；过渡起点不包含其他控制器的合成结果。
public abstract class LcPlayerPoseContribution {
	private static final Vec3 ONE = new Vec3(1, 1, 1);
	private static final LcPlayerPoseContribution NONE = new Identity();

	public static LcPlayerPoseContribution identity() {
		return NONE;
	}

	public static LcPlayerPoseContribution of(LcPlayerModelPose pose, LcPlayerAnimationPolicy policy) {
		return of(pose, policy, null);
	}

	public static LcPlayerPoseContribution of(LcPlayerModelPose pose, LcPlayerAnimationPolicy policy,
	                                          @Nullable LcPlayerPoseContribution previous) {
		var direct = previous instanceof Transition transition ? transition.target : previous;
		return new Direct(pose, policy, direct instanceof Direct old && old.policy.equals(policy) ? old.sample : null);
	}

	public static LcPlayerPoseContribution blend(LcPlayerPoseContribution origin, LcPlayerPoseContribution target,
	                                             double progress, LcTransitionMode mode,
	                                             LcRotationTransitionMode rotation) {
		return blend(origin, target, progress, mode, rotation, null);
	}

	public static LcPlayerPoseContribution blend(LcPlayerPoseContribution origin, LcPlayerPoseContribution target,
	                                             double progress, LcTransitionMode mode,
	                                             LcRotationTransitionMode rotation,
	                                             @Nullable LcPlayerPoseContribution previous) {
		if (progress <= 0) {
			return origin;
		}

		if (progress >= 1) {
			return target;
		}

		var sample = previous instanceof Transition old && old.mode == mode && old.rotation == rotation
				&& (mode != LcTransitionMode.SEQUENTIAL || (old.progress < 0.5) == (progress < 0.5)) ? old.sample : null;
		return new Transition(origin, target, progress, mode, rotation, sample);
	}

	public LcPlayerModelPose apply(LcPlayerModelPose base, LcPlayerModelPose reference) {
		return evaluate(base, reference).pose();
	}

	/// 求值返回下一帧状态；冻结或重复渲染不会修改已经保存的过渡分支。
	public abstract Evaluation evaluate(LcPlayerModelPose base, LcPlayerModelPose reference);

	public record Evaluation(LcPlayerModelPose pose, LcPlayerPoseContribution displayed,
	                         LcPlayerPoseContribution origin) {
	}

	protected abstract Map<String, Factors> factors(LcPlayerModelPose reference);

	/// 冻结本层当前输出及基底保留比例，不递归保存历次打断，也不冻结其他层。
	public LcPlayerPoseContribution freeze(LcPlayerModelPose base, LcPlayerModelPose reference) {
		return this instanceof Transition ? new Frozen(base, apply(base, reference), factors(reference)) : this;
	}

	protected record Factors(Vec3 position, Vec3 rotation, Vec3 scale) {
		private static Factors identity() {
			return new Factors(ONE, ONE, ONE);
		}

		private Factors blend(Factors next, double progress) {
			return new Factors(position.lerp(next.position, progress), rotation.lerp(next.rotation, progress),
					scale.lerp(next.scale, progress));
		}
	}

	private static class Identity extends LcPlayerPoseContribution {
		@Override
		public Evaluation evaluate(LcPlayerModelPose base, LcPlayerModelPose reference) {
			return new Evaluation(base, this, this);
		}

		@Override
		protected Map<String, Factors> factors(LcPlayerModelPose reference) {
			var result = new LinkedHashMap<String, Factors>();
			reference.parts().keySet().forEach(bone -> result.put(bone, Factors.identity()));
			return result;
		}
	}

	private static class Direct extends LcPlayerPoseContribution {
		private final LcPlayerModelPose pose;
		private final LcPlayerAnimationPolicy policy;
		private final LcPlayerModelPose.@Nullable BlendSample sample;

		private Direct(LcPlayerModelPose pose, LcPlayerAnimationPolicy policy, LcPlayerModelPose.@Nullable BlendSample sample) {
			this.pose = pose;
			this.policy = policy;
			this.sample = sample;
		}

		@Override
		public Evaluation evaluate(LcPlayerModelPose base, LcPlayerModelPose reference) {
			var target = policy.transitions().blendType() == LcControllerBlendType.ADDITIVE
					? pose.addedTo(reference, base) : pose;
			var next = base.blend(target, this::weight, policy.transitions().rotationTransitionMode(), sample);
			var displayed = new Direct(pose, policy, next);
			return new Evaluation(next.pose(), displayed, displayed);
		}

		@Override
		protected Map<String, Factors> factors(LcPlayerModelPose reference) {
			var result = new LinkedHashMap<String, Factors>();
			boolean additive = policy.transitions().blendType() == LcControllerBlendType.ADDITIVE;
			reference.parts().forEach((bone, initial) -> {
				var target = pose.parts().getOrDefault(bone, initial);
				double position = additive ? 1 : 1 - weight(bone, Channel.TRANSLATION);
				double rotation = additive ? 1 : 1 - weight(bone, Channel.ROTATION);
				double scale = weight(bone, Channel.SCALE);
				var scales = additive ? new Vec3(scaleFactor(initial.xScale(), target.xScale(), scale),
						scaleFactor(initial.yScale(), target.yScale(), scale),
						scaleFactor(initial.zScale(), target.zScale(), scale)) : ONE.scale(1 - scale);
				result.put(bone, new Factors(ONE.scale(position), ONE.scale(rotation), scales));
			});
			return result;
		}

		private double weight(String bone, Channel channel) {
			return policy.includesBone(bone) && policy.includesChannel(channel) ? policy.weight() : 0;
		}

		private static double scaleFactor(float initial, float target, double weight) {
			return initial == 0 ? 1 : 1 + (target / initial - 1) * weight;
		}
	}

	private static class Transition extends LcPlayerPoseContribution {
		private final LcPlayerPoseContribution origin;
		private final LcPlayerPoseContribution target;
		private final double progress;
		private final LcTransitionMode mode;
		private final LcRotationTransitionMode rotation;
		private final LcPlayerModelPose.@Nullable BlendSample sample;

		private Transition(LcPlayerPoseContribution origin, LcPlayerPoseContribution target, double progress,
		                   LcTransitionMode mode, LcRotationTransitionMode rotation,
		                   LcPlayerModelPose.@Nullable BlendSample sample) {
			this.origin = origin;
			this.target = target;
			this.progress = progress;
			this.mode = mode;
			this.rotation = rotation;
			this.sample = sample;
		}

		@Override
		public Evaluation evaluate(LcPlayerModelPose base, LcPlayerModelPose reference) {
			var previous = origin.evaluate(base, reference);
			var next = target.evaluate(base, reference);
			LcPlayerModelPose.BlendSample blended;
			if (mode == LcTransitionMode.SEQUENTIAL) {
				blended = progress < 0.5 ? previous.pose().blend(base, (_, _) -> progress * 2, rotation, sample)
						: base.blend(next.pose(), (_, _) -> progress * 2 - 1, rotation, sample);
			} else {
				blended = previous.pose().blend(next.pose(), (_, _) -> progress, rotation, sample);
			}

			return new Evaluation(blended.pose(), new Transition(previous.displayed(), next.displayed(), progress,
					mode, rotation, blended), previous.displayed());
		}

		@Override
		protected Map<String, Factors> factors(LcPlayerModelPose reference) {
			var previous = origin.factors(reference);
			var next = target.factors(reference);
			var result = new LinkedHashMap<String, Factors>();
			previous.forEach((bone, value) -> result.put(bone, mode == LcTransitionMode.SEQUENTIAL
					? progress < 0.5 ? value.blend(Factors.identity(), progress * 2)
					: Factors.identity().blend(next.get(bone), progress * 2 - 1)
					: value.blend(next.get(bone), progress)));
			return result;
		}
	}

	private static class Frozen extends LcPlayerPoseContribution {
		private final LcPlayerModelPose before;
		private final LcPlayerModelPose after;
		private final Map<String, Factors> factors;

		private Frozen(LcPlayerModelPose before, LcPlayerModelPose after, Map<String, Factors> factors) {
			this.before = before;
			this.after = after;
			this.factors = Map.copyOf(factors);
		}

		@Override
		public Evaluation evaluate(LcPlayerModelPose base, LcPlayerModelPose reference) {
			var result = new LinkedHashMap<>(base.parts());
			after.parts().forEach((bone, end) -> {
				var start = before.parts().get(bone);
				var current = base.parts().get(bone);
				var factor = factors.get(bone);
				result.put(bone, new PartPose(
						shift(end.x(), start.x(), current.x(), factor.position.x),
						shift(end.y(), start.y(), current.y(), factor.position.y),
						shift(end.z(), start.z(), current.z(), factor.position.z),
						shift(end.xRot(), start.xRot(), current.xRot(), factor.rotation.x),
						shift(end.yRot(), start.yRot(), current.yRot(), factor.rotation.y),
						shift(end.zRot(), start.zRot(), current.zRot(), factor.rotation.z),
						shift(end.xScale(), start.xScale(), current.xScale(), factor.scale.x),
						shift(end.yScale(), start.yScale(), current.yScale(), factor.scale.y),
						shift(end.zScale(), start.zScale(), current.zScale(), factor.scale.z)));
			});
			return new Evaluation(new LcPlayerModelPose(result), this, this);
		}

		@Override
		protected Map<String, Factors> factors(LcPlayerModelPose reference) {
			return factors;
		}

		private static float shift(float output, float input, float current, double factor) {
			return (float) (output + (current - input) * factor);
		}
	}
}
