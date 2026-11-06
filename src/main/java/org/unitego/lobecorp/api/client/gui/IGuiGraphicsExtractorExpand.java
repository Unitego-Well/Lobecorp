package org.unitego.lobecorp.api.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.util.ARGB;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.api.NoMixinException;
import org.unitego.lobecorp.client.gui.state.FloatBlitRenderState;
import org.unitego.lobecorp.client.gui.state.FloatColoredRectangleRenderState;
import org.unitego.lobecorp.client.gui.state.FloatTiledBlitRenderState;

/// 原版 GUI 提取器的浮点坐标重载；纹理像素偏移仍使用整数。
@NullMarked
public interface IGuiGraphicsExtractorExpand {
	default Minecraft lobecorp$getMinecraft() {
		throw new NoMixinException();
	}

	default TextureAtlas lobecorp$getGuiSprites() {
		throw new NoMixinException();
	}

	private GuiGraphicsExtractor lobecorp$graphics() {
		return (GuiGraphicsExtractor) this;
	}

	default void lobecorp$horizontalLine(float x0, float x1, float y, int col) {
		if (x1 < x0) {
			float tmp = x0;
			x0 = x1;
			x1 = tmp;
		}

		this.lobecorp$fill(x0, y, x1 + 1, y + 1, col);
	}

	default void lobecorp$verticalLine(float x, float y0, float y1, int col) {
		if (y1 < y0) {
			float tmp = y0;
			y0 = y1;
			y1 = tmp;
		}

		this.lobecorp$fill(x, y0 + 1, x + 1, y1, col);
	}

	default void lobecorp$fill(float x0, float y0, float x1, float y1, int col) {
		this.lobecorp$fill(RenderPipelines.GUI, x0, y0, x1, y1, col);
	}

	default void lobecorp$fill(RenderPipeline pipeline, float x0, float y0, float x1, float y1, int col) {
		if (x0 < x1) {
			float tmp = x0;
			x0 = x1;
			x1 = tmp;
		}

		if (y0 < y1) {
			float tmp = y0;
			y0 = y1;
			y1 = tmp;
		}

		this.innerFill(pipeline, TextureSetup.noTexture(), x0, y0, x1, y1, col, null);
	}

	default void lobecorp$fillGradient(float x0, float y0, float x1, float y1, int col1, int col2) {
		this.innerFill(RenderPipelines.GUI, TextureSetup.noTexture(), x0, y0, x1, y1, col1, col2);
	}

	default void lobecorp$fill(RenderPipeline renderPipeline, TextureSetup textureSetup, float x0, float y0, float x1, float y1) {
		this.innerFill(renderPipeline, textureSetup, x0, y0, x1, y1, -1, null);
	}

