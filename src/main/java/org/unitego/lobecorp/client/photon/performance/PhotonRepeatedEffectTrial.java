package org.unitego.lobecorp.client.photon.performance;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.animation.LcClientAnimations;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffect;
import org.unitego.lobecorp.client.photon.runtime.QueenBeamEffects;
import org.unitego.lobecorp.config.photon.PhotonRepeatedEffectConfig;
import org.unitego.lobecorp.registry.animation.LcAnimationDefinitions;
import org.unitego.lobecorp.registry.animation.TheQueenOfHatredAnimationDefinitions;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;

import net.neoforged.neoforgespi.language.IModInfo;
import jdk.jfr.FlightRecorder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/// 两个前端共用同一场景、施放时钟和采集所有权；inactive tick 只读空引用。
public class PhotonRepeatedEffectTrial {
	public enum Kind {LASER, NATIVE}

	public enum Mode {BASE, FULL}

	private enum State {SETTLE, PREPARE, WARMUP, PREWARM_EXPORT, MAIN, MAIN_EXPORT, CLEANUP, COMPLETE, FAILED}

	private static final Gson GSON = new Gson();
	private static PhotonRepeatedEffectTrial current;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final ClientLevel level;
	private final MinecraftServer server;
	private final UUID player;
	private final String label;
	private final Kind kind;
	private final Mode mode;
	private final Path gameDirectory;
	private final Path worldDirectory;
	private final Path directory;
	private final String worldName;
	private final BooleanSupplier fullReady;
	private final MemoryMXBean memory;
	private final List<BufferPoolMXBean> buffers;
	private final JsonObject report = new JsonObject();
	private final JsonArray assets = new JsonArray();
	private final JsonArray samples = new JsonArray();
	private final JsonArray actions = new JsonArray();
	private final JsonArray outputs = new JsonArray();
	private final List<PhotonGeoEffect> effects = new ArrayList<>();
	private final List<CompletableFuture<JsonObject>> casts = new ArrayList<>();
	private final long[] firstActive = new long[PhotonRepeatedEffectConfig.COUNT];
	private final long[] lastActive = new long[PhotonRepeatedEffectConfig.COUNT];
	private final long[] peakParticles = new long[PhotonRepeatedEffectConfig.COUNT];
	private final boolean[] triggered = new boolean[PhotonRepeatedEffectConfig.COUNT];
	private final long createdNs = System.nanoTime();
	private State state = State.SETTLE;
	private long stableNs;
	private long previousSampleNs;
	private int warmupTicks;
	private String captureLabel;
	private long captureSession;
	private boolean ownsCapture;
	private boolean ownsSubmissions;
	private boolean failureExportPending;
	private boolean mainArchived;
	private int clientCleanupTicks;
	private long cleanupRequestedNs;
	private boolean cancelled;
	private String problem;
	private CompletableFuture<Scene> preparation;
	private Scene scene;
	private CompletableFuture<Void> cleanup;
	private CompletableFuture<JsonObject> archive;
	private final Vec3 originalPosition;
	private final float originalYaw;
	private final float originalPitch;
	private final boolean originalFlying;

	private record Scene(Vec3 origin, List<QueenBeamPerformanceFixture> fixtures) {
	}

	public static void start(Kind kind, Mode mode, String label, BooleanSupplier fullReady) throws Exception {
		if (current != null) {
			throw new IllegalStateException("repeat_one_trial_per_game");
		}

		if (FMLEnvironment.isProduction() || Minecraft.getInstance().level == null
				|| Minecraft.getInstance().player == null || Minecraft.getInstance().getSingleplayerServer() == null) {
			throw new IllegalStateException("repeat_requires_ide_integrated_client");
		}

		requireIdle();
		current = new PhotonRepeatedEffectTrial(kind, mode, label, fullReady);
	}

