package org.unitego.lobecorp.client.photon.render.validation;

import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.render.PhotonPipelines;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.render.PhotonOcclusionAccess;
import org.unitego.lobecorp.config.photon.PhotonOcclusionConfig;
import org.unitego.lobecorp.util.photon.render.PhotonOcclusionPipelineUtil;
import org.unitego.lobecorp.util.photon.render.PhotonOcclusionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Stream;

/// 模组加载后的隔离配置和管线结构检查；不提交 GPU 绘制，不清理当前帧或正在使用的资源。
/// 真实遮挡、着色器编译和颜色输出需要另行在游戏中验收。
public class PhotonOcclusionRegression {
	/// 原生序列化协议字段，仅供隔离夹具检查开关与旧数据缺省。
	private static final String ENABLE_FIELD = "_enable";
	/// 材质透明裁剪阈值的反射字段名。
	private static final String CUTOFF_FIELD = "alphaCutoff";
	/// Lobecorp 遮挡配置的桥接字段名。
	private static final String OCCLUSION_FIELD = "lobecorp$occlusion";
	/// 原生材质与扩展片段的协议名称；不绑定或分配对应 GPU 缓冲。
	private static final String MASK_UNIFORM = "PhotonMask";
	/// 自定义材质流水线的统一变量块名称。
	private static final String CUSTOM_UNIFORM = "PhotonCustomMaterial";
	/// 逐像素遮挡着色器的预处理标识。
	private static final String PIXEL_DEFINE = "LOBECORP_OCCLUSION_PIXEL";
	/// 整片粒子遮挡着色器的预处理标识。
	private static final String SPRITE_DEFINE = "LOBECORP_OCCLUSION_SPRITE";
	/// 空注册上下文足够处理这些原始值，避免借用当前世界。
	private static final HolderLookup.Provider LOOKUPS = HolderLookup.Provider.create(Stream.empty());
	/// 固定夹具重复使用，防止每次点击测试都向管线缓存添加新源对象。
	private static final List<RenderPipeline> PIPELINES = pipelines();
	/// 不支持遮挡配置的原生渲染流水线集合。
	private static final List<RenderPipeline> UNSUPPORTED = unsupportedPipelines();
	private int assertions;

	public static int runLoaded() {
		var checks = new PhotonOcclusionRegression();
		checks.configuration();
		checks.serialization();
		checks.rendererIsolation();
		checks.pipelineVariants();
		checks.unsupported();
		checks.emptyJobs();
		return checks.assertions;
	}

	private void configuration() {
		var config = new PhotonOcclusionConfig();
		require(!config.isEnable(), "Old effects must leave opaque occlusion disabled");
		equal(config.getAlphaCutoff(), PhotonOcclusionConfig.DEFAULT_ALPHA_CUTOFF, "Default opacity cutoff");

		for (var invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
			config.setAlphaCutoff(invalid);
			equal(config.getAlphaCutoff(), PhotonOcclusionConfig.DEFAULT_ALPHA_CUTOFF, "Non-finite cutoff fallback");
		}

		config.setAlphaCutoff(-0.25f);
		equal(config.getAlphaCutoff(), 0f, "Lower cutoff clamp");
		config.setAlphaCutoff(1.25f);
		equal(config.getAlphaCutoff(), 1f, "Upper cutoff clamp");
		config.setAlphaCutoff(0.75f);
		equal(config.getAlphaCutoff(), 0.75f, "Finite cutoff remains exact");

		var other = new PhotonOcclusionConfig();
		equalConfigs(config, other, "Disabled cutoff must not split native batching");
		config.setEnable(true);
		require(!config.equals(other), "Enabled occlusion must not equal the native path");
		other.setEnable(true);
		require(!config.equals(other), "Different enabled cutoffs must not merge");
		other.setAlphaCutoff(config.getAlphaCutoff());
		equalConfigs(config, other, "Equal cutoffs must retain the hash contract");
	}

