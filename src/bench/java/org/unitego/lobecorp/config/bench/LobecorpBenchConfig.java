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
	public static final int MEASURE_TICKS = 200;
	public static final int STABLE_FRAMES = 20;
	public static final int CLIENT_ENTITY_ID_BASE = -1_000_000;
	public static final int CAMERA_HEIGHT = 14;
	public static final int CAMERA_DISTANCE = 24;
	public static final Duration PHASE_TIMEOUT = Duration.ofSeconds(60);
}
