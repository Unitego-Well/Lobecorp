package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaser;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaserComposition;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaserComposition.Definition;
import org.unitego.lobecorp.util.photon.model.PhotonModelPackExportUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectFileUtil;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/// 组合包保存实际发布ID与协议；预览环境和运行租约不进入发布清单。
public class PhotonCompositionExportUtil {
	private static final String MANIFEST = "assets/lobecorp/compositions/owned_laser.nbt";
	// 编辑器回读单份压缩定义的解压内存上限，与有限组成件集合共同限定输入规模。
	private static final long MAX_DEFINITION_BYTES = 64L * 1024 * 1024;

	public record Loaded(PhotonOwnedLaser runtime, Definition definition) {
	}

	public static void export(FX authoring, Definition composition, String destination) throws IOException {
		export(PhotonOwnedLaser.exportComponents(authoring), composition, destination);
	}

	public static void export(Map<String, CompoundTag> components, Definition composition, String destination) throws IOException {
		var target = PhotonEffectFileUtil.workspacePath(destination);
		if (!target.getFileName().toString().endsWith(".fxpack") || Files.exists(target)) {
			throw new IOException("请选择尚不存在的.fxpack文件");
		}
		var name = target.getFileName().toString().replaceFirst("\\.fxpack$", "");
		if (!name.matches("[a-z0-9_]+")) {
			throw new IOException("组合包名称仅允许小写字母、数字及下划线");
		}
		var definitions = new LinkedHashMap<String, CompoundTag>();
		var resources = new LinkedHashMap<String, byte[]>();
		var ids = new CompoundTag();
		String metadata = null;
		var provider = Platform.getFrozenRegistry();
		for (var entry : components.entrySet()) {
			var copy = new FX();
			copy.deserializeNBT(provider, entry.getValue());
			PhotonModelPackExportUtil.prepare(copy);
			var archive = PhotonModelPackExportUtil.capture("lobecorp",
					"owned_compositions/" + name + "/" + entry.getKey(), copy, provider, false);
			for (var value : archive.definitions().entrySet()) {
				var previous = definitions.putIfAbsent(value.getKey(), value.getValue());
				if (previous != null && !previous.equals(value.getValue())) {
					throw new IOException("组成件依赖同路径不同内容：" + value.getKey());
				}
			}
			for (var value : archive.resources().entrySet()) {
				var previous = resources.putIfAbsent(value.getKey(), value.getValue());
				if (previous != null && !Arrays.equals(previous, value.getValue())) {
					throw new IOException("组成件资源同路径不同内容：" + value.getKey());
				}
			}
			metadata = archive.metadata();
			var root = definitions.get("assets/" + archive.fxId().getNamespace() + "/fx/" + archive.fxId().getPath() + ".fx");
			var space = entry.getValue().get("owned_beam_space");
			if (space != null) {
				root.put("owned_beam_space", space.copy());
			}
			root.put("owned_composition", PhotonOwnedLaserComposition.serialize(composition));
			ids.putString(entry.getKey(), archive.fxId().toString());
		}
		validateComponents(composition, components);
		var manifest = new CompoundTag();
		manifest.putInt("version", 1);
		manifest.put("definition", PhotonOwnedLaserComposition.serialize(composition));
		manifest.put("components", ids);
		definitions.put(MANIFEST, manifest);
		var archive = new PhotonModelPackExportUtil.Archive(PhotonOwnedLaser.ID, definitions, resources, metadata);
		Files.createDirectories(target.getParent());
		var temporary = target.resolveSibling(".composition-" + UUID.randomUUID() + ".fxpack");
		try {
			archive.write(temporary);
			// 回读发布协议及实际组件，避免写出没有消费者或ID不匹配的清单。
			readData(temporary);
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static void validateComponents(Definition definition, Map<String, ?> components) throws IOException {
		for (var cue : definition.cues()) {
			if (!components.containsKey(cue.component())) {
				throw new IOException("组合引用缺失组件：" + cue.component());
			}
		}
	}

	private record Data(Definition definition, Map<String, CompoundTag> components) {
	}

	private static Data readData(Path path) throws IOException {
		return readData(path, false);
	}

	private static Data readData(Path path, boolean effective) throws IOException {
		try (var zip = FileSystems.newFileSystem(path)) {
			var manifestPath = zip.getPath(MANIFEST);
			var manifest = NbtIo.readCompressed(manifestPath, NbtAccounter.create(MAX_DEFINITION_BYTES));
			if (manifest.getIntOr("version", 0) != 1) {
				throw new IOException("不支持的组合清单版本");
			}
			var definition = PhotonOwnedLaserComposition.deserialize(manifest.getCompoundOrEmpty("definition"));
			var ids = manifest.getCompoundOrEmpty("components");
			var components = new LinkedHashMap<String, CompoundTag>();
			var expected = PhotonOwnedLaser.COMPONENTS;
			if (ids.size() != expected.size()) {
				throw new IOException("组合组件集合不完整");
			}
			for (var name : expected) {
				var id = Identifier.tryParse(ids.getStringOr(name, ""));
				if (id == null) {
					throw new IOException("无效的发布组件ID：" + name);
				}
				var file = zip.getPath("assets/" + id.getNamespace() + "/fx/" + id.getPath() + ".fx");
				if (effective) {
					var resource = Minecraft.getInstance().getResourceManager()
							.getResource(Identifier.fromNamespaceAndPath(id.getNamespace(), "fx/" + id.getPath() + ".fx"))
							.orElseThrow(() -> new IOException("发布组件尚未挂载：" + id));
					try (var input = new BufferedInputStream(resource.open())) {
						components.put(name, NbtIo.readCompressed(input, NbtAccounter.create(MAX_DEFINITION_BYTES)));
					}
				} else {
					components.put(name, NbtIo.readCompressed(file, NbtAccounter.create(MAX_DEFINITION_BYTES)));
				}
			}
			validateComponents(definition, components);
			for (var component : components.values()) {
				var published = component.getCompoundOrEmpty("owned_composition");
				if (!published.equals(PhotonOwnedLaserComposition.serialize(definition))) {
					throw new IOException("有效组件的组合协议与发布清单不同，请核对资源包覆盖");
				}
			}
			return new Data(definition, components);
		} catch (IllegalArgumentException invalid) {
			throw new IOException("无效组合协议", invalid);
		}
	}

	/// 仅由明确的编辑器动作调用；原生最低优先级和资源包覆盖规则保持不变。
	public static CompletableFuture<Void> mountAndReload(String source) throws IOException {
		var path = PhotonEffectFileUtil.workspacePath(source);
		readData(path);
		var directory = FXPacks.getFxPacksDir().toPath().toAbsolutePath().normalize();
		Files.createDirectories(directory);
		var installed = directory.resolve(path.getFileName());
		if (!installed.equals(path.toAbsolutePath().normalize())) {
			if (Files.exists(installed)) {
				if (!Arrays.equals(Files.readAllBytes(installed), Files.readAllBytes(path))) {
					throw new IOException("已有同名不同内容的发布包，请使用新名称");
				}
			} else {
				Files.copy(path, installed);
			}
		}
		return Minecraft.getInstance().reloadResourcePacks();
	}

	public static Loaded read(String source, long revision, Level level) throws IOException {
		var path = PhotonEffectFileUtil.workspacePath(source);
		var data = readData(path, true);
		var runtime = PhotonOwnedLaser.previewComponents(data.components(), revision, level);
		runtime.composition(data.definition());
		return new Loaded(runtime, data.definition());
	}
}
