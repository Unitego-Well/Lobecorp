package org.unitego.lobecorp.client.photon.runtime;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.InstancedRendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.trail.TrailEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraTrailEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.unitego.lobecorp.client.photon.render.PhotonIrisPipelineAccess;

import java.util.Set;

import com.lowdragmc.lowdraglib2.Platform;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.ByteArrayTag;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import org.unitego.lobecorp.util.photon.model.PhotonModelSplitUtil;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.render.PhotonPipelines;
import com.lowdragmc.photon.client.render.PhotonRenderTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import org.unitego.lobecorp.client.photon.model.PhotonModelMaterials;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.util.photon.model.PhotonModelSourceUtil;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 仅处理资源定义，不建立运行实例；共享纹理和管线由原有管理器持有。
public class PhotonOwnedEffectAssets {
	private static final Map<PhotonOwnedEffectResources.SourceKey, Map<FX, List<Object>>> PREPARED = new LinkedHashMap<>();
	private static final Map<PhotonOwnedEffectResources.SourceKey, String> DEVICE_STAMPS = new LinkedHashMap<>();

	private static long deviceRevision;

	public static void invalidateDeviceResources() {
		deviceRevision++;
	}

	private static String deviceStamp() {
		return deviceRevision + ":" + PhotonRenderTypes.reloadGeneration() + ":" +
				IrisCompat.isUsingShaderPack() + ":" +
				IrisCompat.packName();
	}

	public static boolean deviceCurrent(PhotonOwnedEffectResources.SourceKey source) {
		return deviceStamp().equals(DEVICE_STAMPS.get(source));
	}

	public static void prepare(FX definition, PhotonOwnedEffectResources.SourceKey source) {
		if (!Minecraft.getInstance().isSameThread()) {
			throw new IllegalStateException("静态资源准备必须在客户端线程");
		}
		var retained = PREPARED.computeIfAbsent(source, _ -> new IdentityHashMap<>());
		prepare(definition, source, retained, new HashSet<>(), 0);
		DEVICE_STAMPS.put(source, deviceStamp());
	}

	private static void prepare(FX definition, PhotonOwnedEffectResources.SourceKey source,
	                            Map<FX, List<Object>> retained, Set<Identifier> path, int depth) {
		if (retained.containsKey(definition)) {
			return;
		}
		if (depth > PhotonSpawnConfig.MAX_DEPTH) {
			throw new IllegalArgumentException("自有特效子资源深度超限");
		}
		var assets = new ArrayList<Object>();
		try {
			for (var object : definition.getFxData().objects()) {
				if (object instanceof PhotonModelOutput model) {
					if (source.kind() == PhotonOwnedEffectResources.SourceKind.AUTHORING
							&& model.config.source == PhotonModelOutputConfig.Source.FILE) {
						// 传入的是独立定义；作者FILE发布不得沿用上个修订的资产快照。
						model.config.setAssetSnapshot(new ByteArrayTag(new byte[0]));
					}
					var sampler = PhotonModelSourceUtil.prepare(model.config);
					var topology = sampler.prepared();
					model.compiledPreparation(() -> topology);
					var materials = new PhotonModelMaterials(model.config.overrideRenderer ? model.config.renderer : model.config.modelRenderer,
							model.rendererRuntime(), topology);
					assets.add(sampler);
					assets.add(materials);
					for (int index = 0; index < topology.groups().size(); index++) {
						prepareMaterials(materials.group(index), model.geometryMode());
					}
				} else if (object instanceof Emitter emitter) {
					prepareMaterials(emitter.rendererRuntime(), emitter.geometryMode(), variants(emitter));
				}
				if (object instanceof PhotonChildSpawner child && child.config.source == PhotonSpawnConfig.Source.FX) {
					var id = child.config.fxLocation;
					if (id == null || !path.add(id)) {
						throw new IllegalArgumentException("自有特效子资源缺失或循环：" + id);
					}
					try {
						var nested = PhotonOwnedEffectResources.resolveDefinition(id, source);
						prepare(nested, source, retained, path, depth + 1);
						assets.add(nested);
					} finally {
						path.remove(id);
					}
				}
			}
		} catch (IOException failure) {
			throw new UncheckedIOException("自有模型静态准备失败", failure);
		}
		retained.put(definition, List.copyOf(assets));
	}