	default void lobecorp$outline(float x, float y, float width, float height, int color) {
		this.lobecorp$fill(x, y, x + width, y + 1, color);
		this.lobecorp$fill(x, y + height - 1, x + width, y + height, color);
		this.lobecorp$fill(x, y + 1, x + 1, y + height - 1, color);
		this.lobecorp$fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	private void innerFill(RenderPipeline renderPipeline, TextureSetup textureSetup, float x0, float y0, float x1, float y1, int color1, @Nullable Integer color2) {
		lobecorp$graphics().submitGuiElementRenderState(
				new FloatColoredRectangleRenderState(
						renderPipeline, textureSetup, new Matrix3x2f(lobecorp$graphics().pose()), x0, y0, x1, y1, color1, color2 != null ? color2 : color1, lobecorp$graphics().peekScissorStack()
				)
		);
	}

	default void lobecorp$blit(
			RenderPipeline renderPipeline,
			Identifier texture,
			float x,
			float y,
			int u,
			int v,
			float width,
			float height,
			int textureWidth,
			int textureHeight,
			int color
	) {
		this.lobecorp$blit(renderPipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color);
	}

	default void lobecorp$blit(
			RenderPipeline renderPipeline, Identifier texture, float x, float y, int u, int v, float width, float height, int textureWidth, int textureHeight
	) {
		this.lobecorp$blit(renderPipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight);
	}

	default void lobecorp$blit(
			RenderPipeline renderPipeline,
			Identifier texture,
			float x,
			float y,
			int u,
			int v,
			float width,
			float height,
			float srcWidth,
			float srcHeight,
			int textureWidth,
			int textureHeight
	) {
		this.lobecorp$blit(renderPipeline, texture, x, y, u, v, width, height, srcWidth, srcHeight, textureWidth, textureHeight, -1);
	}

	default void lobecorp$blit(
			RenderPipeline renderPipeline,
			Identifier texture,
			float x,
			float y,
			int u,
			int v,
			float width,
			float height,
			float srcWidth,
			float srcHeight,
			int textureWidth,
			int textureHeight,
			int color
	) {
		this.innerBlit(
				renderPipeline,
				texture,
				x,
				x + width,
				y,
				y + height,
				(u + 0.0F) / textureWidth,
				(u + srcWidth) / textureWidth,
				(v + 0.0F) / textureHeight,
				(v + srcHeight) / textureHeight,
				color
		);
	}

	default void lobecorp$blit(Identifier location, float x0, float y0, float x1, float y1, float u0, float u1, float v0, float v1) {
		this.innerBlit(RenderPipelines.GUI_TEXTURED, location, x0, x1, y0, y1, u0, u1, v0, v1, -1);
	}

	default void lobecorp$blit(GpuTextureView textureView, GpuSampler sampler, float x0, float y0, float x1, float y1, float u0, float u1, float v0, float v1) {
		this.innerBlit(RenderPipelines.GUI_TEXTURED, textureView, sampler, x0, y0, x1, y1, u0, u1, v0, v1, -1);
	}

	default void lobecorp$blitSprite(RenderPipeline renderPipeline, Identifier location, float x, float y, float width, float height) {
		this.lobecorp$blitSprite(renderPipeline, location, x, y, width, height, -1);
	}

	default void lobecorp$blitSprite(RenderPipeline renderPipeline, Identifier location, float x, float y, float width, float height, float alpha) {
		this.lobecorp$blitSprite(renderPipeline, location, x, y, width, height, ARGB.white(alpha));
	}

	default void lobecorp$blitSprite(RenderPipeline renderPipeline, Identifier location, float x, float y, float width, float height, int color) {
		TextureAtlasSprite sprite = lobecorp$getGuiSprites().getSprite(location);
		GuiSpriteScaling scaling = getSpriteScaling(sprite);
		switch (scaling) {
			case GuiSpriteScaling.Stretch _ ->
					this.lobecorp$blitSprite(renderPipeline, sprite, x, y, width, height, color);
			case GuiSpriteScaling.Tile tile ->
					this.blitTiledSprite(renderPipeline, sprite, x, y, width, height, 0, 0, tile.width(), tile.height(), tile.width(), tile.height(), color);
			case GuiSpriteScaling.NineSlice nineSlice ->
					this.blitNineSlicedSprite(renderPipeline, sprite, nineSlice, x, y, width, height, color);
			default -> {
			}
		}
	}

	default void lobecorp$blitSprite(
			RenderPipeline renderPipeline, Identifier location, float spriteWidth, float spriteHeight, int textureX, int textureY, float x, float y, float width, float height
	) {
		this.lobecorp$blitSprite(renderPipeline, location, spriteWidth, spriteHeight, textureX, textureY, x, y, width, height, -1);
	}

	default void lobecorp$blitSprite(
			RenderPipeline renderPipeline,
			Identifier location,
			float spriteWidth,
			float spriteHeight,
			int textureX,
			int textureY,
			float x,
			float y,
			float width,
			float height,
			int color
	) {
		TextureAtlasSprite sprite = lobecorp$getGuiSprites().getSprite(location);
		GuiSpriteScaling scaling = getSpriteScaling(sprite);
		if (scaling instanceof GuiSpriteScaling.Stretch) {
			this.lobecorp$blitSprite(renderPipeline, sprite, spriteWidth, spriteHeight, textureX, textureY, x, y, width, height, color);
		} else {
			lobecorp$graphics().enableScissor((int) Math.floor(x), (int) Math.floor(y),
					(int) Math.ceil(x + width), (int) Math.ceil(y + height));
			this.lobecorp$blitSprite(renderPipeline, location, x - textureX, y - textureY, spriteWidth, spriteHeight, color);
			lobecorp$graphics().disableScissor();
		}
	}

	default void lobecorp$blitSprite(RenderPipeline renderPipeline, TextureAtlasSprite sprite, float x, float y, float width, float height) {
		this.lobecorp$blitSprite(renderPipeline, sprite, x, y, width, height, -1);
	}

	default void lobecorp$blitSprite(RenderPipeline renderPipeline, TextureAtlasSprite sprite, float x, float y, float width, float height, int color) {
		if (width != 0 && height != 0) {
			this.innerBlit(
					renderPipeline, sprite.atlasLocation(), x, x + width, y, y + height, sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), color
			);
		}
	}

