package org.unitego.lobecorp.mixin.geckolib.client;

import com.geckolib.animation.state.ControllerState;
import org.spongepowered.asm.mixin.Mixin;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.animation.LcAnimationLodCache.Request;
import org.unitego.lobecorp.client.animation.LcAnimationLodStateAccess;

@Mixin(ControllerState.class)
public abstract class ControllerStateLodMixin implements LcAnimationLodStateAccess {
	@Unique
	private @Nullable Request lobecorp$lodRequest;

	@Override
	public @Nullable Request lobecorp$lodRequest() {
		return lobecorp$lodRequest;
	}

	@Override
	public void lobecorp$setLodRequest(@Nullable Request request) {
		lobecorp$lodRequest = request;
	}
}
