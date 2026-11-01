package org.unitego.lobecorp.config.server;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;

/// 服务端全部规则共用一份世界级配置，不读取旧的独立配置文件。
public class ServerConfig {
    public static final String FILE_NAME = "lobecorp-server.toml";
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static ModConfigSpec.Builder section(String key, String english, String chinese) {
        return BUILDER.translation(translation(key, english, chinese)).push(key);
    }

    public static String translation(String key, String english, String chinese) {
        String translation = "lobecorp.configuration.server." + key;
        LangHandler.creates(Lobecorp.NAMESPACE, translation, english, chinese);
        return translation;
    }

    public static ModConfigSpec specification() {
        return Specification.SPEC;
    }

    private static class Specification {
        private static final ModConfigSpec SPEC = build();

        private static ModConfigSpec build() {
            ShieldConfig.init();
            HordeEventConfig.init();
            return BUILDER.build();
        }
    }
}
