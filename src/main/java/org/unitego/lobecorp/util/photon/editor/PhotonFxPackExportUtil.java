package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.fxpack.FXPackExporter;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;
import com.lowdragmc.photon.client.fx.instance.InstanceBaker;
import com.lowdragmc.photon.gui.editor.FXProject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.mixin.photon.editor.client.FXPackExporterAccessor;
import org.unitego.lobecorp.registry.client.photon.LcPhotonSpawnTypes;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/// 在隔离导出副本中收集普通FX与子效果依赖；保留包内其他条目，不对原件执行GC。
public class PhotonFxPackExportUtil {
	private static final ThreadLocal<Boolean> EXPORTING = ThreadLocal.withInitial(() -> false);

	public static boolean preservingPack() {
		return EXPORTING.get();
	}



	/// 原生实例源由上游按NBT遍历；只替换模型配置，不重新编码其余未知字段。

	public static FXPackExporter.Result export(File file, String namespace, String name, FX copy, HolderLookup.Provider provider) throws IOException {
		boolean previous = EXPORTING.get();
		EXPORTING.set(true);
		try {
			var dependencies = new LinkedHashMap<Identifier, FX>();
			collect(copy.serializeNBT(provider), dependencies, new LinkedHashSet<>(), 0);
			var rootId = Identifier.fromNamespaceAndPath(FXPackExporterAccessor.lobecorp$sanitize(namespace),
					FXPackExporterAccessor.lobecorp$sanitize(name));
			verifyExportIdentity(copy, rootId, dependencies, provider);
			var warnings = new ArrayList<String>();
			var target = file.toPath();
			var parent = target.getParent();

			// 临时文件不带 .fxpack 后缀，原生 guard 不会识别；先对正式目标保留旧数据保护与锁定语义。
			PhotonEditorProjectGuardUtil.requireSupportedFile(file);
			if (FXPacks.isFileLocked(file)) {
				throw new IOException("fx pack " + file + " is in use");
			}

			if (parent != null) {
				Files.createDirectories(parent);
			}

			var temporary = target.resolveSibling("." + file.getName() + ".lobecorp-" + System.nanoTime() + ".tmp");
			Throwable primaryFailure = null;

			try {
				if (Files.exists(target)) {
					Files.copy(target, temporary, StandardCopyOption.REPLACE_EXISTING);
				}

				for (var entry : dependencies.entrySet()) {
					var dependency = new FX();
					dependency.deserializeNBT(provider, entry.getValue().serializeNBT(provider));
					var exported = FXPackExporter.exportInto(temporary.toFile(), entry.getKey().getNamespace(), entry.getKey().getPath(), dependency, provider);
					warnings.addAll(exported.warnings());
				}

				var result = FXPackExporter.exportInto(temporary.toFile(), namespace, name, copy, provider);
				warnings.addAll(result.warnings());
				int fileCount;

				try (var zip = FileSystems.newFileSystem(temporary)) {

					try (var files = Files.walk(zip.getPath("assets"))) {
						fileCount = Math.toIntExact(files.filter(Files::isRegularFile).count());
					}
				}

				// 原子替换失败时不回退为非原子覆盖，保留原目标。
				if (FXPacks.isFileLocked(file)) {
					throw new IOException("fx pack " + file + " is in use");
				}

				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				return new FXPackExporter.Result(result.fxId(), fileCount, List.copyOf(warnings));
			} catch (IOException | RuntimeException | Error failure) {
				primaryFailure = failure;
				throw failure;
			} finally {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException cleanupFailure) {
					if (primaryFailure != null) {
						primaryFailure.addSuppressed(cleanupFailure);
					} else {
						throw cleanupFailure;
					}
				}
			}
		} finally {
			if (previous) {
				EXPORTING.set(true);
			} else {
				EXPORTING.remove();
			}
		}
	}

	public static Archive capture(String namespace, String name, FX copy, HolderLookup.Provider provider) throws IOException {
		var dependencies = new LinkedHashMap<Identifier, FX>();
		collect(copy.serializeNBT(provider), dependencies, new LinkedHashSet<>(), 0);
		var id = Identifier.fromNamespaceAndPath(FXPackExporterAccessor.lobecorp$sanitize(namespace),
				FXPackExporterAccessor.lobecorp$sanitize(name));
		verifyExportIdentity(copy, id, dependencies, provider);
		var definitions = new LinkedHashMap<String, CompoundTag>();
		var resources = new LinkedHashMap<String, byte[]>();
		for (var entry : dependencies.entrySet()) {
			var dependency = new FX();
			dependency.deserializeNBT(provider, entry.getValue().serializeNBT(provider));
			captureDefinition(entry.getKey(), dependency, provider, definitions, resources);
		}
		captureDefinition(id, copy, provider, definitions, resources);
		return new Archive(id, Map.copyOf(definitions), Map.copyOf(resources), FXPackExporterAccessor.lobecorp$metadata());
	}

	/// 根ID不可重新绑定到其任何层级的来源；原生packSources会跳过self，必须在写包前单独校验。
	private static void verifyExportIdentity(FX root, Identifier rootId, Map<Identifier, FX> dependencies,
	                                         HolderLookup.Provider provider) throws IOException {
		var pending = new ArrayList<FX>();
		var visited = new LinkedHashSet<Identifier>();
		pending.add(root);
		while (!pending.isEmpty()) {
			var data = pending.removeLast().serializeNBT(provider).getCompoundOrEmpty("fxData");
			for (var source : instanceSources(data, InstanceBaker.directSources(data))) {
				if (source.equals(rootId)) {
					throw new IOException("导出根ID与子特效来源冲突，请更换导出名称：" + rootId);
				}
				if (!visited.add(source)) {
					continue;
				}

				var definition = dependencies.get(source);
				if (definition == null) {
					definition = FXHelper.getFX(source);
				}
				if (definition != null) {
					pending.add(definition);
				}
			}
		}
	}

	private static void captureDefinition(Identifier id, FX fx, HolderLookup.Provider provider,
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
		var definitionPath = "assets/" + id.getNamespace() + "/fx/" + id.getPath() + (".fx");
		var existing = definitions.putIfAbsent(definitionPath, root.copy());
		if (existing != null && !existing.equals(root)) {
			throw new IOException("导出定义同路径不同内容：" + definitionPath);
		}
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

	/// 所有可变内容均为本次请求独占副本；不能把FX、注册表或资源管理器交给写入线程。
	public record Archive(Identifier fxId, Map<String, CompoundTag> definitions, Map<String, byte[]> resources,
	                      String metadata) {
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
				if (!entry.getValue().equals(definition)) {
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
				}
			}
		}
	}
}
