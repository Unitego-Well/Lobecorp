package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.mixin.photon.editor.client.FXPackExporterAccessor;
import org.unitego.lobecorp.util.photon.model.PhotonModelSourceUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/// 独立正确性场景，不采样性能，不改旧场景的世界或负载。
public class PhotonWorkspaceReviewScenario implements BenchClientScenario {
	public static final String ID = "lobecorp.photon-07-workspace-review";
	private final PhotonMigrationNativeCheck common = new PhotonMigrationNativeCheck();
	private final String label = "photon_review_" + UUID.randomUUID().toString().replace("-", "");
	private final Object world = new Object();
	private FX source;
	private PhotonRuntimeFactory.Definition definition;
	private PhotonRuntimeFactory.Lease lease;
	private FXRuntime backend;
	private PhotonChildSpawner owner;
	private UUID targetId;
	private UUID ownerId;
	private long phaseNs;
	private long tick;
	private long frame;
	private long idleBaseline;
	private int rounds;
	private boolean commonDone;
	private boolean settled;
	private boolean waitingForChildren;
	private boolean waitingForFence;
	private boolean complete;

	@Override
	public void setup(BenchClientContext context) {
		phaseNs = System.nanoTime();
		var handle = context.minecraft().getWindow().handle();
		if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) != GLFW.GLFW_TRUE) {
			GLFW.glfwMaximizeWindow(handle);
		}
	}

	@Override
	public BenchClientStepResult stabilize(BenchClientContext context) {
		require(System.nanoTime() - phaseNs < TimeUnit.SECONDS.toNanos(30), "review_window_timeout");
		return context.minecraft().level != null && context.minecraft().player != null
				&& GLFW.glfwGetWindowAttrib(context.minecraft().getWindow().handle(), GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE
				? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
	}

	@Override
	public BenchClientStepResult warmup(BenchClientContext context) {
		return BenchClientStepResult.COMPLETE;
	}

	@Override
	public BenchClientStepResult measure(BenchClientContext context) throws Exception {
		if (!commonDone) {
			if (common.step(context, label)) {
				commonDone = true;
				verifyModelExport(context);
				markFence();
			}
			return BenchClientStepResult.CONTINUE;
		}
		require(System.nanoTime() - phaseNs < TimeUnit.SECONDS.toNanos(30), "review_lifecycle_timeout");
		if (!settled) {
			if (!fenced()) {
				return BenchClientStepResult.CONTINUE;
			}
			idleBaseline = PhotonRuntimeFactory.statistics().get("pool_idle");
			prepareParent();
			settled = true;
			beginRound(context);
		} else if (waitingForChildren) {
			if (owner.getParticleAmount() != 0) {
				return BenchClientStepResult.CONTINUE;
			}
			require(backend.isFinished(), "review_parent_never_finishes");
			lease.close(false);
			waitingForChildren = false;
			waitingForFence = true;
			markFence();
		} else if (waitingForFence && fenced()) {
			require(PhotonRuntimeFactory.statistics().get("pool_idle") <= idleBaseline + 1, "review_project_idle_accumulates");
			rounds++;
			if (rounds < 3) {
				beginRound(context);
			} else {
				complete = true;
				writeResult(context);
				return BenchClientStepResult.COMPLETE;
			}
		}
		return BenchClientStepResult.CONTINUE;
	}

	private void prepareParent() {
		source = new FX();
		var target = new ParticleEmitter();
		target.setName("review_parent_target");
		target.config.setLooping(true);
		target.config.setDuration(20);
		target.config.setStartLifetime(NumberFunction.constant(4));
		target.config.emission.setEmissionRate(NumberFunction.constant(1));
		var spawner = new PhotonChildSpawner();
		spawner.setName("review_parent_owner");
		spawner.config.source = PhotonSpawnConfig.Source.PROJECT;
		spawner.config.includeChildren = false;
		spawner.config.endBehavior = PhotonSpawnConfig.EndBehavior.STOP_EMISSION;
		spawner.config.template.setTransform(target.transform());
		source.getFxData().objects().addAll(List.of(target, spawner));
		targetId = target.id();
		ownerId = spawner.id();
		definition = PhotonRuntimeFactory.registerPreparedSource(source,
				new PhotonRuntimeFactory.PreparedSourceKey(label, 1));
	}

	private void beginRound(BenchClientContext context) {
		var next = PhotonRuntimeFactory.acquire(definition, world, PhotonRuntimeFactory.Mode.HYBRID);
		if (backend != null) {
			require(next.runtime() == backend, "review_parent_backend_changed");
		}
		lease = next;
		backend = next.runtime();
		var player = Objects.requireNonNull(context.minecraft().player, "review_player_missing");
		backend.root.transform().localPosition(new Vector3f((float) player.getX(), (float) player.getY() + 1, (float) player.getZ()));
		var level = Objects.requireNonNull(context.minecraft().level, "review_level_missing");
		backend.emit(() -> level);
		((ParticleEmitter) backend.objects.get(targetId)).setSelfActive(false);
		owner = (PhotonChildSpawner) backend.objects.get(ownerId);
		owner.source(backend, Set.of());
		require(owner.emitNow(1) == 1 && owner.getParticleAmount() == 1, "review_child_not_started");
		owner.remove(false);
		waitingForChildren = true;
		waitingForFence = false;
		phaseNs = System.nanoTime();
	}

	private void markFence() {
		tick = PhotonRuntimeFactory.ticks();
		frame = PhotonRuntimeFactory.frames();
		phaseNs = System.nanoTime();
	}

	private boolean fenced() {
		return PhotonRuntimeFactory.ticks() - tick >= 3 && PhotonRuntimeFactory.frames() - frame >= 3;
	}

	private void verifyModelExport(BenchClientContext context) throws Exception {
		var file = context.artifacts().write(label + "_source.obj", "text/plain", triangle(1));
		var config = new PhotonModelOutputConfig(false);
		config.source = PhotonModelOutputConfig.Source.FILE;
		config.file = file.toString();
		config.setAssetSnapshot(PhotonModelSourceUtil.load(config).snapshot());
		config.snapshotSource = "FILE|null|" + config.file + "|false";
		var model = new PhotonModelOutput(config, null);
		var authored = model.serializeWrapper();
		authored.putString("future_object", "preserved");
		authored.getCompoundOrEmpty("data").getCompoundOrEmpty("config").putString("future_config", "preserved");
		var copy = authored.copy();
		Files.write(file, triangle(4));
		var exporter = FXPackExporterAccessor.lobecorp$create(Platform.getFrozenRegistry());
		((FXPackExporterAccessor) (Object) exporter).lobecorp$walk(copy);
		if (!(IFXObject.deserializeWrapper(copy) instanceof PhotonModelOutput exported)) {
			throw new IllegalStateException("review_export_model_type");
		}
		require(exported.config.source == PhotonModelOutputConfig.Source.EMBEDDED
				&& PhotonModelSourceUtil.load(exported.config).definition().vertices().get(1).x() == 4, "review_export_stale_file");
		require(copy.getStringOr("future_object", "").equals("preserved")
						&& copy.getCompoundOrEmpty("data").getCompoundOrEmpty("config").getStringOr("future_config", "").equals("preserved"),
				"review_export_unknown_lost");
		require(config.source == PhotonModelOutputConfig.Source.FILE
						&& PhotonModelSourceUtil.load(config).definition().vertices().get(1).x() == 1
						&& authored.getCompoundOrEmpty("data").getCompoundOrEmpty("config").getStringOr("future_config", "").equals("preserved"),
				"review_export_mutated_author");
	}

	private static byte[] triangle(int x) {
		return ("v 0 0 0\nv " + x + " 0 0\nv 0 1 0\nf 1 2 3\n").getBytes(StandardCharsets.UTF_8);
	}

	private void writeResult(BenchClientContext context) throws Exception {
		var report = new JsonObject();
		report.addProperty("passed", true);
		report.addProperty("label", label);
		report.addProperty("creation_mode", PhotonRuntimeFactory.mode().name());
		report.addProperty("shader", IrisCompat.isUsingShaderPack());
		report.addProperty("performance_acceptance", false);
		report.addProperty("child_stopped_completion_rounds", rounds);
		report.addProperty("project_pool_bounded", true);
		report.addProperty("native_pack_walk_file_snapshot", true);
		report.addProperty("unknown_fields_preserved", true);
		report.addProperty("pid", ProcessHandle.current().pid());
		context.artifacts().write(label + "_workspace_review.json", "application/json", report.toString());
	}

	@Override
	public void verify(BenchClientContext context) {
		common.verify();
		require(complete && rounds == 3, "review_incomplete");
		require(IrisCompat.isUsingShaderPack() == Boolean.parseBoolean(System.getProperty("lobecorp.photon.review.expectedShader", "false")),
				"review_shader_mismatch");
	}

	@Override
	public void teardown(BenchClientContext context) {
		common.close();
		if (lease != null) {
			lease.close(true);
		}
		if (source != null) {
			PhotonRuntimeFactory.unregisterPreparedSource(source);
		}
	}

	private static void require(boolean valid, String reason) {
		if (!valid) {
			throw new IllegalStateException(reason);
		}
	}
}
