package org.unitego.lobecorp.config.client;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.registry.translation.ClientConfigTranslationKeys;

/// 客户端 HUD 显示开关；不改变服务端伤害、护盾或玩家状态。
public class PlayerHudConfig {
	public static final String PLAYER_HUD_STYLE_TRANSLATION = ClientConfigTranslationKeys.HUD_STYLE_PREFIX;
	public static final ModConfigSpec.EnumValue<HealthBarStyle> HEALTH_STYLE;
	public static final ModConfigSpec.EnumValue<HudStyle> RATIONALITY_STYLE, SHIELD_STYLE, TIMER_STYLE;
	public static final ModConfigSpec.BooleanValue ENABLE_LOW_RATIONALITY_FILTER;
	public static final ModConfigSpec.BooleanValue ENABLE_LC_COLOR_DAMAGE_FILTER;

	static {
		synchronized (ClientConfig.class) {
			var builder = ClientConfig.section("hud", ClientConfigTranslationKeys.HUD);
			builder.translation(ClientConfigTranslationKeys.HUD_STATUS_BARS).push("statusBars");
			HEALTH_STYLE = builder.comment("生命值显示样式。")
					.translation(ClientConfigTranslationKeys.HUD_HEALTH_STYLE)
					.defineEnum("healthStyle", HealthBarStyle.VANILLA);
			RATIONALITY_STYLE = builder.comment("理智条显示样式。")
					.translation(ClientConfigTranslationKeys.HUD_RATIONALITY_STYLE)
					.defineEnum("rationalityStyle", HudStyle.DEFAULT);
			SHIELD_STYLE = builder.comment("护盾样式；NEW 随对应 NEW 值条生效，灵魂护盾保留默认条。")
					.translation(ClientConfigTranslationKeys.HUD_SHIELD_STYLE)
					.defineEnum("shieldStyle", HudStyle.DEFAULT);
			builder.pop().translation(ClientConfigTranslationKeys.HUD_EVENTS).push("events");
			builder.translation(ClientConfigTranslationKeys.HUD_EVENTS_MIDNIGHT_CLEANUP).push("midnight_cleanup");
			TIMER_STYLE = builder.comment("深夜清扫计时器样式，统一控制框、数字精灵与相对布局。")
					.translation(ClientConfigTranslationKeys.HUD_TIMER_STYLE)
					.defineEnum("timerStyle", HudStyle.DEFAULT);
			builder.pop(2).translation(ClientConfigTranslationKeys.HUD_FILTERS).push("filters");
			ENABLE_LOW_RATIONALITY_FILTER = builder.comment("启用低理智屏幕滤镜。")
					.translation(ClientConfigTranslationKeys.HUD_ENABLE_LOW_RATIONALITY_FILTER).define("enable_low_rationality_filter", true);
			ENABLE_LC_COLOR_DAMAGE_FILTER = builder.comment("启用受击时的四色伤害滤镜。")
					.translation(ClientConfigTranslationKeys.HUD_ENABLE_LOBOTOMY_CORPORATION_DAMAGE_FILTER).define("enable_lobotomy_corporation_damage_filter", true);
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
