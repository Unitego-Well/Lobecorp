package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.world.horde.definition.MidnightCleanup;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/// A2方向天空；普通路径在天空后绘制，光影路径在世界阶段末仅合成远平面像素。
public class HordeRedSky {
	private static final int PARAMETER_BYTES = 96;
	private static final double MAXIMUM_OPACITY = 0.78;
	private static final RenderPipeline SKY = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/horde_red_sky"))
			.withVertexShader(Lobecorp.id("core/horde_red_sky"))
			.withFragmentShader(Lobecorp.id("core/horde_red_sky"))
			.withUniform("SkyParameters", UniformType.UNIFORM_BUFFER).withSampler("SkyDepth")
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
	private static GpuBuffer parameters;
	private static ByteBuffer memory;
	private static boolean prepared, failed;

	private record Atmosphere(Vec3 direction, float strength, float time) {
	}

	/// 复用现有资源准备节奏，不在首次非零强度的绘制调用中编译或分配。
	public static void prepare() {
		if (prepared || failed || !HordeVisualConfig.ENABLED.get()
				|| HordeClient.events().stream().noneMatch(event -> event.ready && !event.failed && MidnightCleanup.ID.equals(event.profile))) {
			return;
		}

		try {
			var device = RenderSystem.getDevice();
			if (!device.precompilePipeline(SKY).isValid()) {
				throw new IllegalStateException("天空管线编译失败");
			}
			parameters = device.createBuffer(() -> "Horde directional sky parameters",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, PARAMETER_BYTES);
			memory = MemoryUtil.memAlloc(PARAMETER_BYTES);
			prepared = true;
		} catch (RuntimeException failure) {
			failed = true;
			Lobecorp.LOGGER.error("停止本代际深夜清扫天空表现，其它事件表现继续", failure);
		}
	}

	public static void draw(RenderLevelStageEvent event, boolean shaderPass) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var camera = minecraft.gameRenderer.getMainCamera();
		var skybox = event.getLevelRenderState().skyRenderState.skybox;
		if (!prepared || !HordeVisualConfig.ENABLED.get() || level == null || !camera.isInitialized()
				|| HordeIrisShadows.shadowPass() || shaderPass != HordeIrisShadows.active()
				|| skybox == DimensionType.Skybox.NONE || skybox == DimensionType.Skybox.END) {
			return;
		}

		float partial = minecraft.isPaused() ? 0 : minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		var atmosphere = atmosphere(camera.position(), partial);
		if (atmosphere == null) {
			return;
		}

		var target = minecraft.getMainRenderTarget();
		var color = target.getColorTextureView();
		var depth = target.getDepthTextureView();
		if (color == null || depth == null) {
			return;
		}

		memory.clear();
		camera.getViewRotationProjectionMatrix(new Matrix4f()).invert().get(memory);
		memory.position(64);
		memory.putFloat((float) atmosphere.direction().x).putFloat(0).putFloat((float) atmosphere.direction().z)
				.putFloat(atmosphere.strength());
		memory.putFloat(atmosphere.time()).putFloat(HordeIrisShadows.reverseDepth() ? 1 : 0)
				.putFloat(shaderPass ? 1 : 0).putFloat(0).flip();
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.writeToBuffer(parameters.slice(), memory);
		try (var pass = encoder.createRenderPass(() -> "Horde directional crimson sky", color, OptionalInt.empty())) {
			HordeIrisShadows.setNativePipeline(pass, SKY);
			pass.setViewport(0, 0, target.width, target.height);
			pass.setUniform("SkyParameters", parameters.slice());
			pass.bindTexture("SkyDepth", depth, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(0, 3);
		}
	}

	private static Atmosphere atmosphere(Vec3 camera, float partial) {
		Atmosphere result = null;
		for (var event : HordeClient.events()) {
			if (!event.ready || event.failed || event.tide == null || !MidnightCleanup.ID.equals(event.profile)) {
				continue;
			}

			double time = event.time(partial), station = event.tide.particleStation(camera);
			var center = event.tide.world(station, 0);
			var forward = event.tide.forward(station);
			double lateral = Math.abs(-(camera.x - center.x) * forward.z + (camera.z - center.z) * forward.x);
			double outside = Math.max(0, lateral - event.rules.width() / 2);
			double range = Math.clamp(1 - outside / 256, 0, 1);
			double arrival = station / event.speed - time;
			double departure = event.stop + station / event.speed - time;
			double approach = 0.15 + 0.85 * Math.clamp(1 - Math.max(0, arrival) / (60 * 20), 0, 1);
			double fade = Math.clamp(time / 40, 0, 1)
					* Math.clamp((event.stop + Math.ceil(event.length / event.speed) - time) / 40, 0, 1)
					* Math.clamp((departure + 60) / 60, 0, 1);
			float strength = (float) (MAXIMUM_OPACITY * range * approach * fade);
			if (strength <= 0 || result != null && result.strength() >= strength) {
				continue;
			}

			var entry = event.tide.world(0, 0).subtract(camera).multiply(1, 0, 1);
			var direction = entry.lengthSqr() > 1 ? entry.normalize() : event.tide.forward(0).scale(-1);
			result = new Atmosphere(direction, strength, (float) time);
		}
		return result;
	}

	public static void clear() {
		if (parameters != null) {
			parameters.close();
			parameters = null;
		}
		if (memory != null) {
			MemoryUtil.memFree(memory);
			memory = null;
		}
		prepared = false;
		failed = false;
	}
}
