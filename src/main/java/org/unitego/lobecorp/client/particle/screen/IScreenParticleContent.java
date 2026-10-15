package org.unitego.lobecorp.client.particle.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/// 内容以自身中心为原点，尺寸使用 GUI 像素；位置、动画与生命周期由屏幕粒子管理。
public interface IScreenParticleContent {

	float width();

	float height();

	void extract(GuiGraphicsExtractor graphics, float opacity);
}
