package org.unitego.lobecorp.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.client.PlayerHudConfig;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;

import java.util.Arrays;

public class PlayerScreenFilters {
	private static final Identifier[] DAMAGE_TEXTURES = {
			texture("physics"), texture("spirit"), texture("erosion"), texture("the_soul")
	};
	private static final Identifier[] RATIONALITY_TEXTURES = {
			texture("rationality"), texture("rationality1"), texture("rationality2")
	};
	private static final float[] DAMAGE_ALPHA = new float[4];
	private static final float LOW_RATIONALITY_THRESHOLD = 1.0F / 3.0F;
	private static final float DAMAGE_FADE_PER_TICK = 0.5F;
	private static ClientLevel level;
	private static LocalPlayer sessionPlayer;
	private static float rationalityIntensity;

	public static void hit(int channels) {
		if (sessionUnavailable() || !PlayerHudConfig.ENABLE_LC_COLOR_DAMAGE_FILTER.get()) {
			return;
		}

		for (int index = 0; index < DAMAGE_ALPHA.length; index++) {
			if ((channels & 1 << index) != 0) {
				DAMAGE_ALPHA[index] = 1;
			}
		}
	}

	public static void tick() {
		if (sessionUnavailable() || Minecraft.getInstance().isPaused()) {
			return;
		}

		for (int index = 0; index < DAMAGE_ALPHA.length; index++) {
			DAMAGE_ALPHA[index] = PlayerHudConfig.ENABLE_LC_COLOR_DAMAGE_FILTER.get()
					? Math.max(0, DAMAGE_ALPHA[index] - DAMAGE_FADE_PER_TICK) : 0;
		}
	}

	public static void extractDamage(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (hidden() || !PlayerHudConfig.ENABLE_LC_COLOR_DAMAGE_FILTER.get()) {
			return;
		}

		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		for (int index = 0; index < DAMAGE_ALPHA.length; index++) {
			overlay(graphics, DAMAGE_TEXTURES[index], Math.max(0, DAMAGE_ALPHA[index] - partial * DAMAGE_FADE_PER_TICK));
		}
	}

	public static void extractRationality(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (hidden() || !PlayerHudConfig.ENABLE_LOW_RATIONALITY_FILTER.get()) {
			rationalityIntensity = 0;
			return;
		}

		var player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		float maximum = LcDamageUtil.getMaxRationality(player);
		float ratio = maximum > 0 ? LcDamageUtil.getRationality(player) / maximum : 1;
		// 当前资源不允许负值：最低三分之一映射到旧滤镜的完整三层渐变。
		float target = Math.clamp((LOW_RATIONALITY_THRESHOLD - ratio) / LOW_RATIONALITY_THRESHOLD, 0, 1);
		float delta = Minecraft.getInstance().isPaused() ? 0 : deltaTracker.getRealtimeDeltaTicks();
		rationalityIntensity += (target - rationalityIntensity) * Math.clamp(delta * 0.5F, 0, 1);
		float value = rationalityIntensity;
		if (value <= 0) {
			return;
		}

		overlay(graphics, RATIONALITY_TEXTURES[0], Math.clamp(1 - Math.abs(value - 0.25F) * 4, 0, 1));
		overlay(graphics, RATIONALITY_TEXTURES[1], Math.clamp(1 - Math.abs(value - 0.666F) * 1.5F, 0, 1));
		overlay(graphics, RATIONALITY_TEXTURES[2], Math.clamp((value - 0.666F) * 3, 0, 1));
	}

	private static boolean hidden() {
		if (sessionUnavailable()) {
			return true;
		}

		var minecraft = Minecraft.getInstance();
		var player = minecraft.player;
		return player == null || minecraft.options.hideGui || player.isSpectator();
	}

	private static boolean sessionUnavailable() {
		var minecraft = Minecraft.getInstance();
		var player = minecraft.player;
		if (level != minecraft.level || player != sessionPlayer) {
			clear();
			level = minecraft.level;
			sessionPlayer = player;
		}

		return level == null || player == null;
	}

	public static void clear() {
		Arrays.fill(DAMAGE_ALPHA, 0);
		rationalityIntensity = 0;
		level = null;
		sessionPlayer = null;
	}

	private static Identifier texture(String name) {
		return Lobecorp.id("textures/gui/filter/" + name + ".png");
	}

	private static void overlay(GuiGraphicsExtractor graphics, Identifier texture, float alpha) {
		if (alpha <= 0 || Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty()) {
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int color = Math.clamp(Math.round(alpha * 255), 0, 255) << 24 | 0xFFFFFF;
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, width, height, width, height, color);
	}
}
