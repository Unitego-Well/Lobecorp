package org.unitego.lobecorp.mixin.photon.animation.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.gui.editor.view.timeline.AnimationTrackEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;

import java.util.List;

/// 原生录制需要当前模型实例才能枚举部件、骨骼和形状键属性。
@Mixin(AnimationTrackEditor.class)
public abstract class PhotonModelRecordingMixin {
	@WrapOperation(method = "pollRecording", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/FXObjectType;animatableProperties()Ljava/util/List;"))
	private List<AnimatedPropertyType> lobecorp$recordModelProperties(FXObjectType type,
	                                                                  Operation<List<AnimatedPropertyType>> original, @Local(name = "target") FXObject target) {
		return target instanceof PhotonModelEmitter ? type.animatableProperties(target) : original.call(type);
	}

	@WrapOperation(method = "referenceRecord", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/FXObjectType;animatableProperties()Ljava/util/List;"))
	private static List<AnimatedPropertyType> lobecorp$referenceModelProperties(FXObjectType type,
	                                                                            Operation<List<AnimatedPropertyType>> original, @Local(argsOnly = true, name = "target") FXObject target) {
		return target instanceof PhotonModelEmitter ? type.animatableProperties(target) : original.call(type);
	}
}
