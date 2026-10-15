package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.clipboard.PhotonResourceClipboardUtil;

@Mixin(TextField.class)
public abstract class TextFieldMixin {
	@WrapOperation(method = "onExecuteCommand(Lcom/lowdragmc/lowdraglib2/gui/ui/event/UIEvent;)V", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/gui/ui/elements/TextField$TextFieldClientSupport;getClipboardText()Ljava/lang/String;"))
	private String lobecorp$readSystemText(Operation<String> original) {
		if (!PhotonResourceClipboardUtil.enabled((TextField) (Object) this)) {
			return original.call();
		}

		return Minecraft.getInstance().keyboardHandler.getClipboard();
	}
}
