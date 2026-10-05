package org.unitego.lobecorp.client.photon.render;

import com.lowdragmc.lowdraglib2.gui.texture.renderstate.FloatBlitRenderState;
import com.lowdragmc.lowdraglib2.utils.ColorUtils;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record PhotonNoisePreviewState(Matrix3x2fc pose, float x, float y, float cellHeight,
                                      int columns, int rows, int[] colors, int tint,
                                      @Nullable ScreenRectangle scissorArea) implements GuiElementRenderState {
	@Override
	public RenderPipeline pipeline() {
		return RenderPipelines.GUI;
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.noTexture();
	}

	@Override
	public @Nullable ScreenRectangle bounds() {
		return FloatBlitRenderState.getBounds(x, y, x + columns, y + rows * cellHeight, pose, scissorArea);
	}

	@Override
	public void buildVertices(VertexConsumer consumer) {
		for (int row = 0; row < rows; row++) {
			float top = y + row * cellHeight, bottom = top + cellHeight;
			for (int column = 0; column < columns; column++) {
				int color = ColorUtils.mulColor(colors[row * columns + column], tint);
				float left = x + column, right = left + 1;
				consumer.addVertexWith2DPose(pose, left, top).setColor(color);
				consumer.addVertexWith2DPose(pose, left, bottom).setColor(color);
				consumer.addVertexWith2DPose(pose, right, bottom).setColor(color);
				consumer.addVertexWith2DPose(pose, right, top).setColor(color);
			}
		}
	}
}
