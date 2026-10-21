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
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceInspection;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.compat.PhotonInactiveLegacyFieldUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelFileValidationUtil;
import org.unitego.lobecorp.client.photon.playback.PhotonProjectPreviewAccess;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectPreviewUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedCodecUtil;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.IntTag;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.util.Set;

/// 在反序列化修改对象或轨道之前拒绝已移除的扩展数据，原始资源保持不变。
public class PhotonEditorProjectGuardUtil {
	public static final String REMOVED_DATA_KEY = "removed_runtime_data";
	private static final int MAX_DEPTH = 128;

	public static boolean rejectOpen(Editor editor, IProject project, @Nullable File file) {
		if (file == null && project instanceof PhotonModelProject) {
			return false;
		}
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
		if (project instanceof PhotonModelProject model) {
			return requiresModelProjectData(model.document());
		}
		if (!(project instanceof FXProject fxProject)) {
			return false;
		}

		var preview = ((PhotonProjectPreviewAccess) fxProject).lobecorp$projectPreview();
		return containsDataExtensions(fxProject.getFx().getFxData())
				|| preview != null && requiresPreviewAdapter(preview, 0);
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

		if (!file.isFile()) {
			return false;
		}

		var kind = PhotonResourceDocument.Kind.of(name);

		if (kind != PhotonResourceDocument.Kind.FX && kind != PhotonResourceDocument.Kind.FX_PROJECT
				&& kind != PhotonResourceDocument.Kind.FX_PACK && kind != PhotonResourceDocument.Kind.MODEL_PROJECT
				&& kind != PhotonResourceDocument.Kind.MODEL_FX && kind != PhotonResourceDocument.Kind.MODEL_PACK) {
			return false;
		}

		try {
			return requiresAdapter(PhotonResourceReader.read(file.toPath()));
		} catch (IOException failure) {
			throw new IllegalArgumentException(message(), failure);
		}
	}

