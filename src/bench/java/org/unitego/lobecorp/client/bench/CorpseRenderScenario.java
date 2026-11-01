package org.unitego.lobecorp.client.bench;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;

import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.MetricDirection;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientPose;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import com.zhongbai233.bench.api.neoforge.client.BenchPoseHold;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.client.entity.corpse.CorpseBatchRenderer;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.entity.ordeal.indigo.SweeperVariant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/// 本地真实尸体的固定负载；不产生事件来源，不代表服务器跟踪距离或同步成本。
public class CorpseRenderScenario implements BenchClientScenario {
	private static final int GRID_SPACING = 3;
	private static final double GPU_SECONDS = 28;
	private static final BenchMetricDescriptor COUNT = new BenchMetricDescriptor(
			"lobecorp.corpse_render.entities", "count", MetricDirection.NEUTRAL);
	private final List<EntityCorpse<?>> corpses = new ArrayList<>();
	private final int count = LobecorpBenchConfig.corpseRenderCount();
	private final int columns = (int) Math.ceil(Math.sqrt(count));
	private BlockPos origin;
	private BenchClientPose originalPose;
	private BenchPoseHold pose;
	private CompletableFuture<Path> screenshot;
	private Path screenshotPath;
	private HordeBenchmarkEnvironment environment;
	private UUID gpuToken;
	private int stabilize;
	private int warmup;
	private int recovery;
	private int measured;
	private long firstFrame;
	private long failureBaseline;
	private int sparkWaitTicks;
	private boolean measureComplete;
	private static final BenchMetricDescriptor STATIC_DRAWN = new BenchMetricDescriptor(
			"lobecorp.corpse_render.static_drawn", "count", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor FALLBACK = new BenchMetricDescriptor(
			"lobecorp.corpse_render.fallback", "count", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor DRAWS = new BenchMetricDescriptor(
			"lobecorp.corpse_render.draws", "count", MetricDirection.NEUTRAL);

	@Override
	public void setup(BenchClientContext context) {
		failureBaseline = CorpseBatchRenderer.failures();
		originalPose = context.automation().pose();
		origin = context.player().blockPosition().offset(-columns * GRID_SPACING / 2, 0,
				-columns * GRID_SPACING / 2);
		BenchWindow.reset();
		BenchWindow.ensure(context.minecraft().getWindow().handle());
		BenchSpark.start(context);
	}

	private BlockPos position(int index) {
		return origin.offset(index % columns * GRID_SPACING, 0, index / columns * GRID_SPACING);
	}

	@Override
	public BenchClientStepResult stabilize(BenchClientContext context) {
		BenchWindow.ensure(context.minecraft().getWindow().handle());
		BenchSpark.capture(context, "corpse-profile.txt");
		if (corpses.isEmpty()) {
			for (int index = 0; index < count; index++) {
				var at = position(index);
				if (!context.level().getChunkSource().hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
					return BenchClientStepResult.CONTINUE;
				}
			}
			for (int index = 0; index < count; index++) {
				int id = LobecorpBenchConfig.CLIENT_ENTITY_ID_BASE - 100000 - index;
				if (context.level().getEntity(id) != null) {
					throw new IllegalStateException("尸体基准实体编号已被占用");
				}
				var at = position(index);
				var owner = new Sweeper(OrdealEntityTypes.SWEEPER.get(), context.level());
				owner.setVariant(SweeperVariant.values()[index % SweeperVariant.values().length]);
				owner.setNoAi(true);
				owner.setNoGravity(true);
				owner.setPos(at.getX() + 0.5, context.level().getHeight(
						Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ() + 0.5);
				owner.setYRot(180);
				var corpse = EntityCorpse.createCorpse(owner);
				corpses.add(corpse);
				corpse.setId(id);
				corpse.setNoGravity(true);
				corpse.setInvulnerable(true);
				context.level().addEntity(corpse);
			}
			double centerX = origin.getX() + (columns - 1) * GRID_SPACING / 2.0;
			double centerZ = origin.getZ() + (columns - 1) * GRID_SPACING / 2.0;
			pose = context.automation().holdPose(BenchClientPose.lookingAt(centerX,
					corpses.getFirst().getY() + columns + 8, origin.getZ() - columns - 12,
					centerX, corpses.getFirst().getY() + 1, centerZ));
		}
		requireOwned(context);
		return ++stabilize >= LobecorpBenchConfig.STABILIZE_TICKS
				&& context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)
				? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
	}

	private void requireOwned(BenchClientContext context) {
		if (corpses.size() != count || corpses.stream().anyMatch(corpse -> corpse.isRemoved()
				|| corpse.getOwnerEntity() == null || context.level().getEntity(corpse.getId()) != corpse)) {
			throw new AssertionError("尸体基准负载或实体归属改变");
		}
	}

	@Override
	public BenchClientStepResult warmup(BenchClientContext context) {
		BenchWindow.ensure(context.minecraft().getWindow().handle());
		BenchSpark.capture(context, "corpse-profile.txt");
		requireOwned(context);
		if (++warmup < LobecorpBenchConfig.WARMUP_TICKS) {
			return BenchClientStepResult.CONTINUE;
		}
		if (screenshot == null) {
			screenshot = context.automation().captureScreenshot("corpse-render");
			return BenchClientStepResult.CONTINUE;
		}
		if (!screenshot.isDone()) {
			return BenchClientStepResult.CONTINUE;
		}
		screenshotPath = screenshot.join();
		return ++recovery >= LobecorpBenchConfig.STABLE_FRAMES
				&& context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)
				? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
	}

	@Override
	public BenchClientStepResult measure(BenchClientContext context) {
		BenchWindow.ensure(context.minecraft().getWindow().handle());
		BenchSpark.capture(context, "corpse-profile.txt");
		requireOwned(context);
		if (measured == 0) {
			// 只统计MEASURE阶段的失焦/恢复，预热阶段的偶发切换不计入验收窗口。
			BenchWindow.reset();
			if (PhotonFrameCapture.active()) {
				throw new IllegalStateException("已有GPU捕获，不能替换其他捕获");
			}
			firstFrame = context.frames().sampleCount();
			environment = new HordeBenchmarkEnvironment();
			gpuToken = PhotonFrameCapture.start("corpse_bench_gpu", GPU_SECONDS, () -> true);
		}
		context.metrics().record(COUNT, count);
		var stats = CorpseBatchRenderer.snapshot();
		context.metrics().record(STATIC_DRAWN, stats.get("static_drawn").getAsLong());
		context.metrics().record(FALLBACK, stats.get("fallback").getAsLong());
		context.metrics().record(DRAWS, stats.get("draws").getAsLong());
		if (!measureComplete) {
			measured++;
			if (measured >= LobecorpBenchConfig.CORPSE_MEASURE_TICKS) {
				measureComplete = true;
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
		requireOwned(context);
		if (CorpseBatchRenderer.failures() != failureBaseline) {
			throw new AssertionError("尸体冻结批渲染曾失败，不能仅按实体数量判定通过");
		}
		if (!measureComplete || measured < LobecorpBenchConfig.CORPSE_MEASURE_TICKS
				|| context.frames().sampleCount() <= firstFrame || screenshotPath == null
				|| !Files.isRegularFile(screenshotPath)) {
			throw new AssertionError("尸体基准没有完整帧样本或截图");
		}
		var capture = PhotonFrameCapture.snapshot();
		if (gpuToken == null || !capture.has("capture_id")
				|| !gpuToken.toString().equals(capture.get("capture_id").getAsString())
				|| !"DURATION".equals(capture.get("state").getAsString()) || capture.get("saved_path").isJsonNull()) {
			throw new AssertionError("尸体GPU捕获未完成或不属于本场景");
		}
		var output = context.resultDirectory().resolve("artifacts/gpu/corpse-gpu.json");
		Files.createDirectories(output.getParent());
		Files.copy(Path.of(capture.get("saved_path").getAsString()), output, StandardCopyOption.REPLACE_EXISTING);
		context.artifacts().register(output, "application/json");
		var window = context.minecraft().getWindow().handle();
		// 焦点不再作为验收门禁：后台运行或编辑器抢占焦点都会让失焦成为常态，
		// 只把次数写进报告快照（focus_losses/focus_restores）供分析，不判定整轮失败。
		BenchSpark.requireSaved();
		context.artifacts().register(screenshotPath, "image/png");
	}

	@Override
	public void teardown(BenchClientContext context) throws Exception {
		Throwable failure = null;
		failure = cleanup(failure, () -> saveEnvironment(context));
		failure = cleanup(failure, BenchSpark::stop);
		if (gpuToken != null) {
			failure = cleanup(failure, () -> PhotonFrameCapture.cancel(gpuToken));
		}
		for (var corpse : corpses) {
			if (context.level().getEntity(corpse.getId()) == corpse) {
				failure = cleanup(failure, corpse::discard);
			}
		}
		corpses.clear();
		if (screenshot != null && !screenshot.isDone()) {
			failure = cleanup(failure, () -> screenshot.cancel(false));
		}
		if (pose != null) {
			failure = cleanup(failure, pose::close);
		}
		if (originalPose != null) {
			failure = cleanup(failure, () -> context.automation().setPose(originalPose));
		}
		if (failure instanceof Exception exception) {
			throw exception;
		}
		if (failure instanceof Error error) {
			throw error;
		}
		if (failure != null) {
			throw new IllegalStateException("尸体基准清理失败", failure);
		}
	}

	private void saveEnvironment(BenchClientContext context) {
		if (environment == null) {
			return;
		}
		environment.close();
		var snapshot = environment.snapshot();
		snapshot.addProperty("entities", count);
		snapshot.add("corpse_renderer", CorpseBatchRenderer.snapshot());
		snapshot.addProperty("corpse_batch_enabled", HordeVisualConfig.CORPSE_BATCH.get());
		snapshot.addProperty("measure_ticks", LobecorpBenchConfig.CORPSE_MEASURE_TICKS);
		snapshot.addProperty("gpu_seconds", GPU_SECONDS);
		snapshot.addProperty("scope", "Client-only corpses without event source; GPU covers 28 seconds of the 600-tick MEASURE");
		snapshot.addProperty("spawn_side", "client_local");
		snapshot.addProperty("spark_artifact", "artifacts/spark/corpse-profile.txt");
		snapshot.addProperty("measure_complete", measureComplete);
		var window = context.minecraft().getWindow().handle();
		snapshot.addProperty("window_focused_at_end", BenchWindow.focused(window));
		snapshot.addProperty("window_iconified_at_end", BenchWindow.iconified(window));
		snapshot.addProperty("focus_losses", BenchWindow.losses());
		snapshot.addProperty("focus_restores", BenchWindow.restores());
		var workload = new JsonArray();
		for (var corpse : corpses) {
			var entity = new JsonObject();
			entity.addProperty("id", corpse.getId());
			entity.addProperty("uuid", corpse.getUUID().toString());
			entity.addProperty("owner", corpse.getOwnerEntity() == null ? null : corpse.getOwnerEntity().getType().toString());
			entity.addProperty("x", corpse.getX());
			entity.addProperty("y", corpse.getY());
			entity.addProperty("z", corpse.getZ());
			workload.add(entity);
		}
		snapshot.add("workload", workload);
		var output = context.resultDirectory().resolve("artifacts/gpu/corpse-environment.json");
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, new Gson().toJson(snapshot));
			context.artifacts().register(output, "application/json");
			if (!snapshot.get("cleanup_complete").getAsBoolean()) {
				throw new IllegalStateException("尸体环境探针清理未完成，具体归属已存制品");
			}
		} catch (IOException failure) {
			throw new IllegalStateException("尸体环境制品保存失败", failure);
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
