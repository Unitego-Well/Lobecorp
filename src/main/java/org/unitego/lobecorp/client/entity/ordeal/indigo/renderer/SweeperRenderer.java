package org.unitego.lobecorp.client.entity.ordeal.indigo.renderer;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.conductor.render.ConductorGeoOutlineLayer;
import org.unitego.lobecorp.client.entity.ordeal.indigo.model.SweeperModel;
import org.unitego.lobecorp.client.renderer.AutoGlowingRenderLayer;
import org.unitego.lobecorp.client.renderer.DynamicCubeGeoLayer;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.registry.animation.client.LcDataTickets;
import org.unitego.lobecorp.util.animation.LcModelLodUtil;

import java.util.List;
import java.util.Map;
import org.unitego.lobecorp.world.horde.HordeParticipant;
import com.geckolib.constant.DataTickets;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public class SweeperRenderer extends GeoEntityRenderer<Sweeper, LivingEntityRenderState> {

	public SweeperRenderer(EntityRendererProvider.Context context) {
		super(context, new SweeperModel());
		AutoGlowingRenderLayer<Sweeper, Void, LivingEntityRenderState> glowingLayer =
				new AutoGlowingRenderLayer<>(this, LcDataTickets.SWEEPER_GLOW_STRENGTH);
		withRenderLayer(new DynamicCubeGeoLayer<>(this, "biomass", Map.of(
				Direction.UP, LcDataTickets.SWEEPER_BIOMASS_RATIO), List.of(glowingLayer)));
		withRenderLayer(glowingLayer);
		withRenderLayer(new ConductorGeoOutlineLayer<>(this));
	}

	@Override
	public int getPackedOverlay(Sweeper animatable, @Nullable Void relatedObject, float u, float partialTick) {
		return super.getPackedOverlay(animatable, relatedObject, u, partialTick);
	}

	@Override
	public int getRenderColor(Sweeper entity, @Nullable Void relatedObject, float partialTick) {
		int color = super.getRenderColor(entity, relatedObject, partialTick);
		if (!entity.isAlive() || !HordeParticipant.isEvent(entity)) {
			return color;
		}
		int alpha = (int) (HordeParticipant.get(entity).alpha(entity.level().getGameTime() + partialTick) * 255);
		return (color & 0xFFFFFF) | alpha << 24;
	}

	@Override
	public RenderType getRenderType(LivingEntityRenderState state, Identifier texture) {
		int color = state.getOrDefaultGeckolibData(DataTickets.RENDER_COLOR, -1);
		return (color >>> 24) < 255 ? RenderTypes.entityTranslucent(texture) : super.getRenderType(state, texture);
	}

	@Override
	protected float getShadowStrength(LivingEntityRenderState state) {
		int color = state.getOrDefaultGeckolibData(DataTickets.RENDER_COLOR, -1);
		return super.getShadowStrength(state) * (color >>> 24) / 255f;
	}

	@Override
	public void addRenderData(Sweeper animatable, @Nullable Void relatedObject, LivingEntityRenderState renderState, float partialTick) {
		LcModelLodUtil.capture(animatable, renderState);
		renderState.addGeckolibData(LcDataTickets.SWEEPER_VARIANT, animatable.getVariant());
		renderState.addGeckolibData(LcDataTickets.SWEEPER_BIOMASS_RATIO,
				animatable.getBiomass() / animatable.getBiomassCapacity());
		renderState.addGeckolibData(LcDataTickets.SWEEPER_GLOW_STRENGTH, 0.5f);
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> renderPassInfo, @NonNull BoneSnapshots snapshots) {
		LivingEntityRenderState renderState = renderPassInfo.renderState();
		snapshots.ifPresent("head", snapshot -> {
			snapshot.setRotX(snapshot.getRotX() + -renderState.xRot * Mth.DEG_TO_RAD);
			snapshot.setRotY(snapshot.getRotY() + -renderState.yRot * Mth.DEG_TO_RAD);
		});
	}
}
