package org.unitego.lobecorp.config.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.generator.lang.HordeLang;

/// 主体数量只由显式上限决定，不进行自动降级。
public class HordeVisualConfig {
	/// 扩大粒子覆盖但不提高生成组数或候选预算，单位为格。
	public static final int MAXIMUM_PARTICLE_RANGE = 64;
	/// 虚拟模型最大等比尺寸，与horde_motion.glsl的稳定个体缩放一致。
	public static final float MAXIMUM_INSTANCE_SCALE = 1.2F;
	/// 地面发射与刀光共用既有频率和单轮组数倍率。
	public static final int PARTICLE_FREQUENCY_MULTIPLIER = 5, PARTICLE_GROUP_MULTIPLIER = 2;
	public static final ModConfigSpec.IntValue MAX_VISIBLE, DISTANCE, SOUND_VOICES, PARTICLE_INTERVAL, PARTICLE_RANGE, FRAGMENT_GROUPS;
	public static final ModConfigSpec.BooleanValue ENABLED, STATISTICS, SOUND, DUST;
	public static final ModConfigSpec.IntValue SOUND_RANGE, SOUND_MAX_ACTIVE, EVENT_SHAKE_RANGE, LANDING_SHAKE_RANGE;
	public static final ModConfigSpec.BooleanValue EVENT_SHAKE, LANDING_SHAKE;
	public static final ModConfigSpec.DoubleValue SHAKE_STRENGTH;
    public static final ModConfigSpec.BooleanValue IRIS_DOUBLE_BUFFER, IRIS_MERGE_MATERIALS, REDUCE_HIDDEN_FACES, LOD_ENABLED;
    public static final ModConfigSpec.DoubleValue ROW_SPACING, COLUMN_SPACING;
    public static final ModConfigSpec.IntValue IRIS_WORKSET_WORDS, ANIMATION_FRAMES, CARD_DISTANCE, SINGLE_CARD_DISTANCE, FRAME_DISTANCE;

