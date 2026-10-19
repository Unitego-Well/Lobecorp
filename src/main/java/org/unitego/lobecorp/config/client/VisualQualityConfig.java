package org.unitego.lobecorp.config.client;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/// 仅客户端的视觉预算；不改变服务端技能、碰撞或动画时间。
public class VisualQualityConfig {
	public static final String FILE_NAME = "lobecorp-visual-quality.toml";
	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.BooleanValue ANIMATIONS, MODELS;
	public static final ModConfigSpec.DoubleValue HYSTERESIS;
	public static final ModConfigSpec.DoubleValue MEDIUM_CUBE_DETAIL, FAR_CUBE_DETAIL;
	public static final ModConfigSpec.IntValue CACHE_MIB, MAX_BAKES;
	public static final ModConfigSpec.DoubleValue ANIMATION_NEAR, ANIMATION_FAR, MODEL_NEAR, MODEL_FAR;
	public static final ModConfigSpec.IntValue MEDIUM_FPS, FAR_FPS, CACHE_ENTRIES, MIN_CUBES;
	public static final ModConfigSpec.ConfigValue<List<? extends String>> ANIMATION_EXEMPT, MODEL_EXEMPT, CONTROLLER_EXEMPT;

	static {
		var b = new ModConfigSpec.Builder();
		b.push("animations");
		ANIMATIONS = b.comment("只对通过工具方法显式接入的本模组实体控制器降低姿态采样率；事件继续推进。").define("enabled", true);
		ANIMATION_NEAR = b.defineInRange("nearDistance", 50d, 0d, 4096d);
		ANIMATION_FAR = b.defineInRange("farDistance", 80d, 1d, 8192d);
		MEDIUM_FPS = b.defineInRange("mediumSampleFps", 30, 1, 240);
		FAR_FPS = b.defineInRange("farSampleFps", 12, 1, 240);
		ANIMATION_EXEMPT = b.comment("不降帧的实体注册ID。").defineListAllowEmpty("exemptEntities", List.of(), () -> "", v -> v instanceof String);
		CONTROLLER_EXEMPT = b.comment("不降帧的控制器名称。").defineListAllowEmpty("exemptControllers", List.of(), () -> "", v -> v instanceof String);
		b.pop().push("models");
		MODELS = b.comment("显式接入的实体模型自动烘焙LOD。").define("enabled", true);
		MODEL_NEAR = b.defineInRange("nearDistance", 50d, 0d, 4096d);
		MODEL_FAR = b.defineInRange("farDistance", 100d, 1d, 8192d);
		MEDIUM_CUBE_DETAIL = b.comment("中距离可省略的小细节尺寸，模型单位；16单位为1格，每个骨骼的主要几何保留。")
				.defineInRange("mediumCubeDetailSize", 0.5d, 0d, 32d);
		FAR_CUBE_DETAIL = b.comment("远距离可省略的小细节尺寸，模型单位；零表示不省略。")
				.defineInRange("farCubeDetailSize", 2d, 0d, 64d);
		MIN_CUBES = b.comment("少于此立方体数量的GeckoLib模型保持原样。").defineInRange("minimumEntityCubes", 64, 1, 100000);
		MODEL_EXEMPT = b.comment("不降模型精细度的实体注册ID。")
				.defineListAllowEmpty("exemptModels", List.of(), () -> "", v -> v instanceof String);
		CACHE_ENTRIES = b.comment("每个烘焙缓存的最大条目数；资源重载及退出清理。").defineInRange("cacheEntries", 256, 1, 4096);
		CACHE_MIB = b.comment("每种模型缓存的预算，单位MiB。").defineInRange("cacheMiB", 32, 1, 512);
		MAX_BAKES = b.comment("每种缓存每帧最多新烘焙的模型数，超额临时绘制原模型。").defineInRange("maxBakesPerFrame", 2, 1, 64);
		b.pop();
		HYSTERESIS = b.comment("距离档位恢复迟滞，单位格。").defineInRange("distanceHysteresis", 4d, 0d, 128d);
		SPEC = b.build();
	}
}
