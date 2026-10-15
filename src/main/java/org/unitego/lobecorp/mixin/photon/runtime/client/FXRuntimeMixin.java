package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.ISceneObject;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationRuntimeAccess;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationPreviewScope;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffect;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;

@Mixin(FXRuntime.class)
public abstract class FXRuntimeMixin implements PhotonAnimationRuntimeAccess {
	@Unique
	private @Nullable PhotonAnimationRuntime lobecorp$animations;
	@Unique
	private boolean lobecorp$scenePreview;

	/// 只补充 Geo 实例的独立模拟倍率，保留原生时间轴倍率与随后速度轨道的覆盖顺序。
	@WrapOperation(method = "lambda$new$0", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/IFXObject;setSelfTimeScale(F)V"))
	private void lobecorp$geoSimulationScale(IFXObject root, float rate, Operation<Void> original) {
		float speed = root.getEffectExecutor() instanceof PhotonGeoEffect effect && !effect.isStopped()
				? rate * effect.simulationSpeed() : rate;
		original.call(root, speed);
	}

	@Override
	public @Nullable PhotonAnimationRuntime lobecorp$animations() {
		return lobecorp$animations;
	}

	@Override
	public void lobecorp$setScenePreview(boolean sceneOnly) {
		lobecorp$scenePreview = sceneOnly;
		if (sceneOnly) {
			lobecorp$closeAnimations(null);
		}
	}

	@Inject(method = "emit(Lcom/lowdragmc/photon/client/fx/IEffectExecutor;I)V", at = @At("HEAD"))
	private void lobecorp$startAnimations(CallbackInfo ci) {
		lobecorp$reloadAnimations();
		if (PhotonAnimationPreviewScope.active()) {
			var runtime = (FXRuntime) (Object) this;
			runtime.timelinePlayer.setSignalDispatch(true);
			runtime.timelinePlayer.setAudioDispatch(false);
			if (lobecorp$animations != null && PhotonAnimationPreviewScope.authoring(runtime)) {
				lobecorp$animations.previewOnly();
			}
		}
	}

	@Override
	public void lobecorp$reloadAnimations() {
		lobecorp$closeAnimations(null);
		if (lobecorp$scenePreview) {
			return;
		}

		var runtime = (FXRuntime) (Object) this;
		var controllers = runtime.getObjects().values().stream()
				.filter(PhotonAnimationController.class::isInstance)
				.map(PhotonAnimationController.class::cast).toList();
		if (controllers.size() != 1) {
			return;
		}

		var controller = controllers.getFirst();
		var library = controller.library();
		if (library.migrationBlocked()) {
			Lobecorp.LOGGER.warn(PhotonAnimationDataUtil.INVALID_CONTROLLER_LOG, String.join("; ", library.migrationProblems()));
			return;
		}

		try {
			lobecorp$animations = new PhotonAnimationRuntime(runtime, controller);
		} catch (IllegalArgumentException failure) {
			Lobecorp.LOGGER.warn(PhotonAnimationDataUtil.INVALID_CONTROLLER_LOG, failure.getMessage());
		}
	}

	@Inject(method = "destroy", at = @At("HEAD"))
	private void lobecorp$closeAnimations(CallbackInfo ci) {
		if (lobecorp$animations != null) {
			lobecorp$animations.close();
			lobecorp$animations = null;
		}
	}

	@Inject(method = "emit(Lcom/lowdragmc/photon/client/fx/IEffectExecutor;I)V", at = @At("RETURN"))
	private void lobecorp$previewAfterRestart(CallbackInfo ci) {
		PhotonEditorTools.applyPreview((FXRuntime) (Object) this);
	}

	@Inject(method = "addSceneObjectInternal", at = @At("RETURN"))
	private void lobecorp$retainAnimationTarget(ISceneObject sceneObject, CallbackInfo ci) {
		if (lobecorp$animations != null && sceneObject instanceof FXObject target) {
			lobecorp$animations.retain(target);
		}
	}
}
