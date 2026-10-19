package org.unitego.lobecorp.util.photon.editor;

import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;

/// 设置页仅翻译显示文本；配置路径继续用于草稿、校验与持久化。
public class PhotonVisualQualityTextUtil {
	private static final String PREFIX = "settings.lobecorp.visual_quality.";
	private static final String NAVIGATION = PREFIX + "navigation.";
	public static final String DEFAULT_VALUE = PREFIX + "default";
	public static final String RANGE = PREFIX + "range";
	public static final String EMPTY = PREFIX + "empty";

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

	public static void init() {
		LangHandler.creates(Lobecorp.NAMESPACE, DEFAULT_VALUE, "Default: %s", "默认值：%s");
		LangHandler.creates(Lobecorp.NAMESPACE, RANGE, "Range: %s - %s", "取值范围：%s ～ %s");
		LangHandler.creates(Lobecorp.NAMESPACE, EMPTY, "Empty", "空");
		LangHandler.creates(Lobecorp.NAMESPACE, NAVIGATION + "appearance", "Appearance", "外观");
		LangHandler.creates(Lobecorp.NAMESPACE, NAVIGATION + "behavior", "Behavior", "行为");
		LangHandler.creates(Lobecorp.NAMESPACE, NAVIGATION + "keymap", "Keymap", "快捷键");
		LangHandler.creates(Lobecorp.NAMESPACE, group("effects"), "World effects", "世界特效");
		LangHandler.creates(Lobecorp.NAMESPACE, group("animations"), "Entity animations", "实体动画");
		LangHandler.creates(Lobecorp.NAMESPACE, group("models"), "Model detail", "模型精细度");
		LangHandler.creates(Lobecorp.NAMESPACE, group("adaptation"), "Adaptive response", "自适应响应");
		LangHandler.creates(Lobecorp.NAMESPACE, group("general"), "General", "通用");
		registerEffects();
		registerAnimations();
		registerModels();
		registerAdaptation();
	}

	private static void register(String path, String english, String chinese, String englishTip, String chineseTip) {
		LangHandler.creates(Lobecorp.NAMESPACE, label(path), english, chinese);
		LangHandler.creates(Lobecorp.NAMESPACE, description(path), englishTip, chineseTip);
	}

	private static void registerEffects() {
		register("effects.enabled", "Reduce effect detail", "启用特效降级",
				"Reduce effect density with distance in the world. Editor previews keep full detail.",
				"按世界距离降低特效密度，编辑器预览保持完整精度。");
		register("effects.nearDistance", "Near distance (blocks)", "近距离阈值（格）",
				"Distance where effect density starts decreasing.",
				"开始降低特效密度的距离，单位为格。");
		register("effects.farDistance", "Far distance (blocks)", "远距离阈值（格）",
				"Distance where minimum density is reached; never lower than the near distance.",
				"达到最低密度的距离，实际不小于近距离阈值。");
		register("effects.minimumDensity", "Minimum density", "最低粒子密度",
				"Minimum fraction of particle births. Fractional births carry forward instead of forming bursts.",
				"最低粒子出生比例；小数余量连续累计，避免集中补发。");
		register("effects.adaptToVisibleLoad", "Adapt to visible load", "按可见负载降级",
				"Reduce density when visible effects exceed the workload budget.",
				"可见特效超过负载预算时，动态降低粒子密度。");
		register("effects.visibleWorkBudget", "Visible workload budget", "可见负载预算",
				"Estimated visible particle count plus model triangle count.",
				"以可见粒子数量和模型三角形数量估算负载。");
		register("effects.exemptEmitters", "Exempt emitter names", "免降级的发射器",
				"Comma-separated exact internal emitter names. Parent exemptions apply to spawned children.",
				"以英文逗号分隔发射器内部名称，精确匹配；父发射器的豁免传递给子实例。");
	}

	private static void registerAnimations() {
		register("animations.enabled", "Reduce animation sampling", "启用动画降采样",
				"Only explicitly opted-in mod entity controllers reduce pose sampling. Events keep running.",
				"仅对显式接入的本模组实体控制器降低姿态采样率，关键帧事件继续推进。");
		register("animations.nearDistance", "Near distance (blocks)", "中档起始距离（格）",
				"Distance where the medium animation sampling rate starts.",
				"达到此距离后使用中距离动画采样率，单位为格。");
		register("animations.farDistance", "Far distance (blocks)", "远档起始距离（格）",
				"Distance where the far animation sampling rate starts.",
				"达到此距离后使用远距离动画采样率，单位为格。");
		register("animations.mediumSampleFps", "Medium sampling FPS", "中距离采样帧率",
				"Pose samples per second at medium distance.",
				"中距离每秒采样的动画姿态数量。");
		register("animations.farSampleFps", "Far sampling FPS", "远距离采样帧率",
				"Pose samples per second at far distance.",
				"远距离每秒采样的动画姿态数量。");
		register("animations.exemptEntities", "Exempt entity IDs", "免降采样的实体",
				"Comma-separated full entity registry IDs.",
				"以英文逗号分隔完整实体注册 ID，列表中的实体不降低动画采样率。");
		register("animations.exemptControllers", "Exempt controller names", "免降采样的控制器",
				"Comma-separated exact animation controller names.",
				"以英文逗号分隔控制器名称，列表中的控制器不降低动画采样率。");
	}

