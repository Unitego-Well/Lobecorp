package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import org.unitego.lobecorp.registry.photon.client.LcPhotonModelOutputTypes;
import org.unitego.lobecorp.registry.photon.client.LcPhotonSpawnTypes;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.fxpack.FXPackExporter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.mixin.photon.editor.client.FXPackExporterAccessor;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;

import java.nio.file.Path;
import java.util.Arrays;

import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import net.minecraft.nbt.NbtAccounter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 在隔离导出副本中展开模型材质依赖；保留包内其他条目，不对原件执行GC。
public class PhotonModelPackExportUtil {
	private static final ThreadLocal<Boolean> EXPORTING = ThreadLocal.withInitial(() -> false);

	public static boolean preservingPack() {
		return EXPORTING.get();
	}

	public static void prepare(FX copy) throws IOException {
		for (var object : copy.getFxData().objects()) {
			if (!(object instanceof PhotonModelOutput model)) {
				continue;
			}
			prepare(model.config);
		}
	}

	public static void prepare(PhotonModelOutputConfig config) throws IOException {
		if (config.source == PhotonModelOutputConfig.Source.FILE) {
			config.setAssetSnapshot(new ByteArrayTag(new byte[0]));
		}
		var loaded = PhotonModelSourceUtil.load(config);
		var definition = loaded.definition();
		config.setAssetSnapshot(loaded.snapshot());
		config.source = PhotonModelOutputConfig.Source.EMBEDDED;
		config.resource = null;
		config.file = "";
		var dependencies = new CompoundTag();
		var textures = new ArrayList<Identifier>();
		for (var entry : definition.materials().entrySet()) {
			if (!entry.getValue().materialData().isEmpty()) {
				dependencies.put(entry.getKey(), PhotonModelAssetUtil.material(entry.getValue().materialData()));
			}
			if (!entry.getValue().texture().isEmpty()) {
				var id = Identifier.tryParse(entry.getValue().texture());
				if (id == null) {
					throw new IOException("模型贴图ID无效：" + entry.getValue().texture());
				}
				textures.add(id);
			}
		}
		config.assetMaterialDependencies = dependencies;
		config.assetDependencies = textures;
	}

	/// 原生实例源由上游按NBT遍历；只替换模型配置，不重新编码其余未知字段。
	public static void prepareModelObject(CompoundTag wrapper) throws IOException {
		if (!LcPhotonModelOutputTypes.TYPE_NAME.equals(wrapper.getStringOr("type", ""))) {
			return;
		}
		var candidate = wrapper.copy();
		var data = candidate.getCompoundOrEmpty("data");
		var config = new PhotonModelOutputConfig();
		PersistedParser.deserializeNBT(data.getCompoundOrEmpty("config").copy(), config, Platform.getFrozenRegistry());
		prepare(config);
		var encoded = data.getCompoundOrEmpty("config").copy();
		var prepared = PersistedParser.serializeNBT(config, Platform.getFrozenRegistry());
		for (var key : List.of("source", "resource", "file", "snapshotSource", PhotonModelOutputConfig.ASSET_KEY,
				"assetDependencies", "assetMaterialDependencies")) {
			var value = prepared.get(key);
			if (value == null) {
				encoded.remove(key);
			} else {
				encoded.put(key, value.copy());
			}
		}
		data.put("config", encoded);
		PhotonEditorProjectGuardUtil.requireSupported(candidate);
		wrapper.getCompoundOrEmpty("data").put("config", encoded);
	}

	public static FXPackExporter.Result export(File file, String namespace, String name, FX copy, HolderLookup.Provider provider, boolean modelDefinition) throws IOException {
		boolean previous = EXPORTING.get();
		EXPORTING.set(true);
		try {
			var dependencies = new LinkedHashMap<Identifier, FX>();
			collect(copy.serializeNBT(provider), dependencies, new LinkedHashSet<>(), 0);
			var warnings = new ArrayList<String>();
			for (var entry : dependencies.entrySet()) {
				var dependency = new FX();
				dependency.deserializeNBT(provider, entry.getValue().serializeNBT(provider));
				prepare(dependency);
				var exported = FXPackExporter.exportInto(file, entry.getKey().getNamespace(), entry.getKey().getPath(), dependency, provider);
				warnings.addAll(exported.warnings());
			}
			var result = FXPackExporter.exportInto(file, namespace, name, copy, provider);
			warnings.addAll(result.warnings());
			int fileCount;
			try (var zip = FileSystems.newFileSystem(file.toPath())) {
				if (modelDefinition) {
					var prefix = "assets/" + result.fxId().getNamespace() + "/fx/" + result.fxId().getPath();
					var source = zip.getPath(prefix + ".fx");
					var target = zip.getPath(prefix + ".mfx");
					Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
				}
				try (var files = Files.walk(zip.getPath("assets"))) {
					fileCount = Math.toIntExact(files.filter(Files::isRegularFile).count());
				}
			}
			return new FXPackExporter.Result(result.fxId(), fileCount, List.copyOf(warnings));
		} finally {
			if (previous) {
				EXPORTING.set(true);
			} else {
				EXPORTING.remove();
			}
		}
	}

