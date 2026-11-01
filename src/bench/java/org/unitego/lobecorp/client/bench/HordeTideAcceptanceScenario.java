package org.unitego.lobecorp.client.bench;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhongbai233.bench.api.neoforge.client.BenchCaptureOptions;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientPose;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import com.zhongbai233.bench.api.neoforge.client.BenchPoseHold;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.horde.HordeAmbient;
import org.unitego.lobecorp.client.horde.HordeClient;
import org.unitego.lobecorp.client.horde.HordeParticleSamples;
import org.unitego.lobecorp.client.horde.HordeRenderMetrics;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;
import org.unitego.lobecorp.world.horde.HordeRoute;
import org.unitego.lobecorp.world.horde.HordeRules;
import org.unitego.lobecorp.world.horde.definition.MidnightCleanup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/// 七段视觉取证夹具；COMPLETE 仅代表采集完整，行为结论仍需画面审查及缺失的数值测量。
@SuppressWarnings("resource")
public class HordeTideAcceptanceScenario implements BenchClientScenario {
	public static final String ID = "lobecorp.horde-tide-acceptance";
	// 运行器另有 3600 tick 硬限制；此超时包含截图排队和 Spark 上传等待。
	public static final Duration PHASE_TIMEOUT = Duration.ofMinutes(3);
	private static final int SEGMENT_TICKS = 320;
	// 截图至少间隔 5 秒，每段仅保留低频画面证据。
	private static final int SCREENSHOT_INTERVAL_TICKS = 100;
	private static final int MAX_SEGMENT_IMAGES = 3;
	private static final int SYNC_TIMEOUT = 120;
	private static final int SPARK_TIMEOUT = 600;
	private static final String[] SEGMENTS = {"single-static", "single-moving", "multi-gather-scatter",
			"narrow-gap", "head-tail", "two-tides", "vertical-floors"};
	private static final UUID[] EVENTS = {UUID.fromString("1f14b79a-0026-4dbb-aede-45b6557b7290"),
			UUID.fromString("1f14b79a-0026-4dbb-aede-45b6557b7291")};
	private static final UUID[] TARGETS = {UUID.fromString("cb89098e-1b88-460f-a511-704cbddf0b80"),
			UUID.fromString("cb89098e-1b88-460f-a511-704cbddf0b81"),
			UUID.fromString("cb89098e-1b88-460f-a511-704cbddf0b82")};
	private final JsonArray samples = new JsonArray();
	private final JsonArray images = new JsonArray();
	private final JsonArray segments = new JsonArray();
	private final List<UUID> ownedTargets = new ArrayList<>();
	private final List<UUID> ownedEvents = new ArrayList<>();
	private final Map<UUID, CompoundTag> headers = new HashMap<>();
	private final Map<UUID, Vec3> previousReferences = new HashMap<>();
	private final Map<UUID, String> previousCircles = new HashMap<>();
	private final Gson gson = new Gson();
	private final JsonObject report = new JsonObject();
	private BenchClientPose originalPose;
	private BenchClientPose camera;
	private BenchPoseHold poseHold;
	private HordeBenchmarkEnvironment environment;
	private Vec3 center;
	private Vec3[] requestedPositions;
	private CompletableFuture<Void> serverWork;
	private CompletableFuture<Path> screenshot;
	private JsonObject screenshotRecord;
	private long lastScreenshotTick = -SCREENSHOT_INTERVAL_TICKS;
	private long lastScreenshotNanos;
	private int segmentScreenshotRequests;
	private GameType originalMode;
	private Vec3 serverPose;
	private float serverYaw;
	private float serverPitch;
	private boolean serverNoGravity;
	private boolean serverInvulnerable;
	private boolean clientNoGravity;
	private boolean clientInvulnerable;
	private boolean playerSaved;
	private boolean stageOwned;
	private boolean sparkStarted;
	private boolean stopped;
	private boolean complete;
	private long sequence;
	private long failureBaseline;
	private long firstFrame;
	private long requestTick;
	private long measureStart;
	private long actualSeed;
	private int segment = -1;
	private int elapsed;
	private int syncWait;
	private int screenshotSerial;
	private int segmentImages;
	private int lastScreenshotElapsed = -1;
	private int sparkWait;
	private Throwable pendingFailure;
	private int failureSparkWait;
	private boolean captureFailureAttached;
	private int focusLosses;
	private int windowWidth;
	private int windowHeight;
	private int setupTicks;
	private long segmentStartTick;
	private long lastHeaderTick;
	private int warmupTicks;
	private int warmupCaptureStage;
	private JsonObject visualSettings;
	private boolean originalEnabled;
	private boolean enabledSaved;
	private boolean rulesSaved;
	private boolean originalAdvanceTime;
	private boolean originalAdvanceWeather;
	private boolean originalSpawnMobs;
	private long frozenClock;
	private String worldId;
	private Vec3[] receivedServerPositions;

