package org.unitego.lobecorp.entity.client.renderer.ordeal;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperVariant;
import org.unitego.lobecorp.registry.client.LcDataTickets;

/// 根据清道夫变种选择模型 / 动画 / 纹理资源
///
/// 资源命名约定：sweeper_&lt;variant&gt;.geo.json / .animation.json / .png
public class SweeperModel extends GeoModel<Sweeper> {

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		Boolean isCorpse = renderState.getGeckolibData(LcDataTickets.IS_CORPSE);
		if (isCorpse != null && isCorpse) {
			return Lobecorp.id("entity/sweeper_a_corpse");
		}
//        return Lobecorp.id("entity/sweeper_" + getResourceName(variantOf(renderState)));
		return Lobecorp.id("entity/sweeper_a");
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		Boolean isCorpse = renderState.getGeckolibData(LcDataTickets.IS_CORPSE);
		if (isCorpse != null && isCorpse) {
			return Lobecorp.id("textures/entity/sweeper_a_corpse.png");
		}
//        return Lobecorp.id("textures/entity/sweeper_" + getResourceName(variantOf(renderState)) + ".png");
		return Lobecorp.id("textures/entity/sweeper_a.png");
	}

	@Override
	public Identifier getAnimationResource(Sweeper sweeper) {
//        return Lobecorp.id("entity/sweeper_" + getResourceName(sweeper.getVariant()));
		return Lobecorp.id("entity/sweeper_a");
	}

	private static String getResourceName(SweeperVariant renderState) {
		return renderState.resourceName();
	}

	private static SweeperVariant variantOf(GeoRenderState renderState) {
		return renderState.getOrDefaultGeckolibData(LcDataTickets.SWEEPER_VARIANT, SweeperVariant.A);
	}
}