	/// 所有可变内容均为本次请求独占副本；不能把FX、注册表或资源管理器交给写入线程。
	public record Archive(Identifier fxId, Map<String, CompoundTag> definitions, Map<String, byte[]> resources,
	                      String metadata) {
		public void verify(PhotonResourceDocument document, @Nullable PhotonResourceDocument original) throws IOException {
			var actual = entries(document);
			var previous = original == null ? Map.<String, PhotonResourceDocument>of() : entries(original);
			var mcmeta = actual.get("pack.mcmeta");
			var metadataJson = mcmeta == null ? null : mcmeta.json();
			if (metadataJson == null || !metadataJson.isJsonObject() || !metadataJson.getAsJsonObject().has("pack")) {
				throw new IOException("导出包缺少合法的 pack.mcmeta。");
			}
			var packMetadata = PackMetadataSection.CLIENT_TYPE.codec().parse(JsonOps.INSTANCE,
							metadataJson.getAsJsonObject().get("pack")).result()
					.orElseThrow(() -> new IOException("导出包的 pack.mcmeta 不符合资源包协议。"));
			if (packMetadata.supportedFormats().minInclusive().major() < 0) {
				throw new IOException("导出包的资源包格式不能为负数。");
			}

			for (var entry : definitions.entrySet()) {
				var found = actual.get(entry.getKey());
				var definition = found == null ? null : found.nbt();
				if (definition == null || !entry.getValue().equals(definition)) {
					throw new IOException("导出定义完整NBT往返不一致：" + entry.getKey());
				}
			}

			for (var entry : resources.entrySet()) {
				var found = actual.get(entry.getKey());
				if (found == null || !Arrays.equals(entry.getValue(), found.bytes())) {
					throw new IOException("导出资源字节往返不一致：" + entry.getKey());
				}
			}

			var expected = new LinkedHashSet<>(previous.keySet());
			for (var name : definitions.keySet()) {
				if (name.endsWith(".mfx")) {
					expected.remove(name.substring(0, name.length() - 4) + ".fx");
				}
			}
			for (var name : expected) {
				if (!definitions.containsKey(name) && !resources.containsKey(name)
						&& (actual.get(name) == null || !Arrays.equals(previous.get(name).bytes(), actual.get(name).bytes()))) {
					throw new IOException("增量导出改变了未替换的包条目：" + name);
				}
			}

			expected.add("pack.mcmeta");
			expected.addAll(definitions.keySet());
			expected.addAll(resources.keySet());
			if (!expected.equals(actual.keySet())) {
				throw new IOException("导出包条目集合与预期不一致。");
			}
		}

		private static Map<String, PhotonResourceDocument> entries(PhotonResourceDocument document) throws IOException {
			if (document.kind() != PhotonResourceDocument.Kind.FX_PACK && document.kind() != PhotonResourceDocument.Kind.MODEL_PACK) {
				throw new IOException("导出读回不是特效包。");
			}

			var entries = new LinkedHashMap<String, PhotonResourceDocument>();
			var prefix = document.origin() + "!/";
			for (var entry : document.entries()) {
				if (!entry.origin().startsWith(prefix) || entries.put(entry.origin().substring(prefix.length()), entry) != null) {
					throw new IOException("导出包条目来源或身份无效。");
				}
			}

			return entries;
		}

		public void write(Path target) throws IOException {
			try (var zip = FileSystems.newFileSystem(target, Map.of("create", "true"))) {
				var mcmeta = zip.getPath("pack.mcmeta");
				if (!Files.exists(mcmeta)) {
					Files.writeString(mcmeta, metadata);
				}
				for (var entry : resources.entrySet()) {
					var path = zip.getPath(entry.getKey());
					Files.createDirectories(path.getParent());
					Files.write(path, entry.getValue());
				}
				for (var entry : definitions.entrySet()) {
					var path = zip.getPath(entry.getKey());
					Files.createDirectories(path.getParent());
					NbtIo.writeCompressed(entry.getValue(), path);
					if (entry.getKey().endsWith(".mfx")) {
						Files.deleteIfExists(zip.getPath(entry.getKey().substring(0, entry.getKey().length() - 4) + ".fx"));
					}
				}
			}
		}
	}

