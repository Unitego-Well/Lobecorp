package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.clipboard.PhotonResourceClipboardUtil;

@Mixin(TextArea.class)
public abstract class TextAreaMixin {
	@WrapOperation(method = "onExecuteCommand(Lcom/lowdragmc/lowdraglib2/gui/ui/event/UIEvent;)V", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/gui/ui/elements/TextArea$TextAreaClientSupport;getClipboardText()Ljava/lang/String;"))
	private String lobecorp$readSystemText(Operation<String> original) {
		if (!PhotonResourceClipboardUtil.enabled((TextArea) (Object) this)) {
			return original.call();
		}

		return Minecraft.getInstance().keyboardHandler.getClipboard();
	}
}