	static {
		synchronized (ClientConfig.class) {
			var b = ClientConfig.section("events", "Events", "事件");
			b.translation(ClientConfig.translation("events.midnight_cleanup", "Midnight cleanup", "深夜清扫")).push("midnight_cleanup");
			b.translation(ClientConfig.translation("events.midnight_cleanup.wave", "Wave", "浪潮")).push("wave");
			ENABLED = b.translation(HordeLang.config("visual.enabled", "Enabled", "启用虚拟浪潮")).define("enabled", true);
			b.translation(ClientConfig.translation("horde.display", "Display", "显示")).push("display");
			ROW_SPACING = b.translation(HordeLang.config("visual.row_spacing", "Row spacing (blocks)", "纵向间距（格）")).defineInRange("rowSpacing", 1.8d, 0.25d, 16d);
			COLUMN_SPACING = b.translation(HordeLang.config("visual.column_spacing", "Column spacing (blocks)", "横向间距（格）")).defineInRange("columnSpacing", 1.8d, 0.25d, 16d);
			MAX_VISIBLE = b.translation(HordeLang.config("visual.max_visible", "Maximum visible (-1: unlimited)", "同时显示上限（-1：无限）")).defineInRange("maximumVisible", -1, -1, Integer.MAX_VALUE);
			DISTANCE = b.translation(HordeLang.config("visual.distance", "Display range in chunks (-1: view distance)", "显示范围（区块，-1：视野距离）")).defineInRange("rangeChunks", -1, -1, 1024);
			IRIS_WORKSET_WORDS = b.translation(HordeLang.config("visual.iris_workset_words", "Iris workset (MiWords)", "Iris工作集（MiWords）")).comment("使用4、8或16百万字；中间值向下取档。显存不足时自动缩小工作集，不减少实例。").defineInRange("irisWorksetMiWords", 16, 4, 16);
			IRIS_DOUBLE_BUFFER = b.translation(HordeLang.config("visual.iris_double_buffer", "Iris double buffering", "Iris双缓冲")).define("irisDoubleBuffer", true);
			IRIS_MERGE_MATERIALS = b.translation(HordeLang.config("visual.iris_merge_materials", "Merge Iris material passes", "合并Iris材质通道")).define("irisMergeMaterials", true);
			REDUCE_HIDDEN_FACES = b.translation(HordeLang.config("visual.reduce_hidden_faces", "Remove covered model faces", "剔除模型隐藏面")).define("reduceHiddenFaces", true);
			ANIMATION_FRAMES = b.translation(HordeLang.config("visual.animation_frames", "Uniform animation samples", "动画等距采样帧数")).defineInRange("animationFrames", 20, 4, 20);
			LOD_ENABLED = b.translation(HordeLang.config("visual.lod_enabled", "Distance model quality", "按距离调整模型精度")).define("lodEnabled", true);
		CARD_DISTANCE = b.translation(HordeLang.config("visual.card_distance", "Card distance (blocks, -1 off)", "使用片的距离（格，-1：不使用）")).defineInRange("cardDistance", 32, -1, 4096);
			SINGLE_CARD_DISTANCE = b.translation(HordeLang.config("visual.single_card_distance", "Single card distance (blocks, -1 off)", "单方向片距离（格，-1：关闭）")).defineInRange("singleCardDistance", 64, -1, 4096);
		FRAME_DISTANCE = b.translation(HordeLang.config("visual.frame_distance", "Frame reduction distance (blocks, -1 off, 0 static)", "帧距离（格，-1：不降低，0：不动）")).defineInRange("frameDistance", 10, -1, 4096);
			b.pop().translation(ClientConfig.translation("horde.sound", "Sound", "声音")).push("sound");
			SOUND = b.translation(HordeLang.config("visual.sound", "Aggregate sweeper sounds", "聚合清道夫音效")).define("sound", true);
			SOUND_VOICES = b.translation(HordeLang.config("visual.sound_voices", "Sound voices", "聚合声源上限")).defineInRange("soundVoices", 2, 0, 16);
			SOUND_RANGE = b.comment("客户端位置音效的生成距离上限，最多256格；各类可听距离由sounds.json分别定义，受环境音量影响。不增加模型或粒子范围。")
					.translation(HordeLang.config("visual.sound_range", "Ambient sound range", "环境音效范围"))
					.defineInRange("soundRange", 256, 16, 1024);
			SOUND_MAX_ACTIVE = b.translation(HordeLang.config("visual.sound_max_active", "Base ambient sound concurrency", "环境音效基础并发额度"))
					.defineInRange("soundMaxActive", 4, 1, 16);
			EVENT_SHAKE = b.translation(HordeLang.config("visual.event_shake", "Wave camera shake", "浪潮镜头震动")).define("eventShake", true);
			LANDING_SHAKE = b.translation(HordeLang.config("visual.landing_shake", "Sweeper landing shake", "清道夫落地震动")).define("landingShake", true);
			EVENT_SHAKE_RANGE = b.translation(HordeLang.config("visual.event_shake_range", "Wave shake range", "浪潮震动范围"))
					.defineInRange("eventShakeRange", 64, 1, 256);
			LANDING_SHAKE_RANGE = b.translation(HordeLang.config("visual.landing_shake_range", "Landing shake range", "落地震动范围"))
					.defineInRange("landingShakeRange", 24, 1, 64);
			SHAKE_STRENGTH = b.translation(HordeLang.config("visual.shake_strength", "Camera shake strength", "镜头震动强度"))
					.defineInRange("shakeStrength", 1.0, 0.0, 2.0);
			b.pop().translation(ClientConfig.translation("horde.particles", "Particles", "粒子")).push("particles");
			DUST = b.translation(HordeLang.config("visual.dust", "Path particles", "近处清扫粒子")).define("dust", true);
			PARTICLE_INTERVAL = b.translation(HordeLang.config("visual.particle_interval", "Boundary particle interval (ticks)", "边界粒子生成间隔（刻）")).comment("烟雾和方块碎屑按可见活动浪潮的地面网格持续补发；刀光独立每1至4个粒子逻辑刻成批生成，逻辑频率为游戏刻的5倍；单轮生成组数在原基础上乘2，不复制同一粒子。").defineInRange("particleIntervalTicks", 1, 1, 1200);
			FRAGMENT_GROUPS = b.translation(HordeLang.config("visual.fragment_groups", "Ground particle groups per batch", "单轮地面粒子组数")).comment("基础地面发射组数；每轮按2倍组数分散补发碎屑，烟雾按各网格寿命错峰续接，0关闭。不设额外组数上限，仍受原版粒子设置与生成范围限制（最大64格）。").defineInRange("fragmentGroups", 40, 0, Integer.MAX_VALUE);
			PARTICLE_RANGE = b.translation(HordeLang.config("visual.particle_range", "Particle range (blocks)", "粒子生成范围（格）")).defineInRange("particleRange", MAXIMUM_PARTICLE_RANGE, 1, MAXIMUM_PARTICLE_RANGE);
			b.pop();
			b.translation(ClientConfig.translation("horde.debug", "Debug", "调试")).push("debug");
			STATISTICS = b.translation(HordeLang.config("visual.statistics", "Debug overlay", "性能调试叠层")).define("statistics", false);
			b.pop(4);
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
