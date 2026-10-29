package org.unitego.lobecorp.config.bench;

import java.time.Duration;

public class LobecorpBenchConfig {
    public static final String PROVIDER_ID = "lobecorp";
    public static final String SERVER_SCENARIO = "lobecorp.sweeper-ai";
    public static final String CLIENT_SCENARIO = "lobecorp.sweeper-render";
	public static final String PHOTON_FIRST_CAST_PERFORMANCE_SCENARIO = "lobecorp.photon-02-first-cast-performance";
	public static final String PHOTON_CONTROL_FX_PROPERTY = "lobecorp.photon.bench.controlFx";
	public static final String PHOTON_CONTROL_FX = "photon:first_effect2";
	public static final String PHOTON_CONTROL_LABEL = "photon-02-native-data-control";
	public static final String PHOTON_CONTROL_FX_SHA256 = "284c3a8351128a4154e17e6684e5f46a28518a76c8fce7f19f355e1aedbcfa36";
	public static final String PHOTON_CONTROL_MATERIAL_SHA256 = "eb0a9d95315ce77df57e55693dc72a2e24f0bd95dc790e8e954675d348768344";
	public static final String PHOTON_CONTROL_TEXTURE_SHA256 = "03651b57402bb72511700a76f4c5e81a5bc8aa1eccc16a92f74d2e3424b6e22d";
	public static final int PHOTON_CONTROL_WARMUP_TICKS = 100;
	public static final int PHOTON_CONTROL_MEASURE_SECONDS = 20;
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
