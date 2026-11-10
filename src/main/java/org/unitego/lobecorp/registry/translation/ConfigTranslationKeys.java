package org.unitego.lobecorp.registry.translation;

import static org.unitego.lobecorp.registry.translation.TranslationRegistry.register;

public interface ConfigTranslationKeys {
	String DISPLAY = register("lobecorp.configuration.display", "Display", "显示");
	String AMBIENT = register("lobecorp.configuration.ambient", "Ambient effects", "环境效果");
	String ANIMATIONS = register("lobecorp.configuration.animations", "Animations", "动画");
	String MODELS = register("lobecorp.configuration.models", "Models", "模型");
	String CLIENT_FILE = register("lobecorp.configuration.lobecorp-client.toml", "Client", "客户端");
	String HORDE_EVENTS_FILE = register("lobecorp.configuration.lobecorp-horde-events.toml", "Horde events (server)", "群体事件（服务端）");
	String HORDE_VISUALS_FILE = register("lobecorp.configuration.lobecorp-horde-visuals.toml", "Horde visuals", "群体浪潮视觉");
}
