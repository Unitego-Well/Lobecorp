package org.unitego.lobecorp.client.horde;

import net.minecraft.util.context.ContextKey;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.unitego.lobecorp.Lobecorp;

import java.util.List;

/// 提取固定空间块的编号区间；在世界颜色完成后批量绘制，不提交虚拟阴影。
public class HordeRendering {
	private static final ContextKey<List<HordeClient.Batch>> FRAMES = new ContextKey<>(Lobecorp.id("horde_frames"));
	private static List<HordeClient.Batch> pending = List.of();

	public static void extract(ExtractLevelRenderStateEvent event) {
		var batches = HordeClient.extract(event.getDeltaTracker().getGameTimeDeltaPartialTick(false), event.getFrustum());
		event.getRenderState().setRenderData(FRAMES, batches);
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
		return HordeGpuRenderer.statistics();
	}

	public static void clear() {
		pending = List.of();
		HordeGpuRenderer.clear();
	}
}