	public static Archive capture(String namespace, String name, FX copy, HolderLookup.Provider provider, boolean modelDefinition) throws IOException {
		var dependencies = new LinkedHashMap<Identifier, FX>();
		collect(copy.serializeNBT(provider), dependencies, new LinkedHashSet<>(), 0);
		var definitions = new LinkedHashMap<String, CompoundTag>();
		var resources = new LinkedHashMap<String, byte[]>();
		for (var entry : dependencies.entrySet()) {
			var dependency = new FX();
			dependency.deserializeNBT(provider, entry.getValue().serializeNBT(provider));
			prepare(dependency);
			captureDefinition(entry.getKey(), dependency, provider, false, definitions, resources);
		}
		var id = Identifier.fromNamespaceAndPath(FXPackExporterAccessor.lobecorp$sanitize(namespace),
				FXPackExporterAccessor.lobecorp$sanitize(name));
		captureDefinition(id, copy, provider, modelDefinition, definitions, resources);
		return new Archive(id, Map.copyOf(definitions), Map.copyOf(resources), FXPackExporterAccessor.lobecorp$metadata());
	}

	private static void captureDefinition(Identifier id, FX fx, HolderLookup.Provider provider, boolean modelDefinition,
	                                      Map<String, CompoundTag> definitions, Map<String, byte[]> resources) throws IOException {
		var root = fx.serializeNBT(provider);
		PhotonEditorProjectGuardUtil.requireSupported(root);
		var exporter = (FXPackExporterAccessor) (Object) FXPackExporterAccessor.lobecorp$create(provider);
		exporter.lobecorp$walk(root);
		var instanceSources = exporter.lobecorp$packSources(root, id);
		for (var entry : instanceSources.entrySet()) {
			var path = "assets/" + entry.getKey().getNamespace() + "/fx/" + entry.getKey().getPath() + ".fx";
			try (var input = new ByteArrayInputStream(entry.getValue())) {
				var tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
				var previous = definitions.putIfAbsent(path, tag);
				if (previous != null && !previous.equals(tag)) {
					throw new IOException("实例源存在同路径不同内容：" + path);
				}
			}
		}

		if (!exporter.lobecorp$warnings().isEmpty()) {
			throw new IOException("包依赖未闭合：" + exporter.lobecorp$warnings());
		}
		root.putInt("version", FXProject.VERSION);
		definitions.put("assets/" + id.getNamespace() + "/fx/" + id.getPath() + (modelDefinition ? ".mfx" : ".fx"), root.copy());
		for (var entry : exporter.lobecorp$files().entrySet()) {
			var path = "assets/" + entry.getKey().getNamespace() + "/" + entry.getKey().getPath();
			var bytes = entry.getValue().clone();
			var previous = resources.putIfAbsent(path, bytes);
			if (previous != null && !Arrays.equals(previous, bytes)) {
				throw new IOException("包依赖存在同路径不同内容：" + path);
			}
		}
	}

	public static Set<Identifier> instanceSources(CompoundTag data, Set<Identifier> nativeSources) throws IOException {
		var sources = new LinkedHashSet<>(nativeSources);
		collectSourceIds(data, sources);
		return sources;
	}

	private static @Nullable Identifier childSource(CompoundTag wrapper) throws IOException {
		if (!LcPhotonSpawnTypes.TYPE_NAME.equals(wrapper.getStringOr("type", ""))) {
			return null;
		}

		var config = wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config");
		var source = config.getStringOr("source", PhotonSpawnConfig.Source.PROJECT.getSerializedName());
		var text = config.getStringOr("fxLocation", "");
		if (text.isEmpty() || !source.equals("FX") && !source.equals(PhotonSpawnConfig.Source.FX.getSerializedName())) {
			return null;
		}

		var id = Identifier.tryParse(text);
		if (id == null) {
			throw new IOException("子特效资源ID无效：" + text);
		}
		return id;
	}

	private static void collectSourceIds(Tag tag, Set<Identifier> sources) throws IOException {
		if (tag instanceof CompoundTag compound) {
			var id = childSource(compound);
			if (id != null) {
				sources.add(id);
			}
			for (var child : compound.entrySet()) {
				collectSourceIds(child.getValue(), sources);
			}
		} else if (tag instanceof ListTag list) {
			for (var child : list) {
				collectSourceIds(child, sources);
			}
		}
	}

	private static void collect(Tag tag, Map<Identifier, FX> dependencies, Set<Identifier> active, int depth) throws IOException {
		if (depth > 64 || dependencies.size() > 256) {
			throw new IOException("子特效导出依赖超出预算。");
		}
		if (tag instanceof CompoundTag compound) {
			var id = childSource(compound);
			if (id != null) {
				if (active.contains(id)) {
					throw new IOException("子特效导出依赖循环：" + active + " → " + id);
				}
				if (!dependencies.containsKey(id)) {
					var fx = FXHelper.getFX(id);
					if (fx == null) {
						throw new IOException("子特效导出依赖缺失或受保护：" + id);
					}
					dependencies.put(id, fx);
					active.add(id);
					collect(fx.serializeNBT(Platform.getFrozenRegistry()), dependencies, active, depth + 1);
					active.remove(id);
				}
			}
			for (var child : compound.entrySet()) {
				collect(child.getValue(), dependencies, active, depth + 1);
			}
		} else if (tag instanceof ListTag list) {
			for (var child : list) {
				collect(child, dependencies, active, depth + 1);
			}
		}
	}
}
