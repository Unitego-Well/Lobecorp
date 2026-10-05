package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.util.photon.runtime.PhotonActivatorTimelineUtil;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;

@Mixin(TimelinePlayer.class)
public abstract class TimelinePlayerMixin {
	@Shadow
	@Final
	private FXRuntime runtime;
	@Shadow
	@Final
	private Timeline timeline;
	@Unique
	private final PhotonActivatorTimelineUtil lobecorp$activation = new PhotonActivatorTimelineUtil();

	@Inject(method = "begin", at = @At("HEAD"))
	private void lobecorp$resetActivation(CallbackInfo ci) {
		lobecorp$activation.reset();
	}

	@Invoker("restart")
	protected abstract void lobecorp$restartActivation(FXObject object, Clip clip);

	@Inject(method = "evaluate", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/timeline/TimelinePlayer;applyAnimations(D)V"))
	private void lobecorp$activationState(long time, CallbackInfo ci) {
		lobecorp$activation.evaluate(runtime, timeline, time, this::lobecorp$restartActivation);
	}

	@Inject(method = {"evaluate", "frame"}, at = @At("RETURN"))
	private void lobecorp$previewParameters(CallbackInfo ci) {
		PhotonEditorTools.applyPreview(runtime);
	}
}
