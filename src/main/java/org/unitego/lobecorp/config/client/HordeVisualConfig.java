package org.unitego.lobecorp.config.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.generator.lang.HordeLang;

/// 主体数量只由显式上限决定，不进行自动降级。
public class HordeVisualConfig {
	public static final ModConfigSpec.IntValue MAX_VISIBLE, DISTANCE, SOUND_VOICES, PARTICLE_INTERVAL, PARTICLE_RANGE, FRAGMENT_GROUPS;
	public static final ModConfigSpec.BooleanValue ENABLED, STATISTICS, SOUND, DUST;
	public static final ModConfigSpec.BooleanValue IRIS_DOUBLE_BUFFER, IRIS_MERGE_MATERIALS, REDUCE_HIDDEN_FACES, LOD_ENABLED, CORPSE_BATCH;
	public static final ModConfigSpec.IntValue IRIS_WORKSET_WORDS, ANIMATION_FRAMES, LOD_NEAR, LOD_FAR, CARD_DISTANCE, FRAME_DISTANCE;

	static {
		synchronized (ClientConfig.class) {
			var b = ClientConfig.section("horde", "Horde", "清道夫浪潮");
			ENABLED = b.translation(HordeLang.config("visual.enabled", "Enabled", "启用虚拟浪潮")).define("enabled", true);
			b.translation(ClientConfig.translation("horde.display", "Display", "显示")).push("display");
			MAX_VISIBLE = b.translation(HordeLang.config("visual.max_visible", "Maximum visible (-1: unlimited)", "同时显示上限（-1：无限）")).defineInRange("maximumVisible", -1, -1, Integer.MAX_VALUE);
			DISTANCE = b.translation(HordeLang.config("visual.distance", "Display range in chunks (-1: view distance)", "显示范围（区块，-1：视野距离）")).defineInRange("rangeChunks", -1, -1, 1024);
			IRIS_WORKSET_WORDS = b.translation(HordeLang.config("visual.iris_workset_words", "Iris workset (MiWords)", "Iris工作集（MiWords）")).comment("使用4、8或16百万字；中间值向下取档。显存不足时自动缩小工作集，不减少实例。").defineInRange("irisWorksetMiWords", 16, 4, 16);
			IRIS_DOUBLE_BUFFER = b.translation(HordeLang.config("visual.iris_double_buffer", "Iris double buffering", "Iris双缓冲")).define("irisDoubleBuffer", true);
			IRIS_MERGE_MATERIALS = b.translation(HordeLang.config("visual.iris_merge_materials", "Merge Iris material passes", "合并Iris材质通道")).define("irisMergeMaterials", true);
			CORPSE_BATCH = b.translation(HordeLang.config("visual.corpse_batch", "Batch frozen corpse models", "批量绘制冻结尸体")).define("corpseBatch", true);
			REDUCE_HIDDEN_FACES = b.translation(HordeLang.config("visual.reduce_hidden_faces", "Remove covered model faces", "剔除模型隐藏面")).define("reduceHiddenFaces", true);
			ANIMATION_FRAMES = b.translation(HordeLang.config("visual.animation_frames", "Uniform animation samples", "动画等距采样帧数")).defineInRange("animationFrames", 20, 4, 20);
			LOD_ENABLED = b.translation(HordeLang.config("visual.lod_enabled", "Distance model quality", "按距离调整模型精度")).define("lodEnabled", true);
			LOD_NEAR = b.translation(HordeLang.config("visual.lod_near", "Full quality distance (blocks)", "全精度距离（格）")).defineInRange("lodNear", 48, 1, 4096);
			LOD_FAR = b.translation(HordeLang.config("visual.lod_far", "Medium quality distance (blocks)", "中精度距离（格）")).defineInRange("lodFar", 96, 2, 8192);
		CARD_DISTANCE = b.translation(HordeLang.config("visual.card_distance", "Card distance (blocks, -1 off)", "使用片的距离（格，-1：不使用）")).defineInRange("cardDistance", 32, -1, 4096);
		FRAME_DISTANCE = b.translation(HordeLang.config("visual.frame_distance", "Frame reduction distance (blocks, -1 off, 0 static)", "帧距离（格，-1：不降低，0：不动）")).defineInRange("frameDistance", 10, -1, 4096);
			b.pop().translation(ClientConfig.translation("horde.ambient", "Ambient effects", "环境效果")).push("ambient");
			SOUND = b.translation(HordeLang.config("visual.sound", "Aggregate footsteps", "聚合脚步声")).define("sound", true);
			SOUND_VOICES = b.translation(HordeLang.config("visual.sound_voices", "Sound voices", "聚合声源上限")).defineInRange("soundVoices", 2, 0, 16);
			DUST = b.translation(HordeLang.config("visual.dust", "Path particles", "近处清扫粒子")).define("dust", true);
			PARTICLE_INTERVAL = b.translation(HordeLang.config("visual.particle_interval", "Boundary particle interval (ticks)", "边界粒子生成间隔（刻）")).comment("仅在浪潮边界、分割接合和多潮接合生成碎屑与灰尘；刀光独立每1至4刻成批生成。").defineInRange("particleIntervalTicks", 1, 1, 1200);
			FRAGMENT_GROUPS = b.translation(HordeLang.config("visual.fragment_groups", "Boundary particle groups per tick", "每刻边界粒子组数")).comment("每组在同一点生成方块碎屑和灰尘；0关闭，最多100组。受原版粒子设置与粒子生成范围配置限制（最大32格）。").defineInRange("fragmentGroups", 20, 0, 100);
			PARTICLE_RANGE = b.translation(HordeLang.config("visual.particle_range", "Particle range (blocks)", "粒子生成范围（格）")).defineInRange("particleRange", 24, 1, 32);
			b.pop();
			STATISTICS = b.translation(HordeLang.config("visual.statistics", "Debug overlay", "性能调试叠层")).define("statistics", false);
			b.pop();
		}
	}

	public static double rangeBlocks() {
		int view = Minecraft.getInstance().options.getEffectiveRenderDistance();
		int chunks = DISTANCE.get();
		return Math.max(0, chunks == -1 ? view : Math.min(chunks, view)) * 16d;
	}

    public static void init() {
    }
}
