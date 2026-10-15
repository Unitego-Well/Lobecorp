package org.unitego.lobecorp.attachment;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/// ENTITY 使用 UUID 校验数字 ID；ANIMATION 引用原播放实例，不建立第二个动画时钟。
public record LcAnchorReference(Kind kind, @Nullable UUID sourceId, int entityId,
                                @Nullable String bone, @Nullable String locator) {
	public enum Kind {
		WORLD,
		ENTITY,
		ANIMATION
	}
}

