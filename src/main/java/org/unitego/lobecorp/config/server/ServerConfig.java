package org.unitego.lobecorp.config.server;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.registry.translation.ServerConfigTranslationKeys;

/// 服务端全部规则共用一份世界级配置，不读取旧的独立配置文件。
public class ServerConfig {
	public static final String FILE_NAME = "lobecorp-server.toml";
	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	public static ModConfigSpec.Builder section(String key, String translationKey) {
		return BUILDER.translation(translationKey).push(key);
	}

	public static String translation(String key) {
		return ServerConfigTranslationKeys.path(key);
	}

	public static ModConfigSpec specification() {
		return Specification.SPEC;
	}

	private static class Specification {
		private static final ModConfigSpec SPEC = build();

		private static ModConfigSpec build() {
			ShieldConfig.init();
			HordeConfig.init();
			return BUILDER.build();
		}
	}
}