	private static void prepareMaterials(RendererSetting.Runtime renderer, VertexFormat.Mode mode) {
		prepareMaterials(renderer, mode, List.of());
	}

	private static void precompile(RenderPipeline pipeline) {
		PhotonIrisPipelineAccess.useNativePipeline(() -> {
			if (!RenderSystem.getDevice().precompilePipeline(pipeline).isValid()) {
				throw new IllegalStateException("自有材质管线编译失败");
			}
		});
	}

	private static List<PhotonPipelines.InstancedVariant> variants(Emitter emitter) {
		if (emitter.rendererRuntime() instanceof ParticleRendererSetting.Runtime tile) {
			if (!tile.isUseGPUInstance()) {
				return List.of();
			}
			if (tile.getRenderMode() == ParticleRendererSetting.Mode.Model && tile.getModel() != null) {
				return List.of(PhotonPipelines.InstancedVariant.TILE, tile.isTangent() ? PhotonPipelines.InstancedVariant.MODEL_TANGENT : PhotonPipelines.InstancedVariant.MODEL);
			}
			return List.of(PhotonPipelines.InstancedVariant.TILE);
		}
		if (!(emitter.rendererRuntime() instanceof InstancedRendererSetting.Runtime instanced) || !instanced.isUseGPUInstance()) {
			return List.of();
		}
		return switch (emitter) {
			case BeamEmitter ignored -> List.of(PhotonPipelines.InstancedVariant.BEAM);
			case TrailEmitter ignored -> List.of(PhotonPipelines.InstancedVariant.TRAIL);
			case AraTrailEmitter ignored ->
					List.of(PhotonPipelines.InstancedVariant.ARA, PhotonPipelines.InstancedVariant.ARA_TUBE);
			default -> List.of();
		};
	}

	private static void prepareMaterials(RendererSetting.Runtime renderer, VertexFormat.Mode mode, List<PhotonPipelines.InstancedVariant> variants) {
		for (var setting : renderer.getMaterials()) {
			var type = setting.getRenderType(mode);
			if (type == null) {
				throw new IllegalStateException("自有材质未能创建渲染类型");
			}
			var info = PhotonRenderTypes.drawInfo(type);
			if (info == null) {
				throw new IllegalStateException("自有材质缺少可准备的渲染描述");
			}
			precompile(info.programs().main());
			if (!variants.isEmpty()) {
				var instancedType = mode == VertexFormat.Mode.QUADS ? type : setting.getRenderType(VertexFormat.Mode.QUADS);
				var instancedInfo = instancedType == null ? null : PhotonRenderTypes.drawInfo(instancedType);
				var recipe = instancedInfo == null ? null : instancedInfo.instanced();
				if (recipe != null) {
					for (var variant : variants) {
						var graph = instancedInfo.bindings().graph();
						if (graph != null) {
							precompile(PhotonPipelines.graphShader(graph.compiled(), variant, recipe.key(), graph.usedChannelMask(), graph.usesCustomData()));
						} else if (recipe.customShaderKey() != null) {
							precompile(PhotonPipelines.instancedCustomShader(variant, recipe.customShaderKey()));
						} else {
							precompile(PhotonPipelines.instancedHdrParticle(variant, recipe.hdrFragment(), recipe.key()));
						}
					}
				}
			}
			var manager = Minecraft.getInstance().getTextureManager();
			info.bindings().textures().values().forEach(manager::getTexture);
			if (info.bindings().customUniforms() != null) {
				info.bindings().customUniforms().prepareUpload();
			}
			if (info.bindings().graph() != null) {
				info.bindings().graph().material().prepareUniforms();
			}
		}
	}

	private static final Map<Path, FileFingerprint> AUTHOR_FILES = new LinkedHashMap<>();
	private static final Map<Identifier, String> AUTHOR_RESOURCES = new LinkedHashMap<>();
	private static WeakReference<FX> fingerprintOwner = new WeakReference<>(null);
	private static long fingerprintGeneration = -1;
	private static String fingerprintMemory = "";
	private static String fingerprintValue = "";
	private static int resourceProbe;

