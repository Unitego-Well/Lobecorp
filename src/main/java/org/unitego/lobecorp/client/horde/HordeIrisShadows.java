package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.irisshaders.iris.vertices.ImmediateState;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.fml.ModList;

/// 隔离可选Iris依赖，核对实际顶点布局、阴影阶段和深度方向。
public class HordeIrisShadows {
	/// 自有计算与合成管线不参与光影替换；实体材质管线仍使用 Iris 原有路由。
	public static void setNativePipeline(RenderPass pass, RenderPipeline pipeline) {
		if (!ModList.get().isLoaded("iris")) {
			pass.setPipeline(pipeline);
			return;
		}

		boolean previous = ImmediateState.bypass;
		ImmediateState.bypass = true;
		try {
			pass.setPipeline(pipeline);
		} finally {
			ImmediateState.bypass = previous;
		}
	}

	public static boolean active() {
		return ModList.get().isLoaded("iris") && IrisApi.getInstance().isShaderPackInUse();
	}

	/// Iris会在世界渲染期间动态替换getVertexFormat，不能只检查原版常量。
	public static int entityVertexWords() {
		var format = RenderPipelines.ENTITY_CUTOUT.getVertexFormat();
		if (RenderPipelines.EYES.getVertexFormat() != format) {
			return 0;
		}
		if (format == DefaultVertexFormat.ENTITY && format.getVertexSize() == 36) {
			return 9;
		}
		if (ModList.get().isLoaded("iris") && format == IrisVertexFormats.ENTITY
				&& format.getVertexSize() == 56
				&& format.getOffset(IrisVertexFormats.ENTITY_ID_ELEMENT) == 36
				&& format.getOffset(IrisVertexFormats.MID_TEXTURE_ELEMENT) == 44
				&& format.getOffset(IrisVertexFormats.TANGENT_ELEMENT) == 52) {
			return 14;
		}
		return 0;
	}

	public static int entityId(EntityType<?> type) {
		if (!ModList.get().isLoaded("iris")) {
			return 0;
		}
		var ids = WorldRenderingSettings.INSTANCE.getEntityIds();
		var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return ids == null || id == null ? 0 : ids.getInt(new NamespacedId(id.getNamespace(), id.getPath()));
	}

	public static int shaderGeneration() {
		return ModList.get().isLoaded("iris")
				? Iris.getPipelineManager().getVersionCounterForSodiumShaderReload() : 0;
	}

	public static boolean shadowPass() {
		return ModList.get().isLoaded("iris") && IrisApi.getInstance().isRenderingShadowPass();
	}

	public static boolean reverseDepth() {
		return ModList.get().isLoaded("iris") && IrisApi.getInstance().isShaderPackInUse()
				&& IrisApi.getInstance().isReverseZDuringShaders();
	}
}
