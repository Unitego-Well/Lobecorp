package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientPose;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import com.zhongbai233.bench.api.neoforge.client.BenchPoseHold;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.photon.performance.PhotonSubmissionDiagnostics;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffect;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.mixin.PhotonBenchMixinPlugin;
import org.unitego.lobecorp.util.photon.compat.PhotonInactiveLegacyFieldUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceInspection;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/// 既有编辑器资源的数据对照；保留共同 guard/Mixin，不创建女皇或预热其他 FX。
@SuppressWarnings("resource")
public class PhotonNativeControlScenario implements BenchClientScenario {
	private final String label = LobecorpBenchConfig.PHOTON_CONTROL_LABEL;
	private final PhotonBenchDiagnostics diagnostics = new PhotonBenchDiagnostics(label, "native_data_control");
	private final PhotonBenchCameraAudit cameraAudit = new PhotonBenchCameraAudit();
	private final PhotonBenchFocusAudit focusAudit = new PhotonBenchFocusAudit();
	private final JsonArray dependencies = new JsonArray();
	private PhotonBenchCapture capture;
	private PhotonBenchSpark spark;
	private PhotonGeoEffect effect;
	private BenchClientPose originalPose;
	private BenchClientPose expectedPose;
	private BenchPoseHold poseHold;
	private boolean originalFlying;
	private CompletableFuture<Vec3> worldReady;
	private Vector3f origin;
	private long worldAvailableNs;
	private long worldSettledNs;
	private long maximizeWaitNs;
	private long loadStartNs;
	private long loadEndNs;
	private long fxHelperStartedNs;
	private long fxHelperEndedNs;
	private long createEndNs;
	private long emittedNs;
	private long firstActiveNs;
	private long lastActiveNs;
	private long activeSampleIntervalsNs;
	private long previousSampleNs;
	private boolean previousActive;
	private long activeSamples;
	private long peakParticles;
	private long peakActiveEmitters;
	private int ticks;
	private int casts;
	private boolean measuring;
	private boolean stopping;
	private boolean sparkArchived;
	private Throwable failure;
	private long failureStartedNs;
	private boolean failureCaptureClosed;

	@Override
	public void setup(BenchClientContext context) {
		if (!LobecorpBenchConfig.PHOTON_FIRST_CAST_PERFORMANCE_SCENARIO.equals(System.getProperty("modBench.scenarios", ""))
				|| !LobecorpBenchConfig.PHOTON_CONTROL_FX.equals(System.getProperty(LobecorpBenchConfig.PHOTON_CONTROL_FX_PROPERTY, ""))) {
			throw new IllegalStateException("原生对照要求精确无截图 profile 和已审计 controlFx。");
		}

		if (!ModList.get().isLoaded("sodium") || IrisCompat.isUsingShaderPack()
				|| Boolean.parseBoolean(System.getProperty("lobecorp.photon.bench.shader", "true"))) {
			throw new IllegalStateException("本轮原生数据对照要求 Sodium、无光影及显式 shader=false。");
		}

		if (System.getProperty("modBench.resultDirectory", "").isBlank() || !PhotonBenchMixinPlugin.preservedWindowApplied()
				|| !PhotonSubmissionDiagnostics.probesRequested()) {
			throw new IllegalStateException("缺少鼠标隔离、原窗口桥或实际作业诊断开关。");
		}

		focusAudit.audit(true, "setup");
		originalPose = context.automation().pose();
		originalFlying = context.player().getAbilities().flying;
		context.player().getAbilities().flying = true;
		context.player().setDeltaMovement(Vec3.ZERO);
		context.player().onUpdateAbilities();
		context.automation().stopMovement();
		capture = new PhotonBenchCapture(label, context.seed());
	}

	@Override
	public BenchClientStepResult stabilize(BenchClientContext context) {
		return guarded(context, () -> stabilizeStep(context));
	}

