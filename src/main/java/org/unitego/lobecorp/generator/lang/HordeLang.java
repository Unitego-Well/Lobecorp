package org.unitego.lobecorp.generator.lang;

import org.unitego.lobecorp.Lobecorp;

/// 配置标题和命令消息只在数据生成阶段写入语言集。
public class HordeLang {
	public static void init() {
		add("horde.lobecorp.invalid_waypoints", "Waypoints must be x,y,z;x,y,z", "途经点格式为 x,y,z;x,y,z");
		add("horde.lobecorp.invalid_profile", "Invalid event profile", "事件定义无效");
		add("horde.lobecorp.invalid_event", "Invalid event UUID", "事件 UUID 无效");
		add("horde.lobecorp.start_failed", "Cannot start horde: %s", "无法启动群体事件：%s");
		add("horde.lobecorp.started", "Horde started: %s", "群体事件已启动：%s");
		add("horde.lobecorp.stopped", "Horde %s: %s", "群体事件 %s：%s");
		add("horde.lobecorp.midnight_cleanup", "Midnight Cleanup", "深夜清扫");
		add("lobecorp.configuration.lobecorp-horde-events.toml", "Horde events (server)", "群体事件（服务端）");
		add("lobecorp.configuration.lobecorp-horde-visuals.toml", "Horde visuals", "群体浪潮视觉");
	}

	public static String config(String key, String english, String chinese) {
		String translation = "lobecorp.configuration.horde." + key;
		add(translation, english, chinese);
		return translation;
	}

	private static void add(String key, String english, String chinese) {
		LangHandler.creates(Lobecorp.NAMESPACE, key, english, chinese);
	}
}
