package org.unitego.lobecorp.config.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public class PlayerHudConfig {
	public static final ModConfigSpec.BooleanValue ENABLE_NEW_HEALTH_BAR;
	public static final ModConfigSpec.BooleanValue ENABLE_LOW_RATIONALITY_FILTER;
	public static final ModConfigSpec.BooleanValue ENABLE_LC_COLOR_DAMAGE_FILTER;

	static {
		synchronized (ClientConfig.class) {
			var builder = ClientConfig.section("hud", "HUD", "界面");
			ENABLE_NEW_HEALTH_BAR = builder.comment("用旧项目样式的生命条替换原版生命和吸收生命显示。")
					.translation(ClientConfig.translation("hud.enable_new_health_bar", "Custom health bar", "自定义生命条")).define("enable_new_health_bar", false);
			ENABLE_LOW_RATIONALITY_FILTER = builder.comment("启用低理智屏幕滤镜。")
					.translation(ClientConfig.translation("hud.enable_low_rationality_filter", "Low rationality filter", "低理智滤镜")).define("enable_low_rationality_filter", true);
			ENABLE_LC_COLOR_DAMAGE_FILTER = builder.comment("启用受击时的四色伤害滤镜。")
					.translation(ClientConfig.translation("hud.enable_lobotomy_corporation_damage_filter", "Damage color filter", "受击四色滤镜")).define("enable_lobotomy_corporation_damage_filter", true);
			builder.pop();
		}

	}

	public static void init() {
	}
}
