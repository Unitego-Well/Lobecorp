package org.unitego.lobecorp.config.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.generator.lang.HordeLang;

/// 自动降级只改运行时预算，不回写这些用户设置。
public class HordeVisualConfig {
	public enum Preset {LOW, MEDIUM, HIGH, CUSTOM}

	public static final String FILE_NAME = "lobecorp-horde-visuals.toml";
	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.EnumValue<Preset> PRESET;
	public static final ModConfigSpec.IntValue MAX_VISIBLE, DISTANCE, NEAR_FPS, MEDIUM_FPS,
			POOL_IDLE, CACHE_MIB, POSE_GROUPS, BUILD_PER_FRAME, SOUND_VOICES, DUST_PER_TICK;
	public static final ModConfigSpec.DoubleValue NEAR, FAR, MIN_QUALITY, TARGET_FPS;
	public static final ModConfigSpec.BooleanValue ENABLED, ADAPTIVE, STATISTICS, SOUND, DUST;
	public static final ModConfigSpec.BooleanValue IMPOSTORS;
	public static final ModConfigSpec.DoubleValue IMPOSTOR_DISTANCE;
	public static final ModConfigSpec.IntValue IMPOSTOR_FRAMES, IMPOSTOR_RESOLUTION;
	private static Preset applied;
	private static String signature;

	static {
		var b = new ModConfigSpec.Builder();
		ENABLED = b.translation(HordeLang.config("visual.enabled", "Enabled", "启用虚拟浪潮")).define("enabled", true);
		PRESET = b.translation(HordeLang.config("visual.preset", "Quality preset", "画质预设")).comment("LOW、MEDIUM、HIGH 或 CUSTOM；修改高级参数后自动标为自定义。").defineEnum("preset", Preset.LOW);
		b.push("display");
		MAX_VISIBLE = b.translation(HordeLang.config("visual.max_visible", "Maximum visible (-1: unlimited)", "同时显示上限（-1：无限）")).defineInRange("maximumVisible", 5000, -1, Integer.MAX_VALUE);
		DISTANCE = b.translation(HordeLang.config("visual.distance", "Display range in chunks (-1: view distance)", "显示范围（区块，-1：视野距离）")).comment("范围不超过客户端视野距离。").defineInRange("rangeChunks", 12, -1, 1024);
		NEAR = b.translation(HordeLang.config("visual.near", "Near distance", "近距插值范围")).defineInRange("nearDistance", 16d, 1d, 256d);
		FAR = b.translation(HordeLang.config("visual.far", "Fixed-pose distance", "固定姿态起始距离")).defineInRange("fixedPoseDistance", 64d, 2d, 1024d);
		NEAR_FPS = b.translation(HordeLang.config("visual.near_fps", "Near pose FPS", "近距姿态采样率")).defineInRange("nearSampleFps", 20, 1, 120);
		MEDIUM_FPS = b.translation(HordeLang.config("visual.medium_fps", "Medium pose FPS", "中距姿态采样率")).defineInRange("mediumSampleFps", 8, 1, 60);
		POSE_GROUPS = b.translation(HordeLang.config("visual.pose_groups", "Pose groups", "共享姿态分组")).defineInRange("poseGroups", 8, 1, 32);
		b.pop().push("impostors");
		IMPOSTORS = b.translation(HordeLang.config("visual.impostors", "Distant baked animation", "远处烘焙动画")).define("enabled", true);
		IMPOSTOR_DISTANCE = b.translation(HordeLang.config("visual.impostor_distance", "3D to baked transition distance", "三维转烘焙距离")).defineInRange("distance", 32d, 8d, 512d);
		IMPOSTOR_FRAMES = b.translation(HordeLang.config("visual.impostor_frames", "Baked frames per running cycle", "每个奔跑周期的烘焙帧数")).defineInRange("frames", 12, 4, 32);
		IMPOSTOR_RESOLUTION = b.translation(HordeLang.config("visual.impostor_resolution", "Baked view resolution", "单视角烘焙分辨率")).defineInRange("resolution", 96, 32, 256);
		b.pop().push("budget");
		POOL_IDLE = b.translation(HordeLang.config("visual.pool_idle", "Idle pool capacity", "闲置对象池容量")).defineInRange("idlePoolCapacity", 256, 0, 4096);
		CACHE_MIB = b.translation(HordeLang.config("visual.cache_mib", "Mesh cache MiB", "网格缓存 MiB")).defineInRange("meshCacheMiB", 32, 1, 512);
		BUILD_PER_FRAME = b.translation(HordeLang.config("visual.build_per_frame", "Mesh builds per frame", "每帧网格烘焙数")).defineInRange("meshBuildsPerFrame", 2, 1, 64);
		ADAPTIVE = b.translation(HordeLang.config("visual.adaptive", "Adaptive quality", "自动降级")).define("adaptive", true);
		TARGET_FPS = b.translation(HordeLang.config("visual.target_fps", "Target FPS", "目标帧率")).defineInRange("targetFps", 60d, 15d, 240d);
		MIN_QUALITY = b.translation(HordeLang.config("visual.min_quality", "Minimum visual quality", "最低表现精度比例")).comment("自动降级仅降低模型、姿态和附属表现精度，不减少主体显示预算。").defineInRange("minimumQuality", 0.25d, 0.01d, 1d);
		b.pop().push("ambient");
		SOUND = b.translation(HordeLang.config("visual.sound", "Aggregate footsteps", "聚合脚步声")).define("sound", true);
		SOUND_VOICES = b.translation(HordeLang.config("visual.sound_voices", "Sound voices", "聚合声源上限")).defineInRange("soundVoices", 2, 0, 16);
		DUST = b.translation(HordeLang.config("visual.dust", "Running dust", "近处奔跑扬尘")).define("dust", true);
		DUST_PER_TICK = b.translation(HordeLang.config("visual.dust_per_tick", "Dust per tick", "每刻扬尘预算")).defineInRange("dustPerTick", 2, 0, 64);
		b.pop();
		STATISTICS = b.translation(HordeLang.config("visual.statistics", "Debug overlay", "性能调试叠层")).define("statistics", false);
		SPEC = b.build();
	}

