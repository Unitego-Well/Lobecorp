package org.unitego.lobecorp.animation;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.cache.animation.Animation;
import org.unitego.lobecorp.animation.LcAnimationPose.Pose;

import java.util.IdentityHashMap;
import java.util.Map;

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

	protected static final class ControllerRuntime {
		protected boolean initialized;
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
