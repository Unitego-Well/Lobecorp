package org.unitego.lobecorp.registry.translation.photon.visual;

import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.registry.translation.TranslationRegistry;

/// 设置页仅翻译显示文本；配置路径继续用于草稿、校验与持久化。
public class PhotonVisualQualityTranslations {
	private static final String PREFIX = "settings.lobecorp.visual_quality.";
	private static final String NAVIGATION = PREFIX + "navigation.";
	public static final String DEFAULT_VALUE = PREFIX + "default";
	public static final String RANGE = PREFIX + "range";
	public static final String EMPTY = PREFIX + "empty";

	public static final String DEFAULT_VALUE_TEXT = TranslationRegistry.register(DEFAULT_VALUE,
			"Default: %s", "默认值：%s");
	public static final String RANGE_TEXT = TranslationRegistry.register(RANGE,
			"Range: %s - %s", "取值范围：%s ～ %s");
	public static final String EMPTY_TEXT = TranslationRegistry.register(EMPTY,
			"Empty", "空");
	public static final String NAVIGATION_APPEARANCE = TranslationRegistry.register(NAVIGATION + "appearance",
			"Appearance", "外观");
	public static final String NAVIGATION_BEHAVIOR = TranslationRegistry.register(NAVIGATION + "behavior",
			"Behavior", "行为");
	public static final String NAVIGATION_KEYMAP = TranslationRegistry.register(NAVIGATION + "keymap",
			"Keymap", "快捷键");
	public static final String GROUP_EFFECTS = TranslationRegistry.register(group("effects"),
			"World effects", "世界特效");
	public static final String GROUP_ANIMATIONS = TranslationRegistry.register(group("animations"),
			"Entity animations", "实体动画");
	public static final String GROUP_MODELS = TranslationRegistry.register(group("models"),
			"Model detail", "模型精细度");
	public static final String GROUP_CACHE = TranslationRegistry.register(group("cache"),
			"Cache", "缓存");
	public static final String GROUP_ADAPTATION = TranslationRegistry.register(group("adaptation"),
			"Adaptive response", "自适应响应");
	public static final String GROUP_GENERAL = TranslationRegistry.register(group("general"),
			"General", "通用");

	public static final String EFFECTS_ENABLED = TranslationRegistry.register(label("effects.enabled"),
			"Reduce effect detail", "启用特效降级");
	public static final String EFFECTS_ENABLED_TOOLTIP = TranslationRegistry.register(description("effects.enabled"),
			"Reduce effect density with distance in the world. Editor previews keep full detail.", "按世界距离降低特效密度，编辑器预览保持完整精度。");
	public static final String EFFECTS_NEAR_DISTANCE = TranslationRegistry.register(label("effects.nearDistance"),
			"Near distance (blocks)", "近距离阈值（格）");
	public static final String EFFECTS_NEAR_DISTANCE_TOOLTIP = TranslationRegistry.register(description("effects.nearDistance"),
			"Distance where effect density starts decreasing.", "开始降低特效密度的距离，单位为格。");
	public static final String EFFECTS_FAR_DISTANCE = TranslationRegistry.register(label("effects.farDistance"),
			"Far distance (blocks)", "远距离阈值（格）");
	public static final String EFFECTS_FAR_DISTANCE_TOOLTIP = TranslationRegistry.register(description("effects.farDistance"),
			"Distance where minimum density is reached; never lower than the near distance.", "达到最低密度的距离，实际不小于近距离阈值。");
	public static final String EFFECTS_MINIMUM_DENSITY = TranslationRegistry.register(label("effects.minimumDensity"),
			"Minimum density", "最低粒子密度");
	public static final String EFFECTS_MINIMUM_DENSITY_TOOLTIP = TranslationRegistry.register(description("effects.minimumDensity"),
			"Minimum fraction of particle births. Fractional births carry forward instead of forming bursts.", "最低粒子出生比例；小数余量连续累计，避免集中补发。");
	public static final String EFFECTS_ADAPT_TO_VISIBLE_LOAD = TranslationRegistry.register(label("effects.adaptToVisibleLoad"),
			"Adapt to visible load", "按可见负载降级");
	public static final String EFFECTS_ADAPT_TO_VISIBLE_LOAD_TOOLTIP = TranslationRegistry.register(description("effects.adaptToVisibleLoad"),
			"Reduce density when visible effects exceed the workload budget.", "可见特效超过负载预算时，动态降低粒子密度。");
	public static final String EFFECTS_VISIBLE_WORK_BUDGET = TranslationRegistry.register(label("effects.visibleWorkBudget"),
			"Visible workload budget", "可见负载预算");
	public static final String EFFECTS_VISIBLE_WORK_BUDGET_TOOLTIP = TranslationRegistry.register(description("effects.visibleWorkBudget"),
			"Estimated visible particle count plus model triangle count.", "以可见粒子数量和模型三角形数量估算负载。");
	public static final String EFFECTS_EXEMPT_EMITTERS = TranslationRegistry.register(label("effects.exemptEmitters"),
			"Exempt emitter names", "免降级的发射器");
	public static final String EFFECTS_EXEMPT_EMITTERS_TOOLTIP = TranslationRegistry.register(description("effects.exemptEmitters"),
			"Comma-separated exact internal emitter names. Parent exemptions apply to spawned children.", "以英文逗号分隔发射器内部名称，精确匹配；父发射器的豁免传递给子实例。");

