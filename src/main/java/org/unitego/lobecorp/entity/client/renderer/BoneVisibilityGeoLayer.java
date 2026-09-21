package org.unitego.lobecorp.entity.client.renderer;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/// 根据当前渲染状态控制指定骨骼是否可见。
///
/// <p>该层只跳过目标骨骼自身的几何体，不会连带隐藏子骨骼，与原有饰品骨骼可见性行为一致。
/// 可见性在每次渲染时重新计算，不保存跨帧状态。</p>
public class BoneVisibilityGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState>
		extends GeoRenderLayer<T, O, R> {
	private final Map<String, Predicate<R>> visibilityRules;

	public BoneVisibilityGeoLayer(GeoRenderer<T, O, R> renderer, Map<String, Predicate<R>> visibilityRules) {
		super(renderer);
		this.visibilityRules = Map.copyOf(visibilityRules);
	}

	/// 使用布尔渲染数据创建骨骼可见性层。未写入对应数据时，骨骼默认可见。
	public static <T extends GeoAnimatable, O, R extends GeoRenderState>
	BoneVisibilityGeoLayer<T, O, R> fromDataTickets(GeoRenderer<T, O, R> renderer,
			Map<String, DataTicket<Boolean>> visibilityDataTickets) {
		Map<String, Predicate<R>> visibilityRules = new HashMap<>();
		visibilityDataTickets.forEach((boneName, dataTicket) -> visibilityRules.put(boneName,
				renderState -> renderState.getOrDefaultGeckolibData(dataTicket, true)));
		return new BoneVisibilityGeoLayer<>(renderer, visibilityRules);
	}

	@Override
	public void preRender(RenderPassInfo<R> renderPassInfo, @NonNull SubmitNodeCollector renderTasks) {
		if (!renderPassInfo.willRender()) {
			return;
		}
		renderPassInfo.addBoneUpdater((ignoredRenderPass, snapshots) -> visibilityRules.forEach((boneName, isVisible) ->
				snapshots.get(boneName).ifPresent(snapshot ->
						snapshot.skipRender(!isVisible.test(renderPassInfo.renderState())))));
	}
}
