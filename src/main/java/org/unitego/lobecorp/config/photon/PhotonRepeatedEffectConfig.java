package org.unitego.lobecorp.config.photon;

import java.util.Locale;

public class PhotonRepeatedEffectConfig {
	public static final String SCENARIO = "lobecorp.photon-03-repeat-performance";
	public static final String WORLD_PREFIX = "photon_repeat_";
	public static final String NATIVE_FX = "photon:first_effect2";
	public static final String FX_FILE = "ldlib2/assets/photon/fx/first_effect2.fx";
	public static final String MATERIAL_FILE = "ldlib2/assets/lobecorp/the_queen_of_hatred/particle.material.nbt";
	public static final String TEXTURE = "lobecorp:the_queen_of_hatred/particle.png";
	public static final String FX_SHA256 = "284c3a8351128a4154e17e6684e5f46a28518a76c8fce7f19f355e1aedbcfa36";
	public static final String MATERIAL_SHA256 = "eb0a9d95315ce77df57e55693dc72a2e24f0bd95dc790e8e954675d348768344";
	public static final String TEXTURE_SHA256 = "03651b57402bb72511700a76f4c5e81a5bc8aa1eccc16a92f74d2e3424b6e22d";
	public static final int COUNT = 3;
	public static final int WARMUP_TICKS = 100;
	public static final int SETTLE_SECONDS = 10;
	public static final int CAPTURE_SECONDS = 40;
	public static final int PREWARM_LIMIT_SECONDS = 120;
	public static final int FIRST_TRIGGER_SECONDS = 2;
	public static final int INTERVAL_SECONDS = 5;
	public static final int INACTIVE_EXTENSION_FIELDS = 12;
	public static final double LANE_SPACING = 6;
	public static final double CAMERA_HEIGHT = 10;
	public static final double CAMERA_DISTANCE = 18;
	public static final float CAMERA_PITCH = 15;
	public static final long SAMPLE_INTERVAL_NS = 250_000_000L;
	public static final long TRIAL_TIMEOUT_NS = 240_000_000_000L;
	public static final int MAX_SAMPLES = 1000;

	/// 只审计参数本身；类路径中的模组文件名和固定 MCP 端口不是测试开关。
	public static boolean isTestVmArgument(String argument) {
		var lower = argument.toLowerCase(Locale.ROOT);
		if (lower.startsWith("-d")) {
			var key = lower.substring(2).split("=", 2)[0];
			return key.startsWith("modbench.") || key.startsWith("lobecorp.photon.")
					|| key.startsWith("mc_debug_") || key.startsWith("minecraft.debug.")
					|| key.contains("gametest") || key.contains("hotswap") || key.startsWith("spark.") || key.startsWith("jdk.jfr.");
		}

		return lower.contains("jdwp") || lower.contains("flightrecord")
				|| lower.contains("hotswap") || lower.contains("enhancedclassredefinition");
	}

	public static int triggerSeconds(int index) {
		if (index < 0 || index >= COUNT) {
			throw new IllegalArgumentException("repeat_trigger_index");
		}

		return FIRST_TRIGGER_SECONDS + index * INTERVAL_SECONDS;
	}
}
