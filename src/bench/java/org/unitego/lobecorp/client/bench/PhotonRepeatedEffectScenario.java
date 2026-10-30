package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import org.unitego.lobecorp.client.photon.performance.PhotonRepeatedEffectTrial;
import org.unitego.lobecorp.client.photon.performance.PhotonSubmissionDiagnostics;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.config.photon.PhotonRepeatedEffectConfig;
import org.unitego.lobecorp.mixin.PhotonBenchMixinPlugin;
import org.unitego.lobecorp.registry.photon.client.LcPhotonMcpCommands;
import net.neoforged.fml.ModList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.nio.file.StandardOpenOption;

import org.unitego.lobecorp.client.photon.performance.PhotonRepeatedEffectAssets;

/// Bench 仅负责外部诊断与归档；工作负载和帧采集全部由 main 中的共用核心持有。
public class PhotonRepeatedEffectScenario implements BenchClientScenario {
	private final String label = "photon_repeat_full_" + UUID.randomUUID().toString().replace("-", "");
	private final PhotonBenchDiagnostics diagnostics = new PhotonBenchDiagnostics(label, "repeat_full", PhotonRepeatedEffectConfig.SCENARIO);
	private PhotonBenchSpark spark;
	private boolean diagnosticsStarted;
	private boolean sparkStopping;
	private boolean archived;
	private boolean started;
	private Exception failure;
	private long failureNs;

	@Override
	public void setup(BenchClientContext context) throws Exception {
		if (!PhotonRepeatedEffectConfig.SCENARIO.equals(System.getProperty("modBench.scenarios", ""))
				|| !PhotonBenchMixinPlugin.preservedWindowApplied()
				|| System.getProperty("modBench.resultDirectory", "").isBlank()
				|| !ModList.get().isLoaded(LcPhotonMcpCommands.MOD_ID)
				|| !Boolean.getBoolean("lobecorp.photon.diag.cast")
				|| !Boolean.getBoolean(PhotonSubmissionDiagnostics.PROBES_PROPERTY)) {
			throw new IllegalStateException("repeat_full_requires_exact_03_preserved_window_cast_and_explicit_probes");
		}

		var control = System.getProperty(LobecorpBenchConfig.PHOTON_CONTROL_FX_PROPERTY, "");
		if (!control.isBlank() && !PhotonRepeatedEffectConfig.NATIVE_FX.equals(control)) {
			throw new IllegalStateException("repeat_unknown_control_fx");
		}

		PhotonRepeatedEffectTrial.start(control.isBlank() ? PhotonRepeatedEffectTrial.Kind.LASER : PhotonRepeatedEffectTrial.Kind.NATIVE,
				PhotonRepeatedEffectTrial.Mode.FULL, label, () -> ready(context));
		started = true;
	}

	private boolean ready(BenchClientContext context) {
		if (!diagnosticsStarted) {
			diagnostics.start();
			diagnosticsStarted = true;
			spark = PhotonBenchSpark.start(context.minecraft(), label);
		}

		return spark.poll();
	}

	@Override
	public BenchClientStepResult measure(BenchClientContext context) throws Exception {
		if (failure != null) {
			return finishFailure(context);
		}

		try {
			return measureStep(context);
		} catch (Exception problem) {
			failure = problem;
			failureNs = System.nanoTime();
			try {
				PhotonRepeatedEffectTrial.cancel(label);
			} catch (Exception cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}

			return BenchClientStepResult.CONTINUE;
		}
	}

	private BenchClientStepResult finishFailure(BenchClientContext context) throws Exception {
		var status = PhotonRepeatedEffectTrial.status(label);
		boolean pending = !status.get("terminal").getAsBoolean() || status.get("cleanup_pending").getAsBoolean();
		try {
			diagnostics.finish(context);
			if (spark != null) {
				spark.close();
				var sparkStatus = spark.snapshot();
				status.add("spark", sparkStatus);
				pending |= sparkStatus.get("cleanup_unconfirmed").getAsBoolean();
			}
		} catch (Exception cleanupFailure) {
			status.addProperty("diagnostic_cleanup_error", cleanupFailure.toString());
		}

		if (pending && System.nanoTime() - failureNs < TimeUnit.SECONDS.toNanos(60)) {
			return BenchClientStepResult.CONTINUE;
		}

		status.addProperty("adapter_failure", failure.toString());
		status.addProperty("cleanup_unconfirmed", pending);
		context.artifacts().write(label + "-failure.json", "application/json", status.toString());
		throw failure;
	}

	private BenchClientStepResult measureStep(BenchClientContext context) throws Exception {
		// ClientRuntimeEvents 是唯一 tick 驱动，Bench 不重复推进调度。
		var status = PhotonRepeatedEffectTrial.status(label);
		if (!status.get("terminal").getAsBoolean()) {
			return BenchClientStepResult.CONTINUE;
		}

		diagnostics.finish(context);
		if (spark != null) {
			if (!sparkStopping) {
				if (!spark.poll()) {
					return BenchClientStepResult.CONTINUE;
				}

				spark.stop();
				sparkStopping = true;
			}

			if (!spark.poll() || !spark.complete()) {
				return BenchClientStepResult.CONTINUE;
			}

			spark.archive(context);
		}

		archive(context, status);
		if (!"COMPLETE".equals(status.get("state").getAsString())) {
			throw new IllegalStateException("repeat_failed:" + status);
		}

		return BenchClientStepResult.COMPLETE;
	}

