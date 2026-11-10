package org.unitego.lobecorp.config.client;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.registry.translation.ClientConfigTranslationKeys;

/// 四组客户端选项共用一份配置，避免配置界面出现多个客户端入口。
public class ClientConfig {
	public static final String FILE_NAME = "lobecorp-client.toml";
	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue CORPSE_BATCH = BUILDER
            .translation(ClientConfigTranslationKeys.CORPSE_BATCH)
            .define("corpseBatch", true);

	/// 调用方在同一监视器内完成整组定义，防止并行类初始化交错push/pop。
	public static ModConfigSpec.Builder section(String key, String translationKey) {
		return BUILDER.translation(translationKey).push(key);
	}

	public static String translation(String key) {
		return ClientConfigTranslationKeys.path(key);
	}

	public static ModConfigSpec specification() {
		return Specification.SPEC;
	}

	/// 延迟构建，允许任何一组选项先初始化，不在循环类初始化途中提前封闭Builder。
	private static class Specification {
		private static final ModConfigSpec SPEC = build();

		private static ModConfigSpec build() {
			DamageParticleConfig.init();
			PlayerHudConfig.init();
			VisualQualityConfig.init();
			HordeVisualConfig.init();
			return BUILDER.build();
		}
	}
}
