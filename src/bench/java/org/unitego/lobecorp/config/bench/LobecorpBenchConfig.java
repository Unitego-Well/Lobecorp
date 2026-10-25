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

	public static int corpseRenderCount() {
		int count = Integer.getInteger("lobecorp.bench.corpseRenderCount", 64);
		if (count != 64 && count != 256) {
			throw new IllegalArgumentException("尸体基准数量只能为64或256");
		}
		return count;
	}
    public static final int CLIENT_ENTITY_ID_BASE = -1_000_000;
    public static final int CAMERA_HEIGHT = 14;
    public static final int CAMERA_DISTANCE = 24;
    public static final Duration PHASE_TIMEOUT = Duration.ofSeconds(120);
}
