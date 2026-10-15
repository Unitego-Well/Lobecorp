package org.unitego.lobecorp.client.particle.screen;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 原生纹理的完整 UV 内容，尺寸使用 GUI 像素。
@NullMarked
@SuppressWarnings("unused")
public class ScreenTextureContent implements IScreenParticleContent {

	private static final String INVALID_SIZE = "屏幕粒子纹理尺寸必须为有限正数";
	private final Identifier texture;
	private final float width;
	private final float height;
	private final int color;

	public ScreenTextureContent(Identifier texture, float width, float height) {
		this(texture, width, height, -1);
	}

	public ScreenTextureContent(Identifier texture, float width, float height, int color) {
		if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) {
			throw new IllegalArgumentException(INVALID_SIZE);
		}

		this.texture = Objects.requireNonNull(texture);
		this.width = width;
		this.height = height;
		this.color = color;
	}

	@Override
	public float width() {
		return width;
	}

	@Override
	public float height() {
		return height;
	}

	@Override
	public void extract(GuiGraphicsExtractor graphics, float opacity) {
		if (!(opacity > 0)) {
			return;
		}

		var resource = Minecraft.getInstance().getTextureManager().getTexture(texture);
		var textures = TextureSetup.singleTexture(resource.getTextureView(), resource.getSampler());
		var pose = new Matrix3x2f(graphics.pose());
		var scissor = graphics.peekScissorStack();
		int halfWidth = (int) Math.ceil(width / 2);
		int halfHeight = (int) Math.ceil(height / 2);
		var bounds = new ScreenRectangle(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2).transformMaxBounds(pose);
		if (scissor != null) {
			bounds = bounds.intersection(scissor);
		}

		if (bounds == null) {
			return;
		}

		int alpha = Math.round((color >>> 24) * Math.min(opacity, 1));
		graphics.submitGuiElementRenderState(new TextureState(pose, textures, width / 2, height / 2,
				color & 0xFFFFFF | alpha << 24, scissor, bounds));
	}

	private record TextureState(Matrix3x2fc pose, TextureSetup textureSetup, float halfWidth, float halfHeight,
	                            int color, @Nullable ScreenRectangle scissorArea,
	                            ScreenRectangle bounds) implements GuiElementRenderState {

		@Override
		public RenderPipeline pipeline() {
			return RenderPipelines.GUI_TEXTURED;
		}

		@Override
		public void buildVertices(VertexConsumer buffer) {
			vertex(buffer, -halfWidth, -halfHeight, 0, 0);
			vertex(buffer, -halfWidth, halfHeight, 0, 1);
			vertex(buffer, halfWidth, halfHeight, 1, 1);
			vertex(buffer, halfWidth, -halfHeight, 1, 0);
		}

		private void vertex(VertexConsumer buffer, float x, float y, float u, float v) {
			buffer.addVertex(pose.m00() * x + pose.m10() * y + pose.m20(),
					pose.m01() * x + pose.m11() * y + pose.m21(), 0).setColor(color).setUv(u, v);
		}
	}
}
