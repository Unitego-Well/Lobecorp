package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorSelectorConfigurator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.BasicStyle;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.timeline.ClipTrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivatorTrackAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivatorOptions;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.util.photon.runtime.PhotonActivatorTimelineUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonActivatorClipUtil;

import java.util.List;
import java.util.Locale;

@Mixin(ClipTrackEditor.class)
public abstract class ClipTrackEditorMixin {
	@Shadow
	private boolean clipDragInvalid(TimelineContext ctx, ClipTrackEditor.ClipTrackUIState st, Clip clip) {
		throw new AssertionError();
	}

	@WrapOperation(method = "lambda$createClipElement$1", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib2/gui/ui/style/BasicStyle;backgroundTexture(Lcom/lowdragmc/lowdraglib2/gui/texture/IGuiTexture;)Lcom/lowdragmc/lowdraglib2/gui/ui/style/BasicStyle;"))
	private BasicStyle lobecorp$activationBackground(BasicStyle style, IGuiTexture backgroundTexture, Operation<BasicStyle> original,
	                                                 @Local(argsOnly = true, name = "ctx") TimelineContext ctx, @Local(argsOnly = true, name = "track") Track track) {
		return original.call(style, PhotonActivatorClipUtil.background(ctx, track, backgroundTexture));
	}

	@WrapOperation(method = "lambda$createClipElement$1", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib2/gui/ui/style/BasicStyle;overlayTexture(Lcom/lowdragmc/lowdraglib2/gui/texture/IGuiTexture;)Lcom/lowdragmc/lowdraglib2/gui/ui/style/BasicStyle;"))
	private BasicStyle lobecorp$activationOverlay(BasicStyle style, IGuiTexture backgroundTexture, Operation<BasicStyle> original,
	                                              @Local(argsOnly = true, name = "ctx") TimelineContext ctx, @Local(argsOnly = true, name = "track") Track track,
	                                              @Local(argsOnly = true, name = "clip") Clip clip, @Local(argsOnly = true, name = "st") ClipTrackEditor.ClipTrackUIState st,
	                                              @Local(argsOnly = true, name = "baseColor") ColorPattern baseColor) {
		return original.call(style, PhotonActivatorClipUtil.overlay(ctx, track, clip, backgroundTexture, baseColor,
				() -> clipDragInvalid(ctx, st, clip)));
	}

	@Inject(method = "createClipElement", at = @At("RETURN"))
	private void lobecorp$activationLayout(TimelineContext ctx, Track track, ClipTrackEditor.ClipTrackUIState st,
	                                       Clip clip, CallbackInfoReturnable<UIElement> cir) {
		PhotonActivatorClipUtil.reposition(ctx, track, clip, cir.getReturnValue());
	}

	@WrapOperation(method = "lambda$createClipElement$6", at = @At(value = "FIELD", target = "Lcom/lowdragmc/photon/gui/editor/view/timeline/ClipTrackEditor$ClipTrackUIState;dragMode:I", opcode = Opcodes.PUTFIELD))
	private void lobecorp$activationDragMode(ClipTrackEditor.ClipTrackUIState state, int mode, Operation<Void> original,
	                                         @Local(argsOnly = true, name = "ctx") TimelineContext ctx, @Local(argsOnly = true, name = "track") Track track) {
		original.call(state, PhotonActivatorClipUtil.isPoint(ctx, track) ? 0 : mode);
	}

	@Inject(method = "trackConfigurator", at = @At("RETURN"), cancellable = true)
	private void lobecorp$trackOptions(TimelineContext ctx, Track track, CallbackInfoReturnable<IConfigurable> cir) {
		if (!(track instanceof ActivatorTrack)) {
			return;
		}

		var original = cir.getReturnValue();
		cir.setReturnValue(IConfigurable.create(group -> {
			original.buildConfigurator(group);
			lobecorp$activationOptions(group, ctx, track);
		}));
	}

	@Unique
	private static void lobecorp$activationOptions(ConfiguratorGroup group, TimelineContext ctx, Track track) {
		if (!PhotonEditorSettings.of(ctx.editor()).enabled("control")) {
			return;
		}

		var options = PhotonActivatorTimelineUtil.options(track);
		var controls = new ConfiguratorGroup(PhotonEditorTextUtil.key("control"), false);
		controls.addConfigurator(new BooleanConfigurator(PhotonEditorTextUtil.key("control.start"),
				() -> PhotonActivatorTimelineUtil.options(track).startControl(), value -> {
			var old = PhotonActivatorTimelineUtil.options(track);
			lobecorp$change(ctx, track, new PhotonActivatorOptions(value, old.startBehavior(), old.endControl(), old.endBehavior(), old.clearOnRestart()));
		}, PhotonActivatorOptions.DEFAULT.startControl(), true));
		controls.addConfigurator(new ConfiguratorSelectorConfigurator<>(PhotonEditorTextUtil.key("control.start_behavior"),
				() -> PhotonActivatorTimelineUtil.options(track).startBehavior(), value -> {
			var old = PhotonActivatorTimelineUtil.options(track);
			lobecorp$change(ctx, track, new PhotonActivatorOptions(old.startControl(), value, old.endControl(), old.endBehavior(), old.clearOnRestart()));
		}, options.startBehavior(), true, List.of(PhotonActivatorOptions.StartBehavior.values()),
				value -> PhotonEditorTextUtil.key("control." + value.name().toLowerCase(Locale.ROOT)), (behavior, subGroup) -> {
			if (behavior == PhotonActivatorOptions.StartBehavior.RESTART) {
				subGroup.addConfigurator(new BooleanConfigurator(PhotonEditorTextUtil.key("control.clear_on_restart"),
						() -> PhotonActivatorTimelineUtil.options(track).clearOnRestart(), value -> {
					var old = PhotonActivatorTimelineUtil.options(track);
					lobecorp$change(ctx, track, new PhotonActivatorOptions(old.startControl(), old.startBehavior(), old.endControl(), old.endBehavior(), value));
				}, PhotonActivatorOptions.DEFAULT.clearOnRestart(), true));
			}
		}));
		controls.addConfigurator(new BooleanConfigurator(PhotonEditorTextUtil.key("control.end"),
				() -> PhotonActivatorTimelineUtil.options(track).endControl(), value -> {
			var old = PhotonActivatorTimelineUtil.options(track);
			lobecorp$change(ctx, track, new PhotonActivatorOptions(old.startControl(), old.startBehavior(), value, old.endBehavior(), old.clearOnRestart()));
		}, PhotonActivatorOptions.DEFAULT.endControl(), true));
		controls.addConfigurator(new ConfiguratorSelectorConfigurator<>(PhotonEditorTextUtil.key("control.end_behavior"),
				() -> PhotonActivatorTimelineUtil.options(track).endBehavior(), value -> {
			var old = PhotonActivatorTimelineUtil.options(track);
			lobecorp$change(ctx, track, new PhotonActivatorOptions(old.startControl(), old.startBehavior(), old.endControl(), value, old.clearOnRestart()));
		}, options.endBehavior(), true, List.of(PhotonActivatorOptions.EndBehavior.values()),
				value -> PhotonEditorTextUtil.key("control." + value.name().toLowerCase(Locale.ROOT)), (_, _) -> {
		}));
		group.addConfigurator(controls);
	}

	@Unique
	private static void lobecorp$change(TimelineContext ctx, Track track, PhotonActivatorOptions options) {
		if (track.lock()) {
			return;
		}

		var before = PhotonActivatorTimelineUtil.options(track);
		if (before.equals(options)) {
			return;
		}

		ctx.pushEdit(PhotonEditorTextUtil.key("control"),
				() -> lobecorp$apply(ctx, track, options),
				() -> lobecorp$apply(ctx, track, before));
	}

	@Unique
	private static void lobecorp$apply(TimelineContext ctx, Track track, PhotonActivatorOptions options) {
		((PhotonActivatorTrackAccess) track).lobecorp$setActivationOptions(options);
		ctx.refreshLaneLayout();
		ctx.refreshPreview();
	}
}