	private PhotonRepeatedEffectTrial(Kind kind, Mode mode, String label, BooleanSupplier fullReady) throws Exception {
		if (!label.matches("[a-z0-9_]{1,80}")) {
			throw new IllegalArgumentException("repeat_label");
		}

		this.kind = kind;
		this.mode = mode;
		this.label = label;
		this.fullReady = fullReady;
		level = Objects.requireNonNull(minecraft.level);
		server = Objects.requireNonNull(minecraft.getSingleplayerServer());
		player = Objects.requireNonNull(minecraft.player).getUUID();
		worldName = server.getWorldData().getLevelName();
		gameDirectory = minecraft.gameDirectory.toPath().toRealPath();
		worldDirectory = server.getWorldPath(LevelResource.ROOT).toRealPath();
		requireWorld();
		if (mode == Mode.FULL && (!gameDirectory.endsWith(Path.of("build", "modBench", "runs", "default", "client"))
				|| !PhotonRepeatedEffectConfig.SCENARIO.equals(System.getProperty("modBench.scenarios", "")))) {
			throw new IllegalStateException("repeat_full_requires_03");
		}

		if (mode == Mode.BASE && (ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
				.anyMatch(PhotonRepeatedEffectTrial::testArgument)
				|| System.getenv().keySet().stream().anyMatch(key -> key.startsWith("LOBECORP_LASER_PROFILE") || key.startsWith("MC_DEBUG_")))) {
			throw new IllegalStateException("repeat_base_has_test_flags");
		}

		if (IrisCompat.isUsingShaderPack() || !ModList.get().isLoaded("sodium")) {
			throw new IllegalStateException("repeat_requires_standard_sodium_no_shader");
		}

		if (mode == Mode.FULL && (!ModList.get().isLoaded("spark")
				|| ManagementFactory.getRuntimeMXBean().getInputArguments().stream().noneMatch(arg -> arg.contains("jdwp")))) {
			throw new IllegalStateException("repeat_full_requires_spark_and_debug");
		}

		// 在采集前初始化 MXBean，之后只采样，不触发 GC 或堆转储。
		memory = ManagementFactory.getMemoryMXBean();
		buffers = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
		originalPosition = minecraft.player.position();
		originalYaw = minecraft.player.getYRot();
		originalPitch = minecraft.player.getXRot();
		originalFlying = minecraft.player.getAbilities().flying;
		var performance = gameDirectory.resolve("performance");
		Files.createDirectories(performance);
		if (!performance.toRealPath().equals(performance)) {
			throw new IllegalStateException("repeat_output_directory_link");
		}

		directory = Files.createDirectory(performance.resolve(label));
		report.addProperty("label", label);
		report.addProperty("pid", ProcessHandle.current().pid());
		report.addProperty("kind", kind.name());
		report.addProperty("mode", mode.name());
		report.addProperty("world", worldName);
		report.addProperty("world_seed", server.overworld().getSeed());
		report.addProperty("world_path", worldDirectory.toString());
		var worldIdentity = worldName + "\n" + server.overworld().getSeed() + "\n" + level.dimension();
		PhotonRepeatedEffectAssets.record(worldIdentity.getBytes(StandardCharsets.UTF_8), null,
				"world_name_seed_dimension", "runtime_world_identity_snapshot", assets);
		if (Files.isRegularFile(worldDirectory.resolve("level.dat"))) {
			PhotonRepeatedEffectAssets.file(worldDirectory.resolve("level.dat"), null, assets);
		} else {
			report.addProperty("world_level_dat_hash", "unavailable_not_saved");
		}
		report.addProperty("world_hash_observed_ns", System.nanoTime());
		report.addProperty("game_directory", gameDirectory.toString());
		report.addProperty("owner_player_uuid", player.toString());
		report.addProperty("output_directory", directory.toString());
		report.addProperty("created_ns", createdNs);
		report.addProperty("primary_seconds", PhotonRepeatedEffectConfig.CAPTURE_SECONDS);
		report.addProperty("tail_scope", "40s natural capture only; later lifecycle is cleanup, not measured");
		report.addProperty("measurement_overhead", "runtime frame/GPU/allocation and bounded activity/memory sampling");
		report.addProperty("raw_trace_scope", "existing bounded raw tail and aggregate ranges; quality/denied/dropped/truncated retained in exports");
		var args = ManagementFactory.getRuntimeMXBean().getInputArguments();
		report.add("vm_arguments", GSON.toJsonTree(args));
		report.add("test_vm_arguments", GSON.toJsonTree(args.stream().filter(PhotonRepeatedEffectTrial::testArgument).toList()));
		report.add("initial_environment", environment());
		report.add("mod_versions", GSON.toJsonTree(ModList.get().getMods().stream()
				.collect(Collectors.toMap(IModInfo::getModId, mod -> mod.getVersion().toString()))));
		report.add("jfr_recordings_at_start", GSON.toJsonTree(FlightRecorder.isInitialized()
				? FlightRecorder.getFlightRecorder().getRecordings().stream()
				.map(recording -> recording.getId() + ":" + recording.getName() + ":" + recording.getState()).toList()
				: List.of()));
		report.add("assets", assets);
		report.add("samples", samples);
		report.add("triggers", actions);
		report.add("outputs", outputs);
		report.addProperty("screenshots", false);
		for (var type : List.of(PhotonRepeatedEffectTrial.class, PhotonRepeatedEffectConfig.class, PhotonRepeatedEffectAssets.class,
				QueenBeamPerformanceFixture.class, QueenBeamEffects.class)) {
			try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
				if (input == null) {
					throw new IllegalStateException("repeat_source_class_missing:" + type.getName());
				}

				PhotonRepeatedEffectAssets.record(input.readAllBytes(), null, type.getName(), "loaded_class_bytes", assets);
			}
		}