	private static boolean requiresAdapter(PhotonResourceDocument document) {
		if (document.kind() == PhotonResourceDocument.Kind.MODEL_PROJECT) {
			return requiresModelProject(document.nbt());
		}
		if (document.kind() == PhotonResourceDocument.Kind.MODEL_FX) {
			var tag = document.nbt();
			if (tag == null || !(tag.get("version") instanceof IntTag version) || version.intValue() != PhotonModelFileValidationUtil.DEFINITION_VERSION
					|| !(tag.get("fxData") instanceof CompoundTag data) || !(data.get("objects") instanceof ListTag objects)
					|| objects.stream().noneMatch(value -> value instanceof CompoundTag object && object.getStringOr("type", "").equals("lobecorp_model_emitter"))) {
				return true;
			}
			return containsTag(tag);
		}
		if (document.kind() == PhotonResourceDocument.Kind.MODEL_PACK
				&& document.entries().stream().noneMatch(entry -> entry.kind() == PhotonResourceDocument.Kind.MODEL_FX)) {
			return true;
		}

		if (document.kind() == PhotonResourceDocument.Kind.FX || document.kind() == PhotonResourceDocument.Kind.FX_PROJECT) {
			if (document.kind() == PhotonResourceDocument.Kind.FX_PROJECT
					? containsProjectTag(document.nbt()) : containsTag(document.nbt())) {
				return true;
			}
		}

		for (var entry : document.entries()) {
			if (requiresAdapter(entry)) {
				return true;
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
						if (name.endsWith(FX.SUFFIX) || name.endsWith(".mfx") || name.endsWith(".mfxproj")) {
							if (requiresAdapter(PhotonResourceReader.read(entry))) {
								throw new IllegalArgumentException(message());
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

	/// 工程data快照保留项目测试字段的位置语义，不能按独立FX数据检查。
	public static void requireSupportedProjectData(CompoundTag data) {
		var wrapper = new CompoundTag();
		wrapper.put("data", data);
		if (containsProjectTag(wrapper)) {
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

	/// 工程测试字段只允许出现在工程data层，FX发布数据及其他位置仍按旧字段阻断。
	public static boolean containsProjectTag(@Nullable CompoundTag project) {
		if (project != null && project.getCompoundOrEmpty("meta").getStringOr("suffix", "").equals(".mfxproj")) {
			return requiresModelProject(project);
		}
		if (project == null || !(project.get("data") instanceof CompoundTag data)
				|| !data.contains(PhotonProjectPreviewUtil.PROJECT_FIELD)) {
			return containsTag(project);
		}

		if (requiresPreviewAdapter(data.get(PhotonProjectPreviewUtil.PROJECT_FIELD), 2)) {
			return true;
		}

		var checked = project.copy();
		checked.getCompoundOrEmpty("data").remove(PhotonProjectPreviewUtil.PROJECT_FIELD);
		return containsTag(checked);
	}

	private static boolean containsTag(@Nullable Tag tag, int depth) {
		if (depth > MAX_DEPTH) {
			throw new IllegalArgumentException(message());
		}

		if (tag instanceof CompoundTag compound) {
			var type = compound.getStringOr("type", "");
			if (type.equals("lobecorp_animation_controller")) {
				return requiresNamedAdapter(compound, depth);
			}
			if (type.equals("lobecorp_model_emitter")) {
				if (!(compound.get("data") instanceof CompoundTag data)) {
					return true;
				}
				try {
					PhotonModelFileValidationUtil.emitterData(data);
					return containsTag(data, depth + 1);
				} catch (IOException | RuntimeException invalid) {
					return true;
				}
			}

			if (type.equals("activator") && compound.get("data") instanceof CompoundTag data
					&& data.contains(PhotonActivationOptions.TAG)) {
				return requiresActivationAdapter(compound, data, depth);
			}
			if (PhotonResourceInspection.isLegacyType(type)) {
				return true;
			}

			for (var entry : compound.entrySet()) {
				if (PhotonResourceInspection.isLegacyField(entry.getKey()) && PhotonInactiveLegacyFieldUtil.allowed(compound, entry.getKey(), entry.getValue())) {
					continue;
				}
				if (PhotonResourceInspection.isLegacyField(entry.getKey()) || containsTag(entry.getValue(), depth + 1)) {
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

	private static boolean requiresModelProject(@Nullable CompoundTag project) {
		if (project == null || !(project.get("data") instanceof CompoundTag data)
				|| !project.getCompoundOrEmpty("meta").getStringOr("version", "").equals("1.0")
				|| !project.getCompoundOrEmpty("meta").getStringOr("suffix", "").equals(".mfxproj")) {
			return true;
		}
		return requiresModelProjectData(data) || containsTag(project);
	}

	private static boolean requiresModelProjectData(CompoundTag data) {
		try {
			PhotonModelFileValidationUtil.projectData(data);
			return containsTag(data, 1);
		} catch (IOException | RuntimeException invalid) {
			return true;
		}
	}

	/// 仅接受当前控制器包装和可保留的原生轨道库，未恢复的内部扩展继续阻断。
	private static boolean requiresNamedAdapter(CompoundTag wrapper, int depth) {
		if (!Set.of("type", "data").containsAll(wrapper.keySet())
				|| !(wrapper.get("data") instanceof CompoundTag data)
				|| data.contains("version") && (!(data.get("version") instanceof IntTag version) || version.intValue() != 0)
				|| !(data.get("definition") instanceof CompoundTag definition)) {
			return true;
		}

		try {
			var library = PhotonNamedCodecUtil.read(definition);
			for (var animation : library.animations()) {
				for (var track : animation.tracks()) {
					if (requiresNamedTrackAdapter(track, depth + 3)) {
						return true;
					}
				}
			}

			return containsTag(data, depth + 1);
		} catch (IOException | IllegalArgumentException invalid) {
			return true;
		}
	}

	private static boolean requiresNamedTrackAdapter(CompoundTag track, int depth) {
		if (depth > MAX_DEPTH || !(track.get("data") instanceof CompoundTag data)) {
			return true;
		}

		var type = track.getStringOr("type", "");
		if (type.equals("group")) {
			if (data.contains("children") && !(data.get("children") instanceof ListTag)) {
				return true;
			}

			for (var child : data.getListOrEmpty("children")) {
				if (!(child instanceof CompoundTag nested) || requiresNamedTrackAdapter(nested, depth + 2)) {
					return true;
				}
			}

			return false;
		}

		return !Set.of("activator", "control", "animation").contains(type);
	}

	/// 激活扩展仅在原生激活轨道包装中放行，内部未知扩展仍保留保护。
	private static boolean requiresActivationAdapter(CompoundTag wrapper, CompoundTag data, int depth) {
		if (!Set.of("type", "data").containsAll(wrapper.keySet()) || !validActivationData(data)) {
			return true;
		}
		if (!(data.get(PhotonActivationOptions.TAG) instanceof CompoundTag source)) {
			return true;
		}

		try {
			PhotonActivationOptions.read(source);
			if (containsTag(source, depth + 2)) {
				return true;
			}

			var checked = wrapper.copy();
			checked.getCompoundOrEmpty("data").remove(PhotonActivationOptions.TAG);
			return containsTag(checked, depth);
		} catch (IllegalArgumentException invalid) {
			return true;
		}
	}

	private static boolean validActivationData(CompoundTag data) {
		if (data.contains("target") && data.read("target", UUIDUtil.CODEC).isEmpty()
				|| data.contains("name") && !(data.get("name") instanceof StringTag)
				|| data.contains("mute") && !(data.get("mute") instanceof ByteTag)
				|| data.contains("lock") && !(data.get("lock") instanceof ByteTag)
				|| data.contains("clips") && !(data.get("clips") instanceof ListTag)) {
			return false;
		}

		for (var value : data.getListOrEmpty("clips")) {
			if (!(value instanceof CompoundTag clip)
					|| clip.contains("clipTarget") && clip.read("clipTarget", UUIDUtil.CODEC).isEmpty()
					|| clip.contains("randomSeed") && !(clip.get("randomSeed") instanceof ByteTag)
					|| clip.contains("seed") && !(clip.get("seed") instanceof NumericTag)) {
				return false;
			}

			for (var key : new String[]{"start", "duration", "speed"}) {
				if (clip.contains(key) && (!(clip.get(key) instanceof NumericTag number) || !Double.isFinite(number.doubleValue()))) {
					return false;
				}
			}

			if (clip.getDoubleOr("start", 0) < 0 || clip.getDoubleOr("duration", 0) < 0) {
				return false;
			}
		}

		return true;
	}

	/// 只放行已保留的工程测试容器，内部未恢复的类型和旧字段仍逐项阻断。
	private static boolean requiresPreviewAdapter(Tag tag, int depth) {
		if (!(tag instanceof CompoundTag source)) {
			return true;
		}

		try {
			PhotonProjectPreviewUtil.read(source);
			return containsTag(source, depth);
		} catch (IOException | IllegalArgumentException invalid) {
			return true;
		}
	}

	public static String message() {
		return Component.translatable(PhotonEditorTextUtil.key(REMOVED_DATA_KEY)).getString();
	}

	private static void notifyUnsupported(Editor editor) {
		Dialog.showNotification("Dialog.info", message(), null).show(editor);
	}
}
