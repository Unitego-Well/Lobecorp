package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil.Stage;

import java.util.Map;
import java.util.function.Function;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public class GlDeviceMixin {
	@WrapOperation(method = {"getOrCompilePipeline", "precompilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/shaders/ShaderSource;)Lcom/mojang/blaze3d/opengl/GlRenderPipeline;"}, at = @At(value = "INVOKE",
			target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
	private Object lobecorp$pipeline(Map<Object, Object> map, Object key,
	                                 Function<Object, Object> mapping, Operation<Object> original) {
		return PhotonMaterialDiagnosticsUtil.cache(Stage.DEVICE_PIPELINE_CACHE, Stage.DEVICE_PIPELINE_BUILD,
				map, key, mapping, original);
	}

	@WrapOperation(method = "getOrCompileShader", at = @At(value = "INVOKE",
			target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
	private Object lobecorp$shader(Map<Object, Object> map, Object key,
	                               Function<Object, Object> mapping, Operation<Object> original) {
		return PhotonMaterialDiagnosticsUtil.cache(Stage.DEVICE_SHADER_CACHE, Stage.DEVICE_SHADER_BUILD,
				map, key, mapping, original);
	}

	@WrapOperation(method = "compileShader", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/opengl/GlStateManager;glCompileShader(I)V"))
	private void lobecorp$compileShader(int shader, Operation<Void> original) {
		PhotonMaterialDiagnosticsUtil.call(Stage.GL_COMPILE_SHADER_CALL, original, shader);
	}

	@WrapOperation(method = "compileProgram", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/opengl/GlProgram;link(Lcom/mojang/blaze3d/opengl/GlShaderModule;Lcom/mojang/blaze3d/opengl/GlShaderModule;Lcom/mojang/blaze3d/vertex/VertexFormat;Ljava/lang/String;)Lcom/mojang/blaze3d/opengl/GlProgram;"))
	private GlProgram lobecorp$link(GlShaderModule vertex, GlShaderModule fragment, VertexFormat format,
	                                String label, Operation<GlProgram> original) {
		return PhotonMaterialDiagnosticsUtil.call(Stage.PROGRAM_LINK, original, vertex, fragment, format, label);
	}
}
