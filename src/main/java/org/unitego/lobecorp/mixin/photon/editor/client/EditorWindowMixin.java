package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.gui.editor.FXEditor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTitleUtil;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProjectEditor;

@Mixin(EditorWindow.class)
public abstract class EditorWindowMixin {

	@WrapOperation(method = "/^(createEditorButton|lambda\\$createEditorButton\\$\\d+)$/", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/editor/ui/Editor;getTitle()Lnet/minecraft/network/chat/Component;"))
	private static Component lobecorp$compactTitle(Editor editor, Operation<Component> original) {
		return PhotonEditorTitleUtil.title(editor, original.call(editor));
	}

	@Inject(method = "createEditorButton", at = @At("RETURN"))
	private void lobecorp$pathTooltip(Editor editor, CallbackInfoReturnable<UIElement> ci) {
		if (!(editor instanceof FXEditor) && !(editor instanceof PhotonModelProjectEditor)) {
			return;
		}

		for (var child : ci.getReturnValue().getChildren()) {
			if (!(child instanceof TextElement)) {
				continue;
			}

			child.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> PhotonEditorTitleUtil.appendPathTooltip(editor, event));
			return;
		}
	}
}
