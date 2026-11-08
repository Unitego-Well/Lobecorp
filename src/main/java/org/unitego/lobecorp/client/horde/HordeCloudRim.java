package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;

import java.io.IOException;
import java.nio.ByteBuffer;

/// 借用原版云面与索引，只在真实轮廓内叠加朝源、断续的绯红边缘。
public class HordeCloudRim {
	private static final int PARAMETER_BYTES = 32;
	private static final Identifier CLOUD_TEXTURE = Identifier.withDefaultNamespace("textures/environment/clouds.png");
	private static final RenderPipeline NORMAL = pipeline(false), REVERSE = pipeline(true);
	private static GpuBuffer parameters;
	private static ByteBuffer memory;
	private static DynamicTexture mask;
	private static boolean failed;

	private static RenderPipeline pipeline(boolean reverse) {
		return RenderPipeline.builder(RenderPipelines.CLOUDS_SNIPPET)
				.withLocation(Lobecorp.id(reverse ? "pipeline/horde_cloud_rim_reverse" : "pipeline/horde_cloud_rim"))
				.withVertexShader(Lobecorp.id("core/horde_cloud_rim"))
				.withFragmentShader(Lobecorp.id("core/horde_cloud_rim"))
				.withUniform("RimParameters", UniformType.UNIFORM_BUFFER).withSampler("CloudMask")
				.withDepthStencilState(new DepthStencilState(reverse ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.LESS_THAN_OR_EQUAL, false))
				.withCull(false).build();
	}

	public static void prepare() {
		if (parameters != null || failed) {
			return;
		}

		try {
			var device = RenderSystem.getDevice();
			if (!device.precompilePipeline(NORMAL).isValid() || !device.precompilePipeline(REVERSE).isValid()) {
				throw new IllegalStateException("云缘管线编译失败");
			}

			try (var input = Minecraft.getInstance().getResourceManager().getResourceOrThrow(CLOUD_TEXTURE).open()) {
				var image = NativeImage.read(input);
				try {
					mask = new DynamicTexture(() -> "Horde cloud occupancy", image);
				} catch (RuntimeException exception) {
					image.close();
					throw exception;
				}
			}

			parameters = device.createBuffer(() -> "Horde cloud rim parameters",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, PARAMETER_BYTES);
			memory = MemoryUtil.memAlloc(PARAMETER_BYTES);
		} catch (IOException | RuntimeException exception) {
			clear();
			failed = true;
			Lobecorp.LOGGER.error("停止本代际事件云缘，保留原云和天空", exception);
		}
	}

	public static boolean ready() {
		return parameters != null && mask != null && !failed;
	}

	/// CloudInfo、CloudFaces、原索引和动态矩阵由调用方当前RenderPass持有，不转移所有权。
	public static void draw(RenderPass pass, Vec3 direction, float strength, float time, int cellX, int cellZ,
	                        int baseVertex, int firstIndex, int indexCount, int instanceCount) {
		memory.clear();
		memory.putFloat((float) direction.x).putFloat(0).putFloat((float) direction.z).putFloat(strength);
		memory.putFloat(time).putFloat(cellX).putFloat(cellZ).putFloat(0).flip();
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(), memory);
		HordeIrisShadows.setNativePipeline(pass, HordeIrisShadows.reverseDepth() ? REVERSE : NORMAL);
		pass.setUniform("RimParameters", parameters.slice());
		pass.bindTexture("CloudMask", mask.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
		pass.drawIndexed(baseVertex, firstIndex, indexCount, instanceCount);
	}

	public static void clear() {
		if (parameters != null) {
			parameters.close();
			parameters = null;
		}
		if (mask != null) {
			mask.close();
			mask = null;
		}
		if (memory != null) {
			MemoryUtil.memFree(memory);
			memory = null;
		}
		failed = false;
	}
}
