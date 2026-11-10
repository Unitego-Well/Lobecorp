package org.unitego.lobecorp.registry.translation;

import static org.unitego.lobecorp.registry.translation.TranslationRegistry.register;

public interface EncounterTranslationKeys {
	String INVALID_WAYPOINTS = register("encounter.lobecorp.invalid_waypoints", "Waypoints must be x,y,z;x,y,z", "途经点格式为 x,y,z;x,y,z");
	String INVALID_PROFILE = register("encounter.lobecorp.invalid_profile", "Invalid encounter profile", "遭遇定义无效");
	String INVALID_EVENT = register("encounter.lobecorp.invalid_event", "Invalid encounter UUID", "遭遇 UUID 无效");
	String START_FAILED = register("encounter.lobecorp.start_failed", "Cannot start encounter: %s", "无法启动遭遇：%s");
	String STARTED = register("encounter.lobecorp.started", "Encounter started: %s", "遭遇已启动：%s");
	String STOPPED = register("encounter.lobecorp.stopped", "Encounter %s: %s", "遭遇 %s：%s");
	String TEMPLATE_TOO_SLOW = register("encounter.lobecorp.template_too_slow", "The route cannot finish within three minutes at this speed", "当前速度无法在三分钟内通过模板线路");
}
