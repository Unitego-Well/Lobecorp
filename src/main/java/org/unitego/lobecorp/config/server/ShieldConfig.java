package org.unitego.lobecorp.config.server;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.registry.translation.ServerConfigTranslationKeys;

/// 服务端护盾规则；控制四色共存、破盾免伤和每级护盾值。
public class ShieldConfig {
	public static final ModConfigSpec.BooleanValue ENABLE_MULTI_SHIELD;
	public static final ModConfigSpec.BooleanValue ENABLE_SHIELD_DAMAGE_IMMUNITY;
	public static final ModConfigSpec.DoubleValue SHIELD_ADDITIONAL_VALUE_PER_LEVEL;

	static {
		var builder = ServerConfig.section("combat", ServerConfigTranslationKeys.COMBAT);
		builder.translation(ServerConfigTranslationKeys.COMBAT_SHIELDS).push("shields");
		ENABLE_MULTI_SHIELD = builder.translation(ServerConfigTranslationKeys.COMBAT_SHIELDS_MULTIPLE).comment("允许四色护盾同时存在。")
				.define("enable_multi_shield", false);
		ENABLE_SHIELD_DAMAGE_IMMUNITY = builder.translation(ServerConfigTranslationKeys.COMBAT_SHIELDS_DAMAGE_IMMUNITY).comment("护盾耗尽时免除本次对应通道的溢出伤害。")
				.define("enable_shield_damage_immunity", false);
		SHIELD_ADDITIONAL_VALUE_PER_LEVEL = builder.translation(ServerConfigTranslationKeys.COMBAT_SHIELDS_VALUE_PER_LEVEL).comment("效果每级提供的护盾值；重施时按当前配置重新填满。")
				.defineInRange("shield_additional_value_per_level", 10.0, 0.0, 1000.0);
		builder.pop(2);
	}

	public static void init() {
	}
}