	public static double rangeBlocks() {
		int view = Minecraft.getInstance().options.getEffectiveRenderDistance();
		int chunks = DISTANCE.get();
		return Math.max(0, chunks == -1 ? view : Math.min(chunks, view)) * 16d;
	}

	public static void refreshPreset() {
		Preset requested = PRESET.get();
		String current = signature();
		if (applied == null) {
			applied = requested;
			signature = current;
			return;
		}
		if (requested != applied && requested != Preset.CUSTOM) {
			apply(requested);
			applied = requested;
			signature = signature();
			SPEC.save();
		} else if (requested != Preset.CUSTOM && signature != null && !signature.equals(current)) {
			PRESET.set(Preset.CUSTOM);
			applied = Preset.CUSTOM;
			signature = current;
			SPEC.save();
		} else {
			applied = requested;
			signature = current;
		}
	}

	public static void apply(Preset preset) {
		if (preset == Preset.CUSTOM) {
			return;
		}
		int band = preset.ordinal();
		NEAR.set(new double[]{16, 24, 32}[band]);
		FAR.set(new double[]{64, 96, 128}[band]);
		NEAR_FPS.set(new int[]{20, 30, 40}[band]);
		MEDIUM_FPS.set(new int[]{8, 12, 16}[band]);
		SOUND_VOICES.set(new int[]{2, 3, 4}[band]);
		DUST_PER_TICK.set(new int[]{2, 4, 8}[band]);
	}

	private static String signature() {
		return MAX_VISIBLE.get() + ":" + DISTANCE.get() + ":" + NEAR.get() + ":" + FAR.get()
				+ ":" + NEAR_FPS.get() + ":" + MEDIUM_FPS.get()
				+ ":" + SOUND_VOICES.get() + ":" + DUST_PER_TICK.get()
				+ ":" + POOL_IDLE.get() + ":" + CACHE_MIB.get() + ":" + POSE_GROUPS.get()
				+ ":" + BUILD_PER_FRAME.get() + ":" + ADAPTIVE.get() + ":" + TARGET_FPS.get()
				+ ":" + MIN_QUALITY.get() + ":" + SOUND.get() + ":" + DUST.get()
				+ ":" + IMPOSTORS.get() + ":" + IMPOSTOR_DISTANCE.get()
				+ ":" + IMPOSTOR_FRAMES.get() + ":" + IMPOSTOR_RESOLUTION.get();
	}
}
