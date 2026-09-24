package org.unitego.lobecorp.animation;

import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;

import java.util.List;

public record Playback(RawAnimation animation, double speed, boolean reversed, int transitionTicks,
                       LcTransitionMode transitionMode) {
	public boolean autoEnds() {
		List<RawAnimation.Stage> stages = animation.getAnimationStages();
		return !stages.isEmpty() && stages.getLast().loopType() == LoopType.PLAY_ONCE;
	}
}