	private void serialization() {
		var config = new PhotonOcclusionConfig();
		var disabled = serialize(config);
		require(!disabled.getBooleanOr(ENABLE_FIELD, true), "Native toggle serializes disabled state");
		require(!disabled.contains(CUTOFF_FIELD), "Disabled toggle skips its parameter fields");
		var legacy = deserialize(new CompoundTag());
		require(!legacy.isEnable(), "Missing old toggle data remains disabled");
		equal(legacy.getAlphaCutoff(), PhotonOcclusionConfig.DEFAULT_ALPHA_CUTOFF, "Missing cutoff keeps the default");

		config.setEnable(true);
		config.setAlphaCutoff(0.875f);
		var encoded = serialize(config);
		require(encoded.getBooleanOr(ENABLE_FIELD, false) && encoded.contains(CUTOFF_FIELD), "Enabled toggle writes the cutoff");
		var restored = deserialize(encoded);
		require(restored.isEnable(), "Enabled toggle round trip");
		equal(restored.getAlphaCutoff(), config.getAlphaCutoff(), "Cutoff round trip");

		encoded.putFloat(CUTOFF_FIELD, Float.NaN);
		equal(deserialize(encoded).getAlphaCutoff(), PhotonOcclusionConfig.DEFAULT_ALPHA_CUTOFF, "Malformed cutoff remains finite after loading");
		encoded.putFloat(CUTOFF_FIELD, 2f);
		equal(deserialize(encoded).getAlphaCutoff(), 1f, "Loaded cutoff respects the upper bound");
	}

	private void rendererIsolation() {
		var first = new RendererSetting();
		var second = new RendererSetting();
		var a = access(first).lobecorp$getOcclusionConfig();
		var b = access(second).lobecorp$getOcclusionConfig();
		require(a != b, "Each renderer owns its occlusion config");
		var firstRuntime = new TestRuntime(first);
		var secondRuntime = new TestRuntime(second);
		require(access(firstRuntime).lobecorp$getOcclusionConfig() == a, "Runtime reads its original renderer config");
		require(access(secondRuntime).lobecorp$getOcclusionConfig() == b, "Other runtime keeps its own renderer config");
		require(first.equals(second) && firstRuntime.effectiveEquals(secondRuntime), "Disabled settings retain native equality");
		require(first.hashCode() == second.hashCode() && firstRuntime.effectiveHashCode() == secondRuntime.effectiveHashCode(),
				"Disabled settings retain native hashes");

		a.setAlphaCutoff(0.5f);
		require(first.equals(second) && firstRuntime.effectiveEquals(secondRuntime), "Disabled cutoff changes do not split draws");
		a.setEnable(true);
		require(!first.equals(second) && !firstRuntime.effectiveEquals(secondRuntime), "Enabled renderer cannot merge with the original path");
		require(!access(secondRuntime).lobecorp$isOcclusionEnabled(), "Another runtime does not inherit the switch");
		b.setEnable(true);
		require(!first.equals(second) && !firstRuntime.effectiveEquals(secondRuntime), "Enabled threshold differences split batching");
		b.setAlphaCutoff(a.getAlphaCutoff());
		require(first.equals(second) && firstRuntime.effectiveEquals(secondRuntime), "Equal enabled renderers may share native batching");
		require(first.hashCode() == second.hashCode() && firstRuntime.effectiveHashCode() == secondRuntime.effectiveHashCode(),
				"Enabled equality preserves both hash contracts");
		firstRuntime.clear();
		require(access(firstRuntime).lobecorp$isOcclusionEnabled(), "Clearing runtime overrides does not edit source configuration");

		var encoded = PersistedParser.serializeNBT(first, LOOKUPS);
		var restored = new RendererSetting();
		PersistedParser.deserializeNBT(encoded, restored, LOOKUPS);
		require(access(restored).lobecorp$isOcclusionEnabled(), "Native renderer serializer retains the extension");
		equal(access(restored).lobecorp$getOcclusionAlphaCutoff(), a.getAlphaCutoff(), "Native renderer cutoff round trip");
		encoded.remove(OCCLUSION_FIELD);
		var oldRenderer = new RendererSetting();
		PersistedParser.deserializeNBT(encoded, oldRenderer, LOOKUPS);
		require(!access(oldRenderer).lobecorp$isOcclusionEnabled(), "Renderer without the extension remains on the native path");
	}

