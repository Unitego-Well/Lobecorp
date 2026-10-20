package org.unitego.lobecorp.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.unitego.lobecorp.client.hud.layer.DamageScreenFilterLayer;
import org.unitego.lobecorp.client.hud.layer.RationalityScreenFilterLayer;
import org.unitego.lobecorp.config.client.PlayerHudConfig;

/// 两个滤镜层共用一个会话实例，注册、通知与清理由同一 INSTANCE 访问。
public class PlayerScreenFilters {
	public static final PlayerScreenFilters INSTANCE = new PlayerScreenFilters();
	private final DamageScreenFilterLayer damage = new DamageScreenFilterLayer();
	private final RationalityScreenFilterLayer rationality = new RationalityScreenFilterLayer();
	private ClientLevel level;
	private LocalPlayer sessionPlayer;

	private PlayerScreenFilters() {
	}

	public void hit(int channels) {
		if (sessionUnavailable() || !PlayerHudConfig.ENABLE_LC_COLOR_DAMAGE_FILTER.get()) {
			return;
		}

		damage.hit(channels);
	}

	public void tick() {
		if (sessionUnavailable()) {
			return;
		}

		if (!PlayerHudConfig.ENABLE_LC_COLOR_DAMAGE_FILTER.get()) {
			damage.clear();
		}

		if (!PlayerHudConfig.ENABLE_LOW_RATIONALITY_FILTER.get()) {
			rationality.clear();
		}
	}

	public void extractDamage(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (visible() && PlayerHudConfig.ENABLE_LC_COLOR_DAMAGE_FILTER.get()) {
			damage.render(graphics, deltaTracker);
		}
	}

	public void extractRationality(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (visible() && PlayerHudConfig.ENABLE_LOW_RATIONALITY_FILTER.get()) {
			rationality.render(graphics, deltaTracker);
		}
	}

	private boolean visible() {
		if (sessionUnavailable()) {
			return false;
		}

		var minecraft = Minecraft.getInstance();
		var player = minecraft.player;
		return player != null && !minecraft.options.hideGui && !player.isSpectator();
	}

	private boolean sessionUnavailable() {
		var minecraft = Minecraft.getInstance();
		var player = minecraft.player;
		if (level != minecraft.level || player != sessionPlayer) {
			clear();
			level = minecraft.level;
			sessionPlayer = player;
		}

		damage.playerChanged(player);
		rationality.playerChanged(player);
		return level == null || player == null;
	}

	public void clear() {
		damage.clear();
		rationality.clear();
		level = null;
		sessionPlayer = null;
	}
}
