package org.unitego.lobecorp.registry.translation;

import static org.unitego.lobecorp.registry.translation.TranslationRegistry.register;

public interface ServerConfigTranslationKeys {
	String EVENTS = register("lobecorp.configuration.server.events", "Events", "事件");
	String EVENTS_MIDNIGHT_CLEANUP = register("lobecorp.configuration.server.events.midnight_cleanup", "Midnight cleanup", "深夜清扫");
	String EVENTS_MIDNIGHT_CLEANUP_WAVE = register("lobecorp.configuration.server.events.midnight_cleanup.wave", "Wave", "浪潮");
	String EVENTS_BUDGET = register("lobecorp.configuration.server.events.budget", "Runtime budget", "运行预算");
	String DEBUG = register("lobecorp.configuration.server.debug", "Debug", "调试");
	String COMBAT = register("lobecorp.configuration.server.combat", "Combat", "战斗");
	String COMBAT_SHIELDS = register("lobecorp.configuration.server.combat.shields", "Shields", "护盾");
	String COMBAT_SHIELDS_MULTIPLE = register("lobecorp.configuration.server.combat.shields.multiple", "Allow multiple shields", "允许多种护盾共存");
	String COMBAT_SHIELDS_DAMAGE_IMMUNITY = register("lobecorp.configuration.server.combat.shields.damageImmunity", "Prevent overflow damage", "免除护盾溢出伤害");
	String COMBAT_SHIELDS_VALUE_PER_LEVEL = register("lobecorp.configuration.server.combat.shields.valuePerLevel", "Shield value per level", "每级护盾值");

	static String path(String key) {
		return "lobecorp.configuration.server." + key;
	}
}