	private void pipelineVariants() {
		for (var original : PIPELINES) {
			require(PhotonOcclusionPipelineUtil.supports(original), "Native particle variant is supported");
			var sourceColor = original.getColorTargetState();
			var sourceDepth = original.getDepthStencilState();
			var depth = PhotonOcclusionPipelineUtil.depth(original);
			var color = PhotonOcclusionPipelineUtil.color(original);
			require(depth != original && color != original && depth != color, "Depth and color own distinct pipeline declarations");
			require(depth == PhotonOcclusionPipelineUtil.depth(original) && color == PhotonOcclusionPipelineUtil.color(original),
					"Repeated lookup reuses the same identity variants");
			require(!depth.getLocation().equals(color.getLocation()) && !color.getLocation().equals(original.getLocation()),
					"Variants use distinct device cache locations");
			require(PhotonOcclusionPipelineUtil.isDepth(depth) && !PhotonOcclusionPipelineUtil.isDepth(color),
					"Depth marker does not leak to color");
			preserved(original, depth);
			preserved(original, color);
			require(color.getFragmentShader().equals(original.getFragmentShader()), "Native color shader remains unchanged");
			require(color.getShaderDefines().equals(original.getShaderDefines()), "Color shader defines remain unchanged");
			require(color.getUniforms().equals(original.getUniforms()), "Color uniform layout remains unchanged");
			require(color.getColorTargetState().equals(sourceColor), "Original RGB and alpha blend factors and write mask remain unchanged");
			require(depth.getFragmentShader().equals(Lobecorp.id("core/photon_occlusion_depth")), "Depth uses the opacity-only shader");
			require(depth.getColorTargetState().writeMask() == ColorTargetState.WRITE_NONE
					&& depth.getColorTargetState().blendFunction().isEmpty(), "Prepass writes no color or alpha");
			require(new HashSet<>(depth.getUniforms()).containsAll(original.getUniforms()), "Depth retains native material, transforms and instance-point blocks");
			require(depth.getUniforms().stream().filter(uniform -> MASK_UNIFORM.equals(uniform.name())).count() == 1,
					"Depth has exactly one cutoff uniform");
			require(depth.getShaderDefines().flags().containsAll(original.getShaderDefines().flags())
							&& depth.getShaderDefines().values().entrySet().containsAll(original.getShaderDefines().values().entrySet()),
					"Depth retains original instance and vertex shader definitions");
			uvBranch(original, depth);
			require(depth.getDepthStencilState() != null && depth.getDepthStencilState().depthTest() == CompareOp.LESS_THAN_OR_EQUAL
					&& depth.getDepthStencilState().writeDepth(), "Prepass tests and writes depth");
			require(color.getDepthStencilState() != null && color.getDepthStencilState().depthTest() == CompareOp.LESS_THAN_OR_EQUAL
					&& !color.getDepthStencilState().writeDepth(), "Color tests resolved depth without writing transparent pixels");
			require(original.getColorTargetState().equals(sourceColor) && Objects.equals(original.getDepthStencilState(), sourceDepth),
					"Creating variants does not mutate the original render pipeline");
		}
	}

	private void preserved(RenderPipeline original, RenderPipeline variant) {
		require(variant.getVertexShader().equals(original.getVertexShader()), "Native vertex shader remains unchanged");
		require(variant.getVertexFormat() == original.getVertexFormat()
				&& variant.getVertexFormatMode() == original.getVertexFormatMode(), "Native CPU and instance vertex layout remains unchanged");
		require(variant.getSamplers().equals(original.getSamplers()), "Texture and lightmap sampler layout remains unchanged");
		require(variant.isCull() == original.isCull() && variant.getPolygonMode() == original.getPolygonMode(),
				"Culling and polygon state remain unchanged");
		require(variant.getStencilTest().equals(original.getStencilTest()), "Stencil state remains unchanged");
	}

	private void uvBranch(RenderPipeline original, RenderPipeline depth) {
		var fragment = original.getFragmentShader();
		require(depth.getShaderDefines().flags().contains(PIXEL_DEFINE) == fragment.equals(Photon.id("core/pixel_hdr_particle")),
				"Pixel UV branch follows the original fragment shader");
		require(depth.getShaderDefines().flags().contains(SPRITE_DEFINE) == fragment.equals(Photon.id("core/sprite_hdr_particle")),
				"Sprite UV branch follows the original fragment shader");
	}