	private void archive(BenchClientContext context, JsonObject status) throws Exception {
		if (archived) {
			return;
		}

		var directory = Path.of(status.get("output_directory").getAsString());
		var diagnosticDirectory = Files.createDirectory(directory.resolve("full_diagnostics"));
		if (diagnosticsStarted) {
			Files.writeString(diagnosticDirectory.resolve("deserialize-details.json"), diagnostics.details().toString(), StandardOpenOption.CREATE_NEW);
		}
		Files.writeString(diagnosticDirectory.resolve("diagnostics.json"), diagnostics.snapshot().toString(), StandardOpenOption.CREATE_NEW);
		if (spark != null && spark.complete()) {
			var snapshot = spark.snapshot();
			var source = Path.of(snapshot.get("profile_path").getAsString());
			var target = diagnosticDirectory.resolve("client.sparkprofile");
			Files.copy(source, target);
			if (!snapshot.get("profile_sha256").getAsString().equals(PhotonRepeatedEffectAssets.sha256(Files.readAllBytes(target)))) {
				throw new IllegalStateException("repeat_spark_owned_copy_hash");
			}

			Files.writeString(diagnosticDirectory.resolve("spark.json"), snapshot.toString(), StandardOpenOption.CREATE_NEW);
		}
		var frontend = new JsonObject();
		frontend.add("deserialize", diagnostics.snapshot());
		frontend.addProperty("bench_jfr_archive_owner", "BenchMod; recording closes after scenario verify/teardown");
		if (spark != null) {
			frontend.add("spark", spark.snapshot());
		}

		var hashes = new JsonArray();
		try (var stream = Files.list(diagnosticDirectory)) {
			for (var path : stream.filter(Files::isRegularFile).toList()) {
				PhotonRepeatedEffectAssets.file(path, null, hashes);
			}
		}

		frontend.add("owned_files", hashes);
		PhotonRepeatedEffectTrial.recordFrontendDiagnostics(label, frontend);
		var mappings = new JsonArray();
		try (var stream = Files.walk(directory)) {
			int index = 0;
			for (var path : stream.filter(Files::isRegularFile).toList()) {
				if (path.equals(directory.resolve("trial.json"))) {
					continue;
				}

				var filename = label + "_" + index++ + "_" + path.getFileName();
				var bytes = Files.readAllBytes(path);
				var archivedPath = context.artifacts().write(filename, "application/octet-stream", bytes);
				var mapping = new JsonObject();
				mapping.addProperty("owned_file", path.toString());
				mapping.addProperty("bench_artifact", archivedPath.toString());
				mapping.addProperty("sha256", PhotonRepeatedEffectAssets.sha256(bytes));
				mappings.add(mapping);
			}
		}

		frontend.add("bench_artifacts", mappings);
		PhotonRepeatedEffectTrial.recordFrontendDiagnostics(label, frontend);
		Files.writeString(directory.resolve("bench-artifacts.json"), mappings.toString(), StandardOpenOption.CREATE_NEW);
		context.artifacts().write(label + "-archive-manifest.json", "application/json", mappings.toString());
		context.artifacts().write(label + "-trial.json", "application/json", Files.readString(directory.resolve("trial.json")));
		context.artifacts().write(label + "-status.json", "application/json", PhotonRepeatedEffectTrial.status(label).toString());
		archived = true;
		NativeMaterialPreparationCheck.captureMainWindowCounters(context, label);
	}

	@Override
	public void verify(BenchClientContext context) throws Exception {
		var status = PhotonRepeatedEffectTrial.status(label);
		if (!archived || !"COMPLETE".equals(status.get("state").getAsString())
				|| !status.get("workload_verified").getAsBoolean() || spark == null || !spark.complete()) {
			throw new IllegalStateException("repeat_verification_incomplete");
		}

		PhotonPersistedMetadataNativeCheck.verify(context, label);
		PhotonRuntimeCopyNativeCheck.verify(context, label);
		PhotonRuntimeLifetimeNativeCheck.verify(context, label);
		NativeMaterialPreparationCheck.verify(context, label);
	}

	@Override
	public void teardown(BenchClientContext context) throws Exception {
		try {
			if (started) {
				PhotonRepeatedEffectTrial.cancel(label);
				context.artifacts().write(label + "-teardown.json", "application/json", PhotonRepeatedEffectTrial.status(label).toString());
			}
		} finally {
			try {
				diagnostics.finish(context);
			} finally {
				if (spark != null) {
					spark.close();
				}
			}
		}
	}
}
