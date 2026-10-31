package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TreeList;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.gui.editor.view.FXHierarchyView;
import com.lowdragmc.photon.gui.editor.view.FXObjectTreeNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.unitego.lobecorp.util.photon.clipboard.PhotonObjectClipboardUtil;
import org.unitego.lobecorp.util.photon.clipboard.PhotonSystemClipboardUtil;
import org.unitego.lobecorp.util.photon.clipboard.PhotonKeyClipboardUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorRenameUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;

@Mixin(FXHierarchyView.class)
public abstract class FXHierarchyViewMixin {
	@Shadow
	@Final
	public FXEditor fxEditor;



	@Inject(method = "<init>", at = @At("RETURN"))
	private void lobecorp$clipboardCommands(FXEditor fxEditor, CallbackInfo ci) {
		if (!(fxEditor instanceof PhotonEffectEditor)) {
			return;
		}

		var view = (FXHierarchyView) (Object) this;
		treeList.setFocusable(true);
		treeList.addEventListener(UIEvents.VALIDATE_COMMAND, event -> PhotonObjectClipboardUtil.command(view, event));
		treeList.addEventListener(UIEvents.EXECUTE_COMMAND, event -> PhotonObjectClipboardUtil.command(view, event));
	}

	@ModifyReturnValue(method = "createMenu", at = @At("RETURN"))
	private TreeBuilder.Menu lobecorp$clipboardMenu(TreeBuilder.Menu menu) {
		if (menu == null || !(fxEditor instanceof PhotonEffectEditor)) {
			return menu;
		}
		if (fxEditor.getCurrentProject() instanceof PhotonModelProject) {
			menu.remove("ldlib.gui.editor.menu.new");
			menu.remove("ldlib.gui.editor.menu.remove");
			menu.remove("ldlib.gui.editor.menu.copy");
			return menu;
		}

		var view = (FXHierarchyView) (Object) this;
		if (PhotonKeyClipboardUtil.systemEnabled(fxEditor)) {
			if (!treeList.getSelected().isEmpty()) {
				menu.leaf(PhotonEditorTextUtil.key("copy_system"), () -> PhotonObjectClipboardUtil.copy(view));
			}
			if (PhotonObjectClipboardUtil.OBJECTS.equals(PhotonSystemClipboardUtil.kind())) {
				menu.leaf(PhotonEditorTextUtil.key("paste_system"), () -> PhotonObjectClipboardUtil.paste(view));
			}
		}

		if (treeList.getSelected().size() == 1) {
			menu.leaf("ldlib.gui.editor.menu.rename", () -> PhotonEditorRenameUtil.object(view, treeList.getSelected().iterator().next().getKey()));
		}
		return menu;
	}

	@Shadow
	@Final
	public TreeList<FXObjectTreeNode> treeList;
}
