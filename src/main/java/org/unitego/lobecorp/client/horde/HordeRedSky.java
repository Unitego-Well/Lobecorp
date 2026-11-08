package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
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
	private static final double MAXIMUM_OPACITY = 0.92;
	/// 到达前二十秒起染，到达即满；仅在当地尾波通过后三秒淡出，单位为刻。
	private static final double APPROACH_TICKS = 20 * 20, TAIL_FADE_TICKS = 60;
	private static final int CLOUD_TINT = 0x360A18;
	private static final float CLOUD_BLEND = 0.72f;
	private static final float FOG_RED = 0.10F, FOG_GREEN = 0.015F, FOG_BLUE = 0.035F;
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
	private static ClientLevel frameLevel;
	private static Atmosphere frameAtmosphere;
	private static DimensionType.Skybox frameSkybox = DimensionType.Skybox.NONE;
	private static final Matrix4f frameInverse = new Matrix4f();

	private record Atmosphere(Vec3 direction, float strength, float time, float coverage) {
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
			HordeCloudRim.prepare();
		} catch (RuntimeException failure) {
			failed = true;
			Lobecorp.LOGGER.error("停止本代际深夜清扫天空表现，其它事件表现继续", failure);
		}
	}

	/// AfterLevel前原版会清空天空状态；在提取阶段冻结本帧天空和云的共同参数。
	public static void extract(ExtractLevelRenderStateEvent event) {
		if (HordeIrisShadows.shadowPass()) {
			return;
		}

		frameLevel = event.getLevel();
		frameSkybox = event.getRenderState().skyRenderState.skybox;
		frameAtmosphere = null;
		if (!HordeVisualConfig.ENABLED.get() || frameSkybox == DimensionType.Skybox.NONE
				|| frameSkybox == DimensionType.Skybox.END) {
			return;
		}

		float partial = Minecraft.getInstance().isPaused() ? 0 : event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		frameAtmosphere = atmosphere(event.getCamera().position(), partial);
		if (frameAtmosphere == null) {
			return;
		}

		event.getCamera().getViewRotationProjectionMatrix(frameInverse).invert();
		int original = event.getRenderState().cloudColor;
		int tint = original & 0xFF000000 | CLOUD_TINT;
		float blend = (float) (CLOUD_BLEND * frameAtmosphere.strength() / MAXIMUM_OPACITY);
		event.getRenderState().cloudColor = ARGB.srgbLerp(blend, original, tint);
	}

	/// 只协调事件影响中的空气远雾，不改流体、特殊状态雾或距离参数。
	public static void fog(ViewportEvent.ComputeFogColor event) {
		var minecraft = Minecraft.getInstance();
		var camera = event.getCamera();
		var level = minecraft.level;
		if (level == null || !HordeVisualConfig.ENABLED.get() || HordeIrisShadows.shadowPass()
				|| level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD
				|| camera.getFluidInCamera() != FogType.NONE && camera.getFluidInCamera() != FogType.ATMOSPHERIC
				|| minecraft.gui.getBossOverlay().shouldCreateWorldFog()
				|| camera.entity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
			return;
		}

		float partial = minecraft.isPaused() ? 0 : (float) event.getPartialTick();
		var atmosphere = atmosphere(camera.position(), partial);
		if (atmosphere == null) {
			return;
		}

		float blend = (float) (atmosphere.strength() / MAXIMUM_OPACITY);
		event.setRed(event.getRed() + (FOG_RED - event.getRed()) * blend);
		event.setGreen(event.getGreen() + (FOG_GREEN - event.getGreen()) * blend);
		event.setBlue(event.getBlue() + (FOG_BLUE - event.getBlue()) * blend);
	}

	public static boolean cloudRimActive() {
		return prepared && HordeVisualConfig.ENABLED.get() && frameLevel == Minecraft.getInstance().level
				&& frameAtmosphere != null && frameAtmosphere.strength() > 0 && HordeCloudRim.ready()
				&& !HordeIrisShadows.shadowPass();
	}

	public static void drawCloudRim(RenderPass pass, int cellX, int cellZ, int baseVertex, int firstIndex, int indexCount, int instanceCount) {
		try {
			HordeCloudRim.draw(pass, frameAtmosphere.direction(), frameAtmosphere.strength(), frameAtmosphere.time(),
					cellX, cellZ, baseVertex, firstIndex, indexCount, instanceCount);
		} catch (RuntimeException exception) {
			HordeCloudRim.clear();
			Lobecorp.LOGGER.error("停止本代际事件云缘，原云继续绘制", exception);
		}
	}

	public static void draw(RenderLevelStageEvent event, boolean shaderPass) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var camera = minecraft.gameRenderer.getMainCamera();
		var skybox = frameSkybox;
		if (!prepared || !HordeVisualConfig.ENABLED.get() || level == null || frameLevel != level || !camera.isInitialized()
				|| HordeIrisShadows.shadowPass() || shaderPass != HordeIrisShadows.active()
				|| skybox == DimensionType.Skybox.NONE || skybox == DimensionType.Skybox.END) {
			return;
		}

		var atmosphere = frameAtmosphere;
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
		frameInverse.get(memory);
		memory.position(64);
		memory.putFloat((float) atmosphere.direction().x).putFloat(0).putFloat((float) atmosphere.direction().z)
				.putFloat(atmosphere.strength());
		memory.putFloat(atmosphere.time()).putFloat(HordeIrisShadows.reverseDepth() ? 1 : 0)
				.putFloat(shaderPass ? 1 : 0).putFloat(atmosphere.coverage()).flip();
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
			double along = (camera.x - center.x) * forward.x + (camera.z - center.z) * forward.z;
			double endDistance = station <= 0 ? Math.max(0, -along) : station >= event.length ? Math.max(0, along) : 0;
			double outside = Math.hypot(Math.max(0, lateral - event.rules.width() / 2), endDistance);
			double range = Math.clamp(1 - outside / 256, 0, 1);
			double arrival = station / event.speed - time;
			double departure = event.stop + station / event.speed - time;
			double approaching = Math.clamp(1 - Math.max(0, arrival) / APPROACH_TICKS, 0, 1);
			double approach = approaching * approaching * (3 - 2 * approaching);
			double fade = Math.clamp((departure + TAIL_FADE_TICKS) / TAIL_FADE_TICKS, 0, 1);
			float strength = (float) (MAXIMUM_OPACITY * range * approach * fade);
			float coverage = (float) approach;
			if (strength <= 0 || result != null && result.strength() >= strength) {
				continue;
			}

			var entry = event.tide.world(0, 0).subtract(camera).multiply(1, 0, 1);
			var direction = entry.lengthSqr() > 1 ? entry.normalize() : event.tide.forward(0).scale(-1);
			result = new Atmosphere(direction, strength, (float) time, coverage);
		}
		return result;
	}

	public static void clear() {
		HordeCloudRim.clear();
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
		frameLevel = null;
		frameAtmosphere = null;
		frameSkybox = DimensionType.Skybox.NONE;
	}
}
