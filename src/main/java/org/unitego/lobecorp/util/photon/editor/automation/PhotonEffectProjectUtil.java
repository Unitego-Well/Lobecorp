package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.Platform;
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
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.io.File;
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

	public record PreparedOpen(IProject project, File file) {
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

	/// 文件读取与完整内容校验先于标签创建，失败不影响用户现有工程。
	public static PreparedOpen prepareOpen(JsonObject request) throws Exception {
		if (Minecraft.getInstance().level == null) {
			throw new PhotonEffectCommandException(Code.WORLD_REQUIRED);
		}

		var path = PhotonEffectJsonUtil.string(request, "path");
		if (!path.endsWith(FXProject.TYPE.getSuffix())) {
			throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION, path);
		}

		var type = FXProject.TYPE;
		var file = PhotonEffectFileUtil.source(request, type.getSuffix()).toFile();
		PhotonEditorProjectGuardUtil.requireSupportedFile(file);
		var data = NbtIo.read(file.toPath());

		if (data == null || !(data.get("data") instanceof CompoundTag contents)) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA);
		}

		var project = type.loadProjectFromFile(file);
		PhotonEffectEditUtil.validate(project);

		if (!contents.equals(snapshot(project))) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA);
		}

		return new PreparedOpen(project, file);
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
		if (!kind.equals("fx")) {
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
			case "model" -> throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION, kind);
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
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(canonical(snapshot(project)).getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static String canonical(Tag value) {
		if (value instanceof CompoundTag compound) {
			var result = new StringBuilder("{");
			compound.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> result.append(entry.getKey().length())
					.append(':').append(entry.getKey()).append('=').append(canonical(entry.getValue())).append(';'));
			return result.append('}').toString();
		}

		if (value instanceof ListTag list) {
			var result = new StringBuilder("[");

			for (var child : list) {
				result.append(canonical(child)).append(';');
			}

			return result.append(']').toString();
		}

		return value.toString();
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

	public static void decode(IProject project, CompoundTag data) {
		PhotonEditorProjectGuardUtil.requireSupportedProjectData(data);
		var reporter = new ProblemReporter.Collector();
		project.deserializeProject(TagValueInput.create(reporter, Platform.getFrozenRegistry(), data.copy()));

		if (!reporter.isEmpty()) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA, reporter.getReport());
		}
	}

	public static IProject copy(IProject original) {
		IProject copy = new FXProject();
		decode(copy, snapshot(original));
		return copy;
	}

	public static JsonObject describe(Handle handle) {
		var project = handle.project();
		var editor = handle.editor();
		var result = new JsonObject();
		result.addProperty("projectId", handle.id());
		result.addProperty("kind", "fx");
		result.addProperty("revision", revision(project));
		result.addProperty("editorPath", editor.getCurrentProjectFile() == null ? "" : editor.getCurrentProjectFile().getAbsolutePath());
		result.addProperty("dirty", editor.isCurrentProjectDirty());
		result.addProperty("playing", editor.sceneView.particleManager.isPlaying());
		result.addProperty("time", editor.sceneView.particleManager.getTime(0));
		result.addProperty("undoCount", editor.historyView.getUndoStack().size());
		result.addProperty("redoCount", editor.historyView.getRedoStack().size());
		var data = snapshot(project);
		boolean containsModel = false;


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