	public static final String ANIMATIONS_ENABLED = TranslationRegistry.register(label("animations.enabled"),
			"Reduce animation sampling", "启用动画降采样");
	public static final String ANIMATIONS_ENABLED_TOOLTIP = TranslationRegistry.register(description("animations.enabled"),
			"Only explicitly opted-in mod entity controllers reduce pose sampling. Events keep running.", "仅对显式接入的本模组实体控制器降低姿态采样率，关键帧事件继续推进。");
	public static final String ANIMATIONS_NEAR_DISTANCE = TranslationRegistry.register(label("animations.nearDistance"),
			"Near distance (blocks)", "中档起始距离（格）");
	public static final String ANIMATIONS_NEAR_DISTANCE_TOOLTIP = TranslationRegistry.register(description("animations.nearDistance"),
			"Distance where the medium animation sampling rate starts.", "达到此距离后使用中距离动画采样率，单位为格。");
	public static final String ANIMATIONS_FAR_DISTANCE = TranslationRegistry.register(label("animations.farDistance"),
			"Far distance (blocks)", "远档起始距离（格）");
	public static final String ANIMATIONS_FAR_DISTANCE_TOOLTIP = TranslationRegistry.register(description("animations.farDistance"),
			"Distance where the far animation sampling rate starts.", "达到此距离后使用远距离动画采样率，单位为格。");
	public static final String ANIMATIONS_MEDIUM_SAMPLE_FPS = TranslationRegistry.register(label("animations.mediumSampleFps"),
			"Medium sampling FPS", "中距离采样帧率");
	public static final String ANIMATIONS_MEDIUM_SAMPLE_FPS_TOOLTIP = TranslationRegistry.register(description("animations.mediumSampleFps"),
			"Pose samples per second at medium distance.", "中距离每秒采样的动画姿态数量。");
	public static final String ANIMATIONS_FAR_SAMPLE_FPS = TranslationRegistry.register(label("animations.farSampleFps"),
			"Far sampling FPS", "远距离采样帧率");
	public static final String ANIMATIONS_FAR_SAMPLE_FPS_TOOLTIP = TranslationRegistry.register(description("animations.farSampleFps"),
			"Pose samples per second at far distance.", "远距离每秒采样的动画姿态数量。");
	public static final String ANIMATIONS_EXEMPT_ENTITIES = TranslationRegistry.register(label("animations.exemptEntities"),
			"Exempt entity IDs", "免降采样的实体");
	public static final String ANIMATIONS_EXEMPT_ENTITIES_TOOLTIP = TranslationRegistry.register(description("animations.exemptEntities"),
			"Comma-separated full entity registry IDs.", "以英文逗号分隔完整实体注册 ID，列表中的实体不降低动画采样率。");
	public static final String ANIMATIONS_EXEMPT_CONTROLLERS = TranslationRegistry.register(label("animations.exemptControllers"),
			"Exempt controller names", "免降采样的控制器");
	public static final String ANIMATIONS_EXEMPT_CONTROLLERS_TOOLTIP = TranslationRegistry.register(description("animations.exemptControllers"),
			"Comma-separated exact animation controller names.", "以英文逗号分隔控制器名称，列表中的控制器不降低动画采样率。");

