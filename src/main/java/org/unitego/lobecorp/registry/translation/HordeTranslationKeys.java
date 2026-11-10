package org.unitego.lobecorp.registry.translation;

import static org.unitego.lobecorp.registry.translation.TranslationRegistry.register;

public interface HordeTranslationKeys {
	String INVALID_WAYPOINTS = register("horde.lobecorp.invalid_waypoints", "Waypoints must be x,y,z;x,y,z", "途经点格式为 x,y,z;x,y,z");
	String INVALID_PROFILE = register("horde.lobecorp.invalid_profile", "Invalid horde profile", "浪潮定义无效");
	String INVALID_EVENT = register("horde.lobecorp.invalid_event", "Invalid horde UUID", "浪潮 UUID 无效");
	String START_FAILED = register("horde.lobecorp.start_failed", "Cannot start horde: %s", "无法启动浪潮：%s");
	String STARTED = register("horde.lobecorp.started", "Horde started: %s", "浪潮已启动：%s");
	String STOPPED = register("horde.lobecorp.stopped", "Horde %s: %s", "浪潮 %s：%s");
	String TEMPLATE_TOO_SLOW = register("horde.lobecorp.template_too_slow", "The route cannot finish within three minutes at this speed", "当前速度无法在三分钟内通过模板线路");
}
