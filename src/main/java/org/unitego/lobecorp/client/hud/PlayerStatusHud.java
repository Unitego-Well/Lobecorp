package org.unitego.lobecorp.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.host.HudHost;
import org.unitego.lobecorp.client.hud.status.PlayerStatusHudView;

/// 玩家状态 HUD 的业务门面；注册、会话清理和护甲偏移仍从这里访问。
@NullMarked
public class PlayerStatusHud {
	public static final Identifier ID = Lobecorp.id("player_status_hud");
	public static final PlayerStatusHud INSTANCE = new PlayerStatusHud();

	private PlayerStatusHud() {
	}

	/// 向统一管理器注册状态条宿主工厂；重复注册由管理器拒绝。
	public void register() {
		HudLayerManager.INSTANCE.register(ID, this::visible, () -> {
			var view = new PlayerStatusHudView();
			return new HudHost(view, view::prepare, _ -> {
			}, view::clear);
		});
	}

	/// 由 NeoForge 状态层回调提取；宿主创建与布局时序由管理器负责。
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		HudLayerManager.INSTANCE.render(ID, graphics, deltaTracker);
	}

	public int armorOffset() {
		var current = Minecraft.getInstance().player;
		return survivalVisible(current) ? PlayerStatusHudView.currentLayout().height() + 1 : 0;
	}

	public void clear() {
		HudLayerManager.INSTANCE.clear(ID);
	}

	private boolean visible() {
		var minecraft = Minecraft.getInstance();
		return !minecraft.options.hideGui && minecraft.level != null && minecraft.player != null
				&& !minecraft.player.isSpectator();
	}

	private boolean survivalVisible(@Nullable LocalPlayer current) {
		return current != null && !current.isSpectator() && !current.isCreative();
	}
}
