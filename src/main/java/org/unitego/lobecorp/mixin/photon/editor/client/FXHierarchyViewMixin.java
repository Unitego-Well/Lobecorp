package org.unitego.lobecorp.mixin.photon.editor.client;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TreeList;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.FXHierarchyView;
import com.lowdragmc.photon.gui.editor.view.FXObjectTreeNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslationKeys;
import org.unitego.lobecorp.util.photon.clipboard.PhotonKeyClipboardUtil;
import org.unitego.lobecorp.util.photon.clipboard.PhotonObjectClipboardUtil;
import org.unitego.lobecorp.util.photon.clipboard.PhotonSystemClipboardUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorRenameUtil;

@Mixin(FXHierarchyView.class)
public abstract class FXHierarchyViewMixin {
	@Shadow
	@Final
	public FXEditor fxEditor;
	@Shadow
	@Final
	public TreeList<FXObjectTreeNode> treeList;

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

		var view = (FXHierarchyView) (Object) this;
		if (PhotonKeyClipboardUtil.systemEnabled(fxEditor)) {
			if (!treeList.getSelected().isEmpty()) {
				menu.leaf(PhotonEditorTranslationKeys.COPY_SYSTEM, () -> PhotonObjectClipboardUtil.copy(view));
			}
			if (PhotonObjectClipboardUtil.OBJECTS.equals(PhotonSystemClipboardUtil.kind())) {
				menu.leaf(PhotonEditorTranslationKeys.PASTE_SYSTEM, () -> PhotonObjectClipboardUtil.paste(view));
			}
		}

		if (treeList.getSelected().size() == 1) {
			menu.leaf("ldlib.gui.editor.menu.rename", () -> PhotonEditorRenameUtil.object(view, treeList.getSelected().iterator().next().getKey()));
		}
		return menu;
	}
}