	@SuppressWarnings("ConstantValue") // 缺省管线的回归断言需要显式传入 null。
	private void unsupported() {
		require(!PhotonOcclusionPipelineUtil.supports(null) && PhotonOcclusionPipelineUtil.depth(null) == null
				&& PhotonOcclusionPipelineUtil.color(null) == null, "Missing pipeline remains untouched");

		for (var pipeline : UNSUPPORTED) {
			require(!PhotonOcclusionPipelineUtil.supports(pipeline), "Custom and wireframe pipelines remain unsupported");
			require(PhotonOcclusionPipelineUtil.depth(pipeline) == pipeline && PhotonOcclusionPipelineUtil.color(pipeline) == pipeline,
					"Unsupported pipeline retains its original identity");
		}
	}

	private void emptyJobs() {
		var jobs = new ArrayList<DrawJob>();
		var renderer = new RendererSetting();
		var runtime = new TestRuntime(renderer);
		PhotonOcclusionUtil.mark(jobs, 0, runtime);
		require(jobs.isEmpty(), "Disabled empty submission creates no jobs");
		access(renderer).lobecorp$getOcclusionConfig().setEnable(true);
		PhotonOcclusionUtil.mark(jobs, 0, runtime);
		require(jobs.isEmpty(), "Enabled empty submission creates no jobs or GPU work");
		require(!PhotonOcclusionUtil.isMarked(null), "Missing job has no inherited frame marker");
	}

	private CompoundTag serialize(PhotonOcclusionConfig config) {
		var problems = new ProblemReporter.Collector();
		var output = TagValueOutput.createWithContext(problems, LOOKUPS);
		config.serialize(output);
		require(problems.isEmpty(), "Config serialization: " + problems.getReport());
		return output.buildResult();
	}

	private PhotonOcclusionConfig deserialize(CompoundTag tag) {
		var config = new PhotonOcclusionConfig();
		var problems = new ProblemReporter.Collector();
		config.deserialize(TagValueInput.create(problems, LOOKUPS, tag));
		require(problems.isEmpty(), "Config deserialization: " + problems.getReport());
		return config;
	}

	private static PhotonOcclusionAccess access(Object renderer) {
		if (!(renderer instanceof PhotonOcclusionAccess access)) {
			throw new AssertionError("Occlusion checks require the renderer mixins after mod loading");
		}

		return access;
	}

	private void equalConfigs(PhotonOcclusionConfig first, PhotonOcclusionConfig second, String message) {
		require(first.equals(second), message);
		require(first.hashCode() == second.hashCode(), message);
	}

	private void equal(float actual, float expected, String message) {
		require(Float.compare(actual, expected) == 0, message);
	}

	private void require(boolean value, String message) {
		assertions++;

		if (!value) {
			throw new AssertionError(message);
		}
	}

	private static List<RenderPipeline> pipelines() {
		var key = new PhotonPipelines.ParticlePipelineKey(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA,
				PhotonPipelines.BLEND_EQUATION_ADD, false, false, true, VertexFormat.Mode.QUADS, false);
		var pipelines = new ArrayList<RenderPipeline>();

		for (var fragment : List.of(Photon.id("core/hdr_particle"), Photon.id("core/pixel_hdr_particle"), Photon.id("core/sprite_hdr_particle"))) {
			pipelines.add(PhotonPipelines.hdrParticle(fragment, key));

			for (var variant : PhotonPipelines.InstancedVariant.values()) {
				pipelines.add(PhotonPipelines.instancedHdrParticle(variant, fragment, key));
			}
		}

		return List.copyOf(pipelines);
	}

	private static List<RenderPipeline> unsupportedPipelines() {
		var base = PIPELINES.getFirst();
		return List.of(
				PhotonPipelines.hdrParticle(PhotonPipelines.ParticlePipelineKey.wireframe(VertexFormat.Mode.QUADS)),
				base.toBuilder().withPolygonMode(PolygonMode.WIREFRAME).build(),
				base.toBuilder().withFragmentShader(Lobecorp.id("test/occlusion_custom_fragment")).build(),
				base.toBuilder().withVertexShader(Lobecorp.id("test/occlusion_custom_vertex")).build(),
				base.toBuilder().withUniform(CUSTOM_UNIFORM, UniformType.UNIFORM_BUFFER).build());
	}

	private static class TestRuntime extends RendererSetting.Runtime {
		private TestRuntime(RendererSetting config) {
			super(config);
		}
	}
}
