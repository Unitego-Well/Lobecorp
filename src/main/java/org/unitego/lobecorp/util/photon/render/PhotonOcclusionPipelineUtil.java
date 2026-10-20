package org.unitego.lobecorp.util.photon.render;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.render.PhotonPipelines;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class PhotonOcclusionPipelineUtil {
	/// 原生粒子顶点阶段；自定义顶点程序保持原渲染路径。
	private static final Identifier PARTICLE_VERTEX = Photon.id("core/particle");
	/// 深度着色器复现这些原生纹理片元阶段的 Alpha 和 UV 运算。
	private static final Identifier HDR_FRAGMENT = Photon.id("core/hdr_particle");
	/// 原生像素粒子的片元阶段，用于识别可复用的遮挡渲染路径。
	private static final Identifier PIXEL_FRAGMENT = Photon.id("core/pixel_hdr_particle");
	/// 原生精灵粒子的片元阶段，用于识别可复用的遮挡渲染路径。
	private static final Identifier SPRITE_FRAGMENT = Photon.id("core/sprite_hdr_particle");
	/// 扩展片元阶段仅写入深度，不改变原生 HDR 颜色阶段。
	private static final Identifier DEPTH_FRAGMENT = Lobecorp.id("core/photon_occlusion_depth");
	/// 原生遮罩块提供不透明度阈值；本工具不持有 GPU 缓冲。
	private static final String MASK_UNIFORM = "PhotonMask";
	/// 自定义着色器材质块用于识别用户着色器，即使它们引用原生程序。
	private static final String CUSTOM_MATERIAL_UNIFORM = "PhotonCustomMaterial";
	/// 编译期 UV 分支对应原生像素和精灵片元阶段。
	private static final String PIXEL_DEFINE = "LOBECORP_OCCLUSION_PIXEL";
	/// 精灵 UV 分支的着色器宏，与原生片元阶段对应。
	private static final String SPRITE_DEFINE = "LOBECORP_OCCLUSION_SPRITE";
	/// 清理后仍保持位置标识唯一，因为设备缓存的寿命可能长于这些 Java 引用。
	private static final String DEPTH_LOCATION = "pipeline/photon_occlusion_depth_";
	/// 颜色管线变体的资源位置前缀，结合递增编号保持设备缓存标识唯一。
	private static final String COLOR_LOCATION = "pipeline/photon_occlusion_color_";
	/// 管线变体的递增标识序列，清理 Java 缓存后仍不重用编号。
	private static final AtomicLong VARIANT_ID = new AtomicLong();
	/// 以管线对象身份为缓存键，确保不同原生声明不共用变体。
	private static final Map<RenderPipeline, RenderPipeline> DEPTH_PIPELINES = new IdentityHashMap<>();
	/// 以原生管线对象身份为键的颜色管线变体缓存。
	private static final Map<RenderPipeline, RenderPipeline> COLOR_PIPELINES = new IdentityHashMap<>();
	/// 本扩展创建的深度管线身份集合，用于识别深度绘制路径。
	private static final Set<RenderPipeline> DEPTH_VARIANTS = Collections.newSetFromMap(new IdentityHashMap<>());

	public static boolean supports(RenderPipeline original) {
		if (original == null || original.getPolygonMode() != PolygonMode.FILL
				|| PhotonPipelines.isWireframe(original)
				|| !PARTICLE_VERTEX.equals(original.getVertexShader())) {
			return false;
		}

		if (original.getUniforms().stream().anyMatch(uniform -> CUSTOM_MATERIAL_UNIFORM.equals(uniform.name()))) {
			return false;
		}

		var fragment = original.getFragmentShader();
		return HDR_FRAGMENT.equals(fragment) || PIXEL_FRAGMENT.equals(fragment) || SPRITE_FRAGMENT.equals(fragment);
	}

	public static RenderPipeline depth(RenderPipeline original) {
		if (!supports(original)) {
			return original;
		}

		return DEPTH_PIPELINES.computeIfAbsent(original, PhotonOcclusionPipelineUtil::createDepth);
	}

	public static RenderPipeline color(RenderPipeline original) {
		if (!supports(original)) {
			return original;
		}

		return COLOR_PIPELINES.computeIfAbsent(original, PhotonOcclusionPipelineUtil::createColor);
	}

	public static boolean isDepth(RenderPipeline pipeline) {
		return DEPTH_VARIANTS.contains(pipeline);
	}

	public static void clear() {
		DEPTH_PIPELINES.clear();
		COLOR_PIPELINES.clear();
		DEPTH_VARIANTS.clear();
	}

	private static RenderPipeline createDepth(RenderPipeline original) {
		var builder = original.toBuilder()
				.withLocation(nextLocation(DEPTH_LOCATION))
				.withFragmentShader(DEPTH_FRAGMENT)
				.withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_NONE))
				.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true));

		if (original.getUniforms().stream().noneMatch(uniform -> MASK_UNIFORM.equals(uniform.name()))) {
			builder.withUniform(MASK_UNIFORM, UniformType.UNIFORM_BUFFER);
		}

		if (PIXEL_FRAGMENT.equals(original.getFragmentShader())) {
			builder.withShaderDefine(PIXEL_DEFINE);
		} else if (SPRITE_FRAGMENT.equals(original.getFragmentShader())) {
			builder.withShaderDefine(SPRITE_DEFINE);
		}

		var pipeline = builder.build();
		DEPTH_VARIANTS.add(pipeline);
		return pipeline;
	}

	private static RenderPipeline createColor(RenderPipeline original) {
		return original.toBuilder()
				.withLocation(nextLocation(COLOR_LOCATION))
				.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
				.build();
	}

	private static Identifier nextLocation(String prefix) {
		return Lobecorp.id(prefix + VARIANT_ID.getAndIncrement());
	}
}
