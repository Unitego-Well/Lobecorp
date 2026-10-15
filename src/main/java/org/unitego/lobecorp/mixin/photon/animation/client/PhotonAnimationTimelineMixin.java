package org.unitego.lobecorp.mixin.photon.animation.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.PhotonParticleManager;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTimelineUtil;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationWorkspace;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationTrack;
import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelAnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.unitego.lobecorp.registry.photon.client.LcPhotonAnimationTypes;

import java.util.UUID;

/// 原 TL 在命名动画模式切换模型和时钟；普通场景继续走原调用链。
@Mixin(FXTimelineView.class)

public abstract class PhotonAnimationTimelineMixin {
	@Shadow
	@Final
	public FXEditor fxEditor;

	@WrapOperation(method = "openAddTrackMenu", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/gui/util/TreeBuilder$Menu;leaf(Lnet/minecraft/network/chat/Component;Ljava/lang/Runnable;)Lcom/lowdragmc/lowdraglib2/gui/util/TreeBuilder$Menu;"))
	private TreeBuilder.Menu lobecorp$testAnimationCalls(TreeBuilder.Menu menu, Component name, Runnable runnable,
	                                                     Operation<TreeBuilder.Menu> original) {
		if (!(name.getContents() instanceof TranslatableContents contents)
				|| !LcPhotonAnimationTypes.CALL_TRACK_TRANSLATION_KEY.equals(contents.getKey())) {
			return original.call(menu, name, runnable);
		}

		var workspace = lobecorp$workspace();
		if (workspace == null || workspace.hasSelection()) {
			return menu;
		}

		return original.call(menu, name, runnable);
	}

	@Inject(method = "bind", at = @At("HEAD"), cancellable = true)
	private void lobecorp$bindChildTarget(Track track, UUID targetId, CallbackInfo ci) {
		if (targetId == null || !(track instanceof PhotonChildAnimationTrack || track instanceof PhotonModelAnimationTrack)) {
			return;
		}

		var runtime = ((TimelineContext) this).runtime();
		var target = runtime == null ? null : runtime.objects.get(targetId);
		var valid = track instanceof PhotonModelAnimationTrack ? target instanceof PhotonModelEmitter
				: target instanceof PhotonEmitterSpawner;

		if (!valid) {
			ci.cancel();
		}
	}

	@Inject(method = "setRecordingTrack", at = @At("RETURN"))
	private void lobecorp$recordingChanged(CallbackInfo ci) {
		var workspace = lobecorp$workspace();
		if (workspace != null) {
			workspace.nativeRecordingChanged();
		}
	}

	@Inject(method = "pollRecording", at = @At("RETURN"))
	private void lobecorp$recordingSync(CallbackInfo ci) {
		var workspace = lobecorp$workspace();
		if (workspace != null) {
			workspace.nativeRecordingPolled();
		}
	}

	@WrapOperation(method = {"snapMarker", "drawMarkerStrip", "markerAt", "addMarker", "removeMarker", "insertTime", "contentMaxTick", "syncBoxStructures", "rebuild", "openTrackMenu", "pasteTracks", "snapTick", "snapKeyTick(DZLjava/util/Set;)D", "addTrack", "removeTrack", "removeTracks", "reorderTracks", "reorderTrack", "updatePlaybackEnd", "pasteClipboard", "lambda$openAddTrackMenu$1", "lambda$openAddTrackMenu$0"}, at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/FXData;timeline()Lcom/lowdragmc/photon/client/fx/timeline/Timeline;"))
	private Timeline lobecorp$animationModel(FXData data, Operation<Timeline> original) {
		var workspace = lobecorp$workspace();
		if (workspace != null) {
			return workspace.timelineContext().animationTimeline();
		}

		return original.call(data);
	}

	@WrapOperation(method = "refreshPreview", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/gui/editor/view/scene/SceneView;requestSimulateTo(J)V"))
	private void lobecorp$animationRefresh(SceneView scene, long time, Operation<Void> original) {
		var workspace = lobecorp$workspace();
		if (workspace != null) {
			workspace.refreshTimelinePreview();
			return;
		}

		original.call(scene, time);
	}

	@WrapOperation(method = {"scrubTo", "lambda$createTimeField$3"}, at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/gui/editor/view/scene/SceneView;requestSimulateTo(J)V"))
	private void lobecorp$animationSeek(SceneView scene, long time, Operation<Void> original) {
		var workspace = lobecorp$workspace();
		if (workspace != null) {
			workspace.timelineContext().seek(time);
			return;
		}

		original.call(scene, time);
	}

	@Inject(method = "togglePlay", at = @At("HEAD"), cancellable = true)
	private void lobecorp$animationPlay(CallbackInfo ci) {
		var workspace = lobecorp$workspace();
		if (workspace != null) {
			workspace.timelineContext().togglePlayback();
			ci.cancel();
		}
	}

	@Inject(method = "stop", at = @At("HEAD"), cancellable = true)
	private void lobecorp$animationStop(CallbackInfo ci) {
		var workspace = lobecorp$workspace();
		if (workspace != null) {
			workspace.timelineContext().stopPlayback();
			ci.cancel();
		}
	}

	@WrapOperation(method = "jumpToEnd", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/gui/editor/view/scene/SceneView;simulateTo(J)V"))
	private void lobecorp$animationEnd(SceneView scene, long time, Operation<Void> original,
	                                   @Local(name = "contentMax") double contentMax) {
		var workspace = lobecorp$workspace();
		if (workspace == null) {
			original.call(scene, time);
			return;
		}

		workspace.clearQueuedPlaybackCommand();
		workspace.previewTime(contentMax);
	}

	@Inject(method = "stepFrames", at = @At("HEAD"), cancellable = true)
	private void lobecorp$animationStep(int delta, CallbackInfo ci) {
		var workspace = lobecorp$workspace();
		if (workspace != null) {
			var time = Math.max(0, workspace.playheadTime(0) + delta);
			workspace.clearQueuedPlaybackCommand();
			workspace.previewTime(time);
			ci.cancel();
		}
	}

	@Inject(method = "syncSignalDispatch",
			at = @At("HEAD"), cancellable = true)
	private void lobecorp$keepSceneState(CallbackInfo ci) {
		var workspace = lobecorp$workspace();
		if (workspace != null) {
			workspace.syncPreviewDispatch(fxEditor.sceneView.particleManager.isPlaying());
			ci.cancel();
		}
	}

	@Inject(method = "updatePlaybackEnd", at = @At("HEAD"), cancellable = true)
	private void lobecorp$keepIndependentPreview(CallbackInfo ci) {
		var workspace = lobecorp$workspace();
		if (workspace != null && !workspace.timelinePlaybackActive()) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "updatePlaybackEnd", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/PhotonParticleManager;getTime()J"))
	private long lobecorp$previewTime(PhotonParticleManager manager, Operation<Long> original) {
		var workspace = lobecorp$workspace();
		return workspace != null
				? (long) Math.floor(workspace.playbackEndTime()) : original.call(manager);
	}

	@WrapOperation(method = "updatePlaybackEnd", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/timeline/TimelinePlayer;getDuration()D"))
	private double lobecorp$previewDuration(TimelinePlayer player, Operation<Double> original) {
		var workspace = lobecorp$workspace();
		return workspace != null
				? workspace.timelineDuration() : original.call(player);
	}

	@WrapOperation(method = "updatePlaybackEnd", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/FXRuntime;isFinished()Z"))
	private boolean lobecorp$previewFinished(FXRuntime runtime, Operation<Boolean> original) {
		var workspace = lobecorp$workspace();
		return workspace != null
				? workspace.previewFinished() : original.call(runtime);
	}

	@WrapOperation(method = "updatePlaybackEnd", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/gui/editor/view/scene/SceneView;reset()V"))
	private void lobecorp$restartPreview(SceneView scene, Operation<Void> original) {
		var workspace = lobecorp$workspace();
		if (workspace != null) {
			workspace.restartTimelinePreview();
			return;
		}

		original.call(scene);
	}

	@WrapOperation(method = "updatePlaybackEnd", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/FXRuntime;emit(Lcom/lowdragmc/photon/client/fx/IEffectExecutor;)V"))
	private void lobecorp$avoidSecondRestart(FXRuntime runtime, IEffectExecutor effect, Operation<Void> original) {
		if (lobecorp$workspace() == null) {
			original.call(runtime, effect);
		}
	}

	@Unique
	private PhotonAnimationWorkspace lobecorp$workspace() {
		return PhotonAnimationTimelineUtil.workspace((TimelineContext) this);
	}

	@Inject(method = "currentTimeTicks", at = @At("HEAD"), cancellable = true)
	private void lobecorp$currentTime(CallbackInfoReturnable<Long> cir) {
		var workspace = lobecorp$workspace();
		if (workspace != null) {
			cir.setReturnValue(workspace.timelineContext().currentTimeTicks());
		}
	}

	@WrapOperation(method = "drawPlayhead", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/PhotonParticleManager;getTime(F)F"))
	private float lobecorp$playhead(PhotonParticleManager manager, float pPartialTicks, Operation<Float> original) {
		var workspace = lobecorp$workspace();
		return workspace == null ? original.call(manager, pPartialTicks) : (float) workspace.playheadTime(pPartialTicks);
	}

	@WrapMethod(method = "pushEdit")
	private void lobecorp$edit(String name, Runnable doFn, Runnable undoFn, Operation<Void> original) {
		var workspace = lobecorp$workspace();
		if (workspace == null) {
			original.call(name, doFn, undoFn);
			return;
		}

		workspace.timelineContext().pushEdit(name, doFn);
	}

	@WrapMethod(method = "pushApplied")
	private void lobecorp$applied(String name, Runnable redo, Runnable undo, Operation<Void> original) {
		var workspace = lobecorp$workspace();
		if (workspace == null) {
			original.call(name, redo, undo);
			return;
		}

		workspace.timelineContext().pushApplied(name, redo, undo);
	}

	@WrapMethod(method = "beginScrub")
	private void lobecorp$beginScrub(Operation<Void> original) {
		var workspace = lobecorp$workspace();
		if (workspace == null) {
			original.call();
			return;
		}

		workspace.timelineContext().beginScrub();
	}

	@WrapMethod(method = "endScrub")
	private void lobecorp$endScrub(Operation<Void> original) {
		var workspace = lobecorp$workspace();
		if (workspace == null) {
			original.call();
			return;
		}

		workspace.timelineContext().endScrub();
	}

}
