package org.unitego.lobecorp.client.bench;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.MetricDirection;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientPose;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import com.zhongbai233.bench.api.neoforge.client.BenchPoseHold;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.entity.ordeal.indigo.SweeperVariant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/// 通过集成服务端生成真实清道夫，客户端只读同步结果并测量渲染成本；AI 开关只影响服务端实体行为。
/// 与本地实体场景不同，本场景覆盖实体同步、服务端 AI tick 与真实实体渲染三条路径。
public class Sweeper30RenderScenario implements BenchClientScenario {
	private static final int GRID_SPACING = 3;
	private static final double GPU_SECONDS = 28;
	private static final BenchMetricDescriptor LOADED_ENTITIES = new BenchMetricDescriptor(
			"lobecorp.sweeper30.loaded_entities", "count", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor SERVER_ENTITIES = new BenchMetricDescriptor(
			"lobecorp.sweeper30.server_entities", "count", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor FRAME_SAMPLES = new BenchMetricDescriptor(
			"lobecorp.sweeper30.frame_samples", "count", MetricDirection.NEUTRAL);

	private final boolean noAi;
	private final String id;
	private final String sparkArtifact;
	private final int count = LobecorpBenchConfig.sweeper30Count();
	private final int columns = (int) Math.ceil(Math.sqrt(count));
	private final int rows = (count + columns - 1) / columns;
	private final BenchServerWorkload workload = new BenchServerWorkload();
	private BlockPos gridOrigin;
	private double groundY;
	private BenchClientPose originalPose;
	private BenchPoseHold pose;
	private CompletableFuture<Path> screenshot;
	private Path screenshotPath;
	private HordeBenchmarkEnvironment environment;
	private UUID gpuToken;
	private int stabilizeTicks;
	private int warmupTicks;
	private int captureRecoveryTicks;
	private int measureTicks;
	private int sparkWaitTicks;
	private long firstFrameCount;
	private long measuredFrames;
	private boolean measureComplete;

	public Sweeper30RenderScenario(boolean noAi) {
		this.noAi = noAi;
		id = noAi ? LobecorpBenchConfig.SWEEPER30_NOAI_SCENARIO : LobecorpBenchConfig.SWEEPER30_AI_SCENARIO;
		sparkArtifact = (noAi ? "sweeper30-noai" : "sweeper30-ai") + "-profile.txt";
	}

	@Override
	public void setup(BenchClientContext context) {
		var server = context.minecraft().getSingleplayerServer();
		if (server == null) {
			throw new IllegalStateException("清道夫30基准需要集成服务端");
		}
		var window = context.minecraft().getWindow().handle();
		GLFW.glfwMaximizeWindow(window);
		BenchWindow.reset();
		BenchWindow.ensure(window);
		originalPose = context.automation().pose();
		int halfX = (columns - 1) * GRID_SPACING / 2;
		int halfZ = (rows - 1) * GRID_SPACING / 2;
		gridOrigin = context.player().blockPosition().offset(-halfX, 0, -halfZ);
		double centerX = gridOrigin.getX() + halfX + 0.5;
		double centerZ = gridOrigin.getZ() + halfZ + 0.5;
		groundY = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(int) Math.floor(centerX), (int) Math.floor(centerZ));
		pose = context.automation().holdPose(BenchClientPose.lookingAt(centerX,
				groundY + LobecorpBenchConfig.CAMERA_HEIGHT, gridOrigin.getZ() - LobecorpBenchConfig.CAMERA_DISTANCE,
				centerX, groundY + 1, centerZ));
		BenchSpark.start(context);
		// 真实敌对实体在和平难度会被服务端移除；先切普通难度保证 AI 实际 tick（队列在生成任务之前）。
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "difficulty normal"));
		var specs = new ArrayList<BenchServerWorkload.Spec>(count);
		for (int index = 0; index < count; index++) {
			int x = gridOrigin.getX() + index % columns * GRID_SPACING;
			int z = gridOrigin.getZ() + index / columns * GRID_SPACING;
			int variant = index % SweeperVariant.values().length;
			var uuid = UUID.nameUUIDFromBytes(("lobecorp-bench:sweeper30:" + context.seed() + ":" + noAi + ":" + index)
					.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			specs.add(new BenchServerWorkload.Spec(uuid, x, z, noAi ? 0 : 32, level -> {
				var sweeper = new Sweeper(OrdealEntityTypes.SWEEPER.get(), level);
				sweeper.setVariant(SweeperVariant.values()[variant]);
				sweeper.setNoAi(noAi);
				if (noAi) {
					sweeper.setNoGravity(true);
				}
				sweeper.setInvulnerable(true);
				sweeper.setPersistenceRequired();
				sweeper.setYRot(180.0F);
				sweeper.setYHeadRot(180.0F);
				return sweeper;
			}));
		}
		workload.spawn(context, specs);
	}

