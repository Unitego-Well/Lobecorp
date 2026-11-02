package org.unitego.lobecorp.client.hud.host;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 仅在关卡/玩家对象身份发生变化时切换会话；不使用 UUID 或维度名称代替。
///
/// 管理器传入的是 [net.minecraft.client.multiplayer.ClientLevel] 与
/// [net.minecraft.client.player.LocalPlayer] 实例，比较保持 `==`。
@NullMarked
public final class HudSessionTracker {
	private @Nullable Object level;
	private @Nullable Object player;

	/// 更新当前对象身份并返回会话是否发生变化。
	public boolean update(@Nullable Object level, @Nullable Object player) {
		boolean changed = this.level != level || this.player != player;
		this.level = level;
		this.player = player;
		return changed;
	}

	/// 更新当前会话并返回是否发生变化。
	public boolean update(HudSession next) {
		return update(next.level(), next.player());
	}

	/// 当前会话是否缺少关卡或玩家。
	public boolean unavailable() {
		return level == null || player == null;
	}

	/// 清空会话身份，使下一次更新视为新会话。
	public void clear() {
		level = null;
		player = null;
	}
}
