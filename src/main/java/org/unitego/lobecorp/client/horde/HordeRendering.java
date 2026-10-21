package org.unitego.lobecorp.client.horde;

import net.minecraft.util.context.ContextKey;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.unitego.lobecorp.Lobecorp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// 只提取完整三维实例，在世界颜色完成后绘制；不登记任何虚拟阴影回调。
public class HordeRendering {
	private static final ContextKey<List<Draw>> FRAMES = new ContextKey<>(Lobecorp.id("horde_frames"));
	private static List<Draw> pending = List.of();

	public static void extract(ExtractLevelRenderStateEvent event) {
		var draws = new ArrayList<Draw>();
		for (var instance : HordeClient.extract(event.getDeltaTracker().getGameTimeDeltaPartialTick(false), event.getFrustum())) {
			var meshes = HordeMeshCache.meshes(instance);
			if (meshes != null) {
				draws.add(new Draw(instance, meshes));
			}
		}
		draws.sort(Comparator.comparingDouble((Draw draw) -> draw.instance().distance()).reversed());
		event.getRenderState().setRenderData(FRAMES, List.copyOf(draws));
	}

	public static void submit(SubmitCustomGeometryEvent event) {
		var draws = event.getLevelRenderState().getRenderData(FRAMES);
		pending = draws == null ? List.of() : draws;
	}

	public static void render(RenderLevelStageEvent.AfterLevel event) {
		if (HordeIrisShadows.shadowPass()) {
			return;
		}
		var draws = pending;
		pending = List.of();
		HordeGpuRenderer.draw(draws, event.getModelViewMatrix(), HordeIrisShadows.reverseDepth());
	}

	public static String statistics() {
		return HordeGpuRenderer.statistics() + ", " + HordeVisibility.statistics();
	}

	public record Draw(HordeClient.Instance instance, HordeMeshCache.Pair meshes) {
	}

	public static void clear() {
		pending = List.of();
		HordeGpuRenderer.clear();
	}
}
