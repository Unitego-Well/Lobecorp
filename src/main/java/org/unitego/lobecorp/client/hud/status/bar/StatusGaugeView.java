package org.unitego.lobecorp.client.hud.status.bar;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.hud.ContainerHudRenderer;
import org.unitego.lobecorp.client.hud.RectangleHudRenderer;
import org.unitego.lobecorp.client.hud.TextureHudRenderer;
import org.unitego.lobecorp.client.hud.TextureLayer;

/// 原版状态条绘制：组件持有三段纹理，绘制时只读取 [StatusGauge] 的数字状态。
///
/// 填充纹理保持完整宽度，通过裁剪呈现比例，不横向压缩图集动画。
@NullMarked
public class StatusGaugeView extends ContainerHudRenderer {
	private final TextureHudRenderer background;
	private final TextureHudRenderer fill;
	private final TextureHudRenderer flash;
	private final RectangleHudRenderer trail = new RectangleHudRenderer();
	private TextureLayer bottom;
	private TextureLayer internal;
	private @Nullable TextureLayer light;

	public StatusGaugeView(TextureLayer bottom, TextureLayer internal, @Nullable TextureLayer light) {
		background = TextureHudRenderer.sprite(bottom.texture());
		fill = TextureHudRenderer.sprite(internal.texture());
		flash = TextureHudRenderer.sprite(bottom.texture());
		add(background);
		add(trail);
		add(fill);
		add(flash);
		setLayers(bottom, internal, light);
	}

	private static void configure(TextureHudRenderer renderer, TextureLayer layer) {
		renderer.setTexture(layer.texture());
		renderer.setPosition(layer.x(), layer.y());
		renderer.setSize(layer.width(), layer.height());
	}

	public void setLayers(TextureLayer bottom, TextureLayer internal, @Nullable TextureLayer light) {
		this.bottom = bottom;
		this.internal = internal;
		setSize(Math.max(bottom.x() + bottom.width(), internal.x() + internal.width()),
				Math.max(bottom.y() + bottom.height(), internal.y() + internal.height()));
		this.light = light;
	}

	/// 在指定屏幕坐标绘制底图、损失拖尾、裁切填充与闪光。
	public void render(GuiGraphicsExtractor graphics, float x, float y, StatusGauge gauge) {
		configure(background, bottom);
		configure(fill, internal);
		fill.setRegion(internal.textureWidth(), internal.textureHeight(), internal.u(), internal.v(), internal.width(), internal.height());
		fill.setSize(gauge.getFill() * internal.width(), internal.height());
		trail.setPosition(internal.x() + gauge.getTrailStart(), internal.y());
		trail.setSize(gauge.getTrailWidth(), internal.height());
		trail.setColor(gauge.getTrailColor());
		flash.setVisible(light != null && gauge.isFlashing());
		if (light != null) {
			configure(flash, light);
		}
		super.render(graphics, x, y);
	}

}
