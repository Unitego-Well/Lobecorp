package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.editor.ui.EditorOsWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.window.ModularUIWindow;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.client.photon.editor.PhotonProjectRawAccess;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceJsonUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.LongArrayTag;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;
import org.unitego.lobecorp.util.photon.model.PhotonModelFileValidationUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class PhotonEffectProjectUtil {
	private static final Map<IProject, String> IDS = new IdentityHashMap<>();
	private static final Map<IProject, String> NAMES = new IdentityHashMap<>();

	public record Handle(String id, FXEditor editor, IProject project) {
	}

	public record PreparedOpen(IProject project, File file, JsonObject sourceReceipt) {
		public JsonObject commit() throws Exception {
			if (Minecraft.getInstance().level == null) {
				throw new PhotonEffectCommandException(Code.WORLD_REQUIRED);
			}

			var request = new JsonObject();
			request.addProperty("path", file.getAbsolutePath());
			PhotonEffectFileUtil.source(request, project.getProjectType().getSuffix());
			Supplier<Editor> factory = PhotonEffectEditor::new;
			var existing = windows();
			boolean newWindow = existing.isEmpty();
			EditorWindow window = newWindow ? null : existing.getFirst();
			var previous = window == null ? null : window.getCurrentEditor();
			FXEditor editor = null;

			try {
				var candidate = (FXEditor) factory.get();
				editor = candidate;

				if (newWindow) {
					window = EditorWindow.open(PhotonEffectEditor.WINDOW_ID, () -> candidate);
					if (window.getCurrentEditor() != candidate) {
						previous = window.getCurrentEditor();
						window.createNewEditor(() -> candidate);
					}
				} else {
					window.createNewEditor(() -> candidate);
				}

				editor.loadProject(project, file);

				if (editor.getCurrentProject() != project) {
					throw new PhotonEffectCommandException(Code.INTERNAL_ERROR);
				}

				var result = describe(new Handle(IDS.computeIfAbsent(project, _ -> UUID.randomUUID().toString()), editor, project));
				result.add("sourceReceipt", sourceReceipt.deepCopy());

				if (newWindow) {
					window.hostAsScreen();
				} else if (window.isInOsWindow() && !window.dockIntoGameWindow()) {
					throw new PhotonEffectCommandException(Code.INTERNAL_ERROR);
				}

				return result;
			} catch (RuntimeException | Error failure) {
				var failedWindow = window == null && editor != null ? editor.getWindow() : window;

				try {
					if (editor != null) {
						editor.closeCurrentProject(false, null);
					}
				} catch (RuntimeException | Error cleanupFailure) {
					failure.addSuppressed(cleanupFailure);
				} finally {
					IDS.remove(project);
					NAMES.remove(project);

					if (editor != null) {
						editor.removeSelf();

						if (failedWindow != null) {
							failedWindow.removeEditor(editor);
						}
					}

					if (previous != null && failedWindow != null && failedWindow.getEditors().containsKey(previous)) {
						failedWindow.showEditor(previous);
						if (newWindow) {
							failedWindow.hostAsScreen();
						}
					}
				}

				throw failure;
			}
		}
	}

	// 工程打开流程参考 photon2mcp（Apache-2.0），已改为当前版本的独立标签与受限文件事务。

	public record PreparedClose(Handle handle, String expectedRevision, boolean discardUnsaved) {
		public JsonObject commit() {
			var current = require(handle.id());
			if (current.project() != handle.project() || current.editor() != handle.editor()) {
				throw new PhotonEffectCommandException(Code.PROJECT_CLOSED);
			}
			if (!expectedRevision.equals(revision(current.project()))) {
				throw new PhotonEffectCommandException(Code.REVISION_CONFLICT);
			}
			var editor = current.editor();
			if (editor.isCurrentProjectDirty() && !discardUnsaved) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "discardUnsaved");
			}

			var window = editor.getWindow();
			var selected = window == null ? null : window.getCurrentEditor();
			if (editor.runtime != null) {
				editor.runtime.destroy(true);
			}
			editor.closeCurrentProject(false, () -> {
				IDS.remove(current.project());
				NAMES.remove(current.project());
				editor.removeSelf();
				if (window != null && window.getEditors().containsKey(editor)) {
					window.removeEditor(editor);
					if (selected != null && selected != editor && window.getEditors().containsKey(selected)) {
						window.showEditor(selected);
					}
				}
			});
			if (editor.getCurrentProject() != null) {
				throw new PhotonEffectCommandException(Code.INTERNAL_ERROR);
			}

			var result = new JsonObject();
			result.addProperty("closed", true);
			result.addProperty("projectId", handle.id());
			return result;
		}
	}

	public static PreparedClose prepareClose(JsonObject request) {
		return new PreparedClose(require(PhotonEffectJsonUtil.string(request, "projectId")),
				PhotonEffectJsonUtil.string(request, "expectedRevision"), PhotonEffectJsonUtil.bool(request, "discardUnsaved", false));
	}

	/// 文件读取与完整内容校验先于标签创建，失败不影响用户现有工程。
	public static PreparedOpen prepareOpen(JsonObject request) throws Exception {
		if (Minecraft.getInstance().level == null) {
			throw new PhotonEffectCommandException(Code.WORLD_REQUIRED);
		}

		var path = PhotonEffectJsonUtil.string(request, "path");
		if (!path.endsWith(FXProject.TYPE.getSuffix()) && !path.endsWith(PhotonModelProject.TYPE.getSuffix())) {
			throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION, path);
		}

		var type = path.endsWith(PhotonModelProject.TYPE.getSuffix()) ? PhotonModelProject.TYPE : FXProject.TYPE;
		var file = PhotonEffectFileUtil.source(request, type.getSuffix()).toFile();
		PhotonEditorProjectGuardUtil.requireSupportedFile(file);
		var source = PhotonResourceReader.read(file.toPath());
		var data = source.nbt();

		if (data == null || !(data.get("data") instanceof CompoundTag contents)) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA);
		}

		var project = type.loadProjectFromFile(file);
		PhotonEffectEditUtil.validate(project);

		var actual = snapshot(project);
		var receipt = new JsonObject();
		receipt.addProperty("sourceSha256", source.sha256());
		receipt.addProperty("sourcePath", source.origin());
		if (!contents.equals(actual)) {
			if (!(project instanceof PhotonModelProject model)) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA);
		}
			var wrapper = model.model().serializeWrapper();
			if (wrapper == null) {
				throw new PhotonEffectCommandException(Code.INVALID_DATA);
			}
			var generated = wrapper.getCompoundOrEmpty("data");
			PhotonModelFileValidationUtil.requireGeneratedEmitterData(contents, actual, generated, model.model().id());
			receipt.addProperty("normalization", "generated_model_emitter_identity");
			receipt.addProperty("generatedObjectId", model.model().id().toString());
		}

		return new PreparedOpen(project, file, receipt);
	}

	public static List<Handle> projects() {
		var result = new ArrayList<Handle>();

		for (var window : windows()) {
			for (var editor : window.getEditors().keySet()) {
				var project = editor.getCurrentProject();

				if (editor instanceof PhotonEffectEditor fxEditor && project instanceof FXProject) {
					result.add(new Handle(IDS.computeIfAbsent(project, _ -> UUID.randomUUID().toString()), fxEditor, project));
				}
			}
		}

		IDS.keySet().retainAll(result.stream().map(Handle::project).toList());
		NAMES.keySet().retainAll(IDS.keySet());
		return result;
	}

	private static List<EditorWindow> windows() {
		var windows = new LinkedHashSet<EditorWindow>();

		if (Minecraft.getInstance().screen instanceof ModularUIScreen screen) {
			addWindows(screen.modularUI, windows);
		}

		for (var window : ModularUIWindow.openWindows()) {
			if (window instanceof EditorOsWindow editorWindow && window.isOpen()) {
				windows.add(editorWindow.getEditorWindow());
			}
		}

		return windows.stream().filter(window -> Objects.equals(window.windowID, PhotonEffectEditor.WINDOW_ID)).toList();
	}

	private static void addWindows(ModularUI ui, LinkedHashSet<EditorWindow> windows) {
		if (ui.ui.rootElement instanceof EditorWindow window) {
			windows.add(window);
		}

		windows.addAll(ui.getElementsByType(EditorWindow.class));

		for (var embedded : ui.getEmbeddedUIs()) {
			addWindows(embedded, windows);
		}
	}

	public static Handle require(String id) {
		return projects().stream().filter(handle -> handle.id().equals(id)).findFirst()
				.orElseThrow(() -> new PhotonEffectCommandException(Code.PROJECT_CLOSED));
	}

	public static JsonObject state(JsonObject request) {
		var projects = projects();

		if (request.has("projectId")) {
			return describe(require(PhotonEffectJsonUtil.string(request, "projectId")));
		}

		var result = new JsonObject();
		var list = new JsonArray();
		projects.forEach(handle -> list.add(describe(handle)));
		result.add("projects", list);
		return result;
	}

	public static Handle create(String kind, String name) {
		if (!kind.equals("fx") && !kind.equals("model")) {
			throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION, kind);
		}

		if (Minecraft.getInstance().level == null) {
			throw new PhotonEffectCommandException(Code.WORLD_REQUIRED);
		}

		var existing = windows();
		var window = existing.isEmpty() ? EditorWindow.open(PhotonEffectEditor.WINDOW_ID, PhotonEffectEditor::new) : existing.getFirst();

		if (existing.isEmpty()) {
			window.hostAsScreen();
		} else if (window.isInOsWindow()) {
			window.dockIntoGameWindow();
		}

		FXEditor editor;
		IProject project;

		switch (kind) {
			case "fx" -> {
				project = new FXProject();
				var current = window.getCurrentEditor();
				editor = current instanceof PhotonEffectEditor fx
						&& current.getCurrentProject() == null ? fx : (FXEditor) window.createNewEditor(PhotonEffectEditor::new);
			}
			case "model" -> {
				project = new PhotonModelProject();
				var current = window.getCurrentEditor();
				editor = current instanceof PhotonEffectEditor fx && current.getCurrentProject() == null
						? fx : (FXEditor) window.createNewEditor(PhotonEffectEditor::new);
			}
			default -> throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, kind);
		}

		if (!name.isBlank()) {
			NAMES.put(project, name);
		}

		editor.loadProject(project, null);

		if (editor.getCurrentProject() != project) {
			throw new PhotonEffectCommandException(Code.INTERNAL_ERROR);
		}

		return projects().stream().filter(handle -> handle.project() == project).findFirst()
				.orElseThrow(() -> new PhotonEffectCommandException(Code.NO_PROJECT));
	}

	/// 工程内容而非调用计数决定版本，用户手工编辑同样使旧版本失效。
	public static String revision(IProject project) {
		return fingerprint(snapshot(project));
	}

	public static String fingerprint(CompoundTag data) {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			updateFingerprint(digest, data);
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static void updateFingerprint(MessageDigest digest, Tag value) {
		digest.update(value.getId());
		switch (value) {
			case CompoundTag compound -> {
				numberFingerprint(digest, compound.size());
				compound.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
					textFingerprint(digest, entry.getKey());
					updateFingerprint(digest, entry.getValue());
				});
			}
			case ListTag list -> {
				numberFingerprint(digest, list.size());
				list.forEach(child -> updateFingerprint(digest, child));
			}
			case ByteArrayTag bytes -> {
				var array = bytes.getAsByteArray();
				numberFingerprint(digest, array.length);
				digest.update(array);
			}
			case IntArrayTag integers -> {
				var array = integers.getAsIntArray();
				numberFingerprint(digest, array.length);
				for (int number : array) {
					numberFingerprint(digest, number);
				}
			}
			case LongArrayTag longs -> {
				var array = longs.getAsLongArray();
				numberFingerprint(digest, array.length);
				for (long number : array) {
					numberFingerprint(digest, number);
				}
			}
			default -> textFingerprint(digest, value.toString());
		}
		}

	private static void textFingerprint(MessageDigest digest, String value) {
		var bytes = value.getBytes(StandardCharsets.UTF_8);
		numberFingerprint(digest, bytes.length);
		digest.update(bytes);
			}

	private static void numberFingerprint(MessageDigest digest, long value) {
		for (int shift = 56; shift >= 0; shift -= 8) {
			digest.update((byte) (value >>> shift));
		}
	}

	public static CompoundTag snapshot(IProject project) {
		var reporter = new ProblemReporter.Collector();
		var output = TagValueOutput.createWithContext(reporter, Platform.getFrozenRegistry());
		project.serializeProject(output);

		if (!reporter.isEmpty()) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA, reporter.getReport());
		}

		return output.buildResult();
	}

	public static CompoundTag container(IProject project) {
		var reporter = new ProblemReporter.Collector();
		var output = TagValueOutput.createWithContext(reporter, Platform.getFrozenRegistry());
		project.serialize(output);
		if (!reporter.isEmpty()) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA, reporter.getReport());
		}
		return output.buildResult();
	}

	public static void verifyContainer(IProject project, CompoundTag container) {
		var restored = copy(project);
		var reporter = new ProblemReporter.Collector();
		restored.deserialize(TagValueInput.create(reporter, Platform.getFrozenRegistry(), container.copy()));
		if (!reporter.isEmpty() || !container.equals(container(restored))) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA, "完整工程容器经原生读取后不一致。");
		}
	}

	public static void decode(IProject project, CompoundTag data) {
		if (project instanceof PhotonModelProject model) {
			model.restore(data);
			return;
		}
		PhotonEditorProjectGuardUtil.requireSupportedProjectData(data);
		var reporter = new ProblemReporter.Collector();
		project.deserializeProject(TagValueInput.create(reporter, Platform.getFrozenRegistry(), data.copy()));

		if (!reporter.isEmpty()) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA, reporter.getReport());
		}
	}

	public static IProject copy(IProject original) {
		if (original instanceof PhotonModelProject model) {
			return model.copy();
		}
		IProject copy = new FXProject();
		decode(copy, snapshot(original));
		if (original instanceof PhotonProjectRawAccess source) {
			// 原生FXProject在客户端经Mixin获得桥接，静态声明不携带该接口。
			((PhotonProjectRawAccess) (Object) copy).lobecorp$rawContainer(source.lobecorp$rawContainer());
		}
		return copy;
	}

	public static JsonObject describe(Handle handle) {
		var project = handle.project();
		var editor = handle.editor();
		var data = snapshot(project);
		var result = new JsonObject();
		result.addProperty("projectId", handle.id());
		result.addProperty("kind", project instanceof PhotonModelProject ? "model" : "fx");
		result.addProperty("revision", fingerprint(data));
		result.addProperty("editorPath", editor.getCurrentProjectFile() == null ? "" : editor.getCurrentProjectFile().getAbsolutePath());
		result.addProperty("dirty", editor.isCurrentProjectDirty());
		result.addProperty("playing", editor.sceneView.particleManager.isPlaying());
		result.addProperty("time", editor.sceneView.particleManager.getTime(0));
		result.addProperty("undoCount", editor.historyView.getUndoStack().size());
		result.addProperty("redoCount", editor.historyView.getRedoStack().size());
		boolean containsModel = project instanceof PhotonModelProject || project instanceof FXProject fxProject
				&& fxProject.getFx().getFxData().objects().stream().anyMatch(object -> object instanceof PhotonModelOutput);
		if (project instanceof PhotonModelProject model) {
			var configuration = PersistedParser.serializeNBT(model.model().config, Platform.getFrozenRegistry());
			var playback = new CompoundTag();
			for (var key : List.of("enabled", "duration", "startDelay", "looping", "paused", "clip", "playbackSpeed", "animationOffset")) {
				var value = configuration.get(key);
				if (value != null) {
					playback.put(key, value.copy());
				}
			}
			result.add("playback", PhotonEffectJsonUtil.json(playback));
			var asset = model.document().get("asset");
			try {
				result.add("modelAsset", switch (asset) {
					case ByteArrayTag bytes ->
							PhotonResourceJsonUtil.read(bytes.getAsByteArray(), PhotonResourceLimits.DEFAULT);
					case StringTag(var text) -> PhotonResourceJsonUtil.read(text, PhotonResourceLimits.DEFAULT);
					case null, default -> throw new IllegalArgumentException("模型工程资产缺失。");
				});
			} catch (IOException invalid) {
				throw new PhotonEffectCommandException(Code.INVALID_DATA, invalid.getMessage());
			}
		}


		// 模型 JSON 已单独返回；重复展开其 NBT 字节数组会使状态回复超过 MCP 通道上限。
		result.addProperty("snapshotIncluded", !containsModel);

		if (!containsModel) {
			result.addProperty("snapshotSnbt", data.toString());
		}

		if (project instanceof FXProject fx) {
			result.addProperty("name", editor.getCurrentProjectFile() == null ? NAMES.getOrDefault(project, editor.getTitle().getString())
					: editor.getCurrentProjectFile().getName());
			var objects = new JsonArray();

			for (var object : fx.getFx().getFxData().objects()) {
				var entry = new JsonObject();
				entry.addProperty("objectId", object.id().toString());
				entry.addProperty("type", object.name());
				entry.addProperty("name", object.getName());
				var wrapper = object.serializeWrapper();

				if (wrapper == null) {
					throw new PhotonEffectCommandException(Code.INVALID_DATA);
				}

				var parameters = wrapper.getCompoundOrEmpty("data");
				if (object instanceof PhotonModelOutput modelOutput) {
					if (!(project instanceof PhotonModelProject)) {
						try {
							entry.add("modelAsset", switch (modelOutput.config.assetSnapshot()) {
								case ByteArrayTag bytes ->
										PhotonResourceJsonUtil.read(bytes.getAsByteArray(), PhotonResourceLimits.DEFAULT);
								case StringTag(var text) ->
										PhotonResourceJsonUtil.read(text, PhotonResourceLimits.DEFAULT);
								case null, default -> throw new IOException("模型快照缺失。");
							});
						} catch (IOException invalid) {
							throw new PhotonEffectCommandException(Code.INVALID_DATA, invalid.getMessage());
						}
					}
					parameters.remove(PhotonModelOutputConfig.ASSET_KEY);
					parameters.getCompoundOrEmpty("config").remove(PhotonModelOutputConfig.ASSET_KEY);
				}

				entry.add("parameters", PhotonEffectJsonUtil.json(parameters));
				entry.add("parameterSchema", PhotonEffectJsonUtil.schema(parameters));
				objects.add(entry);
			}

			result.add("objects", objects);
			result.add("timeline", PhotonEffectJsonUtil.json(fx.getFx().getFxData().timeline().serializeNBT(Platform.getFrozenRegistry())));
		}

		return result;
	}

	/// 解码失败或场景重建失败时恢复原快照，不将失败操作写入历史。
	public static void apply(Handle handle, CompoundTag data) {
		if (handle.editor().getCurrentProject() != handle.project()) {
			throw new PhotonEffectCommandException(Code.PROJECT_CLOSED);
		}

		var before = snapshot(handle.project());

		try {
			decode(handle.project(), data);
			refresh(handle);
		} catch (RuntimeException failure) {
			try {
				decode(handle.project(), before);
				refresh(handle);
			} catch (RuntimeException rollbackFailure) {
				failure.addSuppressed(rollbackFailure);
			}

			throw failure;
		}
	}

	private static void refresh(Handle handle) {
		var editor = handle.editor();

		if (!(handle.project() instanceof FXProject project)) {
			throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION);
		}

		boolean playing = editor.sceneView.particleManager.isPlaying();
		var view = PhotonEffectViewUtil.prepare(handle, new JsonObject());
		var origin = editor.runtime == null ? new Vector3f() : new Vector3f(editor.runtime.root.transform().position());

		if (editor.runtime != null) {
			editor.runtime.destroy(true);
		}

		editor.sceneView.clearScene();
		editor.runtime = project.getFx().createInternalRuntime();
		editor.runtime.root.updatePos(origin);
		editor.hierarchyView.loadFXRuntime(editor.runtime);
		editor.timelineView.clear();
		editor.timelineView.rebuild();
		editor.sceneView.loadScene();
		editor.reloadEffect();
		view.commit();

		if (!playing) {
			editor.sceneView.particleManager.pause();
		}
	}
}