	public static final String MODELS_ENABLED = TranslationRegistry.register(label("models.enabled"),
			"Reduce model detail", "启用模型简化");
	public static final String MODELS_ENABLED_TOOLTIP = TranslationRegistry.register(description("models.enabled"),
			"Automatically bake distant LOD for complex effect meshes and opted-in entity models.", "为复杂特效网格和显式接入的实体模型自动烘焙远距离简化层级。");
	public static final String MODELS_NEAR_DISTANCE = TranslationRegistry.register(label("models.nearDistance"),
			"Near distance (blocks)", "中档起始距离（格）");
	public static final String MODELS_NEAR_DISTANCE_TOOLTIP = TranslationRegistry.register(description("models.nearDistance"),
			"Distance where medium model simplification starts.", "达到此距离后使用中距离模型简化，单位为格。");
	public static final String MODELS_FAR_DISTANCE = TranslationRegistry.register(label("models.farDistance"),
			"Far distance (blocks)", "远档起始距离（格）");
	public static final String MODELS_FAR_DISTANCE_TOOLTIP = TranslationRegistry.register(description("models.farDistance"),
			"Distance where far model simplification starts.", "达到此距离后使用远距离模型简化，单位为格。");
	public static final String MODELS_MINIMUM_TRIANGLES = TranslationRegistry.register(label("models.minimumTriangles"),
			"Minimum triangles", "最低三角形数");
	public static final String MODELS_MINIMUM_TRIANGLES_TOOLTIP = TranslationRegistry.register(description("models.minimumTriangles"),
			"Effect meshes below this triangle count keep their original geometry.", "少于此三角形数量的特效网格保持原样。");
	public static final String MODELS_MEDIUM_GRID = TranslationRegistry.register(label("models.mediumGrid"),
			"Medium grid detail", "中距离网格精度");
	public static final String MODELS_MEDIUM_GRID_TOOLTIP = TranslationRegistry.register(description("models.mediumGrid"),
			"Spatial clustering divisions at medium distance. Higher values retain more detail.", "中距离空间聚类的分段数，数值越大保留的细节越多。");
	public static final String MODELS_FAR_GRID = TranslationRegistry.register(label("models.farGrid"),
			"Far grid detail", "远距离网格精度");
	public static final String MODELS_FAR_GRID_TOOLTIP = TranslationRegistry.register(description("models.farGrid"),
			"Spatial clustering divisions at far distance. Higher values retain more detail.", "远距离空间聚类的分段数，数值越大保留的细节越多。");
	public static final String MODELS_ALLOW_PLANAR_EFFECTS = TranslationRegistry.register(label("models.allowPlanarEffects"),
			"Allow planar effects", "允许特效平面简化");
	public static final String MODELS_ALLOW_PLANAR_EFFECTS_TOOLTIP = TranslationRegistry.register(description("models.allowPlanarEffects"),
			"Also requires per-emitter permission. Compresses geometry without baking shader output into an image.", "还需具体发射器允许平面简化；仅压缩几何，不将着色器输出烘焙为图片。");
	public static final String MODELS_MEDIUM_CUBE_DETAIL_SIZE = TranslationRegistry.register(label("models.mediumCubeDetailSize"),
			"Medium detail size", "中距离细节尺寸");
	public static final String MODELS_MEDIUM_CUBE_DETAIL_SIZE_TOOLTIP = TranslationRegistry.register(description("models.mediumCubeDetailSize"),
			"Small entity details below this size may be omitted. 16 model units equal one block; primary geometry is retained.", "可省略的中距离小细节尺寸；16 模型单位等于 1 格，骨骼主要几何保留。");
	public static final String MODELS_FAR_CUBE_DETAIL_SIZE = TranslationRegistry.register(label("models.farCubeDetailSize"),
			"Far detail size", "远距离细节尺寸");
	public static final String MODELS_FAR_CUBE_DETAIL_SIZE_TOOLTIP = TranslationRegistry.register(description("models.farCubeDetailSize"),
			"Small entity details below this size may be omitted. Uses model units; zero disables omission.", "可省略的远距离小细节尺寸，使用模型单位；0 表示不省略。");
	public static final String MODELS_MINIMUM_ENTITY_CUBES = TranslationRegistry.register(label("models.minimumEntityCubes"),
			"Minimum entity cubes", "最低实体立方体数");
	public static final String MODELS_MINIMUM_ENTITY_CUBES_TOOLTIP = TranslationRegistry.register(description("models.minimumEntityCubes"),
			"GeckoLib models below this cube count keep their original geometry.", "少于此立方体数量的 GeckoLib 模型保持原样。");
	public static final String MODELS_EXEMPT_MODELS = TranslationRegistry.register(label("models.exemptModels"),
			"Exempt model sources", "免简化的模型来源");
	public static final String MODELS_EXEMPT_MODELS_TOOLTIP = TranslationRegistry.register(description("models.exemptModels"),
			"Comma-separated entity registry IDs or exact effect emitter names.", "以英文逗号分隔实体注册 ID 或特效发射器内部名称，列表中的模型保持完整。");
	public static final String CACHE_CACHE_ENTRIES = TranslationRegistry.register(label("cache.cacheEntries"),
			"Cache entry limit", "缓存条目上限");
	public static final String CACHE_CACHE_ENTRIES_TOOLTIP = TranslationRegistry.register(description("cache.cacheEntries"),
			"Maximum entries per model cache. Cleared on resource reload and world exit.", "每种模型缓存的最大条目数，资源重载和退出世界时清理。");
	public static final String CACHE_CACHE_MI_B = TranslationRegistry.register(label("cache.cacheMiB"),
			"Cache budget (MiB)", "缓存预算（MiB）");
	public static final String CACHE_CACHE_MI_B_TOOLTIP = TranslationRegistry.register(description("cache.cacheMiB"),
			"Memory budget per model cache in MiB.", "每种模型缓存的内存预算，单位为 MiB。");
	public static final String CACHE_MAX_BAKES_PER_FRAME = TranslationRegistry.register(label("cache.maxBakesPerFrame"),
			"New bakes per frame", "每帧最大烘焙数");
	public static final String CACHE_MAX_BAKES_PER_FRAME_TOOLTIP = TranslationRegistry.register(description("cache.maxBakesPerFrame"),
			"Maximum new model bakes per cache per frame. Draws original geometry when the budget is exhausted.", "每种缓存每帧最多新烘焙的模型数，预算不足时暂时绘制原模型。");

