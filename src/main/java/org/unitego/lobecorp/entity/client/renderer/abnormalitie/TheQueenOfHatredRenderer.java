package org.unitego.lobecorp.entity.client.renderer.abnormalitie;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.registry.client.LcDataTickets;

public class TheQueenOfHatredRenderer extends GeoEntityRenderer<TheQueenOfHatred, LivingEntityRenderState> {
	/// 头部旋转对应的模型骨骼名称。
	private static final String HEAD_BONE_NAME = "head";

	public TheQueenOfHatredRenderer(EntityRendererProvider.Context context) {
		super(context, new TheQueenOfHatredModel());
	}

	@Override
	public void addRenderData(TheQueenOfHatred animatable, @Nullable Void relatedObject,
			LivingEntityRenderState renderState, float partialTick) {
		renderState.addGeckolibData(LcDataTickets.THE_QUEEN_OF_HATRED_SECOND_PHASE,
				animatable.isSecondPhase());
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> renderPassInfo, BoneSnapshots snapshots) {
		LivingEntityRenderState renderState = renderPassInfo.renderState();
		snapshots.ifPresent(HEAD_BONE_NAME, snapshot -> {
			snapshot.setRotX(snapshot.getRotX() + -renderState.xRot * Mth.DEG_TO_RAD);
			snapshot.setRotY(snapshot.getRotY() + renderState.yRot * Mth.DEG_TO_RAD);
		});
	}
}
