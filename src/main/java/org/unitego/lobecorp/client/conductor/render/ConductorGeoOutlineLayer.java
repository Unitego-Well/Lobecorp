package org.unitego.lobecorp.client.conductor.render;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public class ConductorGeoOutlineLayer<T extends Entity & GeoAnimatable, R extends EntityRenderState & GeoRenderState>
		extends TextureLayerGeoLayer<T, Void, R> {
	public ConductorGeoOutlineLayer(GeoEntityRenderer<T, R> renderer) {
		super(renderer, MissingTextureAtlasSprite.getLocation());
	}

	@Override
	public void addRenderData(T entity, @Nullable Void relatedObject, R state, float partialTick) {
		ConductorRendering.updateEntitySelection(entity, state);
	}

	@Override
	protected Identifier getTextureResource(R renderState) {
		return renderer.getTextureLocation(renderState);
	}

	@Override
	protected @Nullable RenderType getRenderType(R renderState) {
		return ConductorRendering.isSelected(renderState)
				? RenderTypes.outline(getTextureResource(renderState)) : null;
	}

	@Override
	public void submitRenderTask(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
		R state = renderPassInfo.renderState();
		if (!ConductorRendering.isSelected(state))
			return;
		int previousColor = renderPassInfo.renderColor();
		state.addGeckolibData(DataTickets.RENDER_COLOR, state.outlineColor);
		try {
			super.submitRenderTask(renderPassInfo, renderTasks);
		} finally {
			state.addGeckolibData(DataTickets.RENDER_COLOR, previousColor);
		}
	}
}
