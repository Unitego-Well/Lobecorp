package org.unitego.lobecorp.client.hud.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.util.client.gui.GuiUtil;

/// 可复用的全屏纹理滤镜；透明度作为每份提取状态的颜色，不污染后续 GUI 绘制。
public class ScreenFilter {
	private final Identifier texture;
	private float alpha;

	public ScreenFilter(Identifier texture) {
		this.texture = texture;
	}

	public void setAlpha(float value) {
		alpha = Float.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
	}

	public float getAlpha() {
		return alpha;
	}

	public void render(GuiGraphicsExtractor graphics) {
		if (alpha <= 0 || Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty()) {
			return;
		}

		int color = Math.round(alpha * 255) << 24 | 0xFFFFFF;
		GuiUtil.blit(graphics, texture, 0, 0, graphics.guiWidth(), graphics.guiHeight(), 0, 0, 1, 1, color);
	}
}
