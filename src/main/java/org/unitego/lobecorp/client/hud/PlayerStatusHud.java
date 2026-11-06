package org.unitego.lobecorp.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.status.PlayerStatusHudView;

/// 玩家状态 HUD 的业务门面；注册、会话清理和护甲偏移仍从这里访问。
@NullMarked
public class PlayerStatusHud extends AbstractHud {
	public static final Identifier ID = Lobecorp.id("player_status_hud");
	public static final PlayerStatusHud INSTANCE = new PlayerStatusHud();
	private final PlayerStatusHudView view = new PlayerStatusHudView();

	private PlayerStatusHud() {
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
		var minecraft = Minecraft.getInstance();
		view.prepare(minecraft.player, graphics.guiWidth(), graphics.guiHeight(), frameDelta);
		view.render(graphics, x, y);
	}

	public int armorOffset() {
		var current = Minecraft.getInstance().player;
		return survivalVisible(current) ? PlayerStatusHudView.currentLayout().height() + 1 : 0;
	}

	@Override
	protected void onSessionChanged() {
		view.clear();
	}

	private boolean survivalVisible(@Nullable LocalPlayer current) {
		return current != null && !current.isSpectator() && !current.isCreative();
	}


}
