package org.unitego.lobecorp.mixin.photon.animation.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.PhotonParticleManager;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationTab;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationPreviewScope;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationRuntimeAccess;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationWorkspace;
import org.unitego.lobecorp.util.photon.runtime.PhotonEmissionPrimeUtil;

/// 保留原场景的合并请求和逐 tick 粒子重放，仅在命名工作区准备本地播放实例。
@Mixin(SceneView.class)
public abstract class PhotonAnimationSceneSimulationMixin {
	@Shadow
	@Final
	public FXEditor fxEditor;
	@Shadow
	@Final
	public PhotonParticleManager particleManager;

	@WrapMethod(method = "simulateTo")
	private void lobecorp$quietReplay(long time, Operation<Void> original) {
		var workspace = PhotonAnimationTab.editing(fxEditor);
		if (workspace != null) {
			workspace.preserveTestPlayback();
		}

		PhotonAnimationPreviewScope.enter();
		try {
			original.call(time);
		} finally {
			PhotonAnimationPreviewScope.leave();
		}
	}

	@WrapOperation(method = "simulateTo", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/PhotonParticleManager;getTime()J"))
	private long lobecorp$replayFromStart(PhotonParticleManager manager, Operation<Long> original) {
		var workspace = PhotonAnimationTab.editing(fxEditor);
		if (PhotonAnimationTab.replayingScene(fxEditor)) {
			return Long.MAX_VALUE;
		}

		return workspace != null && workspace.replayFromStart() ? Long.MAX_VALUE : original.call(manager);
	}

	@WrapOperation(method = "simulateTo", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/FXRuntime;emit(Lcom/lowdragmc/photon/client/fx/IEffectExecutor;)V"))
	private void lobecorp$prepareReplay(FXRuntime runtime, IEffectExecutor effect, Operation<Void> original) {
		var workspace = lobecorp$emitPreview(runtime, effect, original);
		if (workspace != null) {
			workspace.prepareTimelineSimulation();
		}

		PhotonEmissionPrimeUtil.prime(runtime);
		PhotonEmissionPrimeUtil.flushScene(particleManager);
	}

	@WrapOperation(method = "play", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/FXRuntime;emit(Lcom/lowdragmc/photon/client/fx/IEffectExecutor;)V"))
	private void lobecorp$preparePlayback(FXRuntime runtime, IEffectExecutor effect, Operation<Void> original) {
		if (lobecorp$emitPreview(runtime, effect, original) != null) {
			return;
		}

		PhotonEmissionPrimeUtil.prime(runtime);
		PhotonEmissionPrimeUtil.flushScene(particleManager);
	}

	@Unique
	private @Nullable PhotonAnimationWorkspace lobecorp$emitPreview(FXRuntime runtime, IEffectExecutor effect,
	                                                                Operation<Void> original) {
		var workspace = PhotonAnimationTab.editing(fxEditor);
		((PhotonAnimationRuntimeAccess) runtime).lobecorp$setScenePreview(workspace == null);
		if (workspace == null) {
			original.call(runtime, effect);
			return null;
		}

		PhotonAnimationPreviewScope.enterAuthoring(runtime);
		try {
			original.call(runtime, effect);
		} finally {
			PhotonAnimationPreviewScope.leaveAuthoring();
		}

		return workspace;
	}

	@Inject(method = "simulateTo", at = @At("RETURN"))
	private void lobecorp$finishReplay(long time, CallbackInfo ci) {
		var workspace = PhotonAnimationTab.editing(fxEditor);
		if (workspace != null) {
			workspace.finishTimelineSimulation();
		} else {
			PhotonAnimationTab.finishSceneRestore(fxEditor);
		}
	}
}
