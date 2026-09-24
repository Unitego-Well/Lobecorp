package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

public class LayerController<A extends GeoAnimatable> extends AnimationController<A> {
	private final Supplier<@Nullable Playback> playback;
	private final HandlerDelegate<A> handlerDelegate;

	LayerController(String name, Supplier<@Nullable Playback> playback) {
		this(name, new HandlerDelegate<>(playback));
	}

	private LayerController(String name, HandlerDelegate<A> handlerDelegate) {
		super(name, 0, handlerDelegate);
		this.playback = handlerDelegate.playback;
		this.handlerDelegate = handlerDelegate;
	}

	public void setAnimationStateHandler(AnimationStateHandler<A> handler) {
		handlerDelegate.customHandler = Objects.requireNonNull(handler);
	}

	public void clearAnimationStateHandler() {
		handlerDelegate.customHandler = null;
	}

	public boolean hasCustomAnimationStateHandler() {
		return handlerDelegate.customHandler != null;
	}

	public @Nullable RawAnimation currentAnimation() {
		return currentRawAnimation;
	}

	public void prepareRestart() {
		this.currentRawAnimation = null;
	}

	public boolean hasResolvedAnimation() {
		return timeline != null;
	}

	@Override
	public void initializeNewAnimation(A animatable, GeoRenderState renderState, GeoModel<A> geoModel,
	                                      double previousAnimationSpeed, int previousTransitionTicks) {
		super.initializeNewAnimation(animatable, renderState, geoModel, previousAnimationSpeed,
				previousTransitionTicks);
		Playback current = playback.get();
		if (current == null || !current.reversed() || currentRawAnimation == null) {
			return;
		}
		this.timeline = LcAnimationController.createReversedTimeline(currentRawAnimation, animatable, geoModel,
				triggeredAnimTime > 0 ? previousTransitionTicks : transitionTicks);
		this.animationPoint = timeline == null ? null
				: timeline.createAnimationPoint(timelineTime, null, easingOverride);
	}

	private static class HandlerDelegate<A extends GeoAnimatable> implements AnimationStateHandler<A> {
		private final Supplier<@Nullable Playback> playback;
		private @Nullable AnimationStateHandler<A> customHandler;

		private HandlerDelegate(Supplier<@Nullable Playback> playback) {
			this.playback = playback;
		}

		@Override
		public PlayState handle(AnimationTest<A> test) {
			if (customHandler != null) {
				return customHandler.handle(test);
			}
			Playback current = playback.get();
			return current == null ? PlayState.STOP : test.setAndContinue(current.animation());
		}
	}
}