	private BenchClientStepResult stabilizeStep(BenchClientContext context) {
		focusAudit.audit(true, "stabilize");
		if (spark == null) {
			var minecraft = context.minecraft();
			if (minecraft.level == null || minecraft.player == null || minecraft.screen != null || minecraft.getOverlay() != null) {
				worldAvailableNs = 0;
				return BenchClientStepResult.CONTINUE;
			}

			long now = System.nanoTime();
			if (worldAvailableNs == 0) {
				worldAvailableNs = now;
			}

			if (now - worldAvailableNs < TimeUnit.SECONDS.toNanos(10)) {
				return BenchClientStepResult.CONTINUE;
			}

			worldSettledNs = now;
			diagnostics.start();
			spark = PhotonBenchSpark.start(minecraft, label);
		}

		if (!spark.poll()) {
			return BenchClientStepResult.CONTINUE;
		}

		var handle = context.minecraft().getWindow().handle();
		if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE
				|| context.minecraft().getWindow().getWidth() <= 0 || context.minecraft().getWindow().getHeight() <= 0) {
			return BenchClientStepResult.CONTINUE;
		}

		if (worldReady == null) {
			capture.startPrewarm();
			prepareWorld(context);
		}

		if (!worldReady.isDone()) {
			return BenchClientStepResult.CONTINUE;
		}

		if (expectedPose == null) {
			var position = worldReady.join();
			origin = new Vector3f((float) position.x, (float) position.y, (float) position.z);
			expectedPose = BenchClientPose.lookingAt(origin.x, origin.y + 7, origin.z - 10,
					origin.x, origin.y + 2, origin.z + 5);
			context.automation().setPose(expectedPose);
			poseHold = context.automation().holdPose(expectedPose);
		}

		if (!PhotonBenchCameraAudit.poseMatches(expectedPose, context.automation().pose())) {
			throw new IllegalStateException("原生对照请求相机未生效。");
		}

		capture.prewarmPoseReady();
		if (effect == null) {
			loadControl(context);
		}

