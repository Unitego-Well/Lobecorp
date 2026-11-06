package org.unitego.lobecorp.client.hud.host;

import org.jspecify.annotations.NullMarked;

/// 管理器持有的单个 HUD 宿主生命周期。
@NullMarked
public interface IHudHost {
	/// 确保宿主已创建并按当前屏幕尺寸初始化；重复调用应保持幂等。
	void ensureInitialized(int screenWidth, int screenHeight);

	/// 在当帧作用域内提取一次 HUD。
	void render(HudFrame frame);

	/// 释放宿主持有的 UI 与业务引用；重复调用不得重复释放。
	void release();
}
