package org.unitego.lobecorp.entity.client.renderer.abnormalitie;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.registry.client.LcDataTickets;

public class TheQueenOfHatredModel extends GeoModel<TheQueenOfHatred> {
	/// 憎恶皇后模型资源。
	private static final Identifier MODEL = Lobecorp.id("entity/the_queen_of_hatred");
	/// 憎恶皇后纹理资源。
	private static final Identifier TEXTURE = Lobecorp.id("textures/entity/the_queen_of_hatred.png");
	/// 憎恶皇后第二阶段纹理资源。
	private static final Identifier SECOND_PHASE_TEXTURE =
			Lobecorp.id("textures/entity/the_queen_of_hatred_hysterical.png");
	/// 憎恶皇后动画资源。
	private static final Identifier ANIMATION = Lobecorp.id("entity/the_queen_of_hatred");

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return renderState.getOrDefaultGeckolibData(
				LcDataTickets.THE_QUEEN_OF_HATRED_SECOND_PHASE, false) ? SECOND_PHASE_TEXTURE : TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(TheQueenOfHatred animatable) {
		return ANIMATION;
	}
}
