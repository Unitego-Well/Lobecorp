package org.unitego.lobecorp.client.entity.abnormalitie.the_queen_of_hatred.renderer;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import org.joml.Vector2f;
import org.unitego.lobecorp.client.conductor.render.ConductorGeoOutlineLayer;
import org.unitego.lobecorp.client.entity.abnormalitie.the_queen_of_hatred.model.TheQueenOfHatredModel;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.registry.animation.client.LcDataTickets;

public class TheQueenOfHatredRenderer extends GeoEntityRenderer<TheQueenOfHatred, LivingEntityRenderState> {
	public TheQueenOfHatredRenderer(EntityRendererProvider.Context context) {
		super(context, new TheQueenOfHatredModel());
		withRenderLayer(new ConductorGeoOutlineLayer<>(this));
	}

	@Override
	public void addRenderData(TheQueenOfHatred animatable, Void relatedObject, LivingEntityRenderState renderState, float partialTick) {
		renderState.addGeckolibData(LcDataTickets.QUEEN_PHASE_TWO, animatable.isPhaseTwo());
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> renderPassInfo, BoneSnapshots snapshots) {
		LivingEntityRenderState renderState = renderPassInfo.renderState();
		Vector2f rot = new Vector2f();
//		snapshots.ifPresent("up_body", snapshot -> {
//			rot.add(snapshot.getRotX(), snapshot.getRotY());
//		});
		snapshots.ifPresent("Head", snapshot -> {
			snapshot.setRotX(snapshot.getRotX() + -renderState.xRot * Mth.DEG_TO_RAD);
			snapshot.setRotY(snapshot.getRotY() + -renderState.yRot * Mth.DEG_TO_RAD);
			rot.add(snapshot.getRotX(), snapshot.getRotY());
		});
		snapshots.ifPresent("hair", snapshot -> {
			snapshot.setRotX(snapshot.getRotX() - rot.x);
		});
	}
}
