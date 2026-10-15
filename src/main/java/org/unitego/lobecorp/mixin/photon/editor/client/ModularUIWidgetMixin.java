package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUIWidget;
import com.lowdragmc.lowdraglib2.gui.ui.event.CommandEvents;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorSaveUtil;

@Mixin(ModularUIWidget.class)
public abstract class ModularUIWidgetMixin {
	@Shadow
	@Final
	private ModularUI modularUI;

	@WrapOperation(method = "dispatchCommand(Ljava/lang/String;III)Z", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/gui/ui/event/UIEventDispatcher;dispatchEvent(Lcom/lowdragmc/lowdraglib2/gui/ui/event/UIEvent;)V"))
	private void lobecorp$routeProjectSave(UIEvent event, Operation<Void> original) {
		if (UIEvents.EXECUTE_COMMAND.equals(event.type) && CommandEvents.SAVE.equals(event.command)) {
			var editor = PhotonEditorSaveUtil.currentProjectEditor(modularUI, event.target);
			if (editor != null) {
				event.target = editor;
			}
		}

		original.call(event);
	}

	@WrapOperation(method = "dispatchCommand(Ljava/lang/String;III)Z", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/gui/ui/event/UIEventDispatcher;dispatchAllChildren(Lcom/lowdragmc/lowdraglib2/gui/ui/event/UIEvent;)Z"))
	private boolean lobecorp$validateProjectSave(UIEvent event, Operation<Boolean> original) {
		if (!UIEvents.VALIDATE_COMMAND.equals(event.type) || !CommandEvents.SAVE.equals(event.command)) {
			return original.call(event);
		}

		var editor = PhotonEditorSaveUtil.currentProjectEditor(modularUI, event.target);
		if (editor == null) {
			return original.call(event);
		}

		event.target = editor;
		UIEventDispatcher.dispatchEvent(event);
		return event.propagationStopped;
	}
}
