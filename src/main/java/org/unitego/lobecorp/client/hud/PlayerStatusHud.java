package org.unitego.lobecorp.client.hud;

import net.minecraft.client.DeltaTracker;
import org.jspecify.annotations.NullMarked;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.client.hud.layer.CompositeHudLayer;
import org.unitego.lobecorp.client.hud.layer.HealthBarLayer;
import org.unitego.lobecorp.client.hud.layer.RationalityBarLayer;
import org.unitego.lobecorp.client.hud.layer.ShieldBarLayer;
import org.unitego.lobecorp.config.client.PlayerHudConfig;
import org.unitego.lobecorp.util.entity.damage.LcShieldUtil;

/// 玩家状态 HUD 的唯一组合实例；各条与动画状态由实例持有的子层管理。
@NullMarked
public class PlayerStatusHud extends CompositeHudLayer {
	private static final int LEFT_OFFSET = 105;
	private static final int BOTTOM_OFFSET = 30;
	public static final PlayerStatusHud INSTANCE = new PlayerStatusHud();

	private PlayerStatusHud() {
		addLayer(new HealthBarLayer(), () -> survivalVisible() && PlayerHudConfig.ENABLE_NEW_HEALTH_BAR.get());
		addLayer(new RationalityBarLayer(), this::playerVisible);
		for (var type : LcDamageType.values()) {
			addLayer(new ShieldBarLayer(type), () -> survivalVisible()
					&& minecraft.player != null && minecraft.player.hasEffect(LcShieldUtil.effect(type)));
		}
	}

	@Override
	protected void updatePosition() {
		setPosition(screenWidth / 2 - LEFT_OFFSET, screenHeight - BOTTOM_OFFSET);
	}

	@Override
	protected void renderLayer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!playerVisible()) {
			return;
		}

		var pose = graphics.pose();
		pose.pushMatrix();
		try {
			if (minecraft.player != null && minecraft.player.isCreative()) {
				pose.translate(0, 5);
			} else if (!PlayerHudConfig.ENABLE_NEW_HEALTH_BAR.get()) {
				pose.translate(0, -10);
			}

			super.renderLayer(graphics, deltaTracker);
		} finally {
			pose.popMatrix();
		}
	}

	public int armorOffset() {
		return survivalVisible() ? getHeight() + 1 : 0;
	}

	private boolean playerVisible() {
		var current = minecraft.player;
		return current != null && !current.isSpectator();
	}

	private boolean survivalVisible() {
		var current = minecraft.player;
		return current != null && !current.isSpectator() && !current.isCreative();
	}
}
