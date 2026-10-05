package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonConfiguratorNameAccess;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTooltipUtil;

@Mixin(Configurator.class)
public abstract class ConfiguratorMixin implements PhotonConfiguratorNameAccess {
	@Unique
	private String lobecorp$configuratorName;

	@Inject(method = "<init>(Ljava/lang/String;)V", at = @At("RETURN"))
	private void lobecorp$rememberName(String name, CallbackInfo ci) {
		lobecorp$configuratorName = name;
		var configurator = (Configurator) (Object) this;
		if (PhotonEditorTooltipUtil.hasTooltip(name)) {
			configurator.addEventListener(UIEvents.ADDED, _ -> PhotonEditorTooltipUtil.apply(configurator, name));
		}

		if (!PhotonEditorTools.isFeatureControlName(name)) {
			return;
		}

		configurator.addEventListener(UIEvents.ADDED,
				_ -> PhotonEditorTools.updateControlRegistration(configurator, name, true));
		configurator.addEventListener(UIEvents.REMOVED,
				_ -> PhotonEditorTools.updateControlRegistration(configurator, name, false));
	}

	@Override
	public String lobecorp$name() {
		return lobecorp$configuratorName;
	}
}
