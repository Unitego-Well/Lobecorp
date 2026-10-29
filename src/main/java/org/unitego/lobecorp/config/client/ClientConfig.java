package org.unitego.lobecorp.config.client;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.loading.FMLPaths;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;

/// 四组客户端选项共用一份配置，避免配置界面出现多个客户端入口。
public class ClientConfig {
	public static final String FILE_NAME = "lobecorp-client.toml";
	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	/// 调用方在同一监视器内完成整组定义，防止并行类初始化交错push/pop。
	public static ModConfigSpec.Builder section(String key, String english, String chinese) {
		return BUILDER.translation(translation(key, english, chinese)).push(key);
	}

	public static String translation(String key, String english, String chinese) {
		String translation = "lobecorp.configuration.client." + key;
		LangHandler.creates(Lobecorp.NAMESPACE, translation, english, chinese);
		return translation;
	}

	public static ModConfigSpec specification() {
		return Specification.SPEC;
	}

	/// 只补入统一配置缺失的旧分组；先保留原始字节备份，不删除旧文件。
	private static void migrateLegacy() {
		var directory = FMLPaths.CONFIGDIR.get();
		var destination = directory.resolve(FILE_NAME);
		var legacyFiles = Map.of("hud", "lobecorp-hud-client.toml",
				"visualQuality", "lobecorp-visual-quality.toml", "horde", "lobecorp-horde-visuals.toml");
		Path backup = null;
		try (var unified = CommentedFileConfig.builder(destination).sync().build()) {
			if (Files.isRegularFile(destination)) {
				unified.load();
			}

			for (var entry : legacyFiles.entrySet()) {
				var source = directory.resolve(entry.getValue());
				if (unified.contains(entry.getKey()) || !Files.isRegularFile(source)) {
					continue;
				}

				if (backup == null) {
					var backups = directory.resolve("lobecorp-client-backups");
					Files.createDirectories(backups);
					backup = Files.createTempDirectory(backups, "migration-");
					if (Files.isRegularFile(destination)) {
						Files.copy(destination, backup.resolve(FILE_NAME), StandardCopyOption.COPY_ATTRIBUTES);
					}
				}

				Files.copy(source, backup.resolve(entry.getValue()), StandardCopyOption.COPY_ATTRIBUTES);
				try (var legacy = CommentedFileConfig.builder(source).sync().build()) {
					legacy.load();
					unified.set(entry.getKey(), CommentedConfig.copy(legacy));
				}
			}

			if (backup != null) {
				unified.save();
				Lobecorp.LOGGER.info("客户端配置已合并，原文件备份：{}", backup);
			}
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("客户端配置迁移失败；保留旧文件，备份：" + backup, exception);
		}
	}

	/// 延迟构建，允许任何一组选项先初始化，不在循环类初始化途中提前封闭Builder。
	private static class Specification {
		private static final ModConfigSpec SPEC = build();

		private static ModConfigSpec build() {
			DamageParticleConfig.init();
			PlayerHudConfig.init();
			VisualQualityConfig.init();
			HordeVisualConfig.init();
			migrateLegacy();
			return BUILDER.build();
		}
	}
}
