package org.unitego.lobecorp.generator.lang;

import org.unitego.lobecorp.Lobecorp;

/// 配置标题和命令消息只在数据生成阶段写入语言集。
public class HordeLang {
	public static void init() {
        add("debug.entry.lobecorp.horde_ranges", "Horde ranges", "清道夫浪潮区域");
        add("lobecorp.configuration.display", "Display", "显示");
        add("lobecorp.configuration.ambient", "Ambient effects", "环境效果");
        add("lobecorp.configuration.animations", "Animations", "动画");
        add("lobecorp.configuration.models", "Models", "模型");
        add("lobecorp.configuration.lobecorp-client.toml", "Client", "客户端");
		add("horde.lobecorp.invalid_waypoints", "Waypoints must be x,y,z;x,y,z", "途经点格式为 x,y,z;x,y,z");
		add("horde.lobecorp.invalid_profile", "Invalid event profile", "事件定义无效");
		add("horde.lobecorp.invalid_event", "Invalid event UUID", "事件 UUID 无效");
		add("horde.lobecorp.start_failed", "Cannot start horde: %s", "无法启动群体事件：%s");
		add("horde.lobecorp.started", "Horde started: %s", "群体事件已启动：%s");
		add("horde.lobecorp.stopped", "Horde %s: %s", "群体事件 %s：%s");
		add("horde.lobecorp.template_too_slow", "The route cannot finish within three minutes at this speed", "当前速度无法在三分钟内通过模板线路");
		add("horde.lobecorp.banner.title", "The Noon of Indigo", "靛蓝色的正午");
		add("horde.lobecorp.banner.name", "The Sweepers", "清道夫");
		add("horde.lobecorp.banner.start", "When night falls, they quietly arrive in the Backstreets...", "夜幕降临之际，他们“悄悄地”来到后巷……");
		add("horde.lobecorp.banner.end", "When the sun rises, not a trace will remain...", "旭日东升之时，半点痕迹都不会剩下……");
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
