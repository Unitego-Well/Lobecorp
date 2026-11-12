package org.unitego.lobecorp.client.gui.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record FloatTiledBlitRenderState(
		RenderPipeline pipeline,
		TextureSetup textureSetup,
		Matrix3x2f pose,
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
		int color,
		@Nullable ScreenRectangle scissorArea,
		@Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
	public FloatTiledBlitRenderState(
			RenderPipeline pipeline,
			TextureSetup textureSetup,
			Matrix3x2f pose,
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
			int color,
			@Nullable ScreenRectangle scissorArea
	) {
		this(
				pipeline,
				textureSetup,
				pose,
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
				scissorArea,
				getBounds(x0, y0, x1, y1, pose, scissorArea)
		);
	}

	private static @Nullable ScreenRectangle getBounds(float x0, float y0, float x1, float y1, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
		int left = (int) Math.floor(Math.min(x0, x1));
		int top = (int) Math.floor(Math.min(y0, y1));
		ScreenRectangle bounds = new ScreenRectangle(left, top,
				(int) Math.ceil(Math.max(x0, x1)) - left,
				(int) Math.ceil(Math.max(y0, y1)) - top).transformMaxBounds(pose);
		return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
	}

	@Override
	public void buildVertices(VertexConsumer vertexConsumer) {
		float width = this.x1() - this.x0();
		float height = this.y1() - this.y0();

		for (int tileX = 0; tileX < width; tileX += this.tileWidth()) {
			float remainingWidth = width - tileX;
			float tileWidth;
			float u1;
			if (this.tileWidth() <= remainingWidth) {
				tileWidth = this.tileWidth();
				u1 = this.u1();
			} else {
				tileWidth = remainingWidth;
				u1 = Mth.lerp(remainingWidth / this.tileWidth(), this.u0(), this.u1());
			}

			for (int tileY = 0; tileY < height; tileY += this.tileHeight()) {
				float remainingHeight = height - tileY;
				float tileHeight;
				float v1;
				if (this.tileHeight() <= remainingHeight) {
					tileHeight = this.tileHeight();
					v1 = this.v1();
				} else {
					tileHeight = remainingHeight;
					v1 = Mth.lerp(remainingHeight / this.tileHeight(), this.v0(), this.v1());
				}

				float x0 = this.x0() + tileX;
				float x1 = this.x0() + tileX + tileWidth;
				float y0 = this.y0() + tileY;
				float y1 = this.y0() + tileY + tileHeight;
				vertexConsumer.addVertexWith2DPose(this.pose(), x0, y0).setUv(this.u0(), this.v0()).setColor(this.color());
				vertexConsumer.addVertexWith2DPose(this.pose(), x0, y1).setUv(this.u0(), v1).setColor(this.color());
				vertexConsumer.addVertexWith2DPose(this.pose(), x1, y1).setUv(u1, v1).setColor(this.color());
				vertexConsumer.addVertexWith2DPose(this.pose(), x1, y0).setUv(u1, this.v0()).setColor(this.color());
			}
		}
	}
}
