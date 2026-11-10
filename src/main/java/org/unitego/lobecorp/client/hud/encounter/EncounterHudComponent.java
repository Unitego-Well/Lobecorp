package org.unitego.lobecorp.client.hud.encounter;

import org.unitego.lobecorp.client.hud.ContainerHudRenderer;
import org.jspecify.annotations.NullMarked;

/// 每个活动事件独占一个组件；父容器负责定位和堆叠。
@NullMarked
public abstract class EncounterHudComponent extends ContainerHudRenderer {
	/// 组件当前高度，供事件堆叠使用。
	public int height() {
		return (int) getHeight();
	}
}
