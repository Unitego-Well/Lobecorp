package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.unitego.lobecorp.util.photon.performance.PhotonBenchWorkloadUtil;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.fx.FX;
import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.BenchPhase;
import com.zhongbai233.bench.api.MetricDirection;
import com.zhongbai233.bench.api.neoforge.client.BenchCaptureOptions;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientPose;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import com.zhongbai233.bench.api.neoforge.client.BenchPoseHold;
import net.minecraft.nbt.StringTag;
import com.mojang.blaze3d.vertex.BufferBuilder;
import org.unitego.lobecorp.client.photon.model.IPhotonPackedVertexSink;
import org.unitego.lobecorp.util.photon.model.PhotonPackedVertexUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import org.unitego.lobecorp.client.animation.LcClientAnimations;
import org.unitego.lobecorp.mixin.PhotonBenchMixinPlugin;
import org.unitego.lobecorp.client.photon.performance.QueenBeamPerformanceFixture;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredBeamAnimation;
import org.unitego.lobecorp.registry.animation.TheQueenOfHatredAnimationDefinitions;

import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import net.neoforged.fml.ModList;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffect;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.Lobecorp;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/// 固定几何和相机的诊断负载，静止与动画只切换同一资产的片段；不替代真实光束验收。
@SuppressWarnings("resource") // Minecraft 与玩家均由基准上下文借用，场景不关闭游戏宿主。
public class PhotonModelRenderScenario implements BenchClientScenario {
	private static final int GRID = 16;
	/// 每个进程独立选择预期光影状态，旧入口缺省仍要求开启。
	private static final String SHADER_PROPERTY = "lobecorp.photon.bench.shader";
	private static final int WARMUP = 100;
	private static final int MEASURE = 300;
	/// 窗口最大化请求允许跨 tick 生效，前置等待最多 10 秒。
	private static final long WINDOW_MAXIMIZE_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(10);
	private static final long WORLD_SETTLE_NS = TimeUnit.SECONDS.toNanos(10);
	private final PhotonBenchWorkloadUtil.Layout workload;
	private static final BenchMetricDescriptor MODELS = new BenchMetricDescriptor(
			"lobecorp.photon.loaded_models", "count", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor STARTUP = new BenchMetricDescriptor(
			"lobecorp.photon.create_emit", "ns", MetricDirection.LOWER_IS_BETTER);
	private final String label;
	private final int count;
	private final boolean animated;
	private final boolean cold;
	private final boolean singleCast;
	private final EvidenceMode evidenceMode;
	private long primaryCaptureEndFrame = -1;

	public enum EvidenceMode {
		PERFORMANCE, VISUAL
	}
	private final int staticCount;
	private final int animatedCount;
	private final int meshGrid;
	private final int beamCount;
	private final JsonArray beamMarks = new JsonArray();
	private final Map<UUID, Double> beamPhases = new HashMap<>();
	private final Map<UUID, Integer> beamOwners = new HashMap<>();
	private CompletableFuture<QueenBeamPerformanceFixture> beamFixture;
	private MinecraftServer fixtureServer;
	private CompletableFuture<Void> worldReady;
	private CompletableFuture<List<QueenBeamPerformanceFixture.CastResult>> pendingCast;
	private PhotonBenchCapture capture;
	private PhotonBenchSpark spark;
	private PhotonBenchDiagnostics deserializeDiagnostics;
	private Runnable prepareFixture;
	private boolean worldSetupQueued;
	private boolean sparkStopping;
	private boolean sparkArchived;
	private Throwable deferredFailure;
	private long failureCleanupStartedNs;
	private boolean failureCaptureClosed;
	private long sparkRequestedNs;
	private long sparkReadyNs;
	private long worldAvailableNs;
	private long worldSettledNs;
	private long setupObservedNs;
	private long windowMaximizeWaitStartedNs;
	private long fixtureSetupTriggeredNs;
	private CompletableFuture<Path> beamScreenshot;
	private long verificationStarted;
	private long verificationOutputFrame = -1;
	private boolean verificationCast;
	private boolean originalFlying;
	private int measuredCasts;
	private final List<PhotonGeoEffect> effects = new ArrayList<>();
	private final List<PhotonModelOutput> models = new ArrayList<>();
	private BenchPoseHold poseHold;
	private BenchClientPose originalPose;
	private CompletableFuture<Path> screenshot;
	private Vector3f origin;
	private String assetJson;
	private int screenshotRecovery;
	private int ticks;
	private long createNanos;
	private long firstFrame;
	private boolean created;
	private BenchClientPose expectedPose;
	private int poseRetries;
	private JsonObject worldCensus;
	private JsonObject fixedAnchor;
	private JsonObject beamScreenshotRequest;
	private int orphanedFixtures;
	private final PhotonBenchCameraAudit cameraAudit = new PhotonBenchCameraAudit();
	private final PhotonBenchFocusAudit focusAudit = new PhotonBenchFocusAudit();

	public PhotonModelRenderScenario(String label, int count, boolean animated, boolean cold) {
		this(label, animated ? 0 : count, animated ? count : 0, GRID, 0, cold);
	}

	public PhotonModelRenderScenario(String label, int staticCount, int animatedCount, int meshGrid, int beamCount, boolean cold) {
		this(label, staticCount, animatedCount, meshGrid, beamCount, cold, false);
	}

	public PhotonModelRenderScenario(String label, int staticCount, int animatedCount, int meshGrid, int beamCount, boolean cold,
	                                 boolean singleCast) {
		this(label, staticCount, animatedCount, meshGrid, beamCount, cold, singleCast, EvidenceMode.VISUAL);
	}

	public PhotonModelRenderScenario(String label, int staticCount, int animatedCount, int meshGrid, int beamCount, boolean cold,
	                                 boolean singleCast, EvidenceMode evidenceMode) {
		this.evidenceMode = Objects.requireNonNull(evidenceMode, "evidenceMode");
		if (evidenceMode == EvidenceMode.PERFORMANCE && (!singleCast || beamCount != 1 || staticCount != 0 || animatedCount != 0)) {
			throw new IllegalArgumentException("性能专用场景要求一次真实光束施放，不混入合成模型。");
		}

		workload = new PhotonBenchWorkloadUtil.Layout(staticCount, animatedCount, meshGrid, beamCount);

		this.label = label;
		this.staticCount = staticCount;
		this.animatedCount = animatedCount;
		this.count = staticCount + animatedCount;
		this.animated = animatedCount == count && count > 0;
		this.meshGrid = meshGrid;
		this.beamCount = beamCount;
		this.cold = cold;
		this.singleCast = singleCast;
	}

	@Override
	public void setup(BenchClientContext context) {
		setupObservedNs = System.nanoTime();
		if (evidenceMode == EvidenceMode.PERFORMANCE
				&& !LobecorpBenchConfig.PHOTON_FIRST_CAST_PERFORMANCE_SCENARIO.equals(System.getProperty("modBench.scenarios", ""))) {
			throw new IllegalStateException("性能专用场景必须使用精确 profile，以装载无截图桥。");
		}

		if (!ModList.get().isLoaded("sodium")) {
			throw new IllegalStateException("本轮模型基准要求加载 Sodium。");
		}
		if (singleCast && System.getProperty("modBench.resultDirectory", "").isBlank()) {
			throw new IllegalStateException("首次释放需要启动即生效的 BenchMod 原生鼠标隔离。");
		}

		if (singleCast && !PhotonBenchMixinPlugin.preservedWindowApplied()) {
			throw new IllegalStateException("首次释放窗口保留桥未实际加载，禁止按完整夹具运行。");
		}

		var window = context.minecraft().getWindow();
		GLFW.glfwMaximizeWindow(window.handle());
		GLFW.glfwFocusWindow(window.handle());
		focusAudit.audit(singleCast && beamCount > 0, "setup");
		originalPose = context.automation().pose();
		var player = context.player();
		originalFlying = player.getAbilities().flying;
		player.getAbilities().flying = true;
		player.setDeltaMovement(Vec3.ZERO);
		player.onUpdateAbilities();
		context.automation().stopMovement();
		capture = new PhotonBenchCapture(label, context.seed());
		if (!singleCast) {
			origin = new Vector3f((float) player.getX(), (float) player.getY(), (float) player.getZ());
		}
		assetJson = count == 0 ? "" : asset().toString();
		if (!singleCast) {
			expectedPose = BenchClientPose.lookingAt(
					player.getX(), player.getY() + (count > 16 ? 12 : 7), player.getZ() - (count > 16 ? 24 : 10),
					player.getX(), player.getY() + 2, player.getZ() + 5);
			poseHold = context.automation().holdPose(expectedPose);
		}
		boolean expectedShader = Boolean.parseBoolean(System.getProperty(SHADER_PROPERTY, "true"));
		if (IrisCompat.isUsingShaderPack() != expectedShader) {
			throw new IllegalStateException("实际光影状态与本轮预期不符：" + expectedShader);
		}

		if (Boolean.getBoolean(PhotonPackedVertexUtil.ENABLE_PROPERTY)
				!= IPhotonPackedVertexSink.class.isAssignableFrom(BufferBuilder.class)) {
			throw new IllegalStateException("整块提交开关与实际 Mixin 加载状态不符。");
		}

		var server = context.minecraft().getSingleplayerServer();
		if (server == null) {
			throw new IllegalStateException("Photon 隔离基准需要当前集成服务器。");
		}

		fixtureServer = server;
		worldReady = new CompletableFuture<>();
		if (beamCount > 0) {
			beamFixture = new CompletableFuture<>();
		}
		var playerId = player.getUUID();
		prepareFixture = () -> server.execute(() -> {
			try {
				QueenBeamPerformanceFixture.prepareBenchWorld(server);
				var benchLevel = server.overworld();
				if (singleCast) {
					var spawn = server.getRespawnData();
					var serverPlayer = server.getPlayerList().getPlayer(playerId);
					if (!spawn.dimension().equals(benchLevel.dimension()) || serverPlayer == null
							|| !serverPlayer.level().dimension().equals(benchLevel.dimension())) {
						throw new IllegalStateException("首次释放夹具需要玩家与世界出生点都位于主世界。");
					}

					var position = Vec3.atBottomCenterOf(spawn.pos());
					origin = new Vector3f((float) position.x, (float) position.y, (float) position.z);
					fixedAnchor = new JsonObject();
					fixedAnchor.addProperty("definition", "server_world_respawn_block_bottom_center");
					fixedAnchor.addProperty("dimension", spawn.dimension().identifier().toString());
					fixedAnchor.addProperty("spawn_block_x", spawn.pos().getX());
					fixedAnchor.addProperty("spawn_block_y", spawn.pos().getY());
					fixedAnchor.addProperty("spawn_block_z", spawn.pos().getZ());
					fixedAnchor.addProperty("origin_x", origin.x);
					fixedAnchor.addProperty("origin_y", origin.y);
					fixedAnchor.addProperty("origin_z", origin.z);
					fixedAnchor.addProperty("server_player_initial_x", serverPlayer.getX());
					fixedAnchor.addProperty("server_player_initial_y", serverPlayer.getY());
					fixedAnchor.addProperty("server_player_initial_z", serverPlayer.getZ());
					fixedAnchor.addProperty("server_player_alignment_required", false);
				}
				orphanedFixtures = QueenBeamPerformanceFixture.discardOrphanedFixtures(benchLevel);
				worldCensus = QueenBeamPerformanceFixture.census(benchLevel, new Vec3(origin.x, origin.y, origin.z), 128);
				if (beamFixture != null) {
					var beamOrigin = new Vec3(origin.x, origin.y, origin.z);
					beamFixture.complete(beamCount == 4
							? QueenBeamPerformanceFixture.createConverging(server, playerId, beamOrigin, beamCount)
							: QueenBeamPerformanceFixture.create(server, playerId, beamOrigin, beamCount));
				}
				worldReady.complete(null);
			} catch (RuntimeException failure) {
				worldReady.completeExceptionally(failure);
				if (beamFixture != null) {
					beamFixture.completeExceptionally(failure);
				}
			}
		});
		if (singleCast && evidenceMode != EvidenceMode.PERFORMANCE) {
			sparkRequestedNs = System.nanoTime();
			spark = PhotonBenchSpark.start(context.minecraft(), label);
		} else if (!singleCast) {
			worldSetupQueued = true;
			prepareFixture.run();
		}
	}

	@Override
	public BenchClientStepResult stabilize(BenchClientContext context) {
		return step(context, BenchPhase.STABILIZE);
	}

	private boolean awaitWorldSettle(BenchClientContext context) {
		long now = System.nanoTime();
		if (context.minecraft().level == null || context.minecraft().player == null
				|| context.minecraft().screen != null || context.minecraft().getOverlay() != null) {
			if (worldAvailableNs != 0) {
				Lobecorp.LOGGER.info("PHOTONSTAGE stage=world_settle_reset ns={} previousStartNs={}", now, worldAvailableNs);
				worldAvailableNs = 0;
			}
			return false;
		}
		if (worldAvailableNs == 0) {
			worldAvailableNs = now;
			Lobecorp.LOGGER.info("PHOTONSTAGE stage=world_available ns={} epochMs={} requiredNs={} setupNs={}",
					now, System.currentTimeMillis(), WORLD_SETTLE_NS, setupObservedNs);
		}
		if (now - worldAvailableNs < WORLD_SETTLE_NS) {
			return false;
		}
		worldSettledNs = now;
		deserializeDiagnostics = new PhotonBenchDiagnostics(label, "laser");
		deserializeDiagnostics.start();
		Lobecorp.LOGGER.info("PHOTONSTAGE stage=world_settled ns={} epochMs={} elapsedNs={} captureStarted=false fixtureQueued=false",
				now, System.currentTimeMillis(), now - worldAvailableNs);
		sparkRequestedNs = System.nanoTime();
		spark = PhotonBenchSpark.start(context.minecraft(), label);
		return true;
	}

	private BenchClientStepResult stabilizeStep(BenchClientContext context) {
		focusAudit.audit(singleCast && beamCount > 0, "stabilize");
		if (evidenceMode == EvidenceMode.PERFORMANCE && spark == null && !awaitWorldSettle(context)) {
			return BenchClientStepResult.CONTINUE;
		}
		if (spark != null && !worldSetupQueued) {
			if (!spark.poll()) {
				return BenchClientStepResult.CONTINUE;
			}

			var window = context.minecraft().getWindow();
			if (singleCast && GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_MAXIMIZED) != GLFW.GLFW_TRUE) {
				long now = System.nanoTime();
				if (windowMaximizeWaitStartedNs == 0) {
					windowMaximizeWaitStartedNs = now;
				}
				if (now - windowMaximizeWaitStartedNs >= WINDOW_MAXIMIZE_TIMEOUT_NS) {
					throw new IllegalStateException("首次释放窗口前置等待超过 10 秒：GLFW_MAXIMIZED 未读回 true；focused="
							+ GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_FOCUSED) + "，iconified="
							+ GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_ICONIFIED));
				}

				GLFW.glfwMaximizeWindow(window.handle());
				return BenchClientStepResult.CONTINUE;
			}

