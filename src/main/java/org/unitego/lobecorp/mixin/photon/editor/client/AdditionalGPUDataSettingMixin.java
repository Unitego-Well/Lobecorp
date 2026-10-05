package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.client.gameobject.emitter.data.AdditionalGPUDataSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;

import java.util.function.Supplier;
import java.util.function.Consumer;

@Mixin(AdditionalGPUDataSetting.class)
public abstract class AdditionalGPUDataSettingMixin {
	@Inject(method = "attachRename", at = @At("HEAD"))
	private static void lobecorp$renameDialog(Configurator configurator, Supplier<String> getName, Consumer<String> setName, CallbackInfo ci) {
		configurator.label.addEventListener(UIEvents.DOUBLE_CLICK, event -> {
			var editor = PhotonEditorTools.findEditor(configurator);
			if (editor == null || !PhotonEditorSettings.of(editor).enabled("rename_dialog"))
				return;
			event.stopImmediatePropagation();
			Dialog.stringEditorDialog("ldlib.gui.editor.menu.rename", getName.get(), null, value -> {
				if (PhotonEditorTools.findEditor(configurator) != editor)
					return;
				setName.accept(value.isBlank() ? "" : value.trim());
				configurator.label.setText(getName.get(), false);
				configurator.notifyChanges();
			}).show(editor);
		}, true);
	}
}