	public static final String ADAPTATION_REDUCE_SECONDS = TranslationRegistry.register(label("adaptation.reduceSeconds"),
			"Reduction response (s)", "降低响应时间（秒）");
	public static final String ADAPTATION_REDUCE_SECONDS_TOOLTIP = TranslationRegistry.register(description("adaptation.reduceSeconds"),
			"Response time for reducing detail when the workload exceeds the budget.", "负载超过预算时降低精细度的响应时间，单位为秒。");
	public static final String ADAPTATION_RECOVER_SECONDS = TranslationRegistry.register(label("adaptation.recoverSeconds"),
			"Recovery response (s)", "恢复响应时间（秒）");
	public static final String ADAPTATION_RECOVER_SECONDS_TOOLTIP = TranslationRegistry.register(description("adaptation.recoverSeconds"),
			"Response time for restoring detail after the workload drops.", "负载下降后恢复精细度的响应时间，单位为秒。");
	public static final String DISTANCE_HYSTERESIS = TranslationRegistry.register(label("distanceHysteresis"),
			"Distance hysteresis (blocks)", "距离恢复迟滞（格）");
	public static final String DISTANCE_HYSTERESIS_TOOLTIP = TranslationRegistry.register(description("distanceHysteresis"),
			"Extra distance required before returning to a finer detail level, preventing rapid switching.", "返回更精细档位前额外跨越的距离，避免频繁切档，单位为格。");

	public static String label(String path) {
		return PREFIX + path;
	}

	public static String description(String path) {
		return label(path) + ".tooltip";
	}

	public static String group(String name) {
		return PREFIX + "group." + name;
	}

	public static Component navigation(String name) {
		var key = switch (name) {
			case "Appearance" -> NAVIGATION + "appearance";
			case "Behavior" -> NAVIGATION + "behavior";
			case "Keymap" -> NAVIGATION + "keymap";
			default -> name;
		};
		return Component.translatable(key);
	}
}
