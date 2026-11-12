package org.unitego.lobecorp.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// 图集 ID 与完整原始纹理路径明确区分；精灵默认保留缩放元数据。
@NullMarked
public class TextureHudRenderer extends BasicHudRenderer {
	private final Type type;
	private Identifier texture;
	private int color = -1;
	private float alpha = 1;
	private boolean region;
	private float textureWidth = 1, textureHeight = 1, sourceWidth = 1, sourceHeight = 1;
	private int u, v;
	protected TextureHudRenderer(Identifier texture, Type type) {
		this.texture = texture;
		this.type = type;
	}

	public static TextureHudRenderer sprite(Identifier id) {
		return new TextureHudRenderer(id, Type.SPRITE);
	}

	public static TextureHudRenderer texture(Identifier path) {
		return new TextureHudRenderer(path, Type.TEXTURE);
	}

	public Identifier getTexture() {
		return texture;
	}

	public void setTexture(Identifier texture) {
		this.texture = texture;
	}

	public void setColor(int color) {
		this.color = color;
	}

	public float getAlpha() {
		return alpha;
	}

	public void setAlpha(float alpha) {
		this.alpha = Float.isFinite(alpha) ? Math.clamp(alpha, 0, 1) : 0;
	}

	public void setRegion(float textureWidth, float textureHeight, int u, int v, float sourceWidth, float sourceHeight) {
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.u = u;
		this.v = v;
		this.sourceWidth = sourceWidth;
		this.sourceHeight = sourceHeight;
		region = true;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
		if (alpha <= 0 || width <= 0 || height <= 0) {
			return;
		}
		int tint = Math.round((color >>> 24) * alpha) << 24 | color & 0xFFFFFF;
		if (type == Type.SPRITE) {
			if (region) {
				graphics.lobecorp$blitSprite(RenderPipelines.GUI_TEXTURED, texture, textureWidth, textureHeight, u, v, x, y, width, height, tint);
			} else {
				graphics.lobecorp$blitSprite(RenderPipelines.GUI_TEXTURED, texture, x, y, width, height, tint);
			}
		} else {
			graphics.lobecorp$blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height,
					sourceWidth, sourceHeight, (int) textureWidth, (int) textureHeight, tint);
		}
	}

	public enum Type {SPRITE, TEXTURE}
}
