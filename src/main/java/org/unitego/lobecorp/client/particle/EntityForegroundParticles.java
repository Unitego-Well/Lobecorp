package org.unitego.lobecorp.client.particle;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/// 实体前景四边形粒子：使用实体绘制前的不透明地形深度，半透明地形随后正常覆盖。
/// 方块实体通过独立的不透明特征预绘制补入遮挡深度。
public class EntityForegroundParticles {
	private static final QuadParticleRenderState STATE = new QuadParticleRenderState();
	private static TextureTarget terrainDepth;
	private static ParticleFeatureRenderer.ParticleBufferCache buffer;
	private static boolean captured;

	/// 供 SingleQuadParticle.extract 提交半透明四边形；不再提交原版状态，避免重复绘制。
	public static QuadParticleRenderState state() {
		return STATE;
	}

	public static void captureTerrainDepth() {
		captured = false;
		if (STATE.isEmpty()) {
			clear();
			return;
		}

		var main = Minecraft.getInstance().getMainRenderTarget();
		if (terrainDepth == null || terrainDepth.width != main.width || terrainDepth.height != main.height) {
			if (terrainDepth != null) {
				terrainDepth.destroyBuffers();
			}
			terrainDepth = new TextureTarget("Lobecorp entity foreground depth", main.width, main.height, true, main.useStencil);
		}

		terrainDepth.copyDepthFrom(main);
		captured = true;
	}

	public static boolean needsBlockEntityDepth() {
		return captured && !STATE.isEmpty();
	}

	/// 此时提交队列只含方块实体；沿用原材质的模型、裁切和深度写入规则。
	public static void captureBlockEntityDepth(FeatureRenderDispatcher dispatcher, RenderBuffers buffers) {
		var previousColor = RenderSystem.outputColorTextureOverride;
		var previousDepth = RenderSystem.outputDepthTextureOverride;
		try {
			RenderSystem.outputColorTextureOverride = terrainDepth.getColorTextureView();
			RenderSystem.outputDepthTextureOverride = terrainDepth.getDepthTextureView();
			dispatcher.renderSolidFeatures();
			buffers.bufferSource().endBatch();
			buffers.crumblingBufferSource().endBatch();
		} finally {
			RenderSystem.outputColorTextureOverride = previousColor;
			RenderSystem.outputDepthTextureOverride = previousDepth;
		}
	}
	public static void render() {
		try {
			if (!captured || STATE.isEmpty()) {
				return;
			}

			if (buffer == null) {
				buffer = new ParticleFeatureRenderer.ParticleBufferCache();
			}

			var prepared = STATE.prepare(buffer, true);
			if (prepared == null) {
				return;
			}

			var minecraft = Minecraft.getInstance();
			try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
					() -> "Lobecorp entity foreground particles", minecraft.getMainRenderTarget().getColorTextureView(),
					OptionalInt.empty(), terrainDepth.getDepthTextureView(), OptionalDouble.empty())) {
				pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
				pass.setUniform("Fog", RenderSystem.getShaderFog());
				pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
				STATE.render(prepared, buffer, pass, minecraft.getTextureManager());
			}
		} finally {
			STATE.clear();
			captured = false;
		}
	}

	/// 在世界卸载或无前景粒子时释放本工具拥有的 GPU 资源。
	public static void clear() {
		STATE.clear();
		captured = false;
		if (terrainDepth != null) {
			terrainDepth.destroyBuffers();
			terrainDepth = null;
		}

		if (buffer != null) {
			buffer.close();
			buffer = null;
		}
	}
}