	private void lobecorp$blitSprite(
			RenderPipeline renderPipeline,
			TextureAtlasSprite sprite,
			float spriteWidth,
			float spriteHeight,
			int textureX,
			int textureY,
			float x,
			float y,
			float width,
			float height,
			int color
	) {
		if (width != 0 && height != 0) {
			this.innerBlit(
					renderPipeline,
					sprite.atlasLocation(),
					x,
					x + width,
					y,
					y + height,
					sprite.getU(textureX / spriteWidth),
					sprite.getU((textureX + width) / spriteWidth),
					sprite.getV(textureY / spriteHeight),
					sprite.getV((textureY + height) / spriteHeight),
					color
			);
		}
	}

	private void blitNineSlicedSprite(
			RenderPipeline renderPipeline, TextureAtlasSprite sprite, GuiSpriteScaling.NineSlice nineSlice, float x, float y, float width, float height, int color
	) {
		GuiSpriteScaling.NineSlice.Border border = nineSlice.border();
		int borderLeft = Math.min(border.left(), (int) width / 2);
		int borderRight = Math.min(border.right(), (int) width / 2);
		int borderTop = Math.min(border.top(), (int) height / 2);
		int borderBottom = Math.min(border.bottom(), (int) height / 2);
		if (width == nineSlice.width() && height == nineSlice.height()) {
			this.lobecorp$blitSprite(renderPipeline, sprite, nineSlice.width(), nineSlice.height(), 0, 0, x, y, width, height, color);
		} else if (height == nineSlice.height()) {
			this.lobecorp$blitSprite(renderPipeline, sprite, nineSlice.width(), nineSlice.height(), 0, 0, x, y, borderLeft, height, color);
			this.blitNineSliceInnerSegment(
					renderPipeline,
					nineSlice,
					sprite,
					x + borderLeft,
					y,
					width - borderRight - borderLeft,
					height,
					borderLeft,
					0,
					nineSlice.width() - borderRight - borderLeft,
					nineSlice.height(),
					nineSlice.width(),
					nineSlice.height(),
					color
			);
			this.lobecorp$blitSprite(
					renderPipeline,
					sprite,
					nineSlice.width(),
					nineSlice.height(),
					nineSlice.width() - borderRight,
					0,
					x + width - borderRight,
					y,
					borderRight,
					height,
					color
			);
		} else if (width == nineSlice.width()) {
			this.lobecorp$blitSprite(renderPipeline, sprite, nineSlice.width(), nineSlice.height(), 0, 0, x, y, width, borderTop, color);
			this.blitNineSliceInnerSegment(
					renderPipeline,
					nineSlice,
					sprite,
					x,
					y + borderTop,
					width,
					height - borderBottom - borderTop,
					0,
					borderTop,
					nineSlice.width(),
					nineSlice.height() - borderBottom - borderTop,
					nineSlice.width(),
					nineSlice.height(),
					color
			);
			this.lobecorp$blitSprite(
					renderPipeline,
					sprite,
					nineSlice.width(),
					nineSlice.height(),
					0,
					nineSlice.height() - borderBottom,
					x,
					y + height - borderBottom,
					width,
					borderBottom,
					color
			);
		} else {
			this.lobecorp$blitSprite(renderPipeline, sprite, nineSlice.width(), nineSlice.height(), 0, 0, x, y, borderLeft, borderTop, color);
			this.blitNineSliceInnerSegment(
					renderPipeline,
					nineSlice,
					sprite,
					x + borderLeft,
					y,
					width - borderRight - borderLeft,
					borderTop,
					borderLeft,
					0,
					nineSlice.width() - borderRight - borderLeft,
					borderTop,
					nineSlice.width(),
					nineSlice.height(),
					color
			);
			this.lobecorp$blitSprite(
					renderPipeline,
					sprite,
					nineSlice.width(),
					nineSlice.height(),
					nineSlice.width() - borderRight,
					0,
					x + width - borderRight,
					y,
					borderRight,
					borderTop,
					color
			);
			this.lobecorp$blitSprite(
					renderPipeline,
					sprite,
					nineSlice.width(),
					nineSlice.height(),
					0,
					nineSlice.height() - borderBottom,
					x,
					y + height - borderBottom,
					borderLeft,
					borderBottom,
					color
			);
			this.blitNineSliceInnerSegment(
					renderPipeline,
					nineSlice,
					sprite,
					x + borderLeft,
					y + height - borderBottom,
					width - borderRight - borderLeft,
					borderBottom,
					borderLeft,
					nineSlice.height() - borderBottom,
					nineSlice.width() - borderRight - borderLeft,
					borderBottom,
					nineSlice.width(),
					nineSlice.height(),
					color
			);
			this.lobecorp$blitSprite(
					renderPipeline,
					sprite,
					nineSlice.width(),
					nineSlice.height(),
					nineSlice.width() - borderRight,
					nineSlice.height() - borderBottom,
					x + width - borderRight,
					y + height - borderBottom,
					borderRight,
					borderBottom,
					color
			);
			this.blitNineSliceInnerSegment(
					renderPipeline,
					nineSlice,
					sprite,
					x,
					y + borderTop,
					borderLeft,
					height - borderBottom - borderTop,
					0,
					borderTop,
					borderLeft,
					nineSlice.height() - borderBottom - borderTop,
					nineSlice.width(),
					nineSlice.height(),
					color
			);
			this.blitNineSliceInnerSegment(
					renderPipeline,
					nineSlice,
					sprite,
					x + borderLeft,
					y + borderTop,
					width - borderRight - borderLeft,
					height - borderBottom - borderTop,
					borderLeft,
					borderTop,
					nineSlice.width() - borderRight - borderLeft,
					nineSlice.height() - borderBottom - borderTop,
					nineSlice.width(),
					nineSlice.height(),
					color
			);
			this.blitNineSliceInnerSegment(
					renderPipeline,
					nineSlice,
					sprite,
					x + width - borderRight,
					y + borderTop,
					borderRight,
					height - borderBottom - borderTop,
					nineSlice.width() - borderRight,
					borderTop,
					borderRight,
					nineSlice.height() - borderBottom - borderTop,
					nineSlice.width(),
					nineSlice.height(),
					color
			);
		}
	}

