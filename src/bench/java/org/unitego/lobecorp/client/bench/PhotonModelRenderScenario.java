package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.unitego.lobecorp.util.photon.performance.PhotonBenchWorkloadUtil;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.fx.FX;
import com.zhongbai233.bench.api.BenchMetricDescriptor;
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
	private final PhotonBenchWorkloadUtil.Layout workload;
	private static final BenchMetricDescriptor MODELS = new BenchMetricDescriptor(
			"lobecorp.photon.loaded_models", "count", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor STARTUP = new BenchMetricDescriptor(
			"lobecorp.photon.create_emit", "ns", MetricDirection.LOWER_IS_BETTER);
	private final String label;
	private final int count;
	private final boolean animated;
	private final boolean cold;
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
	private int orphanedFixtures;
	private final PhotonBenchCameraAudit cameraAudit = new PhotonBenchCameraAudit();
	private final PhotonBenchFocusAudit focusAudit = new PhotonBenchFocusAudit();

	public PhotonModelRenderScenario(String label, int count, boolean animated, boolean cold) {
		this(label, animated ? 0 : count, animated ? count : 0, GRID, 0, cold);
	}

	public PhotonModelRenderScenario(String label, int staticCount, int animatedCount, int meshGrid, int beamCount, boolean cold) {
		workload = new PhotonBenchWorkloadUtil.Layout(staticCount, animatedCount, meshGrid, beamCount);

		this.label = label;
		this.staticCount = staticCount;
		this.animatedCount = animatedCount;
		this.count = staticCount + animatedCount;
		this.animated = animatedCount == count && count > 0;
		this.meshGrid = meshGrid;
		this.beamCount = beamCount;
		this.cold = cold;
	}

	@Override
	public void setup(BenchClientContext context) {
		if (!ModList.get().isLoaded("sodium")) {
			throw new IllegalStateException("本轮模型基准要求加载 Sodium。");
		}
		var window = context.minecraft().getWindow();
		GLFW.glfwMaximizeWindow(window.handle());
		GLFW.glfwShowWindow(window.handle());
		GLFW.glfwFocusWindow(window.handle());
		originalPose = context.automation().pose();
		var player = context.player();
		originalFlying = player.getAbilities().flying;
		player.getAbilities().flying = true;
		player.setDeltaMovement(Vec3.ZERO);
		player.onUpdateAbilities();
		context.automation().stopMovement();
		capture = new PhotonBenchCapture(label, context.seed());
		origin = new Vector3f((float) player.getX(), (float) player.getY(), (float) player.getZ());
		assetJson = count == 0 ? "" : asset().toString();
		expectedPose = BenchClientPose.lookingAt(
				player.getX(), player.getY() + (count > 16 ? 12 : 7), player.getZ() - (count > 16 ? 24 : 10),
				player.getX(), player.getY() + 2, player.getZ() + 5);
		poseHold = context.automation().holdPose(expectedPose);
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
		server.execute(() -> {
			try {
				QueenBeamPerformanceFixture.prepareBenchWorld(server);
				var benchLevel = server.overworld();
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
	}

	@Override
	public BenchClientStepResult stabilize(BenchClientContext context) {
		if (!worldReady.isDone()) {
			return BenchClientStepResult.CONTINUE;
		}
		worldReady.join();

		if (!applyExpectedPose(context)) {
			return BenchClientStepResult.CONTINUE;
		}

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
		focusAudit.audit(false);
		if (++ticks < WARMUP) {
			return BenchClientStepResult.CONTINUE;
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
		focusAudit.audit(true);

		if (ticks == 0 && !applyExpectedPose(context)) {
			return BenchClientStepResult.CONTINUE;
		}

		cameraAudit.sample();

		if (ticks == 0) {
			firstFrame = context.frames().sampleCount();
			if (!created) {
				create(context);
			}
			context.metrics().record(STARTUP, createNanos);
			capture.start(beamCount > 0 ? 90 : 120, beamCount > 0 ? () -> cast(context, true) : null);
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

	@Override
	public void verify(BenchClientContext context) throws Exception {
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
		report.addProperty("bench_measure_includes_visual_validation", beamCount > 0);
		report.addProperty("primary_capture_seconds", beamCount > 0 ? 90 : 0);
		report.addProperty("measured_casts", measuredCasts);
		report.add("beam_marks", beamMarks);
		if (beamCount > 0 && measuredCasts != beamCount * 3) {
			throw new AssertionError("真实光束施放数量不完整。");
		}
		verifyBeamPhases();
		if (beamScreenshot != null) {
			context.artifacts().write(label + "-beam.png", "image/png", Files.readAllBytes(beamScreenshot.join()));
		}
		report.addProperty("camera_gate_verified", cameraAudit.samples() > 0 && cameraAudit.changes() == 0);
		report.add("camera_audit_first", cameraAudit.first() == null ? JsonNull.INSTANCE : cameraAudit.first());
		report.add("camera_audit_last", cameraAudit.last() == null ? JsonNull.INSTANCE : cameraAudit.last());
		report.addProperty("camera_audit_samples", cameraAudit.samples());
		report.addProperty("camera_audit_changes", cameraAudit.changes());
		report.addProperty("camera_audit_unavailable_frames", cameraAudit.unavailableFrames());
		report.add("focus_audit", focusAudit.snapshot());
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
		report.addProperty("create_emit_ns", createNanos);
		report.addProperty("create_emit_in_frame_capture", false);
		report.addProperty("measured_frames", context.frames().sampleCount() - firstFrame);
		report.addProperty("sodium", ModList.get().getModContainerById("sodium").orElseThrow()
				.getModInfo().getVersion().toString());
		context.artifacts().write(label + "-workload.json", "application/json", report.toString());
	}

	@Override
	public void teardown(BenchClientContext context) {
		try {
			try {
				if (capture != null) {
					capture.close();
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
			if (removed != 3) {
				throw new AssertionError("真实激光未完成三次移除：" + member.queenId() + "=" + removed);
			}
			for (int phase = 0; phase < 3; phase++) {
				int expectedPhase = phase;
				long count = beamMarks.asList().stream().map(JsonElement::getAsJsonObject)
						.filter(mark -> mark.get("event").getAsString().equals("client_phase")
								&& !mark.get("verification").getAsBoolean()
								&& mark.get("queen").getAsInt() == member.queenId()
								&& mark.get("phase").getAsDouble() == expectedPhase).count();
				if (count != 3) {
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
