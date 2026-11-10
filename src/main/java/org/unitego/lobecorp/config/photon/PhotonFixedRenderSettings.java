package org.unitego.lobecorp.config.photon;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import org.unitego.lobecorp.client.photon.render.PhotonOcclusionAccess;
import org.unitego.lobecorp.registry.translation.PhotonTranslationKeys;

import java.util.List;

/// UI只编辑原渲染器的权威字段，本代理自身不持久化。
public class PhotonFixedRenderSettings implements IConfigurable {
	private final RendererSetting renderer;

	public PhotonFixedRenderSettings(RendererSetting renderer) {
		this.renderer = renderer;
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup group) {
		group.addConfigurator(new SelectorConfigurator<>("RendererSetting.layer", renderer::getLayer, renderer::setLayer,
				RendererSetting.Layer.Translucent, true, List.of(RendererSetting.Layer.values()), Enum::name));
		group.addConfigurator(new NumberConfigurator("photon.emitter.config.renderer.orderInLayer", renderer::getOrderInLayer,
				value -> renderer.setOrderInLayer(value.intValue()), 0, true));
		var config = ((PhotonOcclusionAccess) renderer).lobecorp$getOcclusionConfig();
		group.addConfigurator(new NumberConfigurator(PhotonTranslationKeys.PHOTON_OCCLUSION_ALPHA_CUTOFF_KEY, config::getAlphaCutoff,
				value -> config.setAlphaCutoff(value.floatValue()), PhotonOcclusionConfig.DEFAULT_ALPHA_CUTOFF, true).setRange(0f, 1f));
	}
}
