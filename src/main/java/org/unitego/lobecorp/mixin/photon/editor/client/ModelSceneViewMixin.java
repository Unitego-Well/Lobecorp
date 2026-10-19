package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.PhotonParticleManager;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Metric;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/// 仅观察原生场景请求、重放和重置，不改变模拟执行。
@Mixin(SceneView.class)
public abstract class ModelSceneViewMixin {



	@Shadow
	@Final
	public PhotonParticleManager particleManager;
	@Shadow
	private long pendingSimulateTarget;
	@Unique
	private long lobecorp$requestTime;
	@Unique
	private long lobecorp$requestTarget;
	@Unique
	private long lobecorp$requestSession;
	@Unique
	private long lobecorp$processedTicks;
	@Unique
	private long lobecorp$simulationBudget;
	@Unique
	private boolean lobecorp$recordingSimulation;

	@WrapMethod(method = "requestSimulateTo")
	private void lobecorp$captureRequest(long time, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.UI_INPUT)) {
			original.call(time);
			return;
		}
		int node = PhotonPerformanceTraceUtil.node((SceneView) (Object) this);
		PhotonPerformanceTraceUtil.counter(node, Metric.REQUESTS, 1);
		if (pendingSimulateTarget >= 0) {
			PhotonPerformanceTraceUtil.counter(node, Metric.COALESCED_REQUESTS, 1);
		}
		lobecorp$requestSession = PhotonPerformanceTraceUtil.sessionId();
		lobecorp$requestTime = System.nanoTime();
		lobecorp$requestTarget = Math.max(0, time);
		PhotonPerformanceTraceUtil.mark(Stage.UI_INPUT, node, "seek_request", "coalesced_request", time, particleManager.getTime());
		original.call(time);
	}

	@WrapMethod(method = "simulateTo")
	private void lobecorp$captureSeek(long time, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.SEEK)) {
			original.call(time);
			return;
		}
		int node = PhotonPerformanceTraceUtil.node((SceneView) (Object) this);
		long scope = PhotonPerformanceTraceUtil.begin(Stage.SEEK, node, time);
		boolean queued = lobecorp$requestSession == PhotonPerformanceTraceUtil.sessionId()
				&& lobecorp$requestTime != 0 && lobecorp$requestTarget == time;
		PhotonPerformanceTraceUtil.mark(Stage.SEEK, node, "seek_begin", queued ? "queued" : "direct",
				particleManager.getTime(), time);
		if (queued) {
			PhotonPerformanceTraceUtil.mark(Stage.SEEK, node, "seek_queue_ns", "",
					System.nanoTime() - lobecorp$requestTime, time);
		}
		lobecorp$requestTime = 0;
		boolean completed = false;
		try {
			original.call(time);
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.mark(Stage.SEEK, node, completed ? "seek_complete" : "seek_failed",
					"manager_time_is_not_processed_ticks", particleManager.getTime(), time);
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@WrapMethod(method = "reset")
	private void lobecorp$captureReset(Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.FX_RESET)) {
			original.call();
			return;
		}
		int node = PhotonPerformanceTraceUtil.node((SceneView) (Object) this);
		PhotonPerformanceTraceUtil.counter(node, Metric.RESET_COUNT, 1);
		long scope = PhotonPerformanceTraceUtil.begin(Stage.FX_RESET, node);
		boolean completed = false;
		try {
			original.call();
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@WrapMethod(method = "runSimulationTicks")
	private void lobecorp$captureSimulation(long ticks, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.REPLAY)) {
			original.call(ticks);
			return;
		}
		int node = PhotonPerformanceTraceUtil.node((SceneView) (Object) this);
		long scope = PhotonPerformanceTraceUtil.begin(Stage.REPLAY, node, ticks);
		long previousTicks = lobecorp$processedTicks;
		long previousBudget = lobecorp$simulationBudget;
		boolean previousRecording = lobecorp$recordingSimulation;
		lobecorp$processedTicks = 0;
		lobecorp$simulationBudget = 0;
		lobecorp$recordingSimulation = true;
		boolean completed = false;
		try {
			original.call(ticks);
			completed = true;
		} finally {
			long processed = lobecorp$processedTicks;
			long fast = Math.min(processed, Math.max(0, lobecorp$simulationBudget - 2));
			PhotonPerformanceTraceUtil.counter(node, Metric.REPLAY_REQUESTED_TICKS, ticks);
			PhotonPerformanceTraceUtil.counter(node, Metric.REPLAY_TICKS, processed);
			PhotonPerformanceTraceUtil.counter(node, Metric.FAST_SIMULATION_TICKS, fast);
			PhotonPerformanceTraceUtil.counter(node, Metric.FULL_SIMULATION_TICKS, processed - fast);
			PhotonPerformanceTraceUtil.mark(Stage.REPLAY, node,
					completed && processed == ticks ? "replay_complete" : "replay_truncated_or_failed", "", ticks, processed);
			lobecorp$processedTicks = previousTicks;
			lobecorp$simulationBudget = previousBudget;
			lobecorp$recordingSimulation = previousRecording;
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@WrapOperation(method = "runSimulationTicks", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(JJ)J"))
	private long lobecorp$captureBudget(long requested, long limit, Operation<Long> original) {
		long result = original.call(requested, limit);
		if (lobecorp$recordingSimulation) {
			lobecorp$simulationBudget = result;
		}
		return result;
	}

	@WrapOperation(method = "runSimulationTicks", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/PhotonParticleManager;tickInternal()V"))
	private void lobecorp$countProcessedTick(PhotonParticleManager manager, Operation<Void> original) {
		original.call(manager);
		if (lobecorp$recordingSimulation) {
			lobecorp$processedTicks++;
		}
	}

}