			sparkReadyNs = System.nanoTime();
			if (beamCount > 0) {
				capture.startPrewarm();
				focusAudit.audit(true, "setup_prewarm");
			}
			fixtureSetupTriggeredNs = System.nanoTime();
			Lobecorp.LOGGER.info("PHOTONSTAGE stage=fixture_queue ns={} epochMs={} worldSettledNs={} sparkReadyNs={}",
					fixtureSetupTriggeredNs, System.currentTimeMillis(), worldSettledNs, sparkReadyNs);
			worldSetupQueued = true;
			prepareFixture.run();
		}
		if (!worldReady.isDone()) {
			return BenchClientStepResult.CONTINUE;
		}
		worldReady.join();

		if (singleCast && expectedPose == null) {
			expectedPose = BenchClientPose.lookingAt(
					origin.x, origin.y + (count > 16 ? 12 : 7), origin.z - (count > 16 ? 24 : 10),
					origin.x, origin.y + 2, origin.z + 5);
			context.automation().setPose(expectedPose);
			poseHold = context.automation().holdPose(expectedPose);
		}

		if (!applyExpectedPose(context)) {
			return BenchClientStepResult.CONTINUE;
		}

		capture.prewarmPoseReady();

		if (++ticks < LobecorpBenchConfig.STABILIZE_TICKS
				|| !context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)) {
			return BenchClientStepResult.CONTINUE;
		}
		ticks = 0;
		if (!cold) {
			create(context);
		}
		return BenchClientStepResult.COMPLETE;
	}

	@Override
	public BenchClientStepResult warmup(BenchClientContext context) {
		return step(context, BenchPhase.WARMUP);
	}

	private BenchClientStepResult warmupStep(BenchClientContext context) {
		focusAudit.audit(singleCast && beamCount > 0, "warmup");
		if (++ticks < WARMUP) {
			return BenchClientStepResult.CONTINUE;
		}
		if (evidenceMode == EvidenceMode.PERFORMANCE) {
			capture.finishPrewarm(context);
			ticks = 0;
			return BenchClientStepResult.COMPLETE;
		}

		if (!cold && screenshot == null) {
			screenshot = context.automation().captureScreenshot(label);
			return BenchClientStepResult.CONTINUE;
		}
		if (screenshot != null && !screenshot.isDone()) {
			return BenchClientStepResult.CONTINUE;
		}
		if (screenshot != null) {
			screenshot.join();
			if (++screenshotRecovery < LobecorpBenchConfig.STABLE_FRAMES
					|| !context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)) {
				return BenchClientStepResult.CONTINUE;
			}
		}
		capture.finishPrewarm(context);
		ticks = 0;
		return BenchClientStepResult.COMPLETE;
	}

	/// 位姿门禁：显式应用请求位姿并核对；避免 holdPose 静默失效导致跨轮相机不一致。
	private boolean applyExpectedPose(BenchClientContext context) {
		if (expectedPose == null || PhotonBenchCameraAudit.poseMatches(expectedPose, context.automation().pose())) {
			return true;
		}

		context.automation().setPose(expectedPose);

		if (PhotonBenchCameraAudit.poseMatches(expectedPose, context.automation().pose())) {
			return true;
		}

		if (++poseRetries >= 60) {
			throw new IllegalStateException("基准相机位姿未能生效：" + context.automation().pose() + " 期望 " + expectedPose);
		}

		return false;
	}

	private void create(BenchClientContext context) {
		long started = System.nanoTime();
		for (int index = 0; index < count; index++) {
			var fx = new FX();
			var model = new PhotonModelOutput();
			model.config.setAssetSnapshot(StringTag.valueOf(assetJson));
			model.config.clip = index >= staticCount ? "turn" : "";
			model.config.useReferenceDimensions = false;
			fx.getFxData().objects().add(model);
			var effect = new PhotonGeoEffect(fx, context.level());
			effects.add(effect);
			var runtimeModel = (PhotonModelOutput) effect.runtime().objects.get(model.id());
			models.add(runtimeModel);
			var offset = workload.offset(index);
			float x = offset.x();
			float z = offset.z();
			effect.anchor(new Vector3f(origin.x + x, origin.y + 2, origin.z + 5 + z), new Quaternionf(), new Vector3f(2));
			effect.start();
		}
		createNanos = System.nanoTime() - started;
		created = true;
	}

	@Override
	public BenchClientStepResult measure(BenchClientContext context) {
		return step(context, BenchPhase.MEASURE);
	}

	private BenchClientStepResult measureStep(BenchClientContext context) {
		focusAudit.audit(true, sparkStopping ? "spark_finalize" : "measure");
		if (sparkStopping) {
			return finishSpark(context);
		}

		if (ticks == 0 && !applyExpectedPose(context)) {
			return BenchClientStepResult.CONTINUE;
		}

		cameraAudit.sample();

		if (ticks == 0) {
			firstFrame = context.frames().sampleCount();
			Lobecorp.LOGGER.info("PHOTONSTAGE stage=main_capture_request ns={} epochMs={} seconds={} worldSettledNs={}",
					System.nanoTime(), System.currentTimeMillis(), singleCast ? 20 : 90, worldSettledNs);
			if (!created) {
				create(context);
			}
			context.metrics().record(STARTUP, createNanos);
			capture.start(beamCount > 0 ? (singleCast ? 20 : 90) : 120, beamCount > 0 ? () -> cast(context, true) : null,
					singleCast);
		}
		for (var model : models) {
			if (model.failure() != null || model.getParticleAmount() == 0) {
				throw new IllegalStateException("模型负载没有持续参与原生输出：" + model.failure());
			}
		}
		context.metrics().record(MODELS, models.size());
		++ticks;
		if (beamCount == 0) {
			return ticks >= MEASURE ? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
		}

		readCast();
		recordPhases();

		if (singleCast && evidenceMode == EvidenceMode.PERFORMANCE) {
			if (!capture.complete()) {
				return BenchClientStepResult.CONTINUE;
			}

			primaryCaptureEndFrame = context.frames().sampleCount();
			return finishSpark(context);
		}

		if (singleCast) {
			if (pendingCast == null && allBeamsOutput() && verificationOutputFrame < 0) {
				verificationOutputFrame = context.frames().sampleCount();
			}
			if (beamScreenshot == null && verificationOutputFrame >= 0
					&& context.frames().sampleCount() >= verificationOutputFrame + 2) {
				if (!allBeamsOutput()) {
					throw new IllegalStateException("首次释放截图前光束已离开输出阶段。");
				}
				markBeamScreenshot(context);
				beamScreenshot = context.automation().captureScreenshot(label + "-beam", BenchCaptureOptions.immediate());
			}
			if (!capture.complete()) {
				return BenchClientStepResult.CONTINUE;
			}
			if (beamScreenshot == null || !beamScreenshot.isDone()) {
				return BenchClientStepResult.CONTINUE;
			}
			beamScreenshot.join();
			return finishSpark(context);
		}

		if (!verificationCast) {
			if (!capture.complete()) {
				return BenchClientStepResult.CONTINUE;
			}

			verificationCast = true;
			verificationStarted = System.nanoTime();
			cast(context, false);
		}
		if ((System.nanoTime() - verificationStarted) / 1_000_000_000.0 > 15) {
			throw new IllegalStateException("测量外验证未在预算内观察到完整输出和截图。");
		}
		if (pendingCast == null && allBeamsOutput() && verificationOutputFrame < 0) {
			verificationOutputFrame = context.frames().sampleCount();
		}
		if (beamScreenshot == null && verificationOutputFrame >= 0
				&& context.frames().sampleCount() >= verificationOutputFrame + 2) {
			if (!allBeamsOutput()) {
				throw new IllegalStateException("截图请求前真实光束已离开输出阶段。");
			}
			markBeamScreenshot(context);
			beamScreenshot = context.automation().captureScreenshot(label + "-beam", BenchCaptureOptions.immediate());
		}
		if (beamScreenshot != null && beamScreenshot.isDone()) {
			beamScreenshot.join();
			if (!allBeamsOutput()) {
				throw new IllegalStateException("截图完成时真实光束已离开输出阶段。");
			}
			return BenchClientStepResult.COMPLETE;
		}
		return BenchClientStepResult.CONTINUE;
	}

	private BenchClientStepResult step(BenchClientContext context, BenchPhase phase) {
		if (deferredFailure != null) {
			return finishFailedSpark(context);
		}

		try {
			return switch (phase) {
				case STABILIZE -> stabilizeStep(context);
				case WARMUP -> warmupStep(context);
				case MEASURE -> measureStep(context);
				default -> throw new IllegalArgumentException("非客户端 tick 阶段：" + phase);
			};
		} catch (RuntimeException | AssertionError failure) {
			if (spark == null) {
				throw failure;
			}

			deferredFailure = failure;
			failureCleanupStartedNs = System.nanoTime();
			return finishFailedSpark(context);
		}
	}

	/// 子类只传入 RuntimeException 或 AssertionError；首个失败固定计时，后续 tick 继续保存后重抛。
	protected BenchClientStepResult failWithSpark(BenchClientContext context, Throwable failure) {
		if (deferredFailure == null) {
			if (spark == null) {
				if (failure instanceof RuntimeException runtime) {
					throw runtime;
				}
				throw (AssertionError) failure;
			}

			deferredFailure = failure;
			failureCleanupStartedNs = System.nanoTime();
		}

		return finishFailedSpark(context);
	}

	private BenchClientStepResult finishSpark(BenchClientContext context) {
		if (spark == null) {
			return BenchClientStepResult.COMPLETE;
		}

		if (!sparkStopping) {
			spark.poll();
			Lobecorp.LOGGER.info("PHOTONSTAGE stage=spark_stop_request ns={} epochMs={}", System.nanoTime(), System.currentTimeMillis());
			spark.stop();
			sparkStopping = true;
		}
		if (!spark.poll() || !spark.complete()) {
			return BenchClientStepResult.CONTINUE;
		}

		try {
			spark.archive(context);
			sparkArchived = true;
		} catch (Exception failure) {
			throw new IllegalStateException("spark 完成实件归档失败。", failure);
		}
		return BenchClientStepResult.COMPLETE;
	}

	/// 只延迟本场景持有采样后的失败；不阻塞 render 等待需要客户端回执的保存。
	@SuppressWarnings("SameReturnValue") // 等待时只能 CONTINUE，完成后必须重抛原失败。
	private BenchClientStepResult finishFailedSpark(BenchClientContext context) {
		focusAudit.audit(true, "failure_finalize");
		if (!worldSetupQueued && beamFixture != null && !beamFixture.isDone()) {
			beamFixture.completeExceptionally(deferredFailure);
		}
		if (!failureCaptureClosed) {
			failureCaptureClosed = true;
			try {
				finishDeserializeDiagnostics(context);
			} catch (RuntimeException cleanup) {
				deferredFailure.addSuppressed(cleanup);
			}
			try {
				if (capture != null) {
					capture.close(context);
				}
			} catch (RuntimeException cleanup) {
				deferredFailure.addSuppressed(cleanup);
			}
		}

		try {
			if (System.nanoTime() - failureCleanupStartedNs > TimeUnit.SECONDS.toNanos(60)) {
				throw new IllegalStateException("失败后的 spark 收尾超过 60 秒，完成未确认。");
			}

			if (!spark.poll()) {
				return BenchClientStepResult.CONTINUE;
			}
			if (!spark.complete()) {
				spark.stop();
				return BenchClientStepResult.CONTINUE;
			}

			spark.archive(context);
			sparkArchived = true;
		} catch (Exception cleanup) {
			if (cleanup != deferredFailure) {
				deferredFailure.addSuppressed(cleanup);
			}
		}

		try {
			var report = spark.snapshot();
			annotateEvidence(report);
			report.add("focus_audit", focusAudit.snapshot());
			report.addProperty("original_failure", deferredFailure.toString());
			report.addProperty("cleanup_completion_confirmed", spark.complete() && sparkArchived);
			context.artifacts().write(label + "-spark-failure.json", "application/json", report.toString());
		} catch (Exception cleanup) {
			deferredFailure.addSuppressed(cleanup);
		}

		if (deferredFailure instanceof RuntimeException failure) {
			throw failure;
		}
		throw (AssertionError) deferredFailure;
	}

	private void markBeamScreenshot(BenchClientContext context) {
		var mark = new JsonObject();
		mark.addProperty("requested_ns", System.nanoTime());
		mark.addProperty("bench_frame", context.frames().sampleCount());
		mark.add("frame_capture", capture.observation());
		mark.addProperty("player_pose", context.automation().pose().toString());
		mark.addProperty("particle_time_equivalence_verified", false);
		mark.addProperty("animation_time_scope", "latest_authoritative_client_snapshot_not_interpolated_particle_clock");
		var camera = context.minecraft().gameRenderer.getMainCamera();
		var actual = new JsonObject();
		actual.addProperty("initialized", camera.isInitialized());
		actual.addProperty("x", camera.position().x);
		actual.addProperty("y", camera.position().y);
		actual.addProperty("z", camera.position().z);
		actual.addProperty("yaw", camera.yRot());
		actual.addProperty("pitch", camera.xRot());
		actual.addProperty("roll", camera.getRoll());
		actual.addProperty("fov", camera.getFov());
		mark.add("actual_camera", actual);
		var phases = new JsonArray();
		var ids = beamFixture.join().members().stream().map(QueenBeamPerformanceFixture.Member::queenId).toList();
		for (var animation : LcClientAnimations.snapshots()) {
			if (!ids.contains(animation.ownerEntityId()) || !TheQueenOfHatredAnimationDefinitions.isBeam(animation.definitionId())) {
				continue;
			}

			var phase = new JsonObject();
			phase.addProperty("queen", animation.ownerEntityId());
			phase.addProperty("animation", animation.instanceId().toString());
			phase.addProperty("phase", animation.parameters().getOrDefault(TheQueenOfHatredBeamAnimation.PHASE, -1.0));
			phase.addProperty("animation_time_seconds", animation.timeSeconds());
			phase.addProperty("server_tick", animation.serverTick());
			phases.add(phase);
		}
		mark.add("beam_phases", phases);
		beamScreenshotRequest = mark;
	}

	@Override
	public void verify(BenchClientContext context) throws Exception {
		finishDeserializeDiagnostics(context);
		if (models.size() != count || context.frames().sampleCount() <= firstFrame
				|| effects.stream().anyMatch(effect -> !effect.isAlive())) {
			throw new AssertionError("模型基准负载或帧样本不完整。");
		}
		for (var model : models) {
			var frame = model.sampledFrame();
			if (frame == null || frame.groups().isEmpty()) {
				throw new AssertionError("模型没有产生实际姿态采样。");
			}
		}
		var report = new JsonObject();
		report.addProperty("label", label);
		annotateEvidence(report);
		report.addProperty("window_geometry_preserved_by_bench_bridge", PhotonBenchMixinPlugin.preservedWindowApplied());
		report.addProperty("bench_input_isolation_contract_active", !System.getProperty("modBench.resultDirectory", "").isBlank());
		report.addProperty("bench_input_isolation_source", "BenchInputIsolation.active_contract_BenchMod_0.1.5-beta_required_MouseHandlerMixin_InputConstantsMixin");
		report.addProperty("bench_input_isolation_runtime_verification", "not_verified_by_source_contract");
		report.addProperty("setup_focus_requests", 1);
		report.addProperty("mouse_grabbed_observed", context.minecraft().mouseHandler.isMouseGrabbed());
		report.addProperty("glfw_cursor_mode_observed",
				GLFW.glfwGetInputMode(context.minecraft().getWindow().handle(), GLFW.GLFW_CURSOR));
		report.add("spark_client", spark == null ? JsonNull.INSTANCE : spark.snapshot());
		report.addProperty("spark_start_wait_ns", sparkReadyNs == 0 ? 0 : sparkReadyNs - sparkRequestedNs);
		report.addProperty("spark_wait_scope", "before_queen_creation_and_setup_prewarm_capture");
		report.addProperty("fixture_setup_triggered_ns", fixtureSetupTriggeredNs);
		report.add("fixed_world_anchor", fixedAnchor == null ? JsonNull.INSTANCE : fixedAnchor);
		if (evidenceMode == EvidenceMode.VISUAL) {
			report.add("beam_screenshot_request", beamScreenshotRequest == null ? JsonNull.INSTANCE : beamScreenshotRequest);
		}
		report.addProperty("expected_shaderpack_enabled", Boolean.parseBoolean(System.getProperty(SHADER_PROPERTY, "true")));
		report.addProperty("packed_snapshot_requested", Boolean.getBoolean(PhotonPackedVertexUtil.ENABLE_PROPERTY));
		report.addProperty("packed_snapshot_mixin_applied", IPhotonPackedVertexSink.class.isAssignableFrom(BufferBuilder.class));
		report.addProperty("synthetic", true);
		report.addProperty("models", count);
		report.addProperty("animated", animated);
		report.addProperty("cold_first_emit", cold);
		report.addProperty("static_models", staticCount);
		report.addProperty("animated_models", animatedCount);
		report.addProperty("mesh_grid", meshGrid);
		report.addProperty("beam_count", beamCount);
		report.addProperty("beam_layout", beamCount == 4 ? "configured_converging_region" : "parallel_lanes");
		report.addProperty("overdraw_verified", false);
		report.addProperty("targets_reset_before_cast", beamCount == 4);
		report.addProperty("target_drift_during_cast_verified", false);
		if (beamCount > 0) {
			var targets = new JsonArray();
			for (var member : beamFixture.join().members()) {
				var target = new JsonObject();
				target.addProperty("target_uuid", member.targetUuid().toString());
				target.addProperty("max_health", member.targetHealth());
				target.addProperty("spawn_x", member.targetSpawn().x);
				target.addProperty("spawn_y", member.targetSpawn().y);
				target.addProperty("spawn_z", member.targetSpawn().z);
				targets.add(target);
			}
			report.add("beam_targets", targets);
		}
		report.addProperty("vertices_per_model", (meshGrid + 1) * (meshGrid + 1));
		report.addProperty("faces_per_model", meshGrid * meshGrid);
		report.addProperty("shaderpack", IrisCompat.packName());
		report.addProperty("shaderpack_enabled", IrisCompat.isUsingShaderPack());
		report.addProperty("bench_measure_includes_visual_validation", evidenceMode == EvidenceMode.VISUAL && beamCount > 0);
		report.addProperty("bench_measure_includes_spark_finalize", spark != null);
		report.addProperty("bench_measure_scope", "primary_capture_then_async_spark_stop_save_archive_tail");
		report.addProperty("primary_capture_end_bench_frame", primaryCaptureEndFrame);
		report.addProperty("post_primary_capture_bench_frames",
				primaryCaptureEndFrame < 0 ? 0 : context.frames().sampleCount() - primaryCaptureEndFrame);
		report.addProperty("primary_capture_seconds", beamCount > 0 ? (singleCast ? 20 : 90) : 0);
		report.addProperty("measured_casts", measuredCasts);
		report.add("beam_marks", beamMarks);
		if (beamCount > 0 && measuredCasts != beamCount * (singleCast ? 1 : 3)) {
			throw new AssertionError("真实光束施放数量不完整。");
		}
		verifyBeamPhases();
		if (evidenceMode == EvidenceMode.VISUAL && beamScreenshot != null) {
			context.artifacts().write(label + "-beam.png", "image/png", Files.readAllBytes(beamScreenshot.join()));
		}
		if (singleCast && (cameraAudit.samples() == 0 || cameraAudit.changes() != 0 || cameraAudit.unavailableFrames() != 0)) {
			throw new AssertionError("首次释放实际主相机不完整或发生变化，不能通过跨轮相机门禁。");
		}

		report.addProperty("camera_gate_verified", cameraAudit.samples() > 0 && cameraAudit.changes() == 0);
		report.add("camera_audit_first", cameraAudit.first() == null ? JsonNull.INSTANCE : cameraAudit.first());
		report.add("camera_audit_last", cameraAudit.last() == null ? JsonNull.INSTANCE : cameraAudit.last());
		report.addProperty("camera_audit_samples", cameraAudit.samples());
		report.addProperty("camera_audit_changes", cameraAudit.changes());
		report.addProperty("camera_audit_unavailable_frames", cameraAudit.unavailableFrames());
		var focus = focusAudit.snapshot();
		report.add("focus_audit", focus);
		report.addProperty("pose_requested", expectedPose == null ? "" : expectedPose.toString());
		report.addProperty("pose_observed", context.automation().pose().toString());
		report.addProperty("pose_gate_verified",
				expectedPose != null && PhotonBenchCameraAudit.poseMatches(expectedPose, context.automation().pose()));
		report.addProperty("orphaned_fixture_entities_removed", orphanedFixtures);
		report.add("entity_census", worldCensus == null ? JsonNull.INSTANCE : worldCensus);
		report.addProperty("verification_cast_started_ns", verificationStarted);
		var singleplayer = context.minecraft().getSingleplayerServer();

		if (singleplayer != null) {
			var benchLevel = singleplayer.overworld();
			var worldData = singleplayer.getWorldData();
			report.addProperty("world_name", worldData.getLevelName());
			report.addProperty("world_dimension", benchLevel.dimension().identifier().toString());
			report.addProperty("world_difficulty", worldData.getDifficulty().name());
			report.addProperty("world_game_time", benchLevel.getGameTime());
		}

		capture.write(context, report);
		if (evidenceMode == EvidenceMode.PERFORMANCE) {
			verifyPerformanceLoad(report);
		}
		report.addProperty("create_emit_ns", createNanos);
		report.addProperty("create_emit_in_frame_capture", false);
		report.addProperty("measured_frames", context.frames().sampleCount() - firstFrame);
		report.addProperty("sodium", ModList.get().getModContainerById("sodium").orElseThrow()
				.getModInfo().getVersion().toString());
		context.artifacts().write(label + "-workload.json", "application/json", report.toString());
	}

	private void annotateEvidence(JsonObject report) {
		if (deserializeDiagnostics != null) {
			report.addProperty("comparison_group", "laser");
			report.add("deserialize_diagnostics", deserializeDiagnostics.snapshot());
		}
		report.addProperty("setup_observed_ns", setupObservedNs);
		report.addProperty("world_available_ns", worldAvailableNs);
		report.addProperty("world_settled_ns", worldSettledNs);
		report.addProperty("world_settle_required_ns", evidenceMode == EvidenceMode.PERFORMANCE ? WORLD_SETTLE_NS : 0);
		report.addProperty("world_settle_elapsed_ns", worldSettledNs == 0 ? 0 : worldSettledNs - worldAvailableNs);
		report.addProperty("world_settle_scope", "world_and_player_present_screen_and_overlay_absent_before_spark_and_fixture");
		report.addProperty("readiness_confirmed_ns", sparkReadyNs);
		report.addProperty("window_maximize_wait_started_ns", windowMaximizeWaitStartedNs);
		report.addProperty("window_maximize_wait_ns", windowMaximizeWaitStartedNs == 0 || sparkReadyNs == 0
				? 0 : sparkReadyNs - windowMaximizeWaitStartedNs);
		report.addProperty("readiness_scope", "spark_ack_and_native_maximized_before_fixture_not_spark_ack_alone");
		report.addProperty("evidence_mode", evidenceMode.name());
		report.addProperty("image_validation", evidenceMode == EvidenceMode.PERFORMANCE ? "NOT_USED" : "VISUAL");
		if (evidenceMode == EvidenceMode.PERFORMANCE) {
			report.addProperty("performance_verdict", "NOT_EVALUATED");
			report.addProperty("collection_verdict_scope", "load_and_capture_integrity_only");
			report.addProperty("primary_metric_scope", "20_second_capture_excludes_Bench_MEASURE_spark_save_tail");
		}
	}

	/// CPU 模型工作和真实 drain 绘制同时存在，避免空场帧率被当成有效性能负载。
	private void verifyPerformanceLoad(JsonObject report) {
		if (!report.has("measure_submissions") || !report.get("measure_submissions").isJsonObject()) {
			throw new AssertionError("性能采集缺少 measure_submissions，不能用空场帧率验收。");
		}

		var submissions = report.getAsJsonObject("measure_submissions");
		var model = submissions.getAsJsonObject("model");
		if (model == null || model.get("cpu_submission_calls").getAsLong() <= 0
				|| model.get("cpu_jobs_added").getAsLong() <= 0
				|| model.get("cpu_vertices").getAsLong() <= 0
				|| model.get("cpu_index_count").getAsLong() <= 0
				|| model.get("failed_cpu_submissions").getAsLong() != 0
				|| !submissions.get("frame_capture_bound").getAsBoolean()
				|| !submissions.get("draw_call_count_available").getAsBoolean()
				|| submissions.get("draw_calls").getAsLong() <= 0
				|| submissions.get("failed_drains").getAsLong() != 0
				|| submissions.get("scope_protocol_errors").getAsLong() != 0) {
			throw new AssertionError("性能采集没有完整的真实模型 CPU jobs／顶点／绘制提交。");
		}

		report.addProperty("real_workload_verified", true);
		report.addProperty("draw_evidence_scope", "Photon_drain_API_calls_not_model_attributed_or_hardware_completion");
	}

	@Override
	public void teardown(BenchClientContext context) {
		try {
			try {
				finishDeserializeDiagnostics(context);
			} finally {
				finishSynchronousSpark(context);
			}
		} finally {
			teardownFixture(context);
		}
	}

	private void finishDeserializeDiagnostics(BenchClientContext context) {
		if (deserializeDiagnostics != null) {
			deserializeDiagnostics.finish(context);
		}
	}

	private void teardownFixture(BenchClientContext context) {
		try {
			try {
				if (capture != null) {
					capture.close(context);
				}
			} finally {
				try {
					clearBeams();
				} finally {
					effects.forEach(PhotonGeoEffect::close);
				}
			}
		} finally {
			effects.clear();
			models.clear();
			try {
				if (poseHold != null) {
					poseHold.close();
				}
			} finally {
				try {
					if (originalPose != null) {
						context.player().getAbilities().flying = originalFlying;
						context.player().setDeltaMovement(Vec3.ZERO);
						context.player().onUpdateAbilities();
						context.automation().setPose(originalPose);
					}
				} finally {
					if (screenshot != null && !screenshot.isDone()) {
						screenshot.cancel(false);
					}
					if (beamScreenshot != null && !beamScreenshot.isDone()) {
						beamScreenshot.cancel(false);
					}
				}
			}
		}
	}

	/// 上游取消／超时直接同步 teardown，不能在这里等待主线程回执；明确记录未确认边界。
	private void finishSynchronousSpark(BenchClientContext context) {
		if (!worldSetupQueued && beamFixture != null && !beamFixture.isDone()) {
			beamFixture.completeExceptionally(new IllegalStateException("夹具创建前已结束。"));
		}
		if (spark == null) {
			return;
		}

		RuntimeException failure = null;
		try {
			if (!spark.complete() && spark.poll() && !sparkStopping) {
				spark.stop();
				sparkStopping = true;
			}
		} catch (RuntimeException cleanup) {
			failure = cleanup;
		}

		try {
			spark.close();
		} catch (RuntimeException cleanup) {
			if (failure == null) {
				failure = cleanup;
			} else {
				failure.addSuppressed(cleanup);
			}
		}

		try {
			var report = spark.snapshot();
			annotateEvidence(report);
			report.add("focus_audit", focusAudit.snapshot());
			report.addProperty("synchronous_teardown_wait_supported", false);
			report.addProperty("cleanup_completion_confirmed", spark.complete() && sparkArchived);
			context.artifacts().write(label + "-spark-teardown.json", "application/json", report.toString());
		} catch (Exception cleanup) {
			if (failure == null) {
				failure = new IllegalStateException("spark 收尾状态归档失败。", cleanup);
			} else {
				failure.addSuppressed(cleanup);
			}
		}

		if (failure != null) {
			if (deferredFailure == null) {
				throw failure;
			}
			deferredFailure.addSuppressed(failure);
		}
	}

	private void cast(BenchClientContext context, boolean measured) {
		if (pendingCast != null) {
			throw new IllegalStateException("前次真实施放尚未完成。");
		}

		var server = context.minecraft().getSingleplayerServer();
		if (server == null || beamFixture == null) {
			throw new IllegalStateException("真实施放失去当前夹具。");
		}

		pendingCast = new CompletableFuture<>();
		var request = pendingCast;
		var fixture = beamFixture.join();
		server.execute(() -> {
			try {
				request.complete(fixture.cast(server));
			} catch (RuntimeException failure) {
				request.completeExceptionally(failure);
			}
		});
		if (measured) {
			measuredCasts += beamCount;
		}
	}

	private void readCast() {
		if (pendingCast == null || !pendingCast.isDone()) {
			return;
		}

		for (var result : pendingCast.join()) {
			if (!result.started()) {
				throw new IllegalStateException("真实激光未开始：" + result.status());
			}

			var mark = new JsonObject();
			mark.addProperty("event", "server_cast_completed");
			mark.addProperty("queen", result.queenId());
			mark.addProperty("status", result.status());
			mark.addProperty("observed_ns", System.nanoTime());
			mark.addProperty("verification", verificationCast);
			beamMarks.add(mark);
		}
		pendingCast = null;
	}

	private void recordPhases() {
		var ids = beamFixture.join().members().stream().map(QueenBeamPerformanceFixture.Member::queenId).toList();
		var present = new HashSet<UUID>();
		for (var animation : LcClientAnimations.snapshots()) {
			if (!ids.contains(animation.ownerEntityId()) || !TheQueenOfHatredAnimationDefinitions.isBeam(animation.definitionId())) {
				continue;
			}

			present.add(animation.instanceId());
			beamOwners.put(animation.instanceId(), animation.ownerEntityId());
			double phase = animation.parameters().getOrDefault(TheQueenOfHatredBeamAnimation.PHASE, -1.0);
			var previous = beamPhases.put(animation.instanceId(), phase);
			if (previous == null || Double.compare(previous, phase) != 0) {
				var mark = new JsonObject();
				mark.addProperty("event", "client_phase");
				mark.addProperty("queen", animation.ownerEntityId());
				mark.addProperty("animation", animation.instanceId().toString());
				mark.addProperty("phase", phase);
				mark.addProperty("observed_ns", System.nanoTime());
				mark.addProperty("verification", verificationCast);
				beamMarks.add(mark);
			}
		}
		recordRemoved(present);
	}

	private boolean allBeamsOutput() {
		return beamFixture.join().members().stream().allMatch(member -> beamPhases.entrySet().stream()
				.anyMatch(entry -> Objects.equals(beamOwners.get(entry.getKey()), member.queenId())
						&& entry.getValue() == TheQueenOfHatredBeamAnimation.Phase.OUTPUT.ordinal()));
	}

	private void recordRemoved(Set<UUID> present) {
		for (var id : List.copyOf(beamPhases.keySet())) {
			if (present.contains(id)) {
				continue;
			}

			var mark = new JsonObject();
			mark.addProperty("event", "client_removed");
			mark.addProperty("queen", beamOwners.remove(id));
			mark.addProperty("animation", id.toString());
			mark.addProperty("observed_ns", System.nanoTime());
			mark.addProperty("verification", verificationCast);
			beamMarks.add(mark);
			beamPhases.remove(id);
		}
	}

	private void verifyBeamPhases() {
		if (beamCount == 0) {
			return;
		}

		for (var member : beamFixture.join().members()) {
			long removed = beamMarks.asList().stream().map(JsonElement::getAsJsonObject)
					.filter(mark -> mark.get("event").getAsString().equals("client_removed")
							&& !mark.get("verification").getAsBoolean()
							&& mark.get("queen").getAsInt() == member.queenId()).count();
			if (removed != (singleCast ? 1 : 3)) {
				throw new AssertionError("真实激光未完成移除：" + member.queenId() + "=" + removed);
			}
			for (int phase = 0; phase < 3; phase++) {
				int expectedPhase = phase;
				long count = beamMarks.asList().stream().map(JsonElement::getAsJsonObject)
						.filter(mark -> mark.get("event").getAsString().equals("client_phase")
								&& !mark.get("verification").getAsBoolean()
								&& mark.get("queen").getAsInt() == member.queenId()
								&& mark.get("phase").getAsDouble() == expectedPhase).count();
				if (count != (singleCast ? 1 : 3)) {
					throw new AssertionError("真实激光客户端阶段不完整：" + member.queenId() + "/" + phase + "=" + count);
				}
			}
		}
	}

	private void clearBeams() {
		if (beamFixture == null || beamFixture.isCompletedExceptionally()) {
			return;
		}

		var server = fixtureServer;
		if (server == null) {
			throw new IllegalStateException("夹具所属服务器缺失，清理未确认。");
		}
		var cleared = new CompletableFuture<Void>();
		// 先登记迟到创建的清理；等待超时也不会撤销这项所有者责任。
		beamFixture.whenComplete((fixture, creationFailure) -> {
			if (creationFailure != null) {
				cleared.complete(null);
				return;
			}
			try {
				if (server.isStopped()) {
					throw new IllegalStateException("夹具所属服务器已停止，清理未确认。");
				}
				server.execute(() -> {
					try {
						fixture.clear(server);
						cleared.complete(null);
					} catch (RuntimeException failure) {
						cleared.completeExceptionally(failure);
					}
				});
			} catch (RuntimeException failure) {
				cleared.completeExceptionally(failure);
			}
		});
		try {
			cleared.get(5, TimeUnit.SECONDS);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("夹具清理被中断；迟到创建清理仍已登记。", interrupted);
		} catch (Exception failure) {
			throw new IllegalStateException("夹具清理未确认；迟到创建清理仍已登记。", failure);
		}
	}

	private JsonObject asset() {
		return PhotonBenchWorkloadUtil.asset(meshGrid);
	}
}
