package org.unitego.lobecorp.animation;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.cache.animation.Animation;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.animation.LcAnimationPose.Pose;
import org.unitego.lobecorp.util.animation.LcRotationTransitionUtil;
import org.unitego.lobecorp.util.animation.LcRotationTransitionUtil.State;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/// 保存每个可动画实例的控制器过渡状态。
public class LcAnimationControllerRuntime {
	protected static double transitionProgress(double currentAge, double startAge, int durationTicks) {
		if (durationTicks <= 0) {
			return 1;
		}

		return Math.clamp((currentAge - startAge) / durationTicks, 0, 1);
	}

	protected static final class RuntimeData {
		protected final Map<AnimationController<?>, ControllerRuntime> controllers = new IdentityHashMap<>();
	}

	/// 重复渲染同一时刻始终读取同一份旧状态，不借额外渲染推进旋转分支。
	protected static class RotationHistory {
		private Map<String, State> previous = Map.of();
		private final Map<String, State> current = new HashMap<>();
		private Map<String, Vector3f> previousAngles = Map.of();
		private final Map<String, Vector3f> currentAngles = new HashMap<>();
		private double age = Double.NaN;
		private @Nullable LcRotationTransitionMode mode;
		private boolean additive;

		protected void beginFrame(double renderAge, LcRotationTransitionMode rotationMode, boolean additive) {
			if (mode != rotationMode || this.additive != additive || renderAge < age) {
				clear();
			}

			mode = rotationMode;
			this.additive = additive;
			if (rotationMode != LcRotationTransitionMode.SHORTEST_PATH) {
				clear();
				return;
			}

			if (Double.compare(age, renderAge) != 0) {
				previous = Map.copyOf(current);
				current.clear();
				previousAngles = Map.copyOf(currentAngles);
				currentAngles.clear();
				age = renderAge;
			}
		}

		protected @Nullable State previous(String bone) {
			return previous.get(bone);
		}

		protected void put(String bone, State state) {
			current.put(bone, state);
		}

		protected Vector3f angles(String bone, Quaternionf rotation, Vector3f fallback) {
			var angles = LcRotationTransitionUtil.nearbyEuler(rotation, previousAngles.getOrDefault(bone, fallback));
			currentAngles.put(bone, new Vector3f(angles));
			return angles;
		}

		protected void clear() {
			previous = Map.of();
			current.clear();
			previousAngles = Map.of();
			currentAngles.clear();
			age = Double.NaN;
			mode = null;
			additive = false;
		}
	}

	protected static final class ControllerRuntime {
		protected final RotationHistory animationRotations = new RotationHistory();
		protected final RotationHistory layerRotations = new RotationHistory();
		protected boolean initialized;
		/// 控制器停止会重置过渡，但不能重新取得首次呈现资格。
		protected boolean presented;
		protected boolean weightTransition;
		protected double weight;
		protected double weightFrom;
		protected double weightTarget;
		protected double weightTransitionStart;
		protected int weightTransitionTicks;
		protected Animation animation;
		protected RawAnimation rawAnimation;
		protected Pose pose;
		protected Pose lastPose;
		protected Pose animationSource;
		protected boolean animationTransition;
		protected double animationTransitionStart;
		protected int animationTransitionTicks;
		protected boolean additive;
		protected boolean controllerExiting;
		protected int animationTriggerRevision;
		protected double animationWeight = 1;
		protected double animationWeightFrom = 1;
		protected double animationWeightTarget = 1;
		protected double animationWeightTransitionStart;
		protected int animationWeightTransitionTicks;
		protected boolean animationWeightTransition;
		protected boolean animationWeightFadingOut;
		protected Animation pendingAnimation;
		protected RawAnimation pendingRawAnimation;
		protected Pose pendingAnimationPose;

		protected void beginAnimationWeightTransition(double target, double startAge, int durationTicks) {
			layerRotations.clear();
			this.animationWeightFrom = this.animationWeight;
			this.animationWeightTarget = target;
			this.animationWeightTransitionStart = startAge;
			this.animationWeightTransitionTicks = durationTicks;
			this.animationWeightTransition = durationTicks > 0 && this.animationWeightFrom != target;
			if (!this.animationWeightTransition) {
				this.animationWeight = target;
			}
		}

		protected void beginWeightTransition(double target, double startAge, int durationTicks, LcAnimationControllerTransitions<?> transitions) {
			animationRotations.clear();
			layerRotations.clear();
			this.weightFrom = this.weight;
			this.weightTarget = target;
			this.weightTransitionStart = startAge;
			this.weightTransitionTicks = durationTicks;
			this.weightTransition = durationTicks > 0 && this.weightFrom != target;
			this.animationTransition = false;
			this.animationSource = null;
			transitions.lobecorp$setAnimationTransitionPaused(false);
			transitions.lobecorp$setControllerTransitionPaused(target == 1 && this.weightTransition
					&& transitions.lobecorp$getFadeInTransitionMode() == LcTransitionMode.SEQUENTIAL);
			if (!this.weightTransition) {
				this.weight = target;
			}
		}

		protected void updateWeight(double renderAge, LcAnimationControllerTransitions<?> transitions) {
			if (!this.weightTransition) {
				return;
			}

			double progress = transitionProgress(renderAge, this.weightTransitionStart, this.weightTransitionTicks);
			this.weight = this.weightFrom + (this.weightTarget - this.weightFrom) * progress;
			if (progress >= 1) {
				this.weightTransition = false;
				if (this.weightTarget == 1) {
					transitions.lobecorp$setControllerTransitionPaused(false);
				}
			}
		}

		protected void reset(LcAnimationControllerTransitions<?> transitions) {
			animationRotations.clear();
			layerRotations.clear();
			this.initialized = false;
			this.weightTransition = false;
			this.weight = 0;
			this.animation = null;
			this.rawAnimation = null;
			this.pose = null;
			this.lastPose = null;
			this.animationSource = null;
			this.animationTransition = false;
			this.animationTriggerRevision = 0;
			this.animationWeight = 1;
			this.animationWeightFrom = 1;
			this.animationWeightTarget = 1;
			this.animationWeightTransition = false;
			this.animationWeightFadingOut = false;
			this.pendingAnimation = null;
			this.pendingRawAnimation = null;
			this.pendingAnimationPose = null;
			this.controllerExiting = false;
			transitions.lobecorp$setAnimationTransitionPaused(false);
			transitions.lobecorp$setControllerTransitionPaused(
					transitions.lobecorp$getFadeInTransitionMode() == LcTransitionMode.SEQUENTIAL);
		}
	}
}
