package org.unitego.lobecorp.client.hud.widget;

import net.minecraft.client.Minecraft;
import org.unitego.lobecorp.client.hud.TextureHudRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;

/// 可复用的全屏纹理滤镜；透明度作为每份提取状态的颜色，不污染后续 GUI 绘制。
@NullMarked
public class ScreenFilter extends TextureHudRenderer {

	public ScreenFilter(Identifier texture) {
		super(texture, Type.TEXTURE);
		setAlpha(0);
	}

	public static Identifier texture(String name) {
		return Lobecorp.id("textures/gui/filter/" + name + ".png");
	}

	/// 按当前透明度铺满屏幕；缺失资源跳过，不影响后续 GUI。
	public void render(GuiGraphicsExtractor graphics) {
		if (getAlpha() <= 0 || Minecraft.getInstance().getResourceManager().getResource(getTexture()).isEmpty()) {
			return;
		}

		setSize(graphics.guiWidth(), graphics.guiHeight());
		super.render(graphics);
	}


}
