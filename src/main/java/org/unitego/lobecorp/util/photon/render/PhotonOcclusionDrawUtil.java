package org.unitego.lobecorp.util.photon.render;

import com.lowdragmc.photon.client.render.PhotonBufferCache;
import com.lowdragmc.photon.client.render.PhotonInstancedDrawState;
import com.lowdragmc.photon.client.render.PhotonMaskUniforms;
import com.lowdragmc.photon.client.render.PhotonMaterialUniforms;
import com.lowdragmc.photon.client.render.PhotonRenderTypes;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.InstancedJob;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.Job;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class PhotonOcclusionDrawUtil {
	/// Uniform 与采样器名称来自受支持的原生粒子着色器。
	private static final String TRANSFORM_UNIFORM = "DynamicTransforms";
	/// 原生粒子材质块的协议名称，供遮挡绘制绑定。
	private static final String MATERIAL_UNIFORM = "PhotonMaterial";
	/// 遮罩参数块的协议名称，供不透明度阈值读取。
	private static final String MASK_UNIFORM = "PhotonMask";
	/// 拖尾控制点参数块的协议名称。
	private static final String POINTS_UNIFORM = "PhotonPoints";
	/// 原生粒子纹理采样器的协议名称。
	private static final String TEXTURE_SAMPLER = "Sampler0";
	/// 原版光照纹理采样器的协议名称。
	private static final String LIGHTMAP_SAMPLER = "Sampler2";
	/// 深度预写阶段的调试名称；对应管线禁止写入颜色。
	private static final String PASS_LABEL = "Lobecorp Photon opaque depth";
	/// 原生绘制使用不改变颜色的乘数和单位纹理变换。
	private static final Vector4f NO_MODULATION = new Vector4f(1, 1, 1, 1);
	/// 无额外平移的零向量，只在绘制入口作为只读参数使用。
	private static final Vector3f NO_OFFSET = new Vector3f();
	/// 无附加矩阵变换的单位矩阵，绘制调用不得修改此共享值。
	private static final Matrix4f IDENTITY = new Matrix4f();

	public static void draw(List<DrawJob> jobs, @Nullable PhotonBufferCache cache,
	                        GpuTextureView colorTexture, @Nullable GpuTextureView depthTexture) {
		if (depthTexture == null || jobs.stream().noneMatch(PhotonOcclusionUtil::isMarked)) {
			return;
		}

		// 与原生遮罩绘制一致，所有可能触发上传的查找均须在开启 GPU 绘制阶段前完成。
		reserveIndices(jobs, cache);
		var draws = prepare(jobs, cache);

		if (draws.isEmpty()) {
			return;
		}

		var lightmap = Minecraft.getInstance().gameRenderer.lightmap();
		var lightmapSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

		try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> PASS_LABEL, colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty())) {
			applyScissor(pass);

			for (var draw : draws) {
				draw(pass, draw, lightmap, lightmapSampler);
			}
		}
	}

	/// 顺序索引存储扩容时会关闭旧缓冲，因此必须先按最大需求预留全部缓冲。
	@SuppressWarnings("resource")
	private static void reserveIndices(List<DrawJob> jobs, @Nullable PhotonBufferCache cache) {
		Map<RenderSystem.AutoStorageIndexBuffer, Integer> counts = new IdentityHashMap<>();

		for (var drawJob : jobs) {
			if (!PhotonOcclusionUtil.isMarked(drawJob)) {
				continue;
			}

			if (drawJob instanceof Job job && cache != null) {
				var state = job.mesh().drawState();
				counts.merge(RenderSystem.getSequentialBuffer(state.mode()), state.indexCount(), Math::max);
				continue;
			}

			if (drawJob instanceof InstancedJob job && job.geometry().indices() == null) {
				counts.merge(RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS),
						job.geometry().indexCount(), Math::max);
			}
		}

		counts.forEach(RenderSystem.AutoStorageIndexBuffer::getBuffer);
	}

	/// 这些网格和缓冲由作业及原生帧缓存持有，直到正常绘制完成。
	@SuppressWarnings("resource")
	private static List<PreparedDraw> prepare(List<DrawJob> jobs, @Nullable PhotonBufferCache cache) {
		List<PreparedDraw> draws = new ArrayList<>();
		int baseVertex = 0;

		for (var drawJob : jobs) {
			if (drawJob instanceof Job job) {
				if (PhotonRenderTypes.drawInfo(job.renderType()) == null) {
					continue;
				}

				var state = job.mesh().drawState();

				if (cache != null && PhotonOcclusionUtil.isMarked(job)) {
					var info = PhotonRenderTypes.drawInfo(PhotonOcclusionUtil.source(job.renderType()));
					var material = PhotonMaterialUniforms.sliceFor(PhotonOcclusionUtil.source(job.renderType()));

					if (info != null && material != null) {
						draws.add(prepare(job, material, info.bindings().textures().get(TEXTURE_SAMPLER),
								NO_OFFSET, cache.get(), sequentialIndices(state.mode(), state.indexCount()),
								baseVertex, state.indexCount(), null));
					}
				}

				// 累计所有 CPU 快速绘制作业，包括未标记作业；原生上传严格采用相同顺序。
				baseVertex += state.vertexCount();
				continue;
			}

			if (drawJob instanceof InstancedJob job && PhotonOcclusionUtil.isMarked(job)) {
				var geometry = job.geometry();
				var indices = geometry.indices() == null
						? sequentialIndices(VertexFormat.Mode.QUADS, geometry.indexCount())
						: new Indices(geometry.indices(), VertexFormat.IndexType.INT);
				draws.add(prepare(job, job.bindings().materialSlice(),
						job.bindings().textures().get(TEXTURE_SAMPLER), job.positionOffset(),
						geometry.vertices(), indices, 0, geometry.indexCount(), job));
			}
		}

		return draws;
	}

	private static PreparedDraw prepare(DrawJob job, GpuBufferSlice material, Identifier texture,
	                                    Vector3f offset, GpuBuffer vertices, Indices indices,
	                                    int baseVertex, int indexCount, @Nullable InstancedJob instance) {
		var transforms = RenderSystem.getDynamicUniforms().writeTransform(
				RenderSystem.getModelViewMatrix(), NO_MODULATION, offset, IDENTITY);
		var mask = PhotonMaskUniforms.sliceFor(new PhotonMaskUniforms.Values(0, PhotonOcclusionUtil.cutoff(job)));
		var source = Minecraft.getInstance().getTextureManager().getTexture(texture);
		return new PreparedDraw(PhotonOcclusionUtil.depthPipeline(job), transforms, material, mask,
				source.getTextureView(), source.getSampler(), vertices, indices, baseVertex, indexCount, instance);
	}

	private static Indices sequentialIndices(VertexFormat.Mode mode, int indexCount) {
		var buffer = RenderSystem.getSequentialBuffer(mode);
		return new Indices(buffer.getBuffer(indexCount), buffer.type());
	}

	private static void applyScissor(RenderPass pass) {
		var scissor = RenderSystem.getScissorStateForRenderTypeDraws();

		if (scissor.enabled()) {
			pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
		}
	}

	/// 实例化几何数据仅借用；原生 endFrame 在颜色及遮罩绘制结束后释放它。
	@SuppressWarnings("resource")
	private static void draw(RenderPass pass, PreparedDraw draw, GpuTextureView lightmap, GpuSampler sampler) {
		pass.setPipeline(draw.pipeline());
		RenderSystem.bindDefaultUniforms(pass);
		pass.setUniform(TRANSFORM_UNIFORM, draw.transforms());
		pass.setUniform(MATERIAL_UNIFORM, draw.material());
		pass.setUniform(MASK_UNIFORM, draw.mask());
		pass.bindTexture(TEXTURE_SAMPLER, draw.texture(), draw.sampler());
		pass.bindTexture(LIGHTMAP_SAMPLER, lightmap, sampler);
		pass.setVertexBuffer(0, draw.vertices());
		pass.setIndexBuffer(draw.indices().buffer(), draw.indices().type());
		var instance = draw.instance();

		if (instance == null) {
			pass.drawIndexed(draw.baseVertex(), 0, draw.indexCount(), 1);
			return;
		}

		var geometry = instance.geometry();

		if (geometry.points() != null) {
			pass.setUniform(POINTS_UNIFORM, geometry.points());
		}

		PhotonInstancedDrawState.begin(geometry.layout(), geometry.instances(), geometry.vertices());

		try {
			pass.drawIndexed(0, 0, draw.indexCount(), geometry.instanceCount());
		} finally {
			PhotonInstancedDrawState.end();
		}
	}

	private record Indices(GpuBuffer buffer, VertexFormat.IndexType type) {
	}

	private record PreparedDraw(RenderPipeline pipeline, GpuBufferSlice transforms, GpuBufferSlice material,
	                            GpuBufferSlice mask, GpuTextureView texture, GpuSampler sampler,
	                            GpuBuffer vertices, Indices indices, int baseVertex, int indexCount,
	                            @Nullable InstancedJob instance) {
	}
}