	@Override
	public void setup(BenchClientContext context) {
		if (context.minecraft().getSingleplayerServer() == null) {
			throw new IllegalStateException("验收夹具需要集成服务端；多人和跨维度环境未执行");
		}
		if (!HordeClient.events().isEmpty()) {
			throw new IllegalStateException("存在其他浪潮事件，拒绝覆盖其状态");
		}
		originalPose = context.automation().pose();
		clientNoGravity = context.player().isNoGravity();
		clientInvulnerable = context.player().isInvulnerable();
		var origin = context.player().position();
		var ground = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(int) Math.floor(origin.x), (int) Math.floor(origin.z));
		center = new Vec3(origin.x, ground, origin.z);
		camera = BenchClientPose.lookingAt(center.x, ground + 20, center.z - 25,
				center.x, ground + 1, center.z);
		originalEnabled = HordeVisualConfig.ENABLED.get();
		enabledSaved = true;
		HordeVisualConfig.ENABLED.set(true);
		visualSettings = visualSettings();
		failureBaseline = HordeRenderMetrics.failureSequence();
		environment = new HordeBenchmarkEnvironment();
		HordeRenderMetrics.start(context.resultDirectory().resolve("artifacts/tide-acceptance/stages.jsonl"),
				false, HordeRenderMetrics.GpuMode.FULL_STAGE_SUM);
		stageOwned = true;
		serverWork = onServer(context, level -> {
			if (!(level.getPlayerByUUID(context.player().getUUID()) instanceof ServerPlayer player)) {
				throw new IllegalStateException("找不到集成服务端玩家");
			}
			serverPose = player.position();
			serverYaw = player.getYRot();
			serverPitch = player.getXRot();
			originalMode = player.gameMode.getGameModeForPlayer();
			serverNoGravity = player.isNoGravity();
			serverInvulnerable = player.isInvulnerable();
			playerSaved = true;
			actualSeed = level.getSeed();
			worldId = level.getServer().getWorldData().getLevelName();
			frozenClock = level.getDefaultClockTime();
			originalAdvanceTime = level.getGameRules().get(GameRules.ADVANCE_TIME);
			originalAdvanceWeather = level.getGameRules().get(GameRules.ADVANCE_WEATHER);
			originalSpawnMobs = level.getGameRules().get(GameRules.SPAWN_MOBS);
			rulesSaved = true;
			level.getGameRules().set(GameRules.ADVANCE_TIME, false, level.getServer());
			level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, level.getServer());
			level.getGameRules().set(GameRules.SPAWN_MOBS, false, level.getServer());
			player.setGameMode(GameType.SPECTATOR);
			player.setPos(camera.x(), camera.y(), camera.z());
			player.setYRot(camera.yaw());
			player.setXRot(camera.pitch());
			player.setNoGravity(true);
			player.setInvulnerable(true);
			for (var id : TARGETS) {
				if (level.getEntity(id) != null) {
					throw new IllegalStateException("固定目标 UUID 已被占用：" + id);
				}
				var cow = new Cow(EntityType.COW, level);
				cow.setUUID(id);
				cow.setNoAi(true);
				cow.setNoGravity(true);
				cow.setInvulnerable(true);
				cow.setSilent(true);
				cow.setPos(center.x, center.y + 80, center.z);
				if (!level.addFreshEntity(cow)) {
					throw new IllegalStateException("真实目标生成失败：" + id);
				}
				ownedTargets.add(id);
			}
		});
		BenchSpark.start(context);
		sparkStarted = true;
	}

	@Override
	public BenchClientStepResult stabilize(BenchClientContext context) throws Exception {
		return runGuarded(context, () -> stabilizeStep(context));
	}

	private BenchClientStepResult stabilizeStep(BenchClientContext context) {
		checkWindow(context, false);
		if (!serverWork.isDone() || !context.player().isSpectator()) {
			if (++setupTicks > SYNC_TIMEOUT) {
				throw new AssertionError("服务端初始化或旁观模式同步超时");
			}
			return BenchClientStepResult.CONTINUE;
		}
		serverWork.join();
		if (poseHold == null) {
			poseHold = context.automation().holdPose(camera);
			context.player().setNoGravity(true);
			context.player().setInvulnerable(true);
		}
		if (windowWidth == 0) {
			windowWidth = context.minecraft().getWindow().getWidth();
			windowHeight = context.minecraft().getWindow().getHeight();
		}
		return context.environment().isFrameStable(8) ? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
	}

	@Override
	public BenchClientStepResult warmup(BenchClientContext context) throws Exception {
		return runGuarded(context, () -> warmupStep(context));
	}

	private BenchClientStepResult warmupStep(BenchClientContext context) throws Exception {
		checkWindow(context, false);
		finishScreenshot(context);

		if (++warmupTicks < 40 || screenshot != null) {
			return BenchClientStepResult.CONTINUE;
		}

		if (warmupCaptureStage == 0) {
			createEvent(context, EVENTS[0], false);
			HordeVisualConfig.ENABLED.set(false);
			startScreenshot(context, "tide-visible-off", "warmup-off");
			warmupCaptureStage = 1;
			return BenchClientStepResult.CONTINUE;
		}

		if (warmupCaptureStage == 1) {
			HordeVisualConfig.ENABLED.set(true);
			warmupCaptureStage = 2;
			return BenchClientStepResult.CONTINUE;
		}

		if (warmupCaptureStage == 2) {
			if (context.scheduler().currentTick() - lastScreenshotTick < SCREENSHOT_INTERVAL_TICKS) {
				return BenchClientStepResult.CONTINUE;
			}

			startScreenshot(context, "tide-visible-on", "warmup-on");
			warmupCaptureStage = 3;
			return BenchClientStepResult.CONTINUE;
		}

		return BenchClientStepResult.COMPLETE;
	}

	@Override
	public BenchClientStepResult measure(BenchClientContext context) throws Exception {
		return runGuarded(context, () -> collect(context));
	}

	private BenchClientStepResult runGuarded(BenchClientContext context, Step action) throws Exception {
		if (pendingFailure != null) {
			return waitForFailedSpark(context);
		}
		try {
			return action.run();
		} catch (Throwable failure) {
			pendingFailure = failure;
			complete = false;
			try {
				BenchSpark.stop();
				stopped = true;
			} catch (Throwable stopFailure) {
				failure.addSuppressed(stopFailure);
			}
			try {
				HordeRenderMetrics.phase(HordeRenderMetrics.Phase.TEARDOWN);
				if (screenshot != null) {
					screenshot.cancel(false);
					screenshot = null;
					screenshotRecord = null;
				}
			} catch (Throwable captureFailure) {
				failure.addSuppressed(captureFailure);
			}
			return BenchClientStepResult.CONTINUE;
		}
	}

	private BenchClientStepResult waitForFailedSpark(BenchClientContext context) throws Exception {
		// 此状态不读取或恢复焦点、不再取证；仅保留客户端等待本次异步上传。
		try {
			BenchSpark.capture(context, "horde-tide-acceptance-profile.txt");
		} catch (Throwable failure) {
			if (!captureFailureAttached) {
				pendingFailure.addSuppressed(failure);
				captureFailureAttached = true;
			}
		}
		if (BenchSpark.saved()) {
			throw pendingException();
		}
		if (++failureSparkWait >= SPARK_TIMEOUT) {
			pendingFailure.addSuppressed(new IllegalStateException("原始验收失败后，Spark 上传在 600 tick 内未返回链接"));
			throw pendingException();
		}
		return BenchClientStepResult.CONTINUE;
	}

	private Exception pendingException() {
		if (pendingFailure instanceof Error error) {
			throw error;
		}
		if (pendingFailure instanceof Exception exception) {
			return exception;
		}
		return new IllegalStateException("验收失败", pendingFailure);
	}

	private BenchClientStepResult collect(BenchClientContext context) throws Exception {
		checkWindow(context, true);
		if (measureStart == 0) {
			measureStart = context.scheduler().currentTick();
			firstFrame = context.frames().sampleCount();
		}
		if (context.scheduler().currentTick() - measureStart > 2900) {
			throw new AssertionError("采集超出保留给运行器的 tick 预算");
		}
		finishScreenshot(context);
		BenchSpark.capture(context, "horde-tide-acceptance-profile.txt");
		if (segment >= SEGMENTS.length) {
			return waitForSpark();
		}
		if (segment < 0) {
			beginSegment(context, 0);
		}
		HordeRenderMetrics.phase(HordeRenderMetrics.Phase.MEASURE);
		updateHeaders(context);
		sample(context);
		if (HordeRenderMetrics.failureSequence() != failureBaseline) {
			throw new AssertionError("GPU 绘制失败序号发生变化");
		}
		requestScreenshot(context);
		if (!positionsSynced(context)) {
			if (++syncWait > SYNC_TIMEOUT) {
				throw new AssertionError("真实目标位置同步超时：" + gson.toJson(gson.toJsonTree(requestedPositions)));
			}
			return BenchClientStepResult.CONTINUE;
		}
		syncWait = 0;
		if (elapsed >= SEGMENT_TICKS && screenshot == null) {
			if (segmentImages < 1) {
				throw new AssertionError("本段未保存有效截图文件");
			}
			var value = new JsonObject();
			value.addProperty("name", SEGMENTS[segment]);
			value.addProperty("capture_status", "COMPLETE");
			value.addProperty("behavior_status", "REQUIRES_VISUAL_REVIEW_AND_UNMEASURED_CHANNELS");
			value.addProperty("screenshots", segmentImages);
			value.addProperty("image_analysis_status", "DEFERRED_OFFLINE");
			value.addProperty("end_scheduler_tick", context.scheduler().currentTick());
			segments.add(value);
			beginSegment(context, segment + 1);
			return BenchClientStepResult.CONTINUE;
		}
		elapsed++;
		if (elapsed % 12 == 0 && elapsed < SEGMENT_TICKS) {
			moveTargets(context);
		}
		return BenchClientStepResult.CONTINUE;
	}

	private void beginSegment(BenchClientContext context, int next) {
		for (var id : List.copyOf(ownedEvents)) {
			send(id, "remove", new CompoundTag());
		}
		ownedEvents.clear();
		headers.clear();
		previousReferences.clear();
		previousCircles.clear();
		segment = next;
		elapsed = 0;
		segmentImages = 0;
		lastScreenshotElapsed = -1;
		segmentStartTick = context.level().getGameTime();
		lastHeaderTick = segmentStartTick;
		segmentScreenshotRequests = 0;
		if (segment >= SEGMENTS.length) {
			BenchSpark.stop();
			stopped = true;
			return;
		}
		createEvent(context, EVENTS[0], false);
		if (segment == 5) {
			createEvent(context, EVENTS[1], true);
		}
		moveTargets(context);
	}

	private void createEvent(BenchClientContext context, UUID id, boolean crossing) {
		var anchors = crossing ? List.of(center.add(0, 0, 250), center.add(0, 0, -250))
				: List.of(center.add(250, 0, 0), center.add(-250, 0, 0));
		var route = HordeRoute.create(HordeSamplingUtil.path(anchors));
		var rules = HordeRules.configured().withOverrides(6000, 500);
		// 固定平面是视觉夹具，不写世界方块；楼层段仅改变真实目标的 Y 带。
		if (!route.prepareDeferred(rules, point -> new Vec3(point.x, center.y, point.z))) {
			throw new IllegalStateException("受控路线准备未完成");
		}
		var definition = new MidnightCleanup();
		var visual = definition.visuals();
		var header = new CompoundTag();
		header.putInt("format", 1);
		header.putString("dimension", context.level().dimension().identifier().toString());
		header.put("rules", rules.save());
		header.putLong("stop", segment == 4 ? 120 : 6000);
		header.putDouble("length", route.length());
		header.putDouble("speed", 0.75);
		header.putInt("node_count", route.size());
		header.putString("model", visual.model().toString());
		header.putString("animation", visual.animation().toString());
		header.putString("run", visual.runAnimation());
		header.putString("texture", visual.texture().toString());
		header.putDouble("light_probe_height", definition.dimensions().eyeHeight());
		header.putDouble("entity_height", definition.dimensions().height());
		header.putLong("tick", segment == 4 ? 290 : 700);
		header.putBoolean("reset", true);
		ownedEvents.add(id);
		send(id, "header", header);
		header.putBoolean("reset", false);
		headers.put(id, header);
		var nodes = new ListTag();
		for (int index = 0; index < route.size(); index++) {
			nodes.add(route.node(index).save());
		}
		var page = new CompoundTag();
		page.putInt("first", 0);
		page.put("nodes", nodes);
		send(id, "route", page);
	}

	private void updateHeaders(BenchClientContext context) {
		long now = context.level().getGameTime();
		if (now - lastHeaderTick < 20) {
			return;
		}
		lastHeaderTick = now;
		for (var entry : headers.entrySet()) {
			entry.getValue().putLong("tick", (segment == 4 ? 290 : 700) + now - segmentStartTick);
			send(entry.getKey(), "header", entry.getValue());
		}
	}

	private void send(UUID id, String kind, CompoundTag body) {
		var packet = new CompoundTag();
		packet.putString("id", id.toString());
		packet.putString("kind", kind);
		packet.putLong("sequence", ++sequence);
		packet.put("body", body.copy());
		HordeClient.apply(packet);
	}

	private void moveTargets(BenchClientContext context) {
		var parked = center.add(0, 80, 0);
		requestedPositions = new Vec3[]{parked, parked.add(3, 0, 0), parked.add(-3, 0, 0)};
		double progress = elapsed / (double) SEGMENT_TICKS;
		switch (segment) {
			case 0 -> {
				if (elapsed >= 60 && elapsed < 140) {
					requestedPositions[0] = center;
				}
			}
			case 1 -> requestedPositions[0] = center.add(0, 0, -18 + progress * 36);
			case 2 -> {
				double separation = elapsed < 100 ? 18 * (1 - progress * 2) : 18 * (progress * 2 - 1);
				requestedPositions = new Vec3[]{center, center.add(0, 0, separation), center.add(0, 0, -separation)};
			}
			case 3 -> requestedPositions = new Vec3[]{center.add(0, 0, -4), center.add(0, 0, 4), parked};
			case 4, 5 -> requestedPositions[0] = center;
			case 6 -> requestedPositions = new Vec3[]{center, center.add(0, 12, 0), center.add(0, -12, 0)};
			default -> throw new IllegalStateException("未知验收段");
		}
		var positions = requestedPositions.clone();
		serverWork = onServer(context, level -> {
			var player = level.getPlayerByUUID(context.player().getUUID());
			if (player != null) {
				player.setPos(camera.x(), camera.y(), camera.z());
				player.setDeltaMovement(Vec3.ZERO);
			}
			var actual = new Vec3[TARGETS.length];
			for (int index = 0; index < TARGETS.length; index++) {
				var entity = level.getEntity(TARGETS[index]);
				if (entity == null) {
					throw new IllegalStateException("真实目标丢失：" + TARGETS[index]);
				}
				var point = positions[index];
				entity.teleportTo(point.x, point.y, point.z);
				entity.setDeltaMovement(Vec3.ZERO);
				actual[index] = entity.position();
			}
			receivedServerPositions = actual;
		});
		requestTick = context.scheduler().currentTick();
	}

	private boolean positionsSynced(BenchClientContext context) {
		if (!serverWork.isDone()) {
			return false;
		}
		serverWork.join();
		for (int index = 0; index < TARGETS.length; index++) {
			var entity = context.level().getEntity(TARGETS[index]);
			if (entity == null || entity.position().distanceToSqr(requestedPositions[index]) > 0.25) {
				return false;
			}
		}
		return true;
	}

	private void sample(BenchClientContext context) {
		var value = new JsonObject();
		value.addProperty("segment", SEGMENTS[segment]);
		value.addProperty("segment_tick", elapsed);
		value.addProperty("actual_segment_tick", context.level().getGameTime() - segmentStartTick);
		value.addProperty("actual_camera", context.automation().pose().toString());
		value.addProperty("camera_error_squared", cameraError(context));
		value.addProperty("tick", context.scheduler().currentTick());
		value.addProperty("frame_sample_count", context.frames().sampleCount());
		value.addProperty("target_request_tick", requestTick);
		value.addProperty("targets_synced", positionsSynced(context));
		value.addProperty("gpu_failure_sequence", HordeRenderMetrics.failureSequence());
		value.addProperty("selected_particle_areas", HordeAmbient.selectedAreas());
		value.addProperty("emitted_particles_tick", HordeAmbient.emittedParticles());
		value.addProperty("focused", focused(context));
		value.addProperty("maximized", maximized(context));
		value.addProperty("iconified", iconified(context));
		var targets = new JsonArray();
		for (int index = 0; index < TARGETS.length; index++) {
			var target = new JsonObject();
			target.addProperty("uuid", TARGETS[index].toString());
			target.add("requested_server_position", gson.toJsonTree(requestedPositions[index]));
			target.add("received_server_position", gson.toJsonTree(receivedServerPositions == null ? null : receivedServerPositions[index]));
			var entity = context.level().getEntity(TARGETS[index]);
			target.add("received_client_position", gson.toJsonTree(entity == null ? null : entity.position()));
			targets.add(target);
		}
		value.add("targets", targets);
		var events = new JsonArray();
		for (var event : HordeClient.events()) {
			if (!ownedEvents.contains(event.id)) {
				continue;
			}
			var item = new JsonObject();
			double time = event.time(0);
			item.addProperty("id", event.id.toString());
			item.addProperty("event_time", time);
			item.addProperty("ready", event.ready);
			item.addProperty("failed", event.failed);
			item.addProperty("submitted_visible_instances_tick_snapshot", HordeClient.batches().stream()
					.filter(batch -> batch.event() == event).mapToLong(HordeClient.Batch::count).sum());
			item.addProperty("cpu_regions", event.regions.size());
			item.addProperty("gpu_circle_count_api", event.split.count());
			var circles = gson.toJsonTree(event.split.circles());
			item.add("cpu_smoothed_circles_not_exact_gpu_table", circles);
			item.addProperty("cpu_circle_table_changed", !circles.toString().equals(previousCircles.put(event.id, circles.toString())));
			if (event.ready && event.tide != null) {
				// 行列身份跨本段固定；不包含 GPU split、terrain 回填及最终着色器 hash 偏移。
				long row = segment == 4 ? 0 : 200;
				int column = event.columns / 2;
				var point = event.tide.position(row, column, event.columns, time,
						event.rules.rowSpacing(), event.rules.columnSpacing(), event.speed);
				item.addProperty("reference_row", row);
				item.addProperty("reference_column", column);
				item.add("cpu_reference_position", gson.toJsonTree(point));
				var previous = previousReferences.put(event.id, point);
				item.add("cpu_reference_displacement", gson.toJsonTree(previous == null ? Vec3.ZERO : point.subtract(previous)));
				var snapshot = HordeParticleSamples.latest(event, time);
				if (snapshot != null) {
					var particle = new JsonObject();
					particle.addProperty("event_time", snapshot.eventTime());
					particle.addProperty("draw_serial", snapshot.drawSerial());
					particle.addProperty("generation", snapshot.generation());
					particle.addProperty("sample_count", snapshot.samples().size());
					particle.addProperty("age_nanos", System.nanoTime() - snapshot.capturedNanos());
					item.add("particle_snapshot_cpu_reference_metadata", particle);
				}
			}
			events.add(item);
		}
		value.add("events", events);
		samples.add(value);
	}

	private void requestScreenshot(BenchClientContext context) {
		long tick = context.scheduler().currentTick();
		if (screenshot != null || elapsed >= SEGMENT_TICKS || segmentScreenshotRequests >= MAX_SEGMENT_IMAGES
				|| elapsed == lastScreenshotElapsed || tick - lastScreenshotTick < SCREENSHOT_INTERVAL_TICKS) {
			return;
		}

		startScreenshot(context, "tide-" + SEGMENTS[segment] + "-" + String.format("%04d", screenshotSerial++),
				SEGMENTS[segment]);
		segmentScreenshotRequests++;
		lastScreenshotElapsed = elapsed;
	}

	private void startScreenshot(BenchClientContext context, String name, String phase) {
		long tick = context.scheduler().currentTick();
		long nanos = System.nanoTime();
		screenshotRecord = new JsonObject();
		screenshotRecord.addProperty("segment", phase);
		screenshotRecord.addProperty("request_tick", tick);
		screenshotRecord.addProperty("request_nanos", nanos);
		if (lastScreenshotNanos != 0) {
			screenshotRecord.addProperty("interval_from_previous_request_ticks", tick - lastScreenshotTick);
			screenshotRecord.addProperty("interval_from_previous_request_nanos", nanos - lastScreenshotNanos);
		}

		screenshotRecord.addProperty("request_frame_sample_count", context.frames().sampleCount());
		lastScreenshotTick = tick;
		lastScreenshotNanos = nanos;
		screenshot = context.automation().captureScreenshot(name, new BenchCaptureOptions(true, true, 0));
	}

	private void finishScreenshot(BenchClientContext context) throws IOException {
		if (screenshot == null || !screenshot.isDone()) {
			return;
		}

		var path = screenshot.join();
		screenshotRecord.addProperty("complete_tick", context.scheduler().currentTick());
		screenshotRecord.addProperty("complete_nanos", System.nanoTime());
		screenshotRecord.addProperty("complete_frame_sample_count", context.frames().sampleCount());
		screenshotRecord.addProperty("path", path.toString());
		boolean exists = Files.isRegularFile(path);
		long size = exists ? Files.size(path) : 0;
		screenshotRecord.addProperty("file_exists", exists);
		screenshotRecord.addProperty("file_size_bytes", size);
		screenshotRecord.addProperty("image_analysis_status", "DEFERRED_OFFLINE");
		images.add(screenshotRecord);
		if (!exists || size <= 0) {
			throw new AssertionError("截图文件缺失或为空：" + path);
		}

		context.artifacts().register(path, "image/png");
		screenshot = null;
		screenshotRecord = null;
		if (segment >= 0 && segment < SEGMENTS.length) {
			segmentImages++;
		}
	}

	private BenchClientStepResult waitForSpark() {
		if (!stopped) {
			BenchSpark.stop();
			stopped = true;
		}
		if (!BenchSpark.saved()) {
			if (++sparkWait > SPARK_TIMEOUT) {
				BenchSpark.requireSaved();
			}
			return BenchClientStepResult.CONTINUE;
		}
		complete = true;
		return BenchClientStepResult.COMPLETE;
	}

	@Override
	public void verify(BenchClientContext context) {
		checkWindow(context, true);
		BenchSpark.requireSaved();
		report.add("visual_settings_at_verify", visualSettings());
		if (!context.environment().isValid() || !visualSettings.equals(visualSettings()) || cameraError(context) > 0.25) {
			throw new AssertionError("环境无效或光影/视觉设置改变：" + context.environment().invalidations());
		}
		if (!complete || segments.size() != SEGMENTS.length || context.frames().sampleCount() <= firstFrame
				|| report.has("window_gate_status") || HordeRenderMetrics.failureSequence() != failureBaseline) {
			throw new AssertionError("七段采集或环境门禁未完成");
		}
	}

	private void checkWindow(BenchClientContext context, boolean measuring) {
		boolean focused = focused(context);
		boolean iconified = iconified(context);
		boolean maximized = maximized(context);
		if (measuring && !focused) {
			focusLosses++;
		}
		if (measuring && iconified) {
			report.addProperty("window_gate_status", "INVALID");
			report.addProperty("window_gate_tick", context.scheduler().currentTick());
			report.addProperty("window_gate_iconified", iconified);
			report.addProperty("window_gate_maximized", maximized);
		}
		if (measuring && (windowWidth != context.minecraft().getWindow().getWidth()
				|| windowHeight != context.minecraft().getWindow().getHeight())) {
			throw new AssertionError("采集窗口尺寸改变");
		}
		if (!measuring) {
			windowWidth = context.minecraft().getWindow().getWidth();
			windowHeight = context.minecraft().getWindow().getHeight();
		}
	}

	private boolean focused(BenchClientContext context) {
		return GLFW.glfwGetWindowAttrib(context.minecraft().getWindow().handle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
	}

	private boolean iconified(BenchClientContext context) {
		return GLFW.glfwGetWindowAttrib(context.minecraft().getWindow().handle(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
	}

	private boolean maximized(BenchClientContext context) {
		return GLFW.glfwGetWindowAttrib(context.minecraft().getWindow().handle(), GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
	}

	private CompletableFuture<Void> onServer(BenchClientContext context, Consumer<ServerLevel> action) {
		var result = new CompletableFuture<Void>();
		var server = context.minecraft().getSingleplayerServer();
		if (server == null) {
			result.completeExceptionally(new IllegalStateException("集成服务端已退出"));
			return result;
		}
		var dimension = context.level().dimension();
		server.execute(() -> {
			try {
				var level = server.getLevel(dimension);
				if (level == null) {
					throw new IllegalStateException("验收维度不存在");
				}
				action.accept(level);
				result.complete(null);
			} catch (Throwable failure) {
				result.completeExceptionally(failure);
			}
		});
		return result;
	}

	@Override
	public void teardown(BenchClientContext context) throws Exception {
		Throwable failure = cleanup(null, () -> {
			if (screenshot != null) {
				screenshot.cancel(false);
				screenshot = null;
				screenshotRecord = null;
			}
		});
		failure = cleanup(failure, () -> {
			if (sparkStarted) {
				BenchSpark.stop();
				BenchSpark.capture(context, "horde-tide-acceptance-profile.txt");
				if (!BenchSpark.saved() && pendingFailure == null) {
					throw new IllegalStateException("客户端结束前未保存 Spark；需保持本客户端并补收链接");
				}
			}
		});
		for (var id : List.copyOf(ownedEvents)) {
			failure = cleanup(failure, () -> {
				send(id, "remove", new CompoundTag());
				ownedEvents.remove(id);
			});
		}
		for (var id : TARGETS) {
			failure = cleanup(failure, () -> onServer(context, level -> {
				if (ownedTargets.contains(id)) {
					var entity = level.getEntity(id);
					if (entity != null) {
						entity.discard();
					}
					ownedTargets.remove(id);
				}
			}).get(10, TimeUnit.SECONDS));
		}
		failure = cleanup(failure, () -> onServer(context, level -> {
			if (playerSaved) {
				if (!(level.getPlayerByUUID(context.player().getUUID()) instanceof ServerPlayer player)) {
					throw new IllegalStateException("恢复玩家时服务端玩家已丢失");
				}
				player.setGameMode(originalMode);
				player.setPos(serverPose);
				player.setYRot(serverYaw);
				player.setXRot(serverPitch);
				player.setNoGravity(serverNoGravity);
				player.setInvulnerable(serverInvulnerable);
			}
		}).get(10, TimeUnit.SECONDS));
		failure = cleanup(failure, () -> onServer(context, level -> {
			if (rulesSaved) {
				level.getGameRules().set(GameRules.ADVANCE_TIME, originalAdvanceTime, level.getServer());
				level.getGameRules().set(GameRules.ADVANCE_WEATHER, originalAdvanceWeather, level.getServer());
				level.getGameRules().set(GameRules.SPAWN_MOBS, originalSpawnMobs, level.getServer());
			}
		}).get(10, TimeUnit.SECONDS));
		failure = cleanup(failure, () -> {
			if (enabledSaved) {
				HordeVisualConfig.ENABLED.set(originalEnabled);
			}
		});
		failure = cleanup(failure, () -> {
			if (poseHold != null) {
				poseHold.close();
				poseHold = null;
			}
		});
		failure = cleanup(failure, () -> {
			if (originalPose != null) {
				context.automation().setPose(originalPose);
				context.player().setNoGravity(clientNoGravity);
				context.player().setInvulnerable(clientInvulnerable);
			}
		});
		failure = cleanup(failure, () -> {
			if (stageOwned) {
				stageOwned = false;
				context.artifacts().register(HordeRenderMetrics.finish(), "application/x-ndjson");
			}
		});
		report.addProperty("cleanup_success_before_report", failure == null);
		report.addProperty("cleanup_failure", failure == null ? "" : failure.toString());
		report.addProperty("owned_events_remaining", ownedEvents.size());
		report.addProperty("owned_targets_remaining", ownedTargets.size());
		failure = cleanup(failure, () -> saveReport(context));
		if (failure instanceof Exception exception) {
			throw exception;
		}
		if (failure != null) {
			throw new IllegalStateException("验收夹具清理未完成", failure);
		}
	}

	private void saveReport(BenchClientContext context) throws IOException {
		if (environment != null) {
			environment.close();
			var snapshot = environment.snapshot();
			report.add("environment", snapshot);
			environment = null;
		}
		report.addProperty("scenario", ID);
		report.addProperty("process_pid", ProcessHandle.current().pid());
		report.addProperty("process_started", ProcessHandle.current().info().startInstant().map(Object::toString).orElse("unknown"));
		report.addProperty("game_directory", context.minecraft().gameDirectory.getAbsolutePath());
		report.addProperty("focus_gate_enabled", false);
		report.addProperty("method_constraint", "Structured in-game state/camera/framebuffer only. Development window placement is initialized once using SetWindowPos with NOACTIVATE and NOZORDER; native maximized state is observed only. After initialization no additional window movement, foreground activation or focus restoration is performed by this fixture. Focus losses are recorded without a rejection gate. No physical system mouse/keyboard injection, manual desktop automation or monitor changes.");
		report.addProperty("pending_failure", pendingFailure == null ? "" : pendingFailure.toString());
		report.addProperty("failure_spark_wait_ticks", failureSparkWait);
		report.addProperty("spark_saved", sparkStarted && BenchSpark.saved());
		var suppressed = new JsonArray();
		if (pendingFailure != null) {
			for (var failure : pendingFailure.getSuppressed()) {
				suppressed.add(failure.toString());
			}
		}
		report.add("pending_failure_suppressed", suppressed);
		report.addProperty("capture_status", complete ? "COMPLETE" : "INCOMPLETE");
		report.addProperty("behavior_status", "REQUIRES_VISUAL_REVIEW_AND_UNMEASURED_CHANNELS");
		report.addProperty("sampling_scope", "client tick snapshots and serialized asynchronous screenshots; NOT every rendered frame; screenshot IO is not performance acceptance");
		report.addProperty("bench_recipe_seed", context.seed());
		report.addProperty("actual_world_seed", actualSeed);
		report.addProperty("world_id", worldId);
		report.addProperty("frozen_default_clock_tick", frozenClock);
		report.addProperty("original_advance_time", originalAdvanceTime);
		report.addProperty("original_advance_weather", originalAdvanceWeather);
		report.addProperty("original_spawn_mobs", originalSpawnMobs);
		report.addProperty("environment_valid", context.environment().isValid());
		report.add("environment_invalidations", gson.toJsonTree(context.environment().invalidations()));
		report.add("visual_settings_at_setup", visualSettings);
		report.addProperty("visible_pixels_on_off", "NOT_RUN");
		report.addProperty("image_analysis_status", "DEFERRED_OFFLINE");
		report.addProperty("gpu_stage_timing_enabled", false);
		report.addProperty("screenshot_minimum_request_interval_ticks", SCREENSHOT_INTERVAL_TICKS);
		report.addProperty("screenshot_maximum_per_segment", MAX_SEGMENT_IMAGES);
		report.addProperty("screenshot_count", images.size());
		report.addProperty("dimension", context.level().dimension().identifier().toString());
		report.addProperty("camera", camera == null ? "" : camera.toString());
		report.addProperty("window_width", windowWidth);
		report.addProperty("window_height", windowHeight);
		report.addProperty("measure_focus_losses", focusLosses);
		report.addProperty("gpu_failure_baseline", failureBaseline);
		report.addProperty("gpu_failure_final", HordeRenderMetrics.failureSequence());
		report.addProperty("synthetic_terrain", "flat fixed Y client visual route; real target entities; no world blocks changed");
		report.addProperty("building_occlusion", "NOT_RUN");
		report.addProperty("first_empty_table_scope", "new event first draw may precede next target collection; spectator local circle remains in later ticks; no permanent empty-table assertion");
		report.addProperty("cpu_circle_scope", "smoothed CPU circles before GPU ordering/truncation; NOT exact uploaded table");
		report.addProperty("gpu_actual_anchors", "NOT_MEASURED");
		report.addProperty("gpu_uploaded_circle_table_exact_values", "NOT_MEASURED");
		report.addProperty("true_3d_occupancy_and_refill", "NOT_MEASURED");
		report.addProperty("I49_numeric_acceptance", "NOT_MEASURED");
		report.addProperty("multiplayer", "NOT_RUN");
		report.addProperty("cross_dimension", "NOT_RUN");
		report.addProperty("head_tail_factor", "segment 5 emission stop=120 instead of 6000; initial tick=290; continuous same event clock thereafter");
		report.addProperty("visual_settings", "existing count/range/LOD/material/shader unchanged; environment must be reviewed");
		report.add("segments", segments);
		report.add("tick_samples", samples);
		report.add("screenshots", images);
		var path = context.resultDirectory().resolve("artifacts/tide-acceptance/acceptance.json");
		Files.createDirectories(path.getParent());
		Files.writeString(path, gson.toJson(report));
		context.artifacts().register(path, "application/json");
		if (report.has("environment") && !report.getAsJsonObject("environment").get("cleanup_complete").getAsBoolean()) {
			throw new IllegalStateException("环境探针清理未完成，详见报告");
		}
	}

	private double cameraError(BenchClientContext context) {
		var actual = context.automation().pose();
		double dx = actual.x() - camera.x();
		double dy = actual.y() - camera.y();
		double dz = actual.z() - camera.z();
		return dx * dx + dy * dy + dz * dz;
	}

	private JsonObject visualSettings() {
		var result = new JsonObject();
		result.addProperty("count", HordeVisualConfig.MAX_VISIBLE.get());
		result.addProperty("range", HordeVisualConfig.DISTANCE.get());
		result.addProperty("animation_frames", HordeVisualConfig.ANIMATION_FRAMES.get());
		result.addProperty("lod", HordeVisualConfig.LOD_ENABLED.get());
		result.addProperty("lod_near", HordeVisualConfig.LOD_NEAR.get());
		result.addProperty("lod_far", HordeVisualConfig.LOD_FAR.get());
		result.addProperty("card_distance", HordeVisualConfig.CARD_DISTANCE.get());
		result.addProperty("frame_distance", HordeVisualConfig.FRAME_DISTANCE.get());
		result.addProperty("iris_material_merge", HordeVisualConfig.IRIS_MERGE_MATERIALS.get());
		result.addProperty("iris_double_buffer", HordeVisualConfig.IRIS_DOUBLE_BUFFER.get());
		result.addProperty("iris_pack", Iris.getCurrentPackName());
		result.addProperty("iris_shaders_active", IrisApi.getInstance().isShaderPackInUse());
		return result;
	}

	private Throwable cleanup(Throwable previous, Cleanup action) {
		try {
			action.run();
		} catch (Throwable failure) {
			if (previous == null) {
				return failure;
			}
			previous.addSuppressed(failure);
		}
		return previous;
	}

	@FunctionalInterface
	private interface Step {
		BenchClientStepResult run() throws Exception;
	}

	@FunctionalInterface
	private interface Cleanup {
		void run() throws Exception;
	}
}
