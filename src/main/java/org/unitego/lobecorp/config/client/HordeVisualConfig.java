package org.unitego.lobecorp.config.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.generator.lang.HordeLang;

/// 主体数量只由显式上限决定，不进行自动降级。
public class HordeVisualConfig {
	public static final String FILE_NAME = "lobecorp-horde-visuals.toml";
	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.IntValue MAX_VISIBLE, DISTANCE, SOUND_VOICES, DUST_PER_TICK;
	public static final ModConfigSpec.BooleanValue ENABLED, STATISTICS, SOUND, DUST;

	static {
		var b = new ModConfigSpec.Builder();
		ENABLED = b.translation(HordeLang.config("visual.enabled", "Enabled", "启用虚拟浪潮")).define("enabled", true);
		b.push("display");
		MAX_VISIBLE = b.translation(HordeLang.config("visual.max_visible", "Maximum visible (-1: unlimited)", "同时显示上限（-1：无限）")).defineInRange("maximumVisible", -1, -1, Integer.MAX_VALUE);
		DISTANCE = b.translation(HordeLang.config("visual.distance", "Display range in chunks (-1: view distance)", "显示范围（区块，-1：视野距离）")).defineInRange("rangeChunks", -1, -1, 1024);
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
}
