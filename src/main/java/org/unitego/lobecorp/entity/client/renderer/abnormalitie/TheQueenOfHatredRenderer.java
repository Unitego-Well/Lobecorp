package org.unitego.lobecorp.entity.client.renderer.abnormalitie;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import org.joml.Vector2f;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;

public class TheQueenOfHatredRenderer extends GeoEntityRenderer<TheQueenOfHatred, LivingEntityRenderState> {
	public TheQueenOfHatredRenderer(EntityRendererProvider.Context context) {
		super(context, new TheQueenOfHatredModel());
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> renderPassInfo, BoneSnapshots snapshots) {
		LivingEntityRenderState renderState = renderPassInfo.renderState();
		Vector2f rot = new Vector2f();
		snapshots.ifPresent("up_body", snapshot -> {
			rot.add(snapshot.getRotX(), snapshot.getRotY());
		});
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
