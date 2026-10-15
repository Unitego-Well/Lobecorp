package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.photon.gui.editor.view.scene.FXObjectAnimationView;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProjectEditor;

/// 模型工程保留原生场景工具栏，仅移除整体运动测试的 A 开关。
@Mixin(SceneView.ParticleSceneEditor.class)
public abstract class ModelParticleSceneEditorMixin {
	@ModifyExpressionValue(method = "initTopBar", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lcom/lowdragmc/photon/gui/editor/view/scene/SceneView;fxObjectAnimationView:Lcom/lowdragmc/photon/gui/editor/view/scene/FXObjectAnimationView;"), require = 2)
	private FXObjectAnimationView lobecorp$captureModelScene(FXObjectAnimationView view,
	                                                         @Share("modelProject") LocalBooleanRef modelProject) {
		modelProject.set(view.sceneView.fxEditor instanceof PhotonModelProjectEditor);
		return view;
	}

	@ModifyExpressionValue(method = "initTopBar", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/gui/editor/view/scene/SceneToggleBuilder;build()Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Toggle;"),
			slice = @Slice(from = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 0,
					target = "Lcom/lowdragmc/photon/gui/editor/view/scene/SceneView;fxObjectAnimationView:Lcom/lowdragmc/photon/gui/editor/view/scene/FXObjectAnimationView;"),
					to = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 0,
							target = "Lcom/lowdragmc/photon/gui/editor/view/scene/SceneView;fxObjectInfoView:Lcom/lowdragmc/photon/gui/editor/view/scene/FXObjectInfoView;")))
	private Toggle lobecorp$hideModelAnimationToggle(Toggle toggle,
	                                                 @Share("modelProject") LocalBooleanRef modelProject) {
		if (modelProject.get()) {
			toggle.setDisplay(false);
		}

		return toggle;
	}
}