	private void blitNineSliceInnerSegment(
			RenderPipeline renderPipeline,
			GuiSpriteScaling.NineSlice nineSlice,
			TextureAtlasSprite sprite,
			float x,
			float y,
			float width,
			float height,
			int textureX,
			int textureY,
			int textureWidth,
			int textureHeight,
			float spriteWidth,
			float spriteHeight,
			int color
	) {
		if (width > 0 && height > 0) {
			if (nineSlice.stretchInner()) {
				this.innerBlit(
						renderPipeline,
						sprite.atlasLocation(),
						x,
						x + width,
						y,
						y + height,
						sprite.getU(textureX / spriteWidth),
						sprite.getU((textureX + textureWidth) / spriteWidth),
						sprite.getV(textureY / spriteHeight),
						sprite.getV((textureY + textureHeight) / spriteHeight),
						color
				);
			} else {
				this.blitTiledSprite(
						renderPipeline, sprite, x, y, width, height, textureX, textureY, textureWidth, textureHeight, spriteWidth, spriteHeight, color
				);
			}
		}
	}

	private void blitTiledSprite(
			RenderPipeline renderPipeline,
			TextureAtlasSprite sprite,
			float x,
			float y,
			float width,
			float height,
			int textureX,
			int textureY,
			int tileWidth,
			int tileHeight,
			float spriteWidth,
			float spriteHeight,
			int color
	) {
		if (width > 0 && height > 0) {
			if (tileWidth > 0 && tileHeight > 0) {
				AbstractTexture spriteTexture = lobecorp$getMinecraft().getTextureManager().getTexture(sprite.atlasLocation());
				GpuTextureView texture = spriteTexture.getTextureView();
				this.innerTiledBlit(
						renderPipeline,
						texture,
						spriteTexture.getSampler(),
						tileWidth,
						tileHeight,
						x,
						y,
						x + width,
						y + height,
						sprite.getU(textureX / spriteWidth),
						sprite.getU((textureX + tileWidth) / spriteWidth),
						sprite.getV(textureY / spriteHeight),
						sprite.getV((textureY + tileHeight) / spriteHeight),
						color
				);
			} else {
				throw new IllegalArgumentException("Tile size must be positive, got " + tileWidth + "x" + tileHeight);
			}
		}
	}