		writeReport();
	}

	private static boolean testArgument(String argument) {
		return PhotonRepeatedEffectConfig.isTestVmArgument(argument);
	}

	private static void requireIdle() {
		if (PhotonFrameCapture.running() || PhotonPerformanceCapture.active()
				|| PhotonPerformanceTraceUtil.sessionId() != 0 || PhotonSubmissionDiagnostics.enabled()
				|| PhotonPerformanceCapture.status().get("exporting").getAsBoolean()) {
			throw new IllegalStateException("repeat_capture_owner_busy");
		}
	}

	private void requireWorld() throws Exception {
		var saves = gameDirectory.resolve("saves");
		if (!worldName.startsWith(PhotonRepeatedEffectConfig.WORLD_PREFIX)
				|| !worldDirectory.getFileName().toString().equals(worldName)
				|| !saves.toRealPath().equals(saves) || !worldDirectory.equals(saves.resolve(worldName))
				|| !level.dimension().equals(server.overworld().dimension())) {
			throw new IllegalStateException("repeat_requires_owned_photon_repeat_world");
		}
	}

	public static void tick() {
		var trial = current;
		if (trial == null || trial.terminal()) {
			return;
		}

		try {
			trial.advance();
		} catch (Exception | LinkageError failure) {
			trial.fail(failure);
		}
	}

	private void advance() throws Exception {
		if (state != State.CLEANUP) {
			if (minecraft.level != level || minecraft.getSingleplayerServer() != server || minecraft.player == null
					|| !minecraft.player.getUUID().equals(player)) {
				throw new IllegalStateException("repeat_world_owner_changed");
			}

			if (System.nanoTime() - createdNs > PhotonRepeatedEffectConfig.TRIAL_TIMEOUT_NS) {
				throw new IllegalStateException("repeat_timeout");
			}
		}

		switch (state) {
			case SETTLE -> settle();
			case PREPARE -> prepared();
			case WARMUP -> warmup();
			case PREWARM_EXPORT -> {
				if (exported(false)) {
					beginCapture(false);
					state = State.MAIN;
				}
			}
			case MAIN -> {
				holdCamera();
				collectCasts();
				observe();
				if (!PhotonFrameCapture.running()) {
					report.add("tail_first_tick_after_capture", activity());
					finishCapture(true);
					state = State.MAIN_EXPORT;
				}
			}
			case MAIN_EXPORT -> {
				collectCasts();
				if (!mainArchived) {
					mainArchived = exported(true);
				}

				if (mainArchived && casts.stream().allMatch(CompletableFuture::isDone)) {
					verifyWorkload();
					beginCleanup();
				}
			}
			case CLEANUP -> finishCleanup();
			default -> {
			}
		}
	}

	public static void prepareWindow() {
		if (FMLEnvironment.isProduction()) {
			throw new IllegalStateException("repeat_requires_ide_client");
		}

		GLFW.glfwMaximizeWindow(Minecraft.getInstance().getWindow().handle());
	}

	private void settle() {
		if (GLFW.glfwGetWindowAttrib(minecraft.getWindow().handle(), GLFW.GLFW_MAXIMIZED) != GLFW.GLFW_TRUE) {
			prepareWindow();
			stableNs = 0;
			return;
		}

		if (minecraft.screen != null || minecraft.getOverlay() != null) {
			stableNs = 0;
			return;
		}

		long now = System.nanoTime();
		if (stableNs == 0) {
			stableNs = now;
		}

		if (now - stableNs < TimeUnit.SECONDS.toNanos(PhotonRepeatedEffectConfig.SETTLE_SECONDS)) {
			return;
		}

		report.addProperty("stable_started_ns", stableNs);
		report.addProperty("gate_passed_ns", now);
		if (mode == Mode.FULL && (fullReady == null || !fullReady.getAsBoolean())) {
			return;
		}

		beginCapture(true);
		preparation = CompletableFuture.supplyAsync(this::prepareScene, server);
		state = State.PREPARE;
	}

	private Scene prepareScene() {
		var fixtures = new ArrayList<QueenBeamPerformanceFixture>();
		try {
			requireWorld();
			var targetLevel = server.overworld();
			var clock = targetLevel.dimensionType().defaultClock().orElseThrow();
			if (!server.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.NOON)) {
				throw new IllegalStateException("repeat_noon_unavailable");
			}

			var rules = targetLevel.getGameRules();
			rules.set(GameRules.ADVANCE_TIME, false, server);
			rules.set(GameRules.ADVANCE_WEATHER, false, server);
			rules.set(GameRules.SPAWN_MOBS, false, server);
			server.setWeatherParameters(0, 0, false, false);
			server.setDifficulty(Difficulty.NORMAL, true);
			var origin = Vec3.atBottomCenterOf(server.getRespawnData().pos());
			if (kind == Kind.LASER) {
				for (int index = 0; index < PhotonRepeatedEffectConfig.COUNT; index++) {
					fixtures.add(QueenBeamPerformanceFixture.create(server, player,
							origin.add(lane(index), 0, 0), 1));
				}
			}

			var owner = server.getPlayerList().getPlayer(player);
			if (owner == null) {
				throw new IllegalStateException("repeat_player_missing");
			}

			owner.teleportTo(targetLevel, origin.x, origin.y + PhotonRepeatedEffectConfig.CAMERA_HEIGHT,
					origin.z - PhotonRepeatedEffectConfig.CAMERA_DISTANCE,
					Set.of(), 0, PhotonRepeatedEffectConfig.CAMERA_PITCH, false);
			return new Scene(origin, List.copyOf(fixtures));
		} catch (Exception failure) {
			for (var fixture : fixtures) {
				try {
					fixture.clear(server);
				} catch (Exception cleanupFailure) {
					failure.addSuppressed(cleanupFailure);
				}
			}

			throw new IllegalStateException("repeat_scene_preparation_failed", failure);
		}
	}

	private static double lane(int index) {
		return (index - 1) * PhotonRepeatedEffectConfig.LANE_SPACING;
	}

	private void prepared() throws Exception {
		if (!preparation.isDone()) {
			return;
		}

		scene = preparation.join();
		Objects.requireNonNull(minecraft.player).getAbilities().flying = true;
		minecraft.player.onUpdateAbilities();
		holdCamera();
		if (kind == Kind.NATIVE) {
			var fx = PhotonRepeatedEffectAssets.loadNative(report, assets);
			for (int index = 0; index < PhotonRepeatedEffectConfig.COUNT; index++) {
				var effect = new PhotonGeoEffect(fx, level);
				effects.add(effect);
				effect.anchor(new Vector3f((float) (scene.origin.x + lane(index)), (float) scene.origin.y + 2,
						(float) scene.origin.z + 5), new Quaternionf(), new Vector3f(1));
			}
		}

		report.add("fixture_members", GSON.toJsonTree(scene.fixtures.stream().map(QueenBeamPerformanceFixture::members).toList()));
		report.addProperty("instances_prepared_ns", System.nanoTime());
		report.add("scene_origin", GSON.toJsonTree(List.of(scene.origin.x, scene.origin.y, scene.origin.z)));
		state = State.WARMUP;
	}

	private void holdCamera() {
		if (scene == null || minecraft.player == null || minecraft.level != level) {
			return;
		}

		minecraft.player.setPos(scene.origin.x, scene.origin.y + PhotonRepeatedEffectConfig.CAMERA_HEIGHT,
				scene.origin.z - PhotonRepeatedEffectConfig.CAMERA_DISTANCE);
		minecraft.player.setYRot(0);
		minecraft.player.setXRot(PhotonRepeatedEffectConfig.CAMERA_PITCH);
		minecraft.player.setDeltaMovement(Vec3.ZERO);
	}

	private void warmup() throws Exception {
		holdCamera();
		observe();
		if (!PhotonFrameCapture.running()) {
			throw new IllegalStateException("repeat_prewarm_expired_before_ready");
		}

		if (++warmupTicks < PhotonRepeatedEffectConfig.WARMUP_TICKS
				|| kind == Kind.LASER && !QueenBeamEffects.prewarmComplete(level)) {
			return;
		}

		report.addProperty("warmup_ticks", warmupTicks);
		if (kind == Kind.LASER) {
			for (var id : List.of(TheQueenOfHatredAnimationDefinitions.LASER,
					TheQueenOfHatredAnimationDefinitions.LASER_PHASE_TWO)) {
				var animation = LcAnimationDefinitions.resolve(id);
				if (animation == null || animation.effectId() == null) {
					throw new IllegalStateException("repeat_laser_definition_missing");
				}

				var fx = animation.effectId();
				PhotonRepeatedEffectAssets.resource(Identifier.fromNamespaceAndPath(fx.getNamespace(),
						"fx/" + fx.getPath() + ".fx"), null, assets);
			}
		}

		PhotonFrameCapture.stop();
		finishCapture(false);
		state = State.PREWARM_EXPORT;
	}

	private void beginCapture(boolean prewarm) {
		requireIdle();
		captureLabel = label + (prewarm ? "_prewarm" : "_main");
		if (mode == Mode.FULL) {
			PhotonPerformanceCapture.start(captureLabel, prewarm ? PhotonRepeatedEffectConfig.PREWARM_LIMIT_SECONDS
					: PhotonRepeatedEffectConfig.CAPTURE_SECONDS, PhotonPerformanceTraceUtil.Mode.GPU);
			captureSession = PhotonPerformanceTraceUtil.sessionId();
		} else {
			PhotonFrameCapture.start(captureLabel, prewarm ? PhotonRepeatedEffectConfig.PREWARM_LIMIT_SECONDS
					: PhotonRepeatedEffectConfig.CAPTURE_SECONDS);
		}

		ownsCapture = true;
		if (mode == Mode.FULL && Boolean.getBoolean(PhotonSubmissionDiagnostics.PROBES_PROPERTY)) {
			PhotonSubmissionDiagnostics.beginCase(captureLabel);
			ownsSubmissions = true;
			PhotonSubmissionDiagnostics.bindFrameCapture();
		}

		if (!prewarm) {
			for (int index = 0; index < PhotonRepeatedEffectConfig.COUNT; index++) {
				int instance = index;
				PhotonFrameCapture.phase("trigger_" + index, PhotonRepeatedEffectConfig.triggerSeconds(index),
						() -> trigger(instance));
			}
		}
	}

	private void trigger(int index) {
		var event = new JsonObject();
		long requested = PhotonFrameCapture.liveStatus().get("started_ns").getAsLong()
				+ TimeUnit.SECONDS.toNanos(PhotonRepeatedEffectConfig.triggerSeconds(index));
		event.addProperty("index", index);
		event.addProperty("requested_ns", requested);
		event.addProperty("requested_offset_seconds", PhotonRepeatedEffectConfig.triggerSeconds(index));
		event.addProperty("requested_window_end_ns", PhotonFrameCapture.liveStatus().get("started_ns").getAsLong()
				+ TimeUnit.SECONDS.toNanos(index + 1 < PhotonRepeatedEffectConfig.COUNT
				? PhotonRepeatedEffectConfig.triggerSeconds(index + 1) : PhotonRepeatedEffectConfig.CAPTURE_SECONDS));
		event.addProperty("dispatch_ns", System.nanoTime());
		event.add("overlap_at_dispatch", activity());
		actions.add(event);
		triggered[index] = true;
		if (kind == Kind.NATIVE) {
			event.addProperty("actual_ns", System.nanoTime());
			effects.get(index).start();
			event.addProperty("started", true);
			event.addProperty("result", "native_emit");
		} else {
			casts.add(CompletableFuture.supplyAsync(() -> {
				var actual = new JsonObject();
				actual.addProperty("index", index);
				actual.addProperty("actual_ns", System.nanoTime());
				var results = scene.fixtures.get(index).cast(server);
				actual.add("cast_results", GSON.toJsonTree(results));
				actual.addProperty("started", results.size() == 1 && results.getFirst().started());
				return actual;
			}, server));
		}
	}

	private void collectCasts() {
		for (var cast : casts) {
			if (!cast.isDone()) {
				continue;
			}

			var result = cast.join();
			var event = actions.get(result.get("index").getAsInt()).getAsJsonObject();
			if (!event.has("actual_ns")) {
				result.entrySet().forEach(entry -> event.add(entry.getKey(), entry.getValue()));
				if (!result.get("started").getAsBoolean()) {
					throw new IllegalStateException("repeat_public_cast_not_started");
				}
			}
		}
	}

	private JsonObject activity() {
		var active = new JsonArray();
		long particles = 0;
		int activeCount = 0;
		long now = System.nanoTime();
		for (int index = 0; index < PhotonRepeatedEffectConfig.COUNT; index++) {
			var item = new JsonObject();
			item.addProperty("index", index);
			boolean alive = false;
			if (kind == Kind.NATIVE && index < effects.size() && triggered[index]) {
				var effect = effects.get(index);
				alive = effect.isAlive();
				long instanceParticles = 0;
				for (var object : effect.runtime().objects.values()) {
					if (object instanceof IParticleEmitter emitter) {
						instanceParticles += emitter.getParticleAmount();
					}
				}

				particles += instanceParticles;
				peakParticles[index] = Math.max(peakParticles[index], instanceParticles);
				item.addProperty("particles", instanceParticles);
			} else if (kind == Kind.LASER && scene != null) {
				var member = scene.fixtures.get(index).members().getFirst();
				var snapshot = LcClientAnimations.snapshotForEntity(member.queenId(),
						value -> TheQueenOfHatredAnimationDefinitions.isBeam(value.definitionId()));
				alive = snapshot != null;
				if (snapshot != null) {
					item.addProperty("animation_instance_uuid", snapshot.instanceId().toString());
					item.addProperty("animation_seconds", snapshot.timeSeconds());
				}
			}

			item.addProperty("active", alive);
			activeCount += alive ? 1 : 0;
			if (alive && triggered[index]) {
				if (firstActive[index] == 0) {
					firstActive[index] = now;
				}

				lastActive[index] = now;
			}

			active.add(item);
		}

		var result = new JsonObject();
		result.addProperty("observed_ns", now);
		result.addProperty("scope", kind == Kind.LASER ? "owned client authoritative beam snapshots" : "owned native runtime liveness and emitter particles");
		result.add("instances", active);
		result.addProperty("particles", particles);
		result.addProperty("active_count", activeCount);
		return result;
	}

	private void observe() {
		long now = System.nanoTime();
		if (now - previousSampleNs < PhotonRepeatedEffectConfig.SAMPLE_INTERVAL_NS) {
			return;
		}

		previousSampleNs = now;
		if (samples.size() >= PhotonRepeatedEffectConfig.MAX_SAMPLES) {
			throw new IllegalStateException("repeat_metadata_capacity");
		}

		var sample = activity();
		sample.addProperty("state", state.name());
		sample.addProperty("heap_used", memory.getHeapMemoryUsage().getUsed());
		sample.addProperty("nonheap_used", memory.getNonHeapMemoryUsage().getUsed());
		var pools = new JsonArray();
		for (var buffer : buffers) {
			var pool = new JsonObject();
			pool.addProperty("name", buffer.getName());
			pool.addProperty("count", buffer.getCount());
			pool.addProperty("bytes", buffer.getMemoryUsed());
			pool.addProperty("capacity", buffer.getTotalCapacity());
			pools.add(pool);
		}

		sample.add("buffer_pools", pools);
		sample.add("environment", environment());
		samples.add(sample);
	}

	private static JsonObject environment() {
		var minecraft = Minecraft.getInstance();
		var result = new JsonObject();
		var window = minecraft.getWindow();
		result.addProperty("width", window.getWidth());
		result.addProperty("height", window.getHeight());
		result.addProperty("focused", GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
		result.addProperty("maximized", GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE);
		result.addProperty("iconified", GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE);
		result.addProperty("fullscreen", window.isFullscreen());
		result.addProperty("vsync", minecraft.options.enableVsync().get());
		result.addProperty("fps_limit", minecraft.options.framerateLimit().get());
		result.addProperty("render_distance", minecraft.options.renderDistance().get());
		result.addProperty("fov", minecraft.options.fov().get());
		result.addProperty("shader_pack", IrisCompat.isUsingShaderPack());

		result.addProperty("graphics", minecraft.options.graphicsPreset().get().toString());
		result.addProperty("particles_setting", minecraft.options.particles().get().toString());
		result.addProperty("sodium", ModList.get().isLoaded("sodium"));
		result.addProperty("spark", ModList.get().isLoaded("spark"));
		result.addProperty("cast_requested", Boolean.getBoolean("lobecorp.photon.diag.cast"));
		result.addProperty("submission_requested", Boolean.getBoolean(PhotonSubmissionDiagnostics.PROBES_PROPERTY));
		result.add("resource_packs", GSON.toJsonTree(minecraft.options.resourcePacks));
		if (minecraft.player != null) {
			result.add("player_position", GSON.toJsonTree(List.of(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ())));
			result.addProperty("yaw", minecraft.player.getYRot());
			result.addProperty("pitch", minecraft.player.getXRot());
		}

		return result;
	}

	private void finishCapture(boolean main) {
		var frame = PhotonFrameCapture.snapshot();
		if (!captureLabel.equals(frame.get("label").getAsString())) {
			throw new IllegalStateException("repeat_frame_owner_changed");
		}

		if (main && !"DURATION".equals(frame.get("state").getAsString())) {
			throw new IllegalStateException("repeat_main_not_natural_duration:" + frame);
		}

		ownsCapture = false;
		report.add(main ? "main_frames" : "prewarm_frames", frame);
		if (ownsSubmissions) {
			report.add(main ? "main_submissions" : "prewarm_submissions", PhotonSubmissionDiagnostics.endCase());
			ownsSubmissions = false;
		}
	}

	private boolean exported(boolean main) throws Exception {
		if (archive == null) {
			JsonObject status = mode == Mode.FULL ? PhotonPerformanceCapture.status() : PhotonFrameCapture.snapshot();
			if (mode == Mode.FULL && status.get("exporting").getAsBoolean()) {
				return false;
			}

			var key = mode == Mode.FULL ? "output" : "saved_path";
			if (!status.has(key) || status.get(key).isJsonNull()
					|| mode == Mode.FULL && (!status.get("error").isJsonNull()
					|| status.get("session_id").getAsLong() != captureSession)) {
				throw new IllegalStateException("repeat_export_failed:" + status);
			}

			if (mode == Mode.FULL) {
				var frozen = PhotonPerformanceCapture.finished();
				if (frozen == null || !captureLabel.equals(frozen.capture().view().label())) {
					throw new IllegalStateException("repeat_export_label_mismatch");
				}

				var capture = frozen.capture();
				var view = capture.view();
				var quality = view.quality();
				var retention = new JsonObject();
				retention.addProperty("aggregate_started_ns", view.startedNs());
				retention.addProperty("aggregate_finished_ns", capture.finishedNs());
				retention.addProperty("raw_truncated", view.overwritten() > 0 || view.dropped() > 0
						|| view.incomplete() > 0 || quality.limit() != null);
				retention.addProperty("aggregate_truncated", quality.deniedAggregateRequests() > 0 || quality.limit() != null);
				retention.addProperty("all_raw_events_complete_claimed", false);
				retention.add("denied_dropped_quality", GSON.toJsonTree(quality));
				retention.add("raw_thread_retention", GSON.toJsonTree(capture.threads().stream()
						.map(PhotonPerformanceTraceUtil.ThreadCapture::retention).toList()));
				retention.addProperty("raw_scope", "per-thread retained timestamp envelopes; not proof of aggregate interval coverage");
				status.add("retention", retention);
				report.add(main ? "main_trace" : "prewarm_trace", status.deepCopy());
			}

			var source = Path.of(status.get(key).getAsString());
			var expectedLabel = captureLabel;
			archive = CompletableFuture.supplyAsync(() -> archive(source, expectedLabel));
			return false;
		}

		if (!archive.isDone()) {
			return false;
		}

		outputs.add(archive.join());
		archive = null;
		writeReport();
		return true;
	}

	private JsonObject archive(Path source, String expectedLabel) {
		try {
			var root = gameDirectory.resolve("performance");
			var real = source.toRealPath();
			if (!real.startsWith(root) || !real.getFileName().toString().startsWith(expectedLabel + "-")) {
				throw new IllegalStateException("repeat_export_path_owner");
			}

			var destination = Files.createDirectory(directory.resolve(expectedLabel));
			var files = new JsonArray();
			List<Path> paths;
			if (Files.isDirectory(real)) {
				try (Stream<Path> stream = Files.walk(real)) {
					paths = stream.filter(Files::isRegularFile).toList();
				}

				var session = JsonParser.parseString(Files.readString(real.resolve("session.json"))).getAsJsonObject();
				// 内存冻结区已校验 session/label；磁盘原件另行登记并哈希后复制。
				if (!expectedLabel.equals(session.get("label").getAsString())
						|| session.get("session_id").getAsLong() != captureSession) {
					throw new IllegalStateException("repeat_disk_session_label");
				}
			} else {
				paths = List.of(real);
				var frame = JsonParser.parseString(Files.readString(real)).getAsJsonObject().getAsJsonObject("summary");
				if (!expectedLabel.equals(frame.get("label").getAsString())) {
					throw new IllegalStateException("repeat_disk_frame_label");
				}
			}

			for (var path : paths) {
				if (!path.toRealPath().startsWith(root)) {
					throw new IllegalStateException("repeat_export_file_link");
				}

				var relative = Files.isDirectory(real) ? real.relativize(path) : path.getFileName();
				var target = destination.resolve(relative);
				Files.createDirectories(target.getParent());
				Files.copy(path, target);
				var hash = PhotonRepeatedEffectAssets.sha256(Files.readAllBytes(path));
				if (!hash.equals(PhotonRepeatedEffectAssets.sha256(Files.readAllBytes(target)))) {
					throw new IllegalStateException("repeat_archive_hash_mismatch");
				}

				var file = new JsonObject();
				file.addProperty("source", path.toString());
				file.addProperty("owned_copy", target.toString());
				file.addProperty("sha256", hash);
				files.add(file);
			}

			if (files.isEmpty()) {
				throw new IllegalStateException("repeat_empty_export");
			}

			var output = new JsonObject();
			output.addProperty("label", expectedLabel);
			output.addProperty("state", "HASH_VERIFIED_COPY");
			output.add("files", files);
			return output;
		} catch (Exception failure) {
			throw new IllegalStateException("repeat_export_archive", failure);
		}
	}

	private void verifyWorkload() {
		collectCasts();
		if (actions.size() != PhotonRepeatedEffectConfig.COUNT) {
			throw new IllegalStateException("repeat_missing_triggers");
		}

		for (int index = 0; index < PhotonRepeatedEffectConfig.COUNT; index++) {
			var action = actions.get(index).getAsJsonObject();
			action.addProperty("first_active_ns", firstActive[index]);
			action.addProperty("last_active_ns", lastActive[index]);
			action.addProperty("peak_particles", peakParticles[index]);
			if (!action.has("started") || !action.get("started").getAsBoolean() || firstActive[index] == 0 || kind == Kind.NATIVE && peakParticles[index] == 0) {
				throw new IllegalStateException("repeat_instance_never_active:" + index);
			}
		}

		if (kind == Kind.NATIVE) {
			effects.forEach(PhotonRepeatedEffectAssets::verifyMaterial);
			report.addProperty("material_resolution_verified", true);
		}

		var frames = report.getAsJsonObject("main_frames");
		if (frames.get("gpu_completed").getAsLong() <= 0) {
			throw new IllegalStateException("repeat_gpu_measurement_missing");
		}

		if (report.has("main_submissions")) {
			var submission = report.getAsJsonObject("main_submissions");
			var counter = submission.getAsJsonObject(kind == Kind.NATIVE ? "other" : "model");
			if (counter == null || counter.get("cpu_jobs_added").getAsLong() + counter.get("instanced_jobs_added").getAsLong() <= 0
					|| submission.get("draw_calls").getAsLong() <= 0) {
				throw new IllegalStateException("repeat_jobs_or_draws_missing");
			}
		}

		report.addProperty("workload_verified", true);
	}

	private void beginCleanup() {
		state = State.CLEANUP;
		if (cleanup != null) {
			return;
		}

		if (minecraft.level == level) {
			report.add("tail_before_cleanup", activity());
		}

		cleanupRequestedNs = System.nanoTime();
		for (var effect : effects) {
			try {
				effect.close();
			} catch (Exception failure) {
				appendProblem(failure);
			}
		}

		var prepared = preparation == null ? CompletableFuture.completedFuture(scene) : preparation.handle((value, _) -> value);
		cleanup = prepared.thenAcceptAsync(value -> {
			if (value == null) {
				return;
			}

			RuntimeException first = null;
			for (var fixture : value.fixtures) {
				try {
					fixture.clear(server);
				} catch (RuntimeException failure) {
					if (first == null) {
						first = failure;
					} else {
						first.addSuppressed(failure);
					}
				}
			}

			if (first != null) {
				throw first;
			}
		}, server);
	}

	private void finishCleanup() {
		if (System.nanoTime() - cleanupRequestedNs > TimeUnit.SECONDS.toNanos(60)) {
			appendProblem(new IllegalStateException("repeat_cleanup_unconfirmed_timeout"));
			report.addProperty("cleanup_unconfirmed", true);
			state = State.FAILED;
			try {
				writeReport();
			} catch (Exception failure) {
				appendProblem(failure);
			}

			return;
		}
		if (cleanup == null || !cleanup.isDone() || archive != null && !archive.isDone()
				|| mode == Mode.FULL && PhotonPerformanceCapture.status().get("exporting").getAsBoolean()
				|| casts.stream().anyMatch(cast -> !cast.isDone())) {
			return;
		}

		if (failureExportPending) {
			try {
				if (!exported(captureLabel.endsWith("_main"))) {
					return;
				}
			} catch (Exception failure) {
				appendProblem(failure);
				report.add("failed_capture_export_status", mode == Mode.FULL
						? PhotonPerformanceCapture.status() : PhotonFrameCapture.snapshot());
			}

			failureExportPending = false;
		}

		if (archive != null) {
			try {
				outputs.add(archive.join());
			} catch (Exception failure) {
				appendProblem(failure);
			}

			archive = null;
		}

		try {
			cleanup.join();
		} catch (Exception failure) {
			appendProblem(failure);
		}

		if (minecraft.level == level && minecraft.player != null && minecraft.player.getUUID().equals(player)) {
			minecraft.player.getAbilities().flying = originalFlying;
			minecraft.player.onUpdateAbilities();
			minecraft.player.setPos(originalPosition);
			minecraft.player.setYRot(originalYaw);
			minecraft.player.setXRot(originalPitch);
		}

		long remaining = scene == null || minecraft.level != level ? 0 : scene.fixtures.stream()
				.flatMap(fixture -> fixture.members().stream())
				.filter(member -> level.getEntity(member.queenUuid()) != null || level.getEntity(member.targetUuid()) != null).count();
		report.addProperty("client_owned_entities_remaining", remaining);
		if (remaining != 0 || ++clientCleanupTicks < 2) {
			return;
		}

		report.addProperty("cleanup_completed_ns", System.nanoTime());
		report.addProperty("owned_server_fixture_clear_completed", !cleanup.isCompletedExceptionally());
		report.addProperty("prewarm_cache_cleanup", "production world lifecycle; caches are not cleared by trial");
		if (kind == Kind.NATIVE) {
			report.addProperty("owned_native_runtimes_closed", effects.stream().noneMatch(PhotonGeoEffect::isAlive));
		}
		report.addProperty("cancelled", cancelled);
		state = problem == null && !cancelled ? State.COMPLETE : State.FAILED;
		try {
			writeReport();
		} catch (Exception failure) {
			appendProblem(failure);
			state = State.FAILED;
		}
	}

	private void fail(Throwable failure) {
		appendProblem(failure);
		if (ownsCapture) {
			// 只取消仍与本任务 label 匹配的帧 owner。
			var snapshot = PhotonFrameCapture.snapshot();
			if (snapshot.has("label") && captureLabel.equals(snapshot.get("label").getAsString())) {
				PhotonFrameCapture.stop();
				report.add("failure_frames", PhotonFrameCapture.snapshot());
				failureExportPending = true;
			}

			ownsCapture = false;
		}

		if (ownsSubmissions) {
			report.add("failure_submissions", PhotonSubmissionDiagnostics.endCase());
			ownsSubmissions = false;
		}

		beginCleanup();
		try {
			writeReport();
		} catch (Exception writeFailure) {
			appendProblem(writeFailure);
		}
	}

	private void appendProblem(Throwable failure) {
		problem = problem == null ? failure.toString() : problem + "; " + failure;
		report.addProperty("error", problem);
	}

	private boolean terminal() {
		return state == State.COMPLETE || state == State.FAILED;
	}

	private void writeReport() throws Exception {
		report.addProperty("state", state.name());
		Files.writeString(directory.resolve("trial.json"), GSON.toJson(report),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	public static void recordFrontendDiagnostics(String label, JsonObject diagnostics) throws Exception {
		var trial = requireLabel(label);
		if (trial.mode != Mode.FULL || !trial.terminal()) {
			throw new IllegalStateException("repeat_frontend_diagnostics_owner");
		}

		trial.report.add("frontend_diagnostics", diagnostics.deepCopy());
		trial.writeReport();
	}

	public static JsonObject runtimeStatus(String label) {
		var result = new JsonObject();
		var args = ManagementFactory.getRuntimeMXBean().getInputArguments();
		result.addProperty("pid", ProcessHandle.current().pid());
		result.add("vm_arguments", GSON.toJsonTree(args));
		result.add("test_vm_arguments", GSON.toJsonTree(args.stream().filter(PhotonRepeatedEffectTrial::testArgument).toList()));
		result.addProperty("world_loaded", Minecraft.getInstance().level != null);
		result.add("environment", environment());
		result.addProperty("base_vm_arguments_clean", args.stream().noneMatch(PhotonRepeatedEffectTrial::testArgument));
		result.add("test_environment", GSON.toJsonTree(System.getenv().entrySet().stream()
				.filter(entry -> entry.getKey().startsWith("LOBECORP_LASER_PROFILE") || entry.getKey().startsWith("MC_DEBUG_"))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
		result.addProperty("active_label", current == null ? null : current.label);
		if (!label.isBlank()) {
			result.add("trial", status(label));
		}

		return result;
	}

	public static JsonObject status(String label) {
		var trial = requireLabel(label);
		var result = trial.report.deepCopy();
		result.remove("samples");
		result.addProperty("sample_count", trial.samples.size());
		result.addProperty("state", trial.state.name());
		result.addProperty("terminal", trial.terminal());
		result.addProperty("owned_report", trial.directory.resolve("trial.json").toString());
		result.addProperty("cleanup_pending", trial.state == State.CLEANUP
				|| trial.report.has("cleanup_unconfirmed") && trial.report.get("cleanup_unconfirmed").getAsBoolean());
		result.addProperty("export_pending", trial.archive != null && !trial.archive.isDone()
				|| trial.failureExportPending);
		result.addProperty("failed", trial.problem != null);
		return result;
	}

	private static PhotonRepeatedEffectTrial requireLabel(String label) {
		if (current == null || !current.label.equals(label)) {
			throw new IllegalStateException("repeat_label_owner_mismatch");
		}

		return current;
	}

	public static void cancel(String label) {
		var trial = requireLabel(label);
		if (!trial.terminal()) {
			if (trial.minecraft.level != trial.level || trial.minecraft.getSingleplayerServer() != trial.server) {
				throw new IllegalStateException("repeat_cancel_world_owner_changed");
			}

			trial.cancelled = true;
			trial.fail(new IllegalStateException("repeat_cancelled"));
		}
	}

	public static void close(String label) throws Exception {
		var trial = requireLabel(label);
		if (trial.mode != Mode.BASE || trial.state != State.COMPLETE || trial.minecraft.level != trial.level
				|| trial.minecraft.getSingleplayerServer() != trial.server) {
			throw new IllegalStateException("repeat_close_requires_completed_owned_base");
		}

		trial.requireWorld();
		trial.minecraft.stop();
	}
}
