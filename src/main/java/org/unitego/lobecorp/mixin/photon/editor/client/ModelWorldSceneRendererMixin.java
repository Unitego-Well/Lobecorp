package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.lowdragmc.lowdraglib2.client.scene.ParticleManager;
import com.lowdragmc.lowdraglib2.client.scene.SceneRenderContext;
import com.lowdragmc.lowdraglib2.client.scene.SceneRenderHook;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.photon.client.PhotonParticleManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProjectEditor;

/// 模型工程在 Photon 表面提交后绘制原生选择辅助与 Gizmo，防止辅助物被后画的表面覆盖。
/// 缓存只属于本次 drawWorld 调用，原生覆盖层每帧执行一次，并在场景相机与输出目标仍有效时刷新。
@Mixin(WorldSceneRenderer.class)
public abstract class ModelWorldSceneRendererMixin {
	@Shadow
	protected Matrix4f projectionMatrix;

	@WrapOperation(method = "drawWorld", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/client/scene/SceneRenderHook;apply(Lcom/lowdragmc/lowdraglib2/client/scene/SceneRenderContext;)V"),
			slice = @Slice(from = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderTranslucentFeatures()V"),
					to = @At(value = "INVOKE",
							target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderTranslucentParticles()V")), require = 1)
	private void lobecorp$deferModelOverlay(SceneRenderHook hook, SceneRenderContext context, Operation<Void> operation,
	                                        @Share("modelOverlay") LocalRef<SceneRenderHook> modelOverlay) {
		if (context.renderer().getParticleManager() instanceof PhotonParticleManager manager
				&& manager.sceneView != null
				&& manager.sceneView.fxEditor instanceof PhotonModelProjectEditor) {
			modelOverlay.set(hook);
			return;
		}

		operation.call(hook, context);
	}

	/// Photon 在 afterRender 内完成两个阶段的真实绘制；覆盖层随后绘制，避免 Gizmo 提前写入深度。
	@WrapOperation(method = "drawWorld", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/client/scene/ParticleManager;afterRender()V"), require = 2)
	private void lobecorp$drawModelOverlayAfterParticles(ParticleManager manager, Operation<Void> operation,
	                                                     @Local(name = "ctx") SceneRenderContext ctx,
	                                                     @Share("modelOverlay") LocalRef<SceneRenderHook> modelOverlay) {
		operation.call(manager);
		var hook = modelOverlay.get();
		if (hook == null) {
			return;
		}

		modelOverlay.set(null);
		if (manager instanceof PhotonParticleManager photon && photon.sceneView != null
				&& photon.sceneView.fxEditor instanceof PhotonModelProjectEditor editor
				&& editor.sceneController() != null) {
			editor.sceneController().preview().captureProjection(
					new Matrix4f(projectionMatrix).mul(RenderSystem.getModelViewMatrix()));
		}

		hook.apply(ctx);
		// 已越过原生粒子阶段的自动刷新点，立即提交本帧辅助线、标记与 Gizmo。
		ctx.bufferSource().endBatch();
	}
}
