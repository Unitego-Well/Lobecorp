package org.unitego.lobecorp.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.config.client.PlayerHudConfig;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;
import org.unitego.lobecorp.util.entity.damage.LcShieldUtil;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class PlayerStatusHud {
	/// 沿用旧 HUD 的底部左侧位置及条内宽度，单位为 GUI 像素。
	private static final int LEFT_OFFSET = 105;
	private static final int BOTTOM_OFFSET = 30;
	private static final int FILL_WIDTH = 78;
	private static final int FILL_HEIGHT = 7;
	private static final float HIGH_RATIONALITY_RATIO = 0.7F;
	private static final float LOW_RATIONALITY_RATIO = 1.0F / 3.0F;
	private static final DecimalFormat NUMBER = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
	private static final Bar HEALTH = new Bar();
	private static final Bar RATIONALITY = new Bar();
	private static final Bar[] SHIELDS = {new Bar(), new Bar(), new Bar(), new Bar()};
	private static LocalPlayer sessionPlayer;

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		var minecraft = Minecraft.getInstance();
		var player = minecraft.player;
		if (player == null || minecraft.options.hideGui || player.isSpectator()) {
			return;
		}

		if (player != sessionPlayer) {
			clear();
			sessionPlayer = player;
		}

		int x = graphics.guiWidth() / 2 - LEFT_OFFSET;
		int y = graphics.guiHeight() - BOTTOM_OFFSET;
		float delta = minecraft.isPaused() ? 0 : deltaTracker.getRealtimeDeltaTicks();
		if (player.isCreative()) {
			y += 5;
		} else if (PlayerHudConfig.ENABLE_NEW_HEALTH_BAR.get()) {
			y -= 9;
			String variant = player.hasEffect(MobEffects.POISON) ? "poisioned"
					: player.hasEffect(MobEffects.WITHER) ? "withered" : player.isFullyFrozen() ? "frozen" : "full";
			HEALTH.draw(graphics, delta, x + 3, y, 90, 9, 11, 1,
					"health/" + variant, "health/" + variant + "_bottom", "health/light",
					player.getHealth(), player.getMaxHealth(), player.getAbsorptionAmount());

			if (player.getAbsorptionAmount() > 0) {
				sprite(graphics, "health/absorbing", x + 13, y, 80, 9);
			}

			y -= 2;
		} else {
			y -= 10;
		}

		float maximum = LcDamageUtil.getMaxRationality(player);
		float value = LcDamageUtil.getRationality(player);
		float ratio = maximum > 0 ? value / maximum : 1;
		String rationality = ratio >= HIGH_RATIONALITY_RATIO ? "rationality_tall"
				: ratio < LOW_RATIONALITY_RATIO ? "rationality_low" : "rationality";
		y -= 11;
		RATIONALITY.draw(graphics, delta, x, y, 93, 11, 14, 2,
				"rationality/" + rationality, "rationality/" + rationality + "_bottom",
				"rationality/" + rationality + "_light", value, maximum, 0);
		if (maximum > 0 && value <= 0) {
			sprite(graphics, "rationality/rationality_low_dynamic", x - 3, y - 1, 98, 14);
		}

		if (player.isCreative()) {
			return;
		}

		var shields = player.getData(LcAttachmentTypes.LC_SHIELDS);
		for (var type : LcDamageType.values()) {
			var pool = shields.get(type);
			if (pool.amount() <= 0 || !player.hasEffect(LcShieldUtil.effect(type))) {
				continue;
			}

			y -= 13;
			String name = switch (type) {
				case PHYSICS -> "physic";
				case SPIRIT -> "spirit";
				case EROSION -> "erosion";
				case THE_SOUL -> "soul";
			};
			String shield = "shield/" + name + "_shield_bar";
			SHIELDS[type.ordinal()].draw(graphics, delta, x, y, 93, 11, 14, 2,
					shield, shield + "_bottom", shield + "_light", pool.amount(), pool.maximum(), 0);
		}
	}

	public static int armorOffset() {
		var player = Minecraft.getInstance().player;
		if (player == null || player.isSpectator() || player.isCreative()) {
			return 0;
		}

		int height = 13 + (PlayerHudConfig.ENABLE_NEW_HEALTH_BAR.get() ? 11 : 0);
		var shields = player.getData(LcAttachmentTypes.LC_SHIELDS);
		for (var type : LcDamageType.values()) {
			if (shields.get(type).amount() > 0 && player.hasEffect(LcShieldUtil.effect(type))) {
				height += 13;
			}
		}

		return height + 1;
	}

	public static void clear() {
		sessionPlayer = null;
		HEALTH.clear();
		RATIONALITY.clear();
		for (var bar : SHIELDS) {
			bar.clear();
		}
	}

	private static Identifier texture(String path) {
		return Lobecorp.id("hud_bar/" + path);
	}

	private static void sprite(GuiGraphicsExtractor graphics, String path, int x, int y, int width, int height) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture(path), x, y, width, height);
	}

	private static class Bar {
		private boolean initialized;
		private float value;
		private float rendered;
		private float flash;
		private float trail;

		private void draw(GuiGraphicsExtractor graphics, float delta, int x, int y, int width, int height,
		                  int fillX, int fillY, String fill, String bottom, String light,
		                  float next, float maximum, float absorption) {
			if (!initialized) {
				initialized = true;
				rendered = next;
				value = next;
				trail = next;
			}

			if (next != value) {
				flash = 2;
				trail = Math.max(trail, value);
				value = next;
			}

			rendered += (next - rendered) * Math.clamp(delta * 1.5F, 0, 1);
			trail += (next - trail) * Math.clamp(delta * 0.2F, 0, 1);
			sprite(graphics, bottom, x, y, width, height);
			int filled = maximum > 0 ? Math.clamp(Math.round(rendered / maximum * FILL_WIDTH), 0, FILL_WIDTH) : 0;
			int trailing = maximum > 0 ? Math.clamp(Math.round(trail / maximum * FILL_WIDTH), 0, FILL_WIDTH) : 0;
			if (trailing > filled) {
				graphics.fill(x + fillX + filled, y + fillY, x + fillX + trailing, y + fillY + FILL_HEIGHT, 0xB2FFFFFF);
			}

			if (filled > 0) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture(fill), FILL_WIDTH, FILL_HEIGHT,
						0, 0, x + fillX, y + fillY, filled, FILL_HEIGHT);
			}

			if (flash > 1.5F || flash > 0 && flash <= 0.5F) {
				sprite(graphics, light, x, y, width, height);
			}

			flash = Math.max(0, flash - delta);
			String text = NUMBER.format(next) + (absorption > 0 ? "+" + NUMBER.format(absorption) : "")
					+ "/" + NUMBER.format(maximum);
			var font = Minecraft.getInstance().font;
			graphics.text(font, text, x - font.width(text) - 3, y + 1, 0xFFFFFFFF);
		}

		private void clear() {
			initialized = false;
			value = rendered = flash = trail = 0;
		}
	}
}
