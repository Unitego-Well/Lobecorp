package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.util.photon.playback.PhotonRuntimePreviewUtil;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.performance.PhotonCastProfiler;

import javax.annotation.Nullable;
import java.io.IOException;

@Mixin(FXRuntime.class)
public abstract class FXRuntimeMixin implements PhotonNamedAccess {







	@Inject(method = "emit(Lcom/lowdragmc/photon/client/fx/IEffectExecutor;ILjava/lang/String;)V", at = @At("RETURN"))
	private void lobecorp$previewAfterRestart(CallbackInfo ci) {
		if (lobecorp$named != null) {
			lobecorp$named.begin();
		}

		PhotonRuntimePreviewUtil.apply((FXRuntime) (Object) this);
	}

	@Unique
	private @Nullable PhotonNamedRuntime lobecorp$named;
	@Unique
	private boolean lobecorp$prepared;

	@Override
	public @Nullable PhotonNamedRuntime lobecorp$namedRuntime() {
		return lobecorp$named;
	}

	@Inject(method = "emit(Lcom/lowdragmc/photon/client/fx/IEffectExecutor;ILjava/lang/String;)V", at = @At("HEAD"))
	private void lobecorp$prepareNamed(CallbackInfo ci) {
		boolean diagnostic = PhotonCastProfiler.enabled();
		long started = diagnostic ? System.nanoTime() : 0;
		boolean wasPrepared = lobecorp$prepared;
		boolean matchesChecked = false;
		boolean matches = false;
		boolean completed = false;
		String reason = "not_prepared";
		try {
			if (lobecorp$prepared) {
				lobecorp$prepared = false;
				reason = "named_missing";
				if (lobecorp$named != null) {
					matchesChecked = true;
					reason = "matches_controller";
					matches = lobecorp$named.matchesController();
					reason = matches ? "reuse" : "controller_changed";
					if (matches) {
						completed = true;
						return;
					}
				}
			}
			lobecorp$buildNamed();
			completed = true;
		} finally {
			if (diagnostic) {
				long ended = System.nanoTime();
				Lobecorp.LOGGER.info("CASTPROF prepareNamed runtime={} startNs={} endNs={} elapsedNs={} prepared={} matchesChecked={} matchesController={} reason={} completed={}",
						System.identityHashCode(this), started, ended, ended - started, wasPrepared,
						matchesChecked, matches, reason, completed);
			}
		}
	}

	@Override
	public void lobecorp$prepareNamedRuntime() {
		if (!lobecorp$prepared) {
			lobecorp$buildNamed();
			lobecorp$prepared = true;
		}
	}

	@Unique
	private void lobecorp$buildNamed() {
		boolean diagnostic = PhotonCastProfiler.enabled();
		long started = diagnostic ? System.nanoTime() : 0;
		String phase = "close_old";
		try {
			lobecorp$closeNamed();
			phase = "controllers";
			var runtime = (FXRuntime) (Object) this;
			var controllers = runtime.objects.values().stream().filter(PhotonNamedController.class::isInstance)
					.map(PhotonNamedController.class::cast).toList();

			if (controllers.size() > 1) {
				throw new IllegalArgumentException("一个特效实例不能含多个命名控制器。");
			}

			if (!controllers.isEmpty()) {
				phase = "create_named";
				try {
					lobecorp$named = new PhotonNamedRuntime(runtime, controllers.getFirst());
				} catch (IOException invalid) {
					throw new IllegalArgumentException("命名动画库适配失败。", invalid);
				}
			}
			phase = "completed";
		} finally {
			if (diagnostic) {
				long ended = System.nanoTime();
				Lobecorp.LOGGER.info("CASTPROF buildNamed runtime={} startNs={} endNs={} elapsedNs={} phase={} named={}",
						System.identityHashCode(this), started, ended, ended - started, phase,
						System.identityHashCode(lobecorp$named));
			}
		}
	}

	@Inject(method = "destroy", at = @At("HEAD"))
	private void lobecorp$stopNamed(CallbackInfo ci) {
		lobecorp$closeNamed();
	}

	@Unique
	private void lobecorp$closeNamed() {
		lobecorp$prepared = false;
		if (lobecorp$named != null) {
			lobecorp$named.close();
			lobecorp$named = null;
		}
	}


	@Unique
	private long lobecorp$profileGeneration;

	@WrapMethod(method = "emit(Lcom/lowdragmc/photon/client/fx/IEffectExecutor;ILjava/lang/String;)V")
	private void lobecorp$captureEmission(IEffectExecutor executor, int delay, @Nullable String startSection, Operation<Void> original) {
		boolean diagnostic = PhotonCastProfiler.enabled();
		long started = diagnostic ? System.nanoTime() : 0;
		boolean emissionCompleted = false;
		try {
			if (!PhotonPerformanceTraceUtil.enabled(Stage.FX_EMIT)) {
				original.call(executor, delay, startSection);
				emissionCompleted = true;
				return;
			}
			int node = PhotonPerformanceOwnerUtil.runtime((FXRuntime) (Object) this);
			PhotonPerformanceTraceUtil.generation(node, (int) ++lobecorp$profileGeneration);
			PhotonPerformanceTraceUtil.mark(Stage.FX_EMIT, node, "emit", "", lobecorp$profileGeneration, delay);
			long scope = PhotonPerformanceTraceUtil.begin(Stage.FX_EMIT, node);
			boolean completed = false;
			try {
				original.call(executor, delay, startSection);
				completed = true;
				emissionCompleted = true;
			} finally {
				PhotonPerformanceTraceUtil.end(scope, completed);
			}
		} finally {
			if (diagnostic) {
				long ended = System.nanoTime();
				Lobecorp.LOGGER.info("CASTPROF emit runtime={} startNs={} endNs={} elapsedNs={} delay={} completed={}",
						System.identityHashCode(this), started, ended, ended - started, delay, emissionCompleted);
			}
		}
	}

	@WrapMethod(method = "destroy")
	private void lobecorp$captureDestruction(boolean force, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.CLEANUP)) {
			original.call(force);
			return;
		}
		long scope = PhotonPerformanceTraceUtil.begin(Stage.CLEANUP, PhotonPerformanceOwnerUtil.runtime((FXRuntime) (Object) this));
		boolean completed = false;
		try {
			original.call(force);
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}
}
