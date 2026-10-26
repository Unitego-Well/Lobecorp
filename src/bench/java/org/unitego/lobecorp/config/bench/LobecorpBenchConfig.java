package org.unitego.lobecorp.config.bench;

import java.time.Duration;

public class LobecorpBenchConfig {
    public static final String PROVIDER_ID = "lobecorp";
    public static final String SERVER_SCENARIO = "lobecorp.sweeper-ai";
    public static final String CLIENT_SCENARIO = "lobecorp.sweeper-render";
    public static final int GRID_WIDTH = 8;
    public static final int ENTITY_COUNT = GRID_WIDTH * GRID_WIDTH;
    public static final int GRID_SPACING = 3;
    public static final int STABILIZE_TICKS = 40;
    public static final int WARMUP_TICKS = 60;
	public static final int MEASURE_TICKS = 620;
    public static final int STABLE_FRAMES = 20;
	public static final int CORPSE_MEASURE_TICKS = 600;

	public static int hordeWidth() {
		return fileInt("hordeWidth", 512);
	}

	public static int hordeLength() {
		return fileInt("hordeLength", 320);
	}

	public static int hordeMeasureTicks() {
		return fileInt("hordeMeasureTicks", MEASURE_TICKS);
	}

	/// -1 使用基准高空相机；>=0 把玩家放到路线该位置的原地面上，且不飞行。
	public static int hordePlayerStation() {
		return fileInt("hordePlayerStation", -1);
	}

	public static int corpseRenderCount() {
		int count = Integer.getInteger("lobecorp.bench.corpseRenderCount", 0);
		if (count == 0) {
			count = fileInt("corpseRenderCount", 64);
		}
		if (count != 64 && count != 256) {
			throw new IllegalArgumentException("尸体基准数量只能为64或256");
		}
		return count;
	}

	/// 运行目录下的 lobecorp-bench.properties 可切换64/256尸体，不写死默认值。
	private static int fileInt(String key, int fallback) {		var file = java.nio.file.Path.of("lobecorp-bench.properties");
		if (!java.nio.file.Files.isRegularFile(file)) {
			return fallback;
		}
		var properties = new java.util.Properties();
		try (var reader = java.nio.file.Files.newBufferedReader(file)) {
			properties.load(reader);
			return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
		} catch (java.io.IOException | NumberFormatException failure) {
			throw new IllegalArgumentException("无法读取基准属性文件 " + file.toAbsolutePath(), failure);
		}
	}
    public static final int CLIENT_ENTITY_ID_BASE = -1_000_000;
    public static final int CAMERA_HEIGHT = 14;
    public static final int CAMERA_DISTANCE = 24;
    public static final Duration PHASE_TIMEOUT = Duration.ofSeconds(120);
}
