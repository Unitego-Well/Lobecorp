package org.unitego.lobecorp.mixin.photon.animation.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationTrack;
import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelAnimationTrack;

import java.util.Collection;
import java.util.Map;

/// 子动画轨道沿用原生目标选择器，只过滤可编排的发射器。
@Mixin(TrackEditor.class)
public abstract class PhotonAnimationTargetPickerMixin {
	@WrapOperation(method = "openObjectPicker", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
	private Collection<?> lobecorp$childTargets(Map<?, ?> objects, Operation<Collection<?>> original,
	                                            TimelineContext ctx, Track track, boolean allowRoot, float x, float y) {
		var values = original.call(objects);

		if (track instanceof PhotonModelAnimationTrack) {
			return values.stream().filter(PhotonModelEmitter.class::isInstance).toList();
		}

		if (!(track instanceof PhotonChildAnimationTrack)) {
			return values;
		}

		return values.stream().filter(PhotonEmitterSpawner.class::isInstance).toList();
	}
}
