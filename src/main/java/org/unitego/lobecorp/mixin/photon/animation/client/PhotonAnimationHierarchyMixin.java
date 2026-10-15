package org.unitego.lobecorp.mixin.photon.animation.client;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.FXHierarchyView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationTab;

@Mixin(FXHierarchyView.class)
public abstract class PhotonAnimationHierarchyMixin {
	@Shadow
	@Final
	public FXEditor fxEditor;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void lobecorp$readOnlyHierarchy(FXEditor fxEditor, CallbackInfo ci) {
		var view = (FXHierarchyView) (Object) this;
		view.treeList.addEventListener(UIEvents.MOUSE_DOWN, this::lobecorp$guardVisibility, true);
		view.treeList.addEventListener(UIEvents.MOUSE_UP, this::lobecorp$guardVisibility, true);
		view.treeList.addEventListener(UIEvents.CLICK, this::lobecorp$guardVisibility, true);
	}

	@Unique
	private void lobecorp$guardVisibility(UIEvent event) {
		if (PhotonAnimationTab.editing(fxEditor) == null) {
			return;
		}

		var view = (FXHierarchyView) (Object) this;
		for (var element = event.target; element != null && element != view.treeList; element = element.getParent()) {
			if (element instanceof Button) {
				event.stopImmediatePropagation();
				return;
			}
		}
	}

	@Inject(method = "createMenu", at = @At("HEAD"), cancellable = true)
	private void lobecorp$noObjectMenu(CallbackInfoReturnable<TreeBuilder.Menu> cir) {
		if (PhotonAnimationTab.editing(fxEditor) != null) {
			cir.setReturnValue(null);
		}
	}

	@Inject(method = {"addSceneObject", "removeSceneObject", "reparent"}, at = @At("HEAD"), cancellable = true)
	private void lobecorp$noStructureChanges(CallbackInfo ci) {
		if (PhotonAnimationTab.editing(fxEditor) != null) {
			ci.cancel();
		}
	}
}
