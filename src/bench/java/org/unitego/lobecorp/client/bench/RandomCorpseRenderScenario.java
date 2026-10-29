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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.world.entity.EntityCorpse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/// 100 具随机生物尸体：服务端生成真实 EntityCorpse（owner 为敌对/中立/被动混合的真实 EntityType），
/// 客户端等待同步后测量；未知 owner 类型走原渲染回退路径，因此该场景同时覆盖冻结批渲染和回退渲染。
public class RandomCorpseRenderScenario implements BenchClientScenario {
	private static final int GRID_SPACING = 3;
	private static final double GPU_SECONDS = 28;
	private static final BenchMetricDescriptor LOADED_CORPSES = new BenchMetricDescriptor(
			"lobecorp.corpse_random.loaded_corpses", "count", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor SERVER_CORPSES = new BenchMetricDescriptor(
			"lobecorp.corpse_random.server_corpses", "count", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor FRAME_SAMPLES = new BenchMetricDescriptor(
			"lobecorp.corpse_random.frame_samples", "count", MetricDirection.NEUTRAL);
	private static final EntityType<?>[] HOSTILE_OWNERS = {
			EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
			EntityType.HUSK, EntityType.STRAY, EntityType.DROWNED, EntityType.WITCH
	};
	private static final EntityType<?>[] NEUTRAL_OWNERS = {
			EntityType.ENDERMAN, EntityType.WOLF, EntityType.POLAR_BEAR,
			EntityType.IRON_GOLEM, EntityType.BEE, EntityType.GOAT
	};
	private static final EntityType<?>[] PASSIVE_OWNERS = {
			EntityType.COW, EntityType.PIG, EntityType.SHEEP,
			EntityType.CHICKEN, EntityType.RABBIT, EntityType.HORSE
	};

	private final int count = LobecorpBenchConfig.corpseRandomCount();
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

	@Override
	public void setup(BenchClientContext context) {
		var server = context.minecraft().getSingleplayerServer();
		if (server == null) {
			throw new IllegalStateException("随机尸体100基准需要集成服务端");
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
		pose = context.automation().holdPose(BenchClientPose.lookingAt(centerX, groundY + columns + 8,
				gridOrigin.getZ() - columns - 12, centerX, groundY + 1, centerZ));
		BenchSpark.start(context);
		var specs = new ArrayList<BenchServerWorkload.Spec>(count);
		for (int index = 0; index < count; index++) {
			int x = gridOrigin.getX() + index % columns * GRID_SPACING;
			int z = gridOrigin.getZ() + index / columns * GRID_SPACING;
			int ownerIndex = index;
			var uuid = UUID.nameUUIDFromBytes(("lobecorp-bench:corpse-random100:" + context.seed() + ":" + index)
					.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			specs.add(new BenchServerWorkload.Spec(uuid, x, z, 0, level -> {
				var type = ownerType(ownerIndex);
				var owner = type.create(level, EntitySpawnReason.COMMAND);
				if (!(owner instanceof LivingEntity living)) {
					throw new IllegalStateException("随机尸体 owner 创建失败：" + type);
				}
				// owner 只作为模板写进尸体 NBT；位置/朝向由 Benchmark 服务端按锚点统一设置到尸体上。
				living.setYRot((float) (new Random(ownerIndex * 1_000_003L + 17L).nextDouble() * 360.0));
				var corpse = EntityCorpse.createCorpse(living);
				corpse.setNoGravity(true);
				corpse.setInvulnerable(true);
				return corpse;
			}));
		}
		workload.spawn(context, specs);
	}

	/// 按 敌对/中立/被动 轮转后在该类内随机，保证 100 具尸体三类都有且顺序可重复。
	private EntityType<?> ownerType(int index) {
		var random = new Random(index * 1_000_003L + 17L);
		return switch (index % 3) {
			case 0 -> HOSTILE_OWNERS[random.nextInt(HOSTILE_OWNERS.length)];
			case 1 -> NEUTRAL_OWNERS[random.nextInt(NEUTRAL_OWNERS.length)];
			default -> PASSIVE_OWNERS[random.nextInt(PASSIVE_OWNERS.length)];
		};
	}

	private static String category(EntityType<?> type) {
		for (var candidate : HOSTILE_OWNERS) {
			if (candidate == type) {
				return "hostile";
			}
		}
		for (var candidate : NEUTRAL_OWNERS) {
			if (candidate == type) {
				return "neutral";
			}
		}
		for (var candidate : PASSIVE_OWNERS) {
			if (candidate == type) {
				return "passive";
			}
		}
		return "unknown";
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
		BenchSpark.capture(context, "corpse-random100-profile.txt");
		if (++warmupTicks < LobecorpBenchConfig.WARMUP_TICKS) {
			return BenchClientStepResult.CONTINUE;
		}
		if (screenshot == null) {
			screenshot = context.automation().captureScreenshot("corpse-random100-render");
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
			BenchWindow.reset();
			firstFrameCount = context.frames().sampleCount();
			if (PhotonFrameCapture.active()) {
				throw new IllegalStateException("已有GPU捕获正在进行，不能替换其他捕获");
			}
			environment = new HordeBenchmarkEnvironment();
			gpuToken = PhotonFrameCapture.start("corpse_random100_bench_gpu", GPU_SECONDS, () -> true);
		}
		BenchWindow.ensure(context.minecraft().getWindow().handle());
		workload.audit(context);
		requireSynced(context);
		BenchSpark.capture(context, "corpse-random100-profile.txt");
		context.metrics().record(LOADED_CORPSES, workload.synced(context));
		context.metrics().record(SERVER_CORPSES, workload.serverPresent());
		if (!measureComplete) {
			measureTicks++;
			if (measureTicks >= LobecorpBenchConfig.corpseRandomMeasureTicks()) {
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
			throw new AssertionError("随机尸体100服务端/客户端数量不一致：expected=" + count
					+ ", serverPresent=" + workload.serverPresent() + ", clientSynced=" + workload.synced(context)
					+ ", " + workload.report(context).issues());
		}
		if (!measureComplete || measureTicks < LobecorpBenchConfig.corpseRandomMeasureTicks() || measuredFrames <= 0) {
			throw new AssertionError("随机尸体100基准没有完整测量帧样本");
		}
		if (screenshotPath == null || !Files.isRegularFile(screenshotPath) || Files.size(screenshotPath) == 0) {
			throw new AssertionError("随机尸体100基准截图缺失");
		}
		var capture = PhotonFrameCapture.snapshot();
		if (gpuToken == null || !capture.has("capture_id") || capture.get("capture_id").isJsonNull()
				|| !gpuToken.toString().equals(capture.get("capture_id").getAsString())
				|| !"DURATION".equals(capture.get("state").getAsString()) || capture.get("saved_path").isJsonNull()) {
			throw new AssertionError("随机尸体100基准GPU捕获未完成或不属于本场景");
		}
		// 焦点不再作为验收门禁：后台运行或编辑器抢占焦点都会让失焦成为常态，
		// 只把次数写进报告快照（focus_losses/focus_restores）供分析，不判定整轮失败。
		BenchSpark.requireSaved();
		var gpu = context.resultDirectory().resolve("artifacts/gpu/corpse-random100-gpu.json");
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
			throw new IllegalStateException("随机尸体100基准清理失败", failure);
		}
	}

	private void requireSynced(BenchClientContext context) {
		workload.await(context, count, LobecorpBenchConfig.entitySyncTimeoutTicks(), "随机尸体100");
	}

	private void saveEnvironment(BenchClientContext context) {
		if (environment == null) {
			return;
		}
		environment.close();
		var value = environment.snapshot();
		value.addProperty("scenario", LobecorpBenchConfig.CORPSE_RANDOM100_SCENARIO);
		value.addProperty("server_side_spawn", true);
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
		value.addProperty("measure_ticks", LobecorpBenchConfig.corpseRandomMeasureTicks());
		value.addProperty("gpu_seconds", GPU_SECONDS);
		value.addProperty("entity_sync_timeout_ticks", LobecorpBenchConfig.entitySyncTimeoutTicks());
		value.addProperty("spark_save_timeout_ticks", LobecorpBenchConfig.sparkSaveTimeoutTicks());
		value.addProperty("spark_artifact", "artifacts/spark/corpse-random100-profile.txt");
		value.addProperty("render_note",
				"Only Sweeper owners use the frozen corpse mesh; other owner types render through the original renderer fallback path");
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
			if (entity instanceof EntityCorpse<?> corpse) {
				var owner = corpse.getOwnerEntity();
				entry.addProperty("owner_type", owner == null ? null : owner.getType().toString());
				entry.addProperty("owner_category", owner == null ? "unknown" : category(owner.getType()));
				entry.addProperty("x", corpse.getX());
				entry.addProperty("y", corpse.getY());
				entry.addProperty("z", corpse.getZ());
			}
			workloadJson.add(entry);
		}
		value.add("workload", workloadJson);
		var output = context.resultDirectory().resolve("artifacts/environment/corpse-random100-environment.json");
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, new Gson().toJson(value));
			context.artifacts().register(output, "application/json");
			if (!value.get("cleanup_complete").getAsBoolean()) {
				throw new IllegalStateException("随机尸体100环境探针清理未完成，详见环境制品");
			}
		} catch (java.io.IOException exception) {
			throw new IllegalStateException("无法保存随机尸体100基准环境", exception);
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