		if (++ticks < LobecorpBenchConfig.STABILIZE_TICKS
				|| !context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)) {
			return BenchClientStepResult.CONTINUE;
		}

		ticks = 0;
		return BenchClientStepResult.COMPLETE;
	}

	private void prepareWorld(BenchClientContext context) {
		var server = context.minecraft().getSingleplayerServer();
		if (server == null) {
			throw new IllegalStateException("原生对照要求独立集成服务器。");
		}

		worldReady = new CompletableFuture<>();
		server.execute(() -> {
			try {
				var worldId = System.getProperty("modBench.client.worldId", "");
				var preset = System.getProperty("modBench.client.worldPreset", "normal");
				var expectedName = preset.equals("normal") ? worldId : worldId + "-" + preset;
				var gamePath = context.minecraft().gameDirectory.toPath().toRealPath();
				var worldPath = server.getWorldPath(LevelResource.ROOT).toRealPath();
				if (worldId.isBlank() || !server.getWorldData().getLevelName().equals(expectedName)
						|| !gamePath.endsWith(Path.of("build", "modBench", "runs", "default", "client"))
						|| !worldPath.equals(gamePath.resolve("saves").resolve(expectedName))) {
					throw new IllegalStateException("原生对照必须使用已核对的独立 Bench 世界。");
				}

				var level = server.overworld();
				if (!context.level().dimension().equals(level.dimension())) {
					throw new IllegalStateException("原生对照要求主世界。");
				}

				var clock = level.dimensionType().defaultClock().orElseThrow();
				if (!server.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.NOON)) {
					throw new IllegalStateException("原生对照正午时钟不可用。");
				}

				var rules = level.getGameRules();
				rules.set(GameRules.ADVANCE_TIME, false, server);
				rules.set(GameRules.ADVANCE_WEATHER, false, server);
				rules.set(GameRules.SPAWN_MOBS, false, server);
				server.setWeatherParameters(0, 0, false, false);
				server.setDifficulty(Difficulty.NORMAL, true);
				worldReady.complete(Vec3.atBottomCenterOf(server.getRespawnData().pos()));
			} catch (Exception problem) {
				worldReady.completeExceptionally(problem);
			}
		});
	}

	private void loadControl(BenchClientContext context) {
		try {
			loadStartNs = System.nanoTime();
			var id = Identifier.parse(LobecorpBenchConfig.PHOTON_CONTROL_FX);
			var resourceId = Identifier.fromNamespaceAndPath(id.getNamespace(), "fx/" + id.getPath() + ".fx");
			// 先走真实首次加载，再审计原字节，避免校验提前预读被测资源。
			fxHelperStartedNs = System.nanoTime();
			var fx = FXHelper.getFX(id);
			fxHelperEndedNs = System.nanoTime();
			if (fx == null || fx.getFxData().objects().size() != 1
					|| !fx.getFxData().objects().getFirst().name().equals("particle_emitter")) {
				throw new IllegalStateException("原生对照加载后对象类型或数量不符。");
			}
			var resource = context.minecraft().getResourceManager().getResource(resourceId).orElseThrow();
			byte[] raw;
			try (var input = resource.open()) {
				raw = input.readAllBytes();
			}

			requireHash(raw, LobecorpBenchConfig.PHOTON_CONTROL_FX_SHA256, "resource:" + resourceId, resource.sourcePackId());
			var gameDirectory = context.minecraft().gameDirectory.toPath();
			requireHash(Files.readAllBytes(gameDirectory.resolve("ldlib2/assets/photon/fx/first_effect2.fx")),
					LobecorpBenchConfig.PHOTON_CONTROL_FX_SHA256, "ldlib2/assets/photon/fx/first_effect2.fx", "instance_file");
			requireHash(Files.readAllBytes(gameDirectory.resolve("ldlib2/assets/lobecorp/the_queen_of_hatred/particle.material.nbt")),
					LobecorpBenchConfig.PHOTON_CONTROL_MATERIAL_SHA256, "ldlib2/assets/lobecorp/the_queen_of_hatred/particle.material.nbt", "instance_file");
			var textureId = Identifier.parse("lobecorp:the_queen_of_hatred/particle.png");
			var texture = context.minecraft().getResourceManager().getResource(textureId).orElseThrow();
			try (var input = texture.open()) {
				requireHash(input.readAllBytes(), LobecorpBenchConfig.PHOTON_CONTROL_TEXTURE_SHA256,
						"resource:" + textureId, texture.sourcePackId());
			}


			var tag = NbtIo.readCompressed(new ByteArrayInputStream(raw), NbtAccounter.unlimitedHeap());
			checkNativeData(tag);
			var objects = tag.getCompoundOrEmpty("fxData").getListOrEmpty("fxObjects");
			if (objects.size() != 1 || !objects.getCompoundOrEmpty(0).getStringOr("type", "").equals("particle_emitter")
					|| tag.getCompoundOrEmpty("fxData").contains("timeline")) {
				throw new IllegalStateException("原生对照对象或轨道结构已改变。");
			}

			loadEndNs = System.nanoTime();
			effect = new PhotonGeoEffect(fx, context.level());
			effect.anchor(new Vector3f(origin.x, origin.y + 2, origin.z + 5), new Quaternionf(), new Vector3f(1));
			createEndNs = System.nanoTime();
		} catch (Exception problem) {
			throw new IllegalStateException("原生对照来源/类型/依赖断言或冷加载失败。", problem);
		}
	}

	private void requireHash(byte[] raw, String expected, String source, String pack) throws Exception {
		var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
		var entry = new JsonObject();
		entry.addProperty("source", source);
		entry.addProperty("source_pack", pack);
		entry.addProperty("sha256", hash);
		entry.addProperty("bytes", raw.length);
		entry.addProperty("matches_inventory", expected.equals(hash));
		dependencies.add(entry);
		if (!expected.equals(hash)) {
			throw new IllegalStateException("资源与只读审计原件不符：" + source);
		}
	}

	private void checkNativeData(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			var type = compound.getStringOr("type", "");
			if (PhotonResourceInspection.isLegacyType(type) || type.startsWith("lobecorp")) {
				throw new IllegalStateException("原生对照出现扩展类型：" + type);
			}

			for (var entry : compound.entrySet()) {
				if (PhotonResourceInspection.isLegacyField(entry.getKey())
						&& !PhotonInactiveLegacyFieldUtil.allowed(compound, entry.getKey(), entry.getValue())) {
					throw new IllegalStateException("原生对照出现生效扩展键：" + entry.getKey());
				}

				checkNativeData(entry.getValue());
			}
		} else if (tag instanceof ListTag list) {
			for (var item : list) {
				checkNativeData(item);
			}
		}
	}

	@Override
	public BenchClientStepResult warmup(BenchClientContext context) {
		return guarded(context, () -> {
			focusAudit.audit(true, "warmup");
			if (++ticks < LobecorpBenchConfig.PHOTON_CONTROL_WARMUP_TICKS) {
				return BenchClientStepResult.CONTINUE;
			}

			capture.finishPrewarm(context);
			return BenchClientStepResult.COMPLETE;
		});
	}

	@Override
	public BenchClientStepResult measure(BenchClientContext context) {
		return guarded(context, () -> {
			focusAudit.audit(true, stopping ? "spark_finalize" : "measure");
			if (stopping) {
				return finishSpark(context);
			}

			cameraAudit.sample();
			if (!measuring) {
				measuring = true;
				capture.start(LobecorpBenchConfig.PHOTON_CONTROL_MEASURE_SECONDS, () -> {
					emittedNs = System.nanoTime();
					effect.start();
					casts++;
				}, true);
			}

			sampleActivity();
			if (!capture.complete()) {
				return BenchClientStepResult.CONTINUE;
			}

			return finishSpark(context);
		});
	}

	private void sampleActivity() {
		if (emittedNs == 0) {
			return;
		}

		long now = System.nanoTime();
		long particles = 0;
		long activeEmitters = 0;
		for (var object : effect.runtime().objects.values()) {
			if (object instanceof IParticleEmitter emitter) {
				particles += emitter.getParticleAmount();
				if (emitter.isPlaying() && emitter.isVisible()) {
					activeEmitters++;
				}
			}
		}

		boolean active = particles > 0 && activeEmitters > 0;
		peakParticles = Math.max(peakParticles, particles);
		peakActiveEmitters = Math.max(peakActiveEmitters, activeEmitters);
		if (active) {
			if (firstActiveNs == 0) {
				firstActiveNs = now;
			}

			lastActiveNs = now;
			activeSamples++;
			if (previousActive) {
				activeSampleIntervalsNs += now - previousSampleNs;
			}
		}

		previousSampleNs = now;
		previousActive = active;
	}

	private BenchClientStepResult guarded(BenchClientContext context, Supplier<BenchClientStepResult> action) {
		if (failure != null) {
			return finishFailure(context);
		}

		try {
			return action.get();
		} catch (RuntimeException | AssertionError problem) {
			failure = problem;
			failureStartedNs = System.nanoTime();
			return finishFailure(context);
		}
	}

	private BenchClientStepResult finishSpark(BenchClientContext context) {
		if (!stopping) {
			if (!spark.poll()) {
				return BenchClientStepResult.CONTINUE;
			}

			spark.stop();
			stopping = true;
		}

		if (!spark.poll() || !spark.complete()) {
			return BenchClientStepResult.CONTINUE;
		}

		try {
			spark.archive(context);
			sparkArchived = true;
		} catch (Exception problem) {
			throw new IllegalStateException("原生对照 spark 归档失败。", problem);
		}

		return BenchClientStepResult.COMPLETE;
	}

	private BenchClientStepResult finishFailure(BenchClientContext context) {
		try {
			diagnostics.finish(context);
			if (!failureCaptureClosed) {
				failureCaptureClosed = true;
				capture.close(context);
			}

			if (spark != null && System.nanoTime() - failureStartedNs < TimeUnit.SECONDS.toNanos(60)
					&& finishSpark(context) == BenchClientStepResult.CONTINUE) {
				return BenchClientStepResult.CONTINUE;
			}

			if (spark != null && !sparkArchived) {
				throw new IllegalStateException("原生对照失败后 spark 完成未确认。");
			}
		} catch (Exception cleanup) {
			failure.addSuppressed(cleanup);
		}

		try {
			var report = report();
			report.addProperty("failure", failure.toString());
			context.artifacts().write(label + "-failure.json", "application/json", report.toString());
		} catch (Exception cleanup) {
			failure.addSuppressed(cleanup);
		}

		if (failure instanceof RuntimeException runtime) {
			throw runtime;
		}

		throw (AssertionError) failure;
	}

	@Override
	public void verify(BenchClientContext context) throws Exception {
		diagnostics.finish(context);
		var report = report();
		try {
			capture.write(context, report);
			verifyMaterial(report);
			var submissions = report.getAsJsonObject("measure_submissions");
			var other = submissions == null ? null : submissions.getAsJsonObject("other");
			if (casts != 1 || peakParticles <= 0 || activeSamples <= 0 || peakActiveEmitters <= 0
					|| other == null || other.get("cpu_jobs_added").getAsLong() + other.get("instanced_jobs_added").getAsLong() <= 0
					|| other.get("failed_cpu_submissions").getAsLong() != 0
					|| !submissions.get("frame_capture_bound").getAsBoolean()
					|| !submissions.get("draw_call_count_available").getAsBoolean()
					|| submissions.get("draw_calls").getAsLong() <= 0 || submissions.get("failed_drains").getAsLong() != 0
					|| submissions.get("scope_protocol_errors").getAsLong() != 0) {
				throw new AssertionError("原生对照没有完整的粒子活跃、普通 FX jobs 和实际绘制证据。");
			}

			if (cameraAudit.samples() == 0 || cameraAudit.changes() != 0 || cameraAudit.unavailableFrames() != 0
					|| !PhotonBenchCameraAudit.poseMatches(expectedPose, context.automation().pose())) {
				throw new AssertionError("原生对照相机门禁未通过。");
			}

			report.addProperty("real_workload_verified", true);
		} finally {
			context.artifacts().write(label + "-workload.json", "application/json", report.toString());
		}
	}

	private void verifyMaterial(JsonObject report) {
		var emitters = effect.runtime().objects.values().stream().filter(ParticleEmitter.class::isInstance)
				.map(ParticleEmitter.class::cast).toList();
		if (emitters.size() != 1) {
			throw new AssertionError("原生对照运行对象类型不符。");
		}

		var materials = emitters.getFirst().config.renderer.getMaterials();
		if (materials.size() != 1 || !(materials.getFirst().getMaterial() instanceof UIResourceMaterial reference)
				|| !(reference.getRawMaterial() instanceof TextureMaterial texture)
				|| !Identifier.parse("lobecorp:the_queen_of_hatred/particle.png").equals(texture.getTexture())) {
			throw new AssertionError("file 材质未解析到已审计的原生纹理材质。");
		}

		report.addProperty("material_resolution_verified_after_capture", true);
		report.addProperty("material_reference_type", reference.getClass().getName());
		report.addProperty("resolved_material_type", texture.getClass().getName());
		report.addProperty("resolved_texture", texture.getTexture().toString());
	}

	private JsonObject report() {
		var report = new JsonObject();
		report.addProperty("label", label);
		report.addProperty("comparison_group", "native_data_control");
		report.addProperty("fx", LobecorpBenchConfig.PHOTON_CONTROL_FX);
		report.addProperty("source", "resources/ldlib2/assets/photon/fx/first_effect2.fx");
		report.addProperty("guard_mixins_installed", true);
		report.addProperty("no_active_extensions", true);
		report.addProperty("strict_zero_extension_keys", false);
		report.addProperty("inactive_extension_key_count", 12);
		report.addProperty("object_count", 1);
		report.addProperty("particle_emitter_count", 1);
		report.addProperty("emission_duration_ticks", 20);
		report.addProperty("particle_lifetime_ticks", 20);
		report.addProperty("looping", false);
		report.addProperty("child_fx_count", 0);
		report.addProperty("resource_material_count", 1);
		report.addProperty("warmup_ticks", LobecorpBenchConfig.PHOTON_CONTROL_WARMUP_TICKS);
		report.addProperty("primary_capture_seconds", LobecorpBenchConfig.PHOTON_CONTROL_MEASURE_SECONDS);
		report.addProperty("measured_casts", casts);
		report.addProperty("cold_load_started_ns", loadStartNs);
		report.addProperty("cold_load_ended_ns", loadEndNs);
		report.addProperty("fx_helper_started_ns", fxHelperStartedNs);
		report.addProperty("fx_helper_ended_ns", fxHelperEndedNs);
		report.addProperty("fx_helper_use_cache", true);
		report.addProperty("load_timing_scope", "normal FXHelper.getFX first, then source hashes and post-load raw NBT assertions");
		report.addProperty("object_creation_ended_ns", createEndNs);
		report.addProperty("cold_load_and_creation_in_prewarm_capture", true);
		report.addProperty("emitted_ns", emittedNs);
		report.addProperty("first_active_ns", firstActiveNs);
		report.addProperty("last_active_ns", lastActiveNs);
		report.addProperty("observed_active_span_ns", firstActiveNs == 0 ? 0 : lastActiveNs - firstActiveNs);
		report.addProperty("active_sample_intervals_ns", activeSampleIntervalsNs);
		report.addProperty("active_duration_scope", "client_tick observations; includes no interpolation across inactive samples");
		report.addProperty("active_samples", activeSamples);
		report.addProperty("peak_particles", peakParticles);
		report.addProperty("peak_active_emitters", peakActiveEmitters);
		report.addProperty("equal_load_ab", false);
		report.addProperty("comparison_limits", "short single native emitter; 20s average includes idle tail; topology/materials/duration differ from laser");
		report.addProperty("performance_verdict", "NOT_EVALUATED");
		report.addProperty("image_validation", "NOT_USED");
		report.addProperty("world_available_ns", worldAvailableNs);
		report.addProperty("world_settled_ns", worldSettledNs);
		report.addProperty("world_settle_required_ns", TimeUnit.SECONDS.toNanos(10));
		report.addProperty("world_settle_elapsed_ns", worldSettledNs == 0 ? 0 : worldSettledNs - worldAvailableNs);
		report.addProperty("window_geometry_preserved_by_bench_bridge", PhotonBenchMixinPlugin.preservedWindowApplied());
		report.addProperty("bench_input_isolation_contract_active", !System.getProperty("modBench.resultDirectory", "").isBlank());
		report.addProperty("bench_input_isolation_runtime_verification", "not_verified_by_source_contract");
		report.addProperty("shaderpack_enabled", IrisCompat.isUsingShaderPack());
		report.addProperty("expected_shaderpack_enabled", false);
		report.addProperty("sodium", ModList.get().getModContainerById("sodium").orElseThrow().getModInfo().getVersion().toString());
		report.add("dependencies", dependencies.deepCopy());
		report.add("deserialize_diagnostics", diagnostics.snapshot());
		report.add("focus_audit", focusAudit.snapshot());
		report.addProperty("camera_audit_samples", cameraAudit.samples());
		report.addProperty("camera_audit_changes", cameraAudit.changes());
		report.addProperty("camera_audit_unavailable_frames", cameraAudit.unavailableFrames());
		if (cameraAudit.first() != null) {
			report.add("camera_audit_first", cameraAudit.first());
			report.add("camera_audit_last", cameraAudit.last());
		}

		if (spark != null) {
			report.add("spark_client", spark.snapshot());
			report.addProperty("spark_archived", sparkArchived);
		}

		return report;
	}

	@Override
	public void teardown(BenchClientContext context) {
		RuntimeException cleanupFailure = null;
		Runnable[] cleanupActions = {
				() -> diagnostics.finish(context),
				() -> closeSpark(context),
				() -> {
					if (capture != null) {
						capture.close(context);
					}
				},
				() -> {
					if (effect != null) {
						effect.close();
					}
				},
				() -> {
					if (poseHold != null) {
						poseHold.close();
					}
				},
				() -> restorePlayer(context)
		};
		for (var action : cleanupActions) {
			try {
				action.run();
			} catch (RuntimeException problem) {
				if (cleanupFailure == null) {
					cleanupFailure = problem;
				} else {
					cleanupFailure.addSuppressed(problem);
				}
			}
		}

		if (cleanupFailure != null) {
			throw cleanupFailure;
		}
	}

	private void closeSpark(BenchClientContext context) {
		if (spark == null) {
			return;
		}

		spark.close();
		try {
			context.artifacts().write(label + "-spark-teardown.json", "application/json", spark.snapshot().toString());
		} catch (Exception problem) {
			throw new IllegalStateException("原生对照 spark 收尾状态归档失败。", problem);
		}
	}

	private void restorePlayer(BenchClientContext context) {
		if (originalPose == null) {
			return;
		}

		context.player().getAbilities().flying = originalFlying;
		context.player().setDeltaMovement(Vec3.ZERO);
		context.player().onUpdateAbilities();
		context.automation().setPose(originalPose);
	}
}
