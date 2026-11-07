package org.unitego.lobecorp.client.hud.status;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.ContainerHudRenderer;
import org.unitego.lobecorp.client.hud.TextureHudRenderer;
import org.unitego.lobecorp.client.hud.status.bar.PanelStatusBar;
import org.unitego.lobecorp.client.hud.widget.PlayerAvatar;
import org.unitego.lobecorp.config.client.HealthBarStyle;
import org.unitego.lobecorp.config.client.HudStyle;
import org.unitego.lobecorp.config.client.PlayerHudConfig;

/// 事件上方的玩家头像与状态条框。
@NullMarked
public class PlayerStatusPanelHud extends ContainerHudRenderer {
	private static final Identifier AVATAR_BOX = sprite("avatar_box");
	private static final Identifier HEALTH_BOX = sprite("bar/health/box");
	private static final Identifier HEALTH_ICON = sprite("bar/health/icon");
	private static final Identifier RATIONALITY_BOX = sprite("bar/rationality/box");
	private static final Identifier RATIONALITY_ICON = sprite("bar/rationality/icon");
	private static final int AVATAR_SIZE = 50;
	private final PanelStatusBar healthBar = new PanelStatusBar(false, HEALTH_BOX, HEALTH_ICON);
	private final PanelStatusBar rationalityBar = new PanelStatusBar(true, RATIONALITY_BOX, RATIONALITY_ICON);
	private final PlayerAvatar avatar = new PlayerAvatar(26, 1.1F);

	public PlayerStatusPanelHud() {
		setSize(AVATAR_SIZE + 3 + 66, AVATAR_SIZE);
		var background = TextureHudRenderer.sprite(AVATAR_BOX);
		background.setSize(AVATAR_SIZE, AVATAR_SIZE);
		add(background);
		avatar.setPosition((AVATAR_SIZE - avatar.getWidth()) / 2F, (AVATAR_SIZE - avatar.getHeight()) / 2F);
		healthBar.setPosition(AVATAR_SIZE, 8);
		rationalityBar.setPosition(AVATAR_SIZE, 25);
		add(avatar);
		add(healthBar);
		add(rationalityBar);
	}

	public static boolean visibleForPlayer() {
		var minecraft = Minecraft.getInstance();
		return minecraft.player != null && !minecraft.player.isSpectator() && !minecraft.options.hideGui;
	}

	private static Identifier texture(String path) {
		return Lobecorp.id("textures/gui/sprites/newhud/" + path + ".png");
	}

	private static Identifier sprite(String path) {
		return Lobecorp.id("newhud/" + path);
	}

	/// 在事件层左上角绘制头像框与两条状态框。
	public void render(GuiGraphicsExtractor graphics, float x, float y, float delta) {
		prepare(delta);
		super.render(graphics, x, y);
	}

	public void prepare(float delta) {
		var player = Minecraft.getInstance().player;
		avatar.setPlayer(player);
		boolean healthVisible = PlayerHudConfig.HEALTH_STYLE.get() == HealthBarStyle.NEW;
		boolean rationalityVisible = PlayerHudConfig.RATIONALITY_STYLE.get() == HudStyle.NEW;
		healthBar.setVisible(healthVisible);
		rationalityBar.setVisible(rationalityVisible);
		rationalityBar.setPosition(AVATAR_SIZE, healthVisible ? 25 : 8);
		if (healthVisible) {
			healthBar.prepare(player, delta);
		} else {
			healthBar.clear();
		}
		if (rationalityVisible) {
			rationalityBar.prepare(player, delta);
		} else {
			rationalityBar.clear();
		}
		setSize(AVATAR_SIZE + Math.max(healthVisible ? healthBar.getWidth() : 0,
				rationalityVisible ? rationalityBar.getWidth() : 0), AVATAR_SIZE);

	}

	public void clear() {
		healthBar.clear();
		rationalityBar.clear();
	}

	public int width() {
		return (int) getWidth();
	}

	public int height() {
		return (int) getHeight();
	}
}
