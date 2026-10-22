package org.unitego.lobecorp.client.horde;

import net.minecraft.util.context.ContextKey;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.unitego.lobecorp.Lobecorp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

/// 近处提取三维姿态，远处使用四视角动画；在世界颜色完成后绘制，不提交虚拟阴影。
public class HordeRendering {
	private static final ContextKey<List<Draw>> FRAMES = new ContextKey<>(Lobecorp.id("horde_frames"));
	private static List<Draw> pending = List.of();

	public static void extract(ExtractLevelRenderStateEvent event) {
		var draws = new ArrayList<Draw>();
		var instances = HordeClient.extract(event.getDeltaTracker().getGameTimeDeltaPartialTick(false), event.getFrustum());
		HordeImpostorAtlas.beginFrame();
		var atlases = new HashMap<HordeVisualDefinition, HordeImpostorAtlas.Atlas>();
		for (var instance : instances) {
			if (!atlases.containsKey(instance.visual())) {
				atlases.put(instance.visual(), HordeImpostorAtlas.get(instance.visual()));
			}
			var atlas = atlases.get(instance.visual());
			float blend = atlas == null ? 0 : (float) HordeSamplingUtil.smooth(
					(instance.distance() - HordeVisualConfig.IMPOSTOR_DISTANCE.get()) / HordeImpostorAtlas.TRANSITION_BLOCKS);
			if (blend < 1) {
				var meshes = HordeMeshCache.meshes(instance);
				if (meshes != null) {
					draws.add(new Draw(instance, meshes, null, 1 - blend));
				}
			}
			if (blend > 0) {
				draws.add(new Draw(instance, HordeImpostorAtlas.QUAD_PAIR, atlas, blend));
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
		return HordeGpuRenderer.statistics() + ", " + HordeVisibility.statistics() + ", " + HordeImpostorAtlas.statistics();
	}

	public record Draw(HordeClient.Instance instance, HordeMeshCache.Pair meshes, HordeImpostorAtlas.Atlas atlas, float opacity) {
	}

	public static void clear() {
		pending = List.of();
		HordeGpuRenderer.clear();
	}
}
