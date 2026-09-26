package org.unitego.lobecorp.entity.client.renderer.ordeal;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.client.renderer.AutoGlowingRenderLayer;
import org.unitego.lobecorp.entity.client.renderer.DynamicCubeGeoLayer;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.registry.client.LcDataTickets;

import java.util.Map;
import java.util.List;

public class SweeperRenderer extends GeoEntityRenderer<Sweeper, LivingEntityRenderState> {

	public SweeperRenderer(EntityRendererProvider.Context context) {
		super(context, new SweeperModel());
		AutoGlowingRenderLayer<Sweeper, Void, LivingEntityRenderState> glowingLayer =
				new AutoGlowingRenderLayer<>(this, LcDataTickets.SWEEPER_GLOW_STRENGTH);
		withRenderLayer(new DynamicCubeGeoLayer<>(this, "biomass", Map.of(
				Direction.UP, LcDataTickets.SWEEPER_BIOMASS_RATIO), List.of(glowingLayer)));
		withRenderLayer(glowingLayer);
	}

	@Override
	public int getPackedOverlay(Sweeper animatable, @Nullable Void relatedObject, float u, float partialTick) {
		return super.getPackedOverlay(animatable, relatedObject, u, partialTick);
	}

	@Override
	public void addRenderData(Sweeper animatable, @Nullable Void relatedObject, LivingEntityRenderState renderState, float partialTick) {
		renderState.addGeckolibData(LcDataTickets.SWEEPER_VARIANT, animatable.getVariant());
		renderState.addGeckolibData(LcDataTickets.SWEEPER_BIOMASS_RATIO,
				animatable.getBiomass() / animatable.getBiomassCapacity());
		renderState.addGeckolibData(LcDataTickets.SWEEPER_GLOW_STRENGTH, 0.5f);
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> renderPassInfo, BoneSnapshots snapshots) {
		LivingEntityRenderState renderState = renderPassInfo.renderState();
		snapshots.ifPresent("head", snapshot -> {
			snapshot.setRotX(snapshot.getRotX() + -renderState.xRot * Mth.DEG_TO_RAD);
			snapshot.setRotY(snapshot.getRotY() + -renderState.yRot * Mth.DEG_TO_RAD);
		});
	}
}
