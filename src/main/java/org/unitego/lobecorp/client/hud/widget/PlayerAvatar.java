package org.unitego.lobecorp.client.hud.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.unitego.lobecorp.client.hud.BasicHudRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.renderer.RenderPipelines;

/// 皮肤脸部与外层独立居中
@NullMarked
public class PlayerAvatar extends BasicHudRenderer {
	private @Nullable LocalPlayer player;
	private final float hatScale;

	public PlayerAvatar(int headSize, float hatScale) {
		setSize(headSize * hatScale, headSize * hatScale);
		this.hatScale = hatScale;
	}

	/// 先画框，再画基础脸与外层；皮肤为动态原始纹理，按 64×64 皮肤 UV 采样。
	public void render(GuiGraphicsExtractor graphics, float x, float y, @Nullable LocalPlayer player) {
		setPlayer(player);
		super.render(graphics, x, y);
	}

	public void setPlayer(@Nullable LocalPlayer player) {
		this.player = player;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
		if (player == null) {
			return;
		}

		var skin = player.getSkin().body().texturePath();
		float hatSize = getWidth();
		float faceSize = hatSize / hatScale;
		float faceInset = (hatSize - faceSize) / 2F;
		graphics.lobecorp$blit(RenderPipelines.GUI_TEXTURED, skin, x + faceInset, y + faceInset,
				8, 8, faceSize, faceSize, 8, 8, 64, 64);
		graphics.lobecorp$blit(RenderPipelines.GUI_TEXTURED, skin, x, y,
				40, 8, hatSize, hatSize, 8, 8, 64, 64);
	}
}
