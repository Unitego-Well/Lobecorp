package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonConfiguratorNameAccess;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorSaveUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTooltipUtil;
import org.unitego.lobecorp.registry.translation.PhotonTranslationKeys;

@Mixin(Configurator.class)
public abstract class ConfiguratorMixin implements PhotonConfiguratorNameAccess {
	@Unique
	private String lobecorp$configuratorName;

	@Inject(method = "<init>(Ljava/lang/String;)V", at = @At("RETURN"))
	private void lobecorp$rememberName(String name, CallbackInfo ci) {
		lobecorp$configuratorName = name;
		var configurator = (Configurator) (Object) this;
		if (PhotonEditorTools.isFeatureControlName(name)) {
			configurator.addEventListener(UIEvents.ADDED,
					_ -> PhotonEditorTools.updateControlRegistration(configurator, name, true));
			configurator.addEventListener(UIEvents.REMOVED,
					_ -> PhotonEditorTools.updateControlRegistration(configurator, name, false));
		}

		if ("RendererSetting.layer".equals(name) || "photon.emitter.config.renderer.orderInLayer".equals(name)) {
			configurator.addEventListener(UIEvents.ADDED, _ -> {
				for (var parent = configurator.getParent(); parent != null; parent = parent.getParent()) {
					if (parent instanceof PhotonConfiguratorNameAccess access && PhotonTranslationKeys.PHOTON_FIXED_RENDER_KEY.equals(access.lobecorp$name())) {
						return;
					}
				}
				if (PhotonEditorSaveUtil.findEditor(configurator) instanceof PhotonEffectEditor) {
					configurator.setDisplay(false);
				}
			});
		}

		if (PhotonEditorTooltipUtil.hasTooltip(name)) {
			configurator.addEventListener(UIEvents.ADDED, _ -> {
				if (PhotonEditorSaveUtil.findEditor(configurator) instanceof PhotonEffectEditor) {
					PhotonEditorTooltipUtil.apply(configurator, name);
				}
			});
		}

	}

	@Override
	public String lobecorp$name() {
		return lobecorp$configuratorName;
	}
}
