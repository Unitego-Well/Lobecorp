package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.util.Set;
import java.util.zip.ZipFile;

/// 在反序列化修改对象或轨道之前拒绝已移除的扩展数据，原始资源保持不变。
public class PhotonEditorProjectGuardUtil {
	private static final Set<String> REMOVED_TYPES = Set.of("lobecorp_animation_controller", "lobecorp_emitter_spawner",
			"lobecorp_model_emitter", "lobecorp_child_animation", "lobecorp_animation_call", "lobecorp_model_animation");
	private static final Set<String> MODEL_SUFFIXES = Set.of(".mfxproj", ".mfx", ".mfxpack");
	public static final String REMOVED_DATA_KEY = "removed_runtime_data";
	private static final int MAX_DEPTH = 128;

	public static boolean rejectOpen(Editor editor, IProject project, @Nullable File file) {
		if (!(editor instanceof FXEditor) || !containsExtensions(project) && (file == null || !containsFileExtensions(file))) {
			return false;
		}

		notifyUnsupported(editor);
		return true;
	}

	public static boolean rejectFile(Editor editor, File file) {
		if (!(editor instanceof FXEditor) || !containsFileExtensions(file)) {
			return false;
		}

		notifyUnsupported(editor);
		return true;
	}

	public static boolean rejectSave(Editor editor) {
		if (!(editor instanceof FXEditor) || editor.getCurrentProject() == null || !containsExtensions(editor.getCurrentProject())) {
			return false;
		}

		notifyUnsupported(editor);
		return true;
	}

	public static boolean rejectEffect(Editor editor, Identifier id) {
		if (!(editor instanceof FXEditor) || !containsEffectExtensions(id)) {
			return false;
		}

		notifyUnsupported(editor);
		return true;
	}

	/// 直接读取资源容器，不经过会跳过未知类型的原生对象解码。
	public static boolean containsEffectExtensions(Identifier id) {
		var location = Identifier.fromNamespaceAndPath(id.getNamespace(), FXHelper.FX_PATH + id.getPath() + FX.SUFFIX);
		try (var input = Minecraft.getInstance().getResourceManager().open(location)) {
			return containsTag(NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()));
		} catch (IOException _) {
			return false;
		}
	}

	public static boolean containsExtensions(IProject project) {
		return project instanceof FXProject fxProject && containsDataExtensions(fxProject.getFx().getFxData());
	}

	private static boolean containsDataExtensions(FXData data) {
		for (var object : data.objects()) {
			var wrapper = object.serializeWrapper();
			if (wrapper != null && containsTag(wrapper)) {
				return true;
			}
		}

		return containsTag(data.timeline().serializeNBT(Platform.getFrozenRegistry()));
	}

	public static boolean containsFileExtensions(File file) {
		var name = file.getName();
		if (MODEL_SUFFIXES.stream().anyMatch(name::endsWith)) {
			return true;
		}

		if (!file.isFile()) {
			return false;
		}

		try {
			if (name.endsWith(".fxpack")) {
				return containsPackExtensions(file);
			}

			if (name.endsWith(".fx")) {
				try (var input = Files.newInputStream(file.toPath())) {
					return containsTag(NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()));
				}
			}

			return name.endsWith(FXProject.TYPE.getSuffix()) && containsTag(NbtIo.read(file.toPath()));
		} catch (IOException failure) {
			throw new IllegalArgumentException(message(), failure);
		}
	}

	private static boolean containsPackExtensions(File file) throws IOException {
		try (var pack = new ZipFile(file)) {
			var entries = pack.entries();
			while (entries.hasMoreElements()) {
				var entry = entries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}

				if (MODEL_SUFFIXES.stream().anyMatch(entry.getName()::endsWith)) {
					return true;
				}

				if (entry.getName().endsWith(".fx")) {
					try (var input = pack.getInputStream(entry)) {
						if (containsTag(NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()))) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	public static void requireSupportedPack(FileSystem pack) {
		try {
			for (var root : pack.getRootDirectories()) {
				try (var paths = Files.walk(root)) {
					var entries = paths.filter(Files::isRegularFile).iterator();
					while (entries.hasNext()) {
						var entry = entries.next();
						var name = entry.getFileName().toString();
						if (MODEL_SUFFIXES.stream().anyMatch(name::endsWith)) {
							throw new IllegalArgumentException(message());
						}

						if (name.endsWith(FX.SUFFIX)) {
							try (var input = Files.newInputStream(entry)) {
								requireSupported(NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()));
							}
						}
					}
				}
			}
		} catch (IOException failure) {
			throw new IllegalArgumentException(message(), failure);
		}
	}

	public static void requireSupported(Tag tag) {
		if (containsTag(tag)) {
			throw new IllegalArgumentException(message());
		}
	}

	public static void requireSupportedFile(File file) {
		if (containsFileExtensions(file)) {
			throw new IllegalArgumentException(message());
		}
	}

	public static boolean containsTag(@Nullable Tag tag) {
		return containsTag(tag, 0);
	}

	private static boolean containsTag(@Nullable Tag tag, int depth) {
		if (depth > MAX_DEPTH) {
			throw new IllegalArgumentException(message());
		}

		if (tag instanceof CompoundTag compound) {
			var type = compound.getStringOr("type", "");
			var separator = type.indexOf(':');
			if (REMOVED_TYPES.contains(type) || separator >= 0 && REMOVED_TYPES.contains(type.substring(separator + 1))) {
				return true;
			}

			for (var entry : compound.entrySet()) {
				if (entry.getKey().startsWith("lobecorp$") || entry.getKey().equals("lobecorpActivation")
						|| entry.getKey().equals("lobecorp_animation_preview") || containsTag(entry.getValue(), depth + 1)) {
					return true;
				}
			}
		} else if (tag instanceof ListTag list) {
			for (var child : list) {
				if (containsTag(child, depth + 1)) {
					return true;
				}
			}
		}

		return false;
	}

	public static String message() {
		return Component.translatable(PhotonEditorTextUtil.key(REMOVED_DATA_KEY)).getString();
	}

	private static void notifyUnsupported(Editor editor) {
		Dialog.showNotification("Dialog.info", message(), null).show(editor);
	}
}
