package org.unitego.lobecorp.entity.client.renderer;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.client.renderer.DynamicCubeGeoLayer.DynamicRenderPass;

import java.util.HashMap;
import java.util.Map;

/// 为存在对应发光遮罩材质的模型追加可动态调节强度的发光渲染。
public class AutoGlowingRenderLayer<T extends GeoAnimatable, O, R extends GeoRenderState>
		extends AutoGlowingGeoLayer<T, O, R> implements DynamicCubeGeoLayer.RenderPassProvider<R> {
	/// 标准纹理文件扩展名。
	private static final String TEXTURE_EXTENSION = ".png";
	/// 发光遮罩纹理文件后缀。
	private static final String GLOWMASK_SUFFIX = "_glowmask.png";
	/// 禁用发光渲染的强度下限。
	private static final float MINIMUM_GLOW_STRENGTH = 0.0F;
	/// 完全发光的不透明度上限。
	private static final float MAXIMUM_GLOW_STRENGTH = 1.0F;

	private final Map<Identifier, Boolean> glowmaskExistence = new HashMap<>();
	private final @Nullable DataTicket<Float> glowStrengthDataTicket;
	private final float glowmaskValue;

	public AutoGlowingRenderLayer(GeoRenderer<T, O, R> renderer, DataTicket<Float> glowStrengthDataTicket) {
		super(renderer);
		this.glowStrengthDataTicket = glowStrengthDataTicket;
		this.glowmaskValue = MAXIMUM_GLOW_STRENGTH;
	}

	public AutoGlowingRenderLayer(GeoRenderer<T, O, R> renderer, float glowmaskValue) {
		super(renderer);
		this.glowStrengthDataTicket = null;
		this.glowmaskValue = glowmaskValue;
	}

	@Override
	protected @NonNull Identifier getTextureResource(@NonNull R renderState) {
		Identifier texture = renderer.getTextureLocation(renderState);
		return texture.withPath(path -> path.endsWith(TEXTURE_EXTENSION)
				? path.substring(0, path.length() - TEXTURE_EXTENSION.length()) + GLOWMASK_SUFFIX
				: path + GLOWMASK_SUFFIX);
	}

	@Override
	protected @Nullable RenderType getRenderType(@NonNull R renderState) {
		Identifier glowmask = getTextureResource(renderState);
		if (!glowmaskExistence.computeIfAbsent(glowmask,
				resource -> Minecraft.getInstance().getResourceManager().getResource(resource).isPresent())) {
			return null;
		}
		return super.getRenderType(renderState);
	}

	@Override
	public void submitRenderTask(@NonNull RenderPassInfo<R> renderPassInfo, @NonNull SubmitNodeCollector renderTasks) {
		float glowStrength = getGlowStrength(renderPassInfo);
		if (glowStrength <= MINIMUM_GLOW_STRENGTH) {
			return;
		}
		int renderColor = renderPassInfo.renderColor();
		int glowColor = ARGB.color(Math.round(ARGB.alpha(renderColor) * glowStrength),
				ARGB.red(renderColor), ARGB.green(renderColor), ARGB.blue(renderColor));
		renderPassInfo.renderState().addGeckolibData(DataTickets.RENDER_COLOR, glowColor);
		try {
			super.submitRenderTask(renderPassInfo, renderTasks);
		} finally {
			renderPassInfo.renderState().addGeckolibData(DataTickets.RENDER_COLOR, renderColor);
		}
	}

	@Override
	public @Nullable DynamicRenderPass createRenderPass(RenderPassInfo<R> renderPassInfo) {
		float glowStrength = getGlowStrength(renderPassInfo);
		RenderType renderType = getRenderType(renderPassInfo.renderState());
		if (glowStrength <= MINIMUM_GLOW_STRENGTH) {
			return null;
		}
		if (renderType == null) {
			return null;
		}
		int renderColor = renderPassInfo.renderColor();
		int glowColor = ARGB.color(Math.round(ARGB.alpha(renderColor) * glowStrength),
				ARGB.red(renderColor), ARGB.green(renderColor), ARGB.blue(renderColor));
		return new DynamicRenderPass(renderType, getBrightness(renderPassInfo.renderState()),
				renderPassInfo.packedOverlay(), glowColor);
	}

	private float getGlowStrength(RenderPassInfo<R> renderPassInfo) {
		return Math.clamp(glowStrengthDataTicket == null
				? glowmaskValue
				: renderPassInfo.getOrDefaultGeckolibData(glowStrengthDataTicket, MAXIMUM_GLOW_STRENGTH),
				MINIMUM_GLOW_STRENGTH, MAXIMUM_GLOW_STRENGTH);
	}
}
