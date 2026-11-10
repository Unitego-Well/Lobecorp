package org.unitego.lobecorp.config.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.registry.translation.HordeConfigTranslationKeys;

import static org.unitego.lobecorp.registry.translation.ClientConfigTranslationKeys.EVENTS;
import static org.unitego.lobecorp.registry.translation.ClientConfigTranslationKeys.EVENTS_MIDNIGHT_CLEANUP;
import static org.unitego.lobecorp.registry.translation.ClientConfigTranslationKeys.EVENTS_MIDNIGHT_CLEANUP_WAVE;
import static org.unitego.lobecorp.registry.translation.ClientConfigTranslationKeys.HORDE_DISPLAY;
import static org.unitego.lobecorp.registry.translation.ClientConfigTranslationKeys.HORDE_SOUND;
import static org.unitego.lobecorp.registry.translation.ClientConfigTranslationKeys.HORDE_PARTICLES;
import static org.unitego.lobecorp.registry.translation.ClientConfigTranslationKeys.HORDE_DEBUG;

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
    public static final ModConfigSpec.BooleanValue IRIS_DOUBLE_BUFFER, IRIS_MERGE_MATERIALS, REDUCE_HIDDEN_FACES;
    public static final ModConfigSpec.DoubleValue ROW_SPACING, COLUMN_SPACING;
    public static final ModConfigSpec.IntValue IRIS_WORKSET_WORDS, ANIMATION_FRAMES, SINGLE_CARD_DISTANCE, FRAME_DISTANCE;

	static {
		synchronized (ClientConfig.class) {
			var b = ClientConfig.section("events", EVENTS);
			b.translation(EVENTS_MIDNIGHT_CLEANUP).push("midnight_cleanup");
			b.translation(EVENTS_MIDNIGHT_CLEANUP_WAVE).push("wave");
			ENABLED = b.translation(HordeConfigTranslationKeys.VISUAL_ENABLED).define("enabled", true);
			b.translation(HORDE_DISPLAY).push("display");
			ROW_SPACING = b.translation(HordeConfigTranslationKeys.VISUAL_ROW_SPACING).defineInRange("rowSpacing", 1.8d, 0.25d, 16d);
			COLUMN_SPACING = b.translation(HordeConfigTranslationKeys.VISUAL_COLUMN_SPACING).defineInRange("columnSpacing", 1.8d, 0.25d, 16d);
			MAX_VISIBLE = b.translation(HordeConfigTranslationKeys.VISUAL_MAX_VISIBLE).defineInRange("maximumVisible", -1, -1, Integer.MAX_VALUE);
			DISTANCE = b.translation(HordeConfigTranslationKeys.VISUAL_DISTANCE).defineInRange("rangeChunks", -1, -1, 1024);
			IRIS_WORKSET_WORDS = b.translation(HordeConfigTranslationKeys.VISUAL_IRIS_WORKSET_WORDS).comment("使用4、8或16百万字；中间值向下取档。显存不足时自动缩小工作集，不减少实例。").defineInRange("irisWorksetMiWords", 16, 4, 16);
			IRIS_DOUBLE_BUFFER = b.translation(HordeConfigTranslationKeys.VISUAL_IRIS_DOUBLE_BUFFER).define("irisDoubleBuffer", true);
			IRIS_MERGE_MATERIALS = b.translation(HordeConfigTranslationKeys.VISUAL_IRIS_MERGE_MATERIALS).define("irisMergeMaterials", true);
			REDUCE_HIDDEN_FACES = b.translation(HordeConfigTranslationKeys.VISUAL_REDUCE_HIDDEN_FACES).define("reduceHiddenFaces", true);
			ANIMATION_FRAMES = b.translation(HordeConfigTranslationKeys.VISUAL_ANIMATION_FRAMES).defineInRange("animationFrames", 20, 4, 20);
			SINGLE_CARD_DISTANCE = b.translation(HordeConfigTranslationKeys.VISUAL_SINGLE_CARD_DISTANCE).defineInRange("singleCardDistance", 64, -1, 4096);
		FRAME_DISTANCE = b.translation(HordeConfigTranslationKeys.VISUAL_FRAME_DISTANCE).defineInRange("frameDistance", 10, -1, 4096);
			b.pop().translation(HORDE_SOUND).push("sound");
			SOUND = b.translation(HordeConfigTranslationKeys.VISUAL_SOUND).define("sound", true);
			SOUND_VOICES = b.translation(HordeConfigTranslationKeys.VISUAL_SOUND_VOICES).defineInRange("soundVoices", 2, 0, 16);
			SOUND_RANGE = b.comment("客户端位置音效的生成距离上限，最多256格；各类可听距离由sounds.json分别定义，受环境音量影响。不增加模型或粒子范围。")
					.translation(HordeConfigTranslationKeys.VISUAL_SOUND_RANGE)
					.defineInRange("soundRange", 256, 16, 1024);
			SOUND_MAX_ACTIVE = b.translation(HordeConfigTranslationKeys.VISUAL_SOUND_MAX_ACTIVE)
					.defineInRange("soundMaxActive", 4, 1, 16);
			EVENT_SHAKE = b.translation(HordeConfigTranslationKeys.VISUAL_EVENT_SHAKE).define("eventShake", true);
			LANDING_SHAKE = b.translation(HordeConfigTranslationKeys.VISUAL_LANDING_SHAKE).define("landingShake", true);
			EVENT_SHAKE_RANGE = b.translation(HordeConfigTranslationKeys.VISUAL_EVENT_SHAKE_RANGE)
					.defineInRange("eventShakeRange", 128, 1, 256);
			LANDING_SHAKE_RANGE = b.translation(HordeConfigTranslationKeys.VISUAL_LANDING_SHAKE_RANGE)
					.defineInRange("landingShakeRange", 24, 1, 64);
			SHAKE_STRENGTH = b.translation(HordeConfigTranslationKeys.VISUAL_SHAKE_STRENGTH)
					.defineInRange("shakeStrength", 1.4, 0.0, 2.0);
			b.pop().translation(HORDE_PARTICLES).push("particles");
			DUST = b.translation(HordeConfigTranslationKeys.VISUAL_DUST).define("dust", true);
			PARTICLE_INTERVAL = b.translation(HordeConfigTranslationKeys.VISUAL_PARTICLE_INTERVAL).comment("方块碎屑按活动浪潮的地面网格持续补发；刀光独立每1至4个粒子逻辑刻成批生成，逻辑频率为游戏刻的5倍；单轮生成组数在原基础上乘2。尘雾独立由Photon体积雾提交，不发射烟雾粒子。").defineInRange("particleIntervalTicks", 1, 1, 1200);
			FRAGMENT_GROUPS = b.translation(HordeConfigTranslationKeys.VISUAL_FRAGMENT_GROUPS).comment("基础地面碎屑发射组数；每轮按2倍组数分散补发，0关闭碎屑。不设额外组数上限，仍受原版粒子设置与生成范围限制（最大64格），不控制Photon体积雾数量。").defineInRange("fragmentGroups", 40, 0, Integer.MAX_VALUE);
			PARTICLE_RANGE = b.translation(HordeConfigTranslationKeys.VISUAL_PARTICLE_RANGE).defineInRange("particleRange", MAXIMUM_PARTICLE_RANGE, 1, MAXIMUM_PARTICLE_RANGE);
			b.pop();
			b.translation(HORDE_DEBUG).push("debug");
			STATISTICS = b.translation(HordeConfigTranslationKeys.VISUAL_STATISTICS).define("statistics", false);
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