	private static void registerModels() {
		register("models.enabled", "Reduce model detail", "启用模型简化",
				"Automatically bake distant LOD for complex effect meshes and opted-in entity models.",
				"为复杂特效网格和显式接入的实体模型自动烘焙远距离简化层级。");
		register("models.nearDistance", "Near distance (blocks)", "中档起始距离（格）",
				"Distance where medium model simplification starts.",
				"达到此距离后使用中距离模型简化，单位为格。");
		register("models.farDistance", "Far distance (blocks)", "远档起始距离（格）",
				"Distance where far model simplification starts.",
				"达到此距离后使用远距离模型简化，单位为格。");
		register("models.minimumTriangles", "Minimum triangles", "最低三角形数",
				"Effect meshes below this triangle count keep their original geometry.",
				"少于此三角形数量的特效网格保持原样。");
		register("models.mediumGrid", "Medium grid detail", "中距离网格精度",
				"Spatial clustering divisions at medium distance. Higher values retain more detail.",
				"中距离空间聚类的分段数，数值越大保留的细节越多。");
		register("models.farGrid", "Far grid detail", "远距离网格精度",
				"Spatial clustering divisions at far distance. Higher values retain more detail.",
				"远距离空间聚类的分段数，数值越大保留的细节越多。");
		register("models.allowPlanarEffects", "Allow planar effects", "允许特效平面简化",
				"Also requires per-emitter permission. Compresses geometry without baking shader output into an image.",
				"还需具体发射器允许平面简化；仅压缩几何，不将着色器输出烘焙为图片。");
		register("models.mediumCubeDetailSize", "Medium detail size", "中距离细节尺寸",
				"Small entity details below this size may be omitted. 16 model units equal one block; primary geometry is retained.",
				"可省略的中距离小细节尺寸；16 模型单位等于 1 格，骨骼主要几何保留。");
		register("models.farCubeDetailSize", "Far detail size", "远距离细节尺寸",
				"Small entity details below this size may be omitted. Uses model units; zero disables omission.",
				"可省略的远距离小细节尺寸，使用模型单位；0 表示不省略。");
		register("models.minimumEntityCubes", "Minimum entity cubes", "最低实体立方体数",
				"GeckoLib models below this cube count keep their original geometry.",
				"少于此立方体数量的 GeckoLib 模型保持原样。");
		register("models.exemptModels", "Exempt model sources", "免简化的模型来源",
				"Comma-separated entity registry IDs or exact effect emitter names.",
				"以英文逗号分隔实体注册 ID 或特效发射器内部名称，列表中的模型保持完整。");
		register("models.cacheEntries", "Cache entry limit", "缓存条目上限",
				"Maximum entries per model cache. Cleared on resource reload and world exit.",
				"每种模型缓存的最大条目数，资源重载和退出世界时清理。");
		register("models.cacheMiB", "Cache budget (MiB)", "缓存预算（MiB）",
				"Memory budget per model cache in MiB.",
				"每种模型缓存的内存预算，单位为 MiB。");
		register("models.maxBakesPerFrame", "New bakes per frame", "每帧最大烘焙数",
				"Maximum new model bakes per cache per frame. Draws original geometry when the budget is exhausted.",
				"每种缓存每帧最多新烘焙的模型数，预算不足时暂时绘制原模型。");
	}

	private static void registerAdaptation() {
		register("adaptation.reduceSeconds", "Reduction response (s)", "降低响应时间（秒）",
				"Response time for reducing detail when the workload exceeds the budget.",
				"负载超过预算时降低精细度的响应时间，单位为秒。");
		register("adaptation.recoverSeconds", "Recovery response (s)", "恢复响应时间（秒）",
				"Response time for restoring detail after the workload drops.",
				"负载下降后恢复精细度的响应时间，单位为秒。");
		register("distanceHysteresis", "Distance hysteresis (blocks)", "距离恢复迟滞（格）",
				"Extra distance required before returning to a finer detail level, preventing rapid switching.",
				"返回更精细档位前额外跨越的距离，避免频繁切档，单位为格。");
	}
}
