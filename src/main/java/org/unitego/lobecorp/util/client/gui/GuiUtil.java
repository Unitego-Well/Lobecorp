package org.unitego.lobecorp.util.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/// 旧 GuiUtil 的浮点绘制能力；只提交当前版本 GUI 状态，不直接操作 RenderSystem 或即时顶点缓冲。
@NullMarked
public class GuiUtil {
	public static void blitSprite(GuiGraphicsExtractor graphics, Identifier id, float x, float y, float width, float height) {
		blitSprite(graphics, id, x, y, width, height, -1);
	}

	public static void blitSprite(GuiGraphicsExtractor graphics, Identifier id, float x, float y, float width, float height, int color) {
		if (invalidSize(width, height)) {
			return;
		}

		var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI).getSprite(id);
		switch (scaling(sprite)) {
			case GuiSpriteScaling.Tile tile -> blitTiledSprite(graphics, sprite, x, y, width, height,
					0, 0, tile.width(), tile.height(), tile.width(), tile.height(), color);
			case GuiSpriteScaling.NineSlice slice ->
					blitNineSlicedSprite(graphics, sprite, slice, x, y, width, height, color);
			default -> blitSprite(graphics, sprite, x, y, width, height, color);
		}
	}

	/// 裁切尺寸同时是目标尺寸，浮点宽度保留到提取顶点阶段。
	public static void blitSprite(GuiGraphicsExtractor graphics, Identifier id, float textureWidth, float textureHeight,
	                              float u, float v, float x, float y, float width, float height) {
		var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI).getSprite(id);
		if (scaling(sprite) instanceof GuiSpriteScaling.Stretch) {
			blitRegion(graphics, sprite, x, y, width, height, u, v, width, height, textureWidth, textureHeight, -1);
		} else {
			blitSprite(graphics, sprite, x, y, width, height);
		}
	}

	public static void blitSprite(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite,
	                              float textureWidth, float textureHeight, float u, float v,
	                              float x, float y, float width, float height) {
		blitSprite(graphics, sprite, textureWidth, textureHeight, u, v, x, y, width, height, -1);
	}

	public static void blitSprite(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite,
	                              float textureWidth, float textureHeight, float u, float v,
	                              float x, float y, float width, float height, int color) {
		blitRegion(graphics, sprite, x, y, width, height, u, v, width, height, textureWidth, textureHeight, color);
	}

	public static void blitSprite(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite,
	                              float x, float y, float width, float height) {
		blitSprite(graphics, sprite, x, y, width, height, -1);
	}

	public static void blitSprite(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite,
	                              float x, float y, float width, float height, int color) {
		blit(graphics, sprite.atlasLocation(), x, y, width, height, sprite.getU0(), sprite.getV0(),
				sprite.getU1(), sprite.getV1(), color);
	}

	public static void blitNineSlicedSprite(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite,
	                                        GuiSpriteScaling.NineSlice slice, float x, float y, float width, float height) {
		blitNineSlicedSprite(graphics, sprite, slice, x, y, width, height, -1);
	}

	public static void blitNineSlicedSprite(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite,
	                                        GuiSpriteScaling.NineSlice slice, float x, float y,
	                                        float width, float height, int color) {
		if (invalidSize(width, height)) {
			return;
		}

		var border = slice.border();
		float left = Math.min(border.left(), width / 2);
		float right = Math.min(border.right(), width / 2);
		float top = Math.min(border.top(), height / 2);
		float bottom = Math.min(border.bottom(), height / 2);
		float[] destinationX = {x, x + left, x + width - right, x + width};
		float[] destinationY = {y, y + top, y + height - bottom, y + height};
		float[] sourceX = {0, left, slice.width() - right, slice.width()};
		float[] sourceY = {0, top, slice.height() - bottom, slice.height()};
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				float targetWidth = destinationX[column + 1] - destinationX[column];
				float targetHeight = destinationY[row + 1] - destinationY[row];
				float sourceWidth = sourceX[column + 1] - sourceX[column];
				float sourceHeight = sourceY[row + 1] - sourceY[row];
				if ((column == 1 || row == 1) && !slice.stretchInner()) {
					blitTiledSprite(graphics, sprite, destinationX[column], destinationY[row], targetWidth, targetHeight,
							sourceX[column], sourceY[row], sourceWidth, sourceHeight, slice.width(), slice.height(), color);
				} else {
					blitRegion(graphics, sprite, destinationX[column], destinationY[row], targetWidth, targetHeight,
							sourceX[column], sourceY[row], sourceWidth, sourceHeight, slice.width(), slice.height(), color);
				}
			}
		}
	}

	public static void blitTiledSprite(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite,
	                                   float x, float y, float width, float height, float u, float v,
	                                   float tileWidth, float tileHeight, float textureWidth, float textureHeight) {
		blitTiledSprite(graphics, sprite, x, y, width, height, u, v, tileWidth, tileHeight, textureWidth, textureHeight, -1);
	}

	public static void blitTiledSprite(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite,
	                                   float x, float y, float width, float height, float u, float v,
	                                   float tileWidth, float tileHeight, float textureWidth, float textureHeight, int color) {
		if (invalidSize(width, height) || invalidSize(tileWidth, tileHeight)) {
			return;
		}

		int columns = (int) Math.ceil(width / tileWidth);
		int rows = (int) Math.ceil(height / tileHeight);
		for (int row = 0; row < rows; row++) {
			float partHeight = Math.min(tileHeight, height - row * tileHeight);
			for (int column = 0; column < columns; column++) {
				float partWidth = Math.min(tileWidth, width - column * tileWidth);
				blitRegion(graphics, sprite, x + column * tileWidth, y + row * tileHeight, partWidth, partHeight,
						u, v, partWidth, partHeight, textureWidth, textureHeight, color);
			}
		}
	}

	private static void blitRegion(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite,
	                               float x, float y, float width, float height, float u, float v,
	                               float sourceWidth, float sourceHeight, float textureWidth, float textureHeight, int color) {
		if (invalidSize(textureWidth, textureHeight) || invalidSize(sourceWidth, sourceHeight)) {
			return;
		}

		blit(graphics, sprite.atlasLocation(), x, y, width, height, sprite.getU(u / textureWidth),
				sprite.getV(v / textureHeight), sprite.getU((u + sourceWidth) / textureWidth),
				sprite.getV((v + sourceHeight) / textureHeight), color);
	}

	public static void blit(GuiGraphicsExtractor graphics, Identifier texture, float x, float y,
	                        float width, float height, float u0, float v0, float u1, float v1, int color) {
		if (invalidSize(width, height)) {
			return;
		}

		var resource = Minecraft.getInstance().getTextureManager().getTexture(texture);
		submit(graphics, RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(resource.getTextureView(), resource.getSampler()),
				x, y, width, height, u0, v0, u1, v1, color, color, true);
	}

	public static void fill(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int color) {
		fillGradient(graphics, x0, y0, x1, y1, color, color);
	}

	public static void fill(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
	                        float x0, float y0, float x1, float y1, int color) {
		fillGradient(graphics, pipeline, x0, y0, x1, y1, color, color);
	}

	public static void fillGradient(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1,
	                                int topColor, int bottomColor) {
		fillGradient(graphics, RenderPipelines.GUI, x0, y0, x1, y1, topColor, bottomColor);
	}

	public static void fillGradient(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
	                                float x0, float y0, float x1, float y1, int topColor, int bottomColor) {
		float left = Math.min(x0, x1);
		float top = Math.min(y0, y1);
		int firstColor = y0 <= y1 ? topColor : bottomColor;
		int lastColor = y0 <= y1 ? bottomColor : topColor;
		submit(graphics, pipeline, TextureSetup.noTexture(), left, top, Math.abs(x1 - x0), Math.abs(y1 - y0),
				0, 0, 0, 0, firstColor, lastColor, false);
	}

	/// 当前版本的管线矩形提取入口；管线使用位置或位置／颜色顶点，绑定纹理由 TextureSetup 明确提供。
	public static void fillRenderType(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
	                                  float x0, float y0, float x1, float y1) {
		fillRenderType(graphics, pipeline, TextureSetup.noTexture(), x0, y0, x1, y1);
	}

	public static void fillRenderType(GuiGraphicsExtractor graphics, RenderPipeline pipeline, TextureSetup textures,
	                                  float x0, float y0, float x1, float y1) {
		float left = Math.min(x0, x1);
		float top = Math.min(y0, y1);
		submit(graphics, pipeline, textures, left, top, Math.abs(x1 - x0), Math.abs(y1 - y0),
				0, 0, 0, 0, -1, -1, false);
	}

	private static GuiSpriteScaling scaling(TextureAtlasSprite sprite) {
		return sprite.contents().getAdditionalMetadata(GuiMetadataSection.TYPE).orElse(GuiMetadataSection.DEFAULT).scaling();
	}

	private static boolean invalidSize(float width, float height) {
		return !Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0;
	}

	private static void submit(GuiGraphicsExtractor graphics, RenderPipeline pipeline, TextureSetup textures,
	                           float x, float y, float width, float height, float u0, float v0, float u1, float v1,
	                           int topColor, int bottomColor, boolean textured) {
		if (!Float.isFinite(x) || !Float.isFinite(y) || invalidSize(width, height)) {
			return;
		}

		var pose = new Matrix3x2f(graphics.pose());
		var scissor = graphics.peekScissorStack();
		int left = (int) Math.floor(x);
		int top = (int) Math.floor(y);
		var bounds = new ScreenRectangle(left, top, (int) Math.ceil(x + width) - left,
				(int) Math.ceil(y + height) - top).transformMaxBounds(pose);
		if (scissor != null) {
			bounds = bounds.intersection(scissor);
		}

		if (bounds != null) {
			graphics.submitGuiElementRenderState(new QuadState(pipeline, textures, pose, x, y, x + width, y + height,
					u0, v0, u1, v1, topColor, bottomColor, textured, scissor, bounds));
		}
	}

	/// 提取时冻结矩阵、颜色、UV 和裁剪；延迟绘制不读取可变 HUD 或当前 pose。
	private record QuadState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
	                         float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
	                         int topColor, int bottomColor, boolean textured,
	                         @Nullable ScreenRectangle scissorArea,
	                         ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer consumer) {
			vertex(consumer, x0, y0, u0, v0, topColor);
			vertex(consumer, x0, y1, u0, v1, bottomColor);
			vertex(consumer, x1, y1, u1, v1, bottomColor);
			vertex(consumer, x1, y0, u1, v0, topColor);
		}

		private void vertex(VertexConsumer consumer, float x, float y, float u, float v, int color) {
			var vertex = consumer.addVertexWith2DPose(pose, x, y).setColor(color);
			if (textured) {
				vertex.setUv(u, v);
			}
		}
	}
}
