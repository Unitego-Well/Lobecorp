package org.unitego.lobecorp.client.hud.host;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 会话身份只比较关卡与玩家对象引用，不携带业务状态。
@NullMarked
public record HudSession(@Nullable Object level, @Nullable Object player) {
	/// 当前会话是否缺少关卡或玩家。
	public boolean unavailable() {
		return level == null || player == null;
	}
}
