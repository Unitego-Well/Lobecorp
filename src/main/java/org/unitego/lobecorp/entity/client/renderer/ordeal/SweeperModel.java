package org.unitego.lobecorp.entity.client.renderer.ordeal;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperVariant;
import org.unitego.lobecorp.registry.client.LcDataTickets;

import java.util.Map;

	/// 根据清道夫变种选择模型、动画和纹理资源。
///
/// 资源命名约定：sweeper_&lt;variant&gt;.geo.json / .animation.json / .png
public class SweeperModel extends GeoModel<Sweeper> {
	/// 清道夫尸体纹理资源。
	public static final Identifier TEXTURES_CORPSE = Lobecorp.id("textures/entity/sweeper_a_corpse.png");
	/// 清道夫尸体模型资源。
	public static final Identifier MODEL_CORPSE = Lobecorp.id("entity/sweeper_a_corpse");
	/// 清道夫 A 变种纹理资源。
	public static final Identifier TEXTURE = Lobecorp.id("textures/entity/sweeper_a.png");
	/// 清道夫动画资源。
	public static final Identifier ANIMATION = Lobecorp.id("entity/sweeper_a");
	/// 当前已有资源对应的清道夫外观；缺少独立资源的变种显式回退到 A。
	private static final SweeperResources SWEEPER_A_RESOURCES =
			new SweeperResources(ANIMATION, TEXTURE, ANIMATION);
	private static final Map<SweeperVariant, SweeperResources> RESOURCES = Map.of(
			SweeperVariant.A, SWEEPER_A_RESOURCES,
			SweeperVariant.B, SWEEPER_A_RESOURCES,
			SweeperVariant.C, SWEEPER_A_RESOURCES,
			SweeperVariant.D, SWEEPER_A_RESOURCES);

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		Boolean isCorpse = renderState.getGeckolibData(LcDataTickets.IS_CORPSE);
		if (isCorpse != null && isCorpse) {
			return MODEL_CORPSE;
		}
		return resources(variantOf(renderState)).model();
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		Boolean isCorpse = renderState.getGeckolibData(LcDataTickets.IS_CORPSE);
		if (isCorpse != null && isCorpse) {
			return TEXTURES_CORPSE;
		}
		return resources(variantOf(renderState)).texture();
	}

	@Override
	public Identifier getAnimationResource(Sweeper sweeper) {
		return resources(sweeper.getVariant()).animation();
	}

	private static SweeperResources resources(SweeperVariant variant) {
		return RESOURCES.getOrDefault(variant, SWEEPER_A_RESOURCES);
	}

	private static SweeperVariant variantOf(GeoRenderState renderState) {
		return renderState.getOrDefaultGeckolibData(LcDataTickets.SWEEPER_VARIANT, SweeperVariant.A);
	}

	private record SweeperResources(Identifier model, Identifier texture, Identifier animation) {
	}
}