	private void innerBlit(
			RenderPipeline renderPipeline, Identifier location, float x0, float x1, float y0, float y1, float u0, float u1, float v0, float v1, int color
	) {
		AbstractTexture texture = lobecorp$getMinecraft().getTextureManager().getTexture(location);
		this.innerBlit(renderPipeline, texture.getTextureView(), texture.getSampler(), x0, y0, x1, y1, u0, u1, v0, v1, color);
	}

	private void innerBlit(
			RenderPipeline pipeline,
			GpuTextureView textureView,
			GpuSampler sampler,
			float x0,
			float y0,
			float x1,
			float y1,
			float u0,
			float u1,
			float v0,
			float v1,
			int color
	) {
		lobecorp$graphics().submitGuiElementRenderState(
				new FloatBlitRenderState(
						pipeline,
						TextureSetup.singleTexture(textureView, sampler),
						new Matrix3x2f(lobecorp$graphics().pose()),
						x0,
						y0,
						x1,
						y1,
						u0,
						u1,
						v0,
						v1,
						color,
						lobecorp$graphics().peekScissorStack()
				)
		);
	}

	private void innerTiledBlit(
			RenderPipeline pipeline,
			GpuTextureView textureView,
			GpuSampler sampler,
			int tileWidth,
			int tileHeight,
			float x0,
			float y0,
			float x1,
			float y1,
			float u0,
			float u1,
			float v0,
			float v1,
			int color
	) {
		lobecorp$graphics().submitGuiElementRenderState(
				new FloatTiledBlitRenderState(
						pipeline,
						TextureSetup.singleTexture(textureView, sampler),
						new Matrix3x2f(lobecorp$graphics().pose()),
						tileWidth,
						tileHeight,
						x0,
						y0,
						x1,
						y1,
						u0,
						u1,
						v0,
						v1,
						color,
						lobecorp$graphics().peekScissorStack()
				)
		);
	}

	private static GuiSpriteScaling getSpriteScaling(TextureAtlasSprite sprite) {
		return sprite.contents().getAdditionalMetadata(GuiMetadataSection.TYPE).orElse(GuiMetadataSection.DEFAULT).scaling();
	}


}