	private record FileFingerprint(String attributes, String content) {
	}

	/// 内存内容与文件属性作门禁；资源流不暴露文件属性，逐次轮转确认一个依赖的内容。
	public static String authoringFingerprint(FX definition) {
		try {
			long generation = PhotonOwnedEffectResources.resourceRevision();
			String memory = definition.serializeNBT(Platform.getFrozenRegistry()).toString();
			boolean reset = fingerprintOwner.get() != definition || fingerprintGeneration != generation;
			if (reset) {
				AUTHOR_FILES.clear();
				AUTHOR_RESOURCES.clear();
				resourceProbe = 0;
				fingerprintValue = "";
			}
			boolean changed = reset || !memory.equals(fingerprintMemory);
			for (var entry : AUTHOR_FILES.entrySet()) {
				var next = fileFingerprint(entry.getKey(), entry.getValue());
				changed |= !next.content().equals(entry.getValue().content());
				entry.setValue(next);
			}
			int dependencyCount = AUTHOR_RESOURCES.size() + AUTHOR_FILES.size();
			if (dependencyCount > 0) {
				int probe = Math.floorMod(resourceProbe++, dependencyCount);
				if (probe < AUTHOR_RESOURCES.size()) {
					var id = new ArrayList<>(AUTHOR_RESOURCES.keySet()).get(probe);
					var next = resourceFingerprint(id);
					changed |= !next.equals(AUTHOR_RESOURCES.put(id, next));
				} else {
					var file = new ArrayList<>(AUTHOR_FILES.keySet()).get(probe - AUTHOR_RESOURCES.size());
					var next = fileFingerprint(file, null);
					var previous = AUTHOR_FILES.put(file, next);
					changed |= previous == null || !next.content().equals(previous.content());
				}
			}
			if (changed || fingerprintValue.isEmpty()) {
				var files = new HashSet<Path>();
				var resources = new HashSet<Identifier>();
				var digest = MessageDigest.getInstance("SHA-256");
				fingerprint(definition, digest, new HashSet<>(), 0, files, resources);
				AUTHOR_FILES.keySet().retainAll(files);
				AUTHOR_RESOURCES.keySet().retainAll(resources);
				fingerprintValue = HexFormat.of().formatHex(digest.digest());
				fingerprintOwner = new WeakReference<>(definition);
				fingerprintGeneration = generation;
				fingerprintMemory = memory;
			}
			return fingerprintValue;
		} catch (IOException failure) {
			fingerprintValue = "";
			throw new UncheckedIOException(failure);
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private static FileFingerprint fileFingerprint(Path file, FileFingerprint cached) throws IOException {
		if (!Files.exists(file)) {
			return new FileFingerprint("missing", "missing");
		}
		var attrs = Files.readAttributes(file, BasicFileAttributes.class);
		var stamp = attrs.size() + ":" + attrs.lastModifiedTime() + ":" + attrs.creationTime() + ":" + attrs.fileKey();
		if (cached != null && stamp.equals(cached.attributes())) {
			return cached;
		}
		try (var input = Files.newInputStream(file)) {
			return new FileFingerprint(stamp, streamFingerprint(input));
		}
	}

	private static String streamFingerprint(InputStream input) throws IOException {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			var buffer = new byte[8192];
			int count;
			while ((count = input.read(buffer)) != -1) {
				digest.update(buffer, 0, count);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private static String resourceFingerprint(Identifier id) throws IOException {
		var resource = Minecraft.getInstance().getResourceManager().getResource(id);
		if (resource.isEmpty()) {
			return "missing";
		}
		try (var input = resource.get().open()) {
			return resource.get().sourcePackId() + ":" + streamFingerprint(input);
		}
	}

	private static void fingerprintResource(Identifier id, MessageDigest digest, Set<Identifier> resources) throws IOException {
		resources.add(id);
		var value = AUTHOR_RESOURCES.get(id);
		if (value == null) {
			value = resourceFingerprint(id);
			AUTHOR_RESOURCES.put(id, value);
		}
		digest.update(id.toString().getBytes(StandardCharsets.UTF_8));
		digest.update(value.getBytes(StandardCharsets.UTF_8));
	}

	private static void fingerprintFile(Path file, MessageDigest digest, Set<Path> files) throws IOException {
		file = file.toAbsolutePath().normalize();
		files.add(file);
		var value = fileFingerprint(file, AUTHOR_FILES.get(file));
		AUTHOR_FILES.put(file, value);
		digest.update(file.toString().getBytes(StandardCharsets.UTF_8));
		digest.update(value.content().getBytes(StandardCharsets.UTF_8));
	}

	private static void fingerprint(FX definition, MessageDigest digest, Set<Identifier> path, int depth,
	                                Set<Path> files, Set<Identifier> resources) throws IOException {
		if (depth > PhotonSpawnConfig.MAX_DEPTH) {
			throw new IOException("作者子资源深度超限");
		}
		digest.update(definition.serializeNBT(Platform.getFrozenRegistry()).toString().getBytes(StandardCharsets.UTF_8));
		for (var object : definition.getFxData().objects()) {
			if (object instanceof PhotonModelOutput model) {
				var config = model.config;
				if (config.source == PhotonModelOutputConfig.Source.FILE && !config.file.isBlank()) {
					fingerprintFile(Path.of(config.file), digest, files);
					fingerprintFile(Path.of(PhotonModelSplitUtil.dataPath(config.file)), digest, files);
					// 只读配置副本，FILE依赖发现不能使用旧的内嵌快照。
					var current = new PhotonModelOutputConfig(false);
					current.source = config.source;
					current.file = config.file;
					current.flipObjV = config.flipObjV;
					config = current;
				} else if (config.source == PhotonModelOutputConfig.Source.RESOURCE && config.resource != null) {
					fingerprintResource(config.resource, digest, resources);
					fingerprintResource(Identifier.fromNamespaceAndPath(config.resource.getNamespace(),
							PhotonModelSplitUtil.dataPath(config.resource.getPath())), digest, resources);
				}
				var loaded = PhotonModelSourceUtil.load(config);
				digest.update(loaded.sha256().getBytes(StandardCharsets.UTF_8));
				for (var material : loaded.definition().materials().values()) {
					if (!material.texture().isBlank()) {
						fingerprintResource(Identifier.parse(material.texture()), digest, resources);
					}
				}
			}
			if (object instanceof Emitter emitter) {
				for (var material : emitter.rendererRuntime().getMaterials()) {
					if (material.getMaterial() instanceof TextureMaterial texture) {
						fingerprintResource(texture.getTexture(), digest, resources);
					}
				}
			}
			if (object instanceof PhotonChildSpawner child && child.config.source == PhotonSpawnConfig.Source.FX) {
				var id = child.config.fxLocation;
				if (id == null || !path.add(id)) {
					throw new IOException("作者子资源缺失或循环：" + id);
				}
				try {
					fingerprintResource(Identifier.fromNamespaceAndPath(id.getNamespace(), "fx/" + id.getPath() + ".fx"), digest, resources);
					fingerprintResource(Identifier.fromNamespaceAndPath(id.getNamespace(), "fx/" + id.getPath() + ".mfx"), digest, resources);
					var key = new PhotonOwnedEffectResources.SourceKey(id, PhotonOwnedEffectResources.SourceKind.AUTHORING, 0);
					fingerprint(PhotonOwnedEffectResources.resolveDefinition(id, key), digest, path, depth + 1, files, resources);
				} finally {
					path.remove(id);
				}
			}
		}
	}

	public static void invalidate(PhotonOwnedEffectResources.SourceKey source) {
		PREPARED.remove(source);
		DEVICE_STAMPS.remove(source);
	}

	public static boolean prepared(PhotonOwnedEffectResources.SourceKey source) {
		return PREPARED.containsKey(source) && !PREPARED.get(source).isEmpty() && deviceCurrent(source);
	}
}
