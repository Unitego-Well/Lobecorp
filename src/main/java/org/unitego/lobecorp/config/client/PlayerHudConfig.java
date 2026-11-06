package org.unitego.lobecorp.config.client;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.api.LcDamageType;

public class PlayerHudConfig {
	public static final String PLAYER_HUD_STYLE_TRANSLATION = "lobecorp.configuration.client.hud.style.";
	public static final ModConfigSpec.EnumValue<HealthBarStyle> HEALTH_STYLE;
	public static final ModConfigSpec.EnumValue<HudStyle> RATIONALITY_STYLE, SHIELD_STYLE, TIMER_STYLE;
	public static final ModConfigSpec.BooleanValue ENABLE_LOW_RATIONALITY_FILTER;
	public static final ModConfigSpec.BooleanValue ENABLE_LC_COLOR_DAMAGE_FILTER;

	static {
		synchronized (ClientConfig.class) {
			ClientConfig.translation("hud.style.default", "Default", "默认");
			ClientConfig.translation("hud.style.vanilla", "Vanilla", "原版");
			ClientConfig.translation("hud.style.new", "new", "new");
			var builder = ClientConfig.section("hud", "HUD", "界面");
			builder.translation(ClientConfig.translation("hud.statusBars", "Status bars", "状态条")).push("statusBars");
			HEALTH_STYLE = builder.comment("生命值显示样式。")
					.translation(ClientConfig.translation("hud.healthStyle", "Health bar style", "生命值样式"))
					.defineEnum("healthStyle", HealthBarStyle.VANILLA);
			RATIONALITY_STYLE = builder.comment("理智条显示样式。")
					.translation(ClientConfig.translation("hud.rationalityStyle", "Rationality bar style", "理智条样式"))
					.defineEnum("rationalityStyle", HudStyle.DEFAULT);
			SHIELD_STYLE = builder.comment("护盾样式；NEW 随对应 NEW 值条生效，灵魂护盾保留默认条。")
					.translation(ClientConfig.translation("hud.shieldStyle", "Shield bar style", "护盾条样式"))
					.defineEnum("shieldStyle", HudStyle.DEFAULT);
			builder.pop().translation(ClientConfig.translation("hud.events", "Events", "事件")).push("events");
			builder.translation(ClientConfig.translation("hud.events.midnight_cleanup", "Midnight cleanup", "深夜清扫")).push("midnight_cleanup");
			TIMER_STYLE = builder.comment("深夜清扫计时器显示样式。")
					.translation(ClientConfig.translation("hud.timerStyle", "Timer style", "计时器样式"))
					.defineEnum("timerStyle", HudStyle.DEFAULT);
			builder.pop(2).translation(ClientConfig.translation("hud.filters", "Screen filters", "屏幕滤镜")).push("filters");
			ENABLE_LOW_RATIONALITY_FILTER = builder.comment("启用低理智屏幕滤镜。")
					.translation(ClientConfig.translation("hud.enable_low_rationality_filter", "Low rationality filter", "低理智滤镜")).define("enable_low_rationality_filter", true);
			ENABLE_LC_COLOR_DAMAGE_FILTER = builder.comment("启用受击时的四色伤害滤镜。")
					.translation(ClientConfig.translation("hud.enable_lobotomy_corporation_damage_filter", "Damage color filter", "受击四色滤镜")).define("enable_lobotomy_corporation_damage_filter", true);
			builder.pop(2);
		}

	}

	public static boolean usesNewShield(LcDamageType type) {
		if (SHIELD_STYLE.get() != HudStyle.NEW) {
			return false;
		}
		return switch (type) {
			case PHYSICS, EROSION -> HEALTH_STYLE.get() == HealthBarStyle.NEW;
			case SPIRIT -> RATIONALITY_STYLE.get() == HudStyle.NEW;
			case THE_SOUL -> false;
		};
	}

	public static void init() {
	}
}
