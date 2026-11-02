package org.unitego.lobecorp.client.conductor.hud;

import static org.unitego.lobecorp.client.conductor.hud.ConductorHudTheme.TOOLTIP_DELAY_MS;

/// 指挥家 HUD 的悬浮目标计时；目标只在准备阶段变化。
public class ConductorHudTooltipState {
	private String targetId = "";
	private long since;

	/// 采样当前悬浮目标；目标变化时重置显示起点。
	public void prepareTarget(String target, long now) {
		String key = target == null ? "" : target;

		if (!key.equals(targetId)) {
			targetId = key;
			since = now;
		}
	}

	/// 判断当前目标是否已超过显示延迟；不修改计时。
	public boolean visible(long now) {
		return !targetId.isEmpty() && now - since >= TOOLTIP_DELAY_MS;
	}

	public String targetId() {
		return targetId;
	}

	public void clear() {
		targetId = "";
		since = 0L;
	}
}
