package org.unitego.lobecorp.client.animation;

import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.animation.LcAnimationLodCache.Request;

public interface LcAnimationLodStateAccess {
	@Nullable Request lobecorp$lodRequest();

	void lobecorp$setLodRequest(@Nullable Request request);
}
