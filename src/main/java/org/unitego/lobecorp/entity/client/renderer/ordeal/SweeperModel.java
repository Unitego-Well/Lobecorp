package org.unitego.lobecorp.entity.client.renderer.ordeal;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperVariant;

/// 根据清道夫变种选择模型 / 动画 / 纹理资源
///
/// 资源命名约定：sweeper_&lt;variant&gt;.geo.json / .animation.json / .png
public class SweeperModel extends GeoModel<Sweeper> {

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return Lobecorp.id("entity/sweeper_" + variantOf(renderState).resourceName());
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return Lobecorp.id("textures/entity/sweeper_" + variantOf(renderState).resourceName() + ".png");
    }

    @Override
    public Identifier getAnimationResource(Sweeper sweeper) {
        return Lobecorp.id("entity/sweeper_" + sweeper.getVariant().resourceName());
    }

    private static SweeperVariant variantOf(GeoRenderState renderState) {
        return renderState.getOrDefaultGeckolibData(SweeperRenderer.VARIANT, SweeperVariant.A);
    }
}