	@Override
	public BenchClientStepResult stabilize(BenchClientContext context) {
		BenchWindow.ensure(context.minecraft().getWindow().handle());
		workload.audit(context);
		requireSynced(context);
		if (++stabilizeTicks < LobecorpBenchConfig.STABILIZE_TICKS
				|| !context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)) {
			return BenchClientStepResult.CONTINUE;
		}
		return BenchClientStepResult.COMPLETE;
	}

	@Override
	public BenchClientStepResult warmup(BenchClientContext context) {
		BenchWindow.ensure(context.minecraft().getWindow().handle());
		workload.audit(context);
		requireSynced(context);
		BenchSpark.capture(context, sparkArtifact);
		if (++warmupTicks < LobecorpBenchConfig.WARMUP_TICKS) {
			return BenchClientStepResult.CONTINUE;
		}

		if (screenshot == null) {
			screenshot = context.automation().captureScreenshot(noAi ? "sweeper30-noai-render" : "sweeper30-ai-render");
			return BenchClientStepResult.CONTINUE;
		}
		if (!screenshot.isDone() || !context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)) {
			return BenchClientStepResult.CONTINUE;
		}
		screenshotPath = screenshot.join();
		return ++captureRecoveryTicks >= LobecorpBenchConfig.STABLE_FRAMES
				? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
	}

	@Override
	public BenchClientStepResult measure(BenchClientContext context) {
		if (measureTicks == 0) {
			// 只统计MEASURE阶段的失焦/恢复；稳定和预热阶段的偶发切换不计入验收窗口。
			BenchWindow.reset();
			firstFrameCount = context.frames().sampleCount();
			if (PhotonFrameCapture.active()) {
				throw new IllegalStateException("已有GPU捕获正在进行，不能替换其他捕获");
			}
			environment = new HordeBenchmarkEnvironment();
			gpuToken = PhotonFrameCapture.start(noAi ? "sweeper30_noai_bench_gpu" : "sweeper30_ai_bench_gpu",
					GPU_SECONDS, () -> true);
		}
		BenchWindow.ensure(context.minecraft().getWindow().handle());
		workload.audit(context);
		requireSynced(context);
		BenchSpark.capture(context, sparkArtifact);
		context.metrics().record(LOADED_ENTITIES, workload.synced(context));
		context.metrics().record(SERVER_ENTITIES, workload.serverPresent());
		if (!measureComplete) {
			measureTicks++;
			if (measureTicks >= LobecorpBenchConfig.sweeper30MeasureTicks()) {
				measureComplete = true;
				measuredFrames = context.frames().sampleCount() - firstFrameCount;
				context.metrics().record(FRAME_SAMPLES, measuredFrames);
				BenchSpark.stop();
			}
		}
		if (measureComplete) {
			if (!BenchSpark.saved()) {
				if (++sparkWaitTicks > LobecorpBenchConfig.sparkSaveTimeoutTicks()) {
					throw new AssertionError("Spark 未在停止后返回上传链接，不能结束客户端");
				}
				return BenchClientStepResult.CONTINUE;
			}
			return BenchClientStepResult.COMPLETE;
		}
		return BenchClientStepResult.CONTINUE;
	}

	@Override
	public void verify(BenchClientContext context) throws Exception {
		requireSynced(context);
		if (workload.serverPresent() != count || workload.synced(context) != count) {
			throw new AssertionError("清道夫30服务端/客户端实体数量不一致：expected=" + count
					+ ", serverPresent=" + workload.serverPresent() + ", clientSynced=" + workload.synced(context)
					+ ", " + workload.report(context).issues());
		}
		if (!measureComplete || measureTicks < LobecorpBenchConfig.sweeper30MeasureTicks() || measuredFrames <= 0) {
			throw new AssertionError("清道夫30基准没有完整测量帧样本");
		}
		if (screenshotPath == null || !Files.isRegularFile(screenshotPath) || Files.size(screenshotPath) == 0) {
			throw new AssertionError("清道夫30基准截图缺失");
		}
		var capture = PhotonFrameCapture.snapshot();
		if (gpuToken == null || !capture.has("capture_id") || capture.get("capture_id").isJsonNull()
				|| !gpuToken.toString().equals(capture.get("capture_id").getAsString())
				|| !"DURATION".equals(capture.get("state").getAsString()) || capture.get("saved_path").isJsonNull()) {
			throw new AssertionError("清道夫30基准GPU捕获未完成或不属于本场景");
		}
		var window = context.minecraft().getWindow().handle();
		if (!BenchWindow.focused(window) || BenchWindow.iconified(window) || BenchWindow.losses() > 0) {
			throw new AssertionError("基准窗口在MEASURE期间失焦或最小化：focused=" + BenchWindow.focused(window)
					+ ", iconified=" + BenchWindow.iconified(window) + ", losses=" + BenchWindow.losses()
					+ ", restores=" + BenchWindow.restores());
		}
		BenchSpark.requireSaved();
		var gpu = context.resultDirectory().resolve("artifacts/gpu/" + (noAi ? "sweeper30-noai" : "sweeper30-ai") + "-gpu.json");
		Files.createDirectories(gpu.getParent());
		Files.copy(Path.of(capture.get("saved_path").getAsString()), gpu, StandardCopyOption.REPLACE_EXISTING);
		context.artifacts().register(gpu, "application/json");
		context.artifacts().register(screenshotPath, "image/png");
	}

	@Override
	public void teardown(BenchClientContext context) throws Exception {
		Throwable failure = null;
		failure = cleanup(failure, () -> saveEnvironment(context));
		if (gpuToken != null) {
			var token = gpuToken;
			gpuToken = null;
			failure = cleanup(failure, () -> PhotonFrameCapture.cancel(token));
		}
		failure = cleanup(failure, () -> workload.discard(context));
		failure = cleanup(failure, BenchSpark::stop);
		if (screenshot != null && !screenshot.isDone()) {
			failure = cleanup(failure, () -> screenshot.cancel(false));
		}
		if (pose != null) {
			failure = cleanup(failure, pose::close);
			pose = null;
		}
		if (originalPose != null) {
			failure = cleanup(failure, () -> context.automation().setPose(originalPose));
			originalPose = null;
		}
		if (failure instanceof Exception exception) {
			throw exception;
		}
		if (failure instanceof Error error) {
			throw error;
		}
		if (failure != null) {
			throw new IllegalStateException("清道夫30基准清理失败", failure);
		}
	}

	private void requireSynced(BenchClientContext context) {
		workload.await(context, count, LobecorpBenchConfig.entitySyncTimeoutTicks(), "清道夫30");
	}

	private void saveEnvironment(BenchClientContext context) {
		if (environment == null) {
			return;
		}
		environment.close();
		var value = environment.snapshot();
		value.addProperty("scenario", id);
		value.addProperty("server_side_spawn", true);
		value.addProperty("no_ai", noAi);
		value.addProperty("entity_count", count);
		value.addProperty("client_synced_entities", workload.synced(context));
		var workloadReport = workload.report(context);
		value.addProperty("server_present_entities", workloadReport.serverPresent());
		value.addProperty("planned_entities", workloadReport.planned());
		value.addProperty("respawn_count", workloadReport.respawns());
		value.addProperty("spawn_failure_count", workloadReport.spawnFailures());
		value.addProperty("server_difficulty", workloadReport.difficulty());
		value.addProperty("previous_difficulty", workloadReport.previousDifficulty());
		value.addProperty("leash_pull_count", workloadReport.leashed());
		value.addProperty("client_missing_recreate_count", workloadReport.recreated());
		value.addProperty("gamerule_spawn_mobs_disabled", workloadReport.gameruleDisabled());
		value.addProperty("gamerule_previous_spawn_mobs", workloadReport.previousSpawnMobs());
		value.addProperty("gamerule_previous_spawn_monsters", workloadReport.previousSpawnMonsters());
		value.add("workload_issues", new Gson().toJsonTree(workloadReport.issues()));
		value.addProperty("grid_columns", columns);
		value.addProperty("grid_rows", rows);
		value.addProperty("grid_spacing", GRID_SPACING);
		value.addProperty("spawn_ground_y", groundY);
		value.addProperty("measure_ticks", LobecorpBenchConfig.sweeper30MeasureTicks());
		value.addProperty("gpu_seconds", GPU_SECONDS);
		value.addProperty("entity_sync_timeout_ticks", LobecorpBenchConfig.entitySyncTimeoutTicks());
		value.addProperty("spark_save_timeout_ticks", LobecorpBenchConfig.sparkSaveTimeoutTicks());
		value.addProperty("spark_artifact", "artifacts/spark/" + sparkArtifact);
		var window = context.minecraft().getWindow().handle();
		value.addProperty("window_focused_at_end", BenchWindow.focused(window));
		value.addProperty("window_iconified_at_end", BenchWindow.iconified(window));
		value.addProperty("focus_losses", BenchWindow.losses());
		value.addProperty("focus_restores", BenchWindow.restores());
		var workloadJson = new JsonArray();
		for (var uuid : workload.uuids()) {
			var entity = context.level().getEntity(uuid);
			var entry = new JsonObject();
			entry.addProperty("uuid", uuid.toString());
			entry.addProperty("client_present", entity != null);
			if (entity != null) {
				entry.addProperty("type", entity.getType().toString());
				entry.addProperty("x", entity.getX());
				entry.addProperty("y", entity.getY());
				entry.addProperty("z", entity.getZ());
				if (entity instanceof Sweeper sweeper) {
					entry.addProperty("variant", sweeper.getVariant().name());
					entry.addProperty("no_ai", sweeper.isNoAi());
				}
			}
			workloadJson.add(entry);
		}
		value.add("workload", workloadJson);
		var output = context.resultDirectory()
				.resolve("artifacts/environment/" + (noAi ? "sweeper30-noai" : "sweeper30-ai") + "-environment.json");
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, new Gson().toJson(value));
			context.artifacts().register(output, "application/json");
			if (!value.get("cleanup_complete").getAsBoolean()) {
				throw new IllegalStateException("清道夫30环境探针清理未完成，详见环境制品");
			}
		} catch (java.io.IOException exception) {
			throw new IllegalStateException("无法保存清道夫30基准环境", exception);
		} finally {
			environment = null;
		}
	}

	private static Throwable cleanup(Throwable previous, Runnable action) {
		try {
			action.run();
		} catch (Throwable failure) {
			if (previous == null) {
				return failure;
			}
			if (previous != failure) {
				previous.addSuppressed(failure);
			}
		}
		return previous;
	}
}
