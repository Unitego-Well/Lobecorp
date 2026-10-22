package org.unitego.lobecorp.client.photon.model.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lowdragmc.photon.gui.editor.FXEditor;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.StringTag;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceJsonUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectEditUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelJsonEditUtil;

import java.util.function.Consumer;

/// 编辑器只提交当前资产的原树副本，使用与 MCP 相同的身份、revision 和撤销事务。
public class PhotonModelWorkspaceSession {
	private final FXEditor editor;
	private final PhotonModelProject project;
	private final Consumer<String> error;
	private JsonObject asset;
	private String revision;
	private boolean committing;

	public PhotonModelWorkspaceSession(FXEditor editor, PhotonModelProject project, Consumer<String> error) {
		this.editor = editor;
		this.project = project;
		this.error = error;
		refresh();
	}

	public void refresh() {
		var tag = project.document().get("asset");
		try {
			asset = switch (tag) {
				case ByteArrayTag bytes ->
						PhotonResourceJsonUtil.read(bytes.getAsByteArray(), PhotonResourceLimits.DEFAULT).getAsJsonObject();
				case StringTag(var text) ->
						PhotonResourceJsonUtil.read(text, PhotonResourceLimits.DEFAULT).getAsJsonObject();
				case null, default -> throw new IllegalStateException("模型资产缺失。");
			};
			revision = PhotonEffectProjectUtil.revision(project);
		} catch (Exception invalid) {
			throw new IllegalStateException("无法读取模型制作数据。", invalid);
		}
	}

	public JsonObject asset() {
		return asset;
	}

	public JsonElement value(String pointer) {
		JsonElement current = asset;
		if (pointer.isEmpty()) {
			return current;
		}
		for (var token : pointer.substring(1).split("/")) {
			var key = token.replace("~1", "/").replace("~0", "~");
			current = current.isJsonArray() ? current.getAsJsonArray().get(Integer.parseInt(key)) : current.getAsJsonObject().get(key);
		}
		return current;
	}

	public JsonElement valueOr(String pointer, JsonElement fallback) {
		try {
			var result = value(pointer);
			return result == null || result.isJsonNull() ? fallback : result;
		} catch (RuntimeException invalid) {
			return fallback;
		}
	}

	public boolean isCommitting() {
		return committing;
	}

	public String revision() {
		return revision;
	}

	public void set(String pointer, JsonElement value) {
		setAt(revision, pointer, value);
	}

	public void setAt(String expectedRevision, String pointer, JsonElement value) {
		attempt(() -> {
			if (!revision.equals(expectedRevision)) {
				throw new IllegalStateException("工程已改变，请重新开始编辑。");
			}
			var operation = new JsonObject();
			operation.addProperty("op", "set_model_asset");
			operation.add("asset", PhotonModelJsonEditUtil.set(asset, pointer, value));
			commit(operation);
		});
	}

	public void change(Consumer<JsonObject> change) {
		changeAt(revision, change);
	}

	public void changeAt(String expectedRevision, Consumer<JsonObject> change) {
		attempt(() -> {
			if (!revision.equals(expectedRevision)) {
				throw new IllegalStateException("工程已改变，请重新开始编辑。");
			}
			var candidate = asset.deepCopy();
			change.accept(candidate);
			var operation = new JsonObject();
			operation.addProperty("op", "set_model_asset");
			operation.add("asset", candidate);
			commit(operation);
		});
	}

	public void playback(String clip) {
		attempt(() -> {
			var operation = new JsonObject();
			operation.addProperty("op", "set_model_playback");
			var values = new JsonObject();
			values.addProperty("clip", clip);
			operation.add("values", values);
			commit(operation);
		});
	}

	private void commit(JsonObject operation) throws Exception {
		var handle = PhotonEffectProjectUtil.projects().stream()
				.filter(value -> value.editor() == editor && value.project() == project).findFirst()
				.orElseThrow(() -> new IllegalStateException("模型工程已关闭。"));
		var request = new JsonObject();
		request.addProperty("projectId", handle.id());
		request.addProperty("expectedRevision", revision);
		var operations = new JsonArray();
		operations.add(operation);
		request.add("operations", operations);
		committing = true;
		try {
			PhotonEffectEditUtil.prepare(request).commit();
			refresh();
		} finally {
			committing = false;
		}
	}

	private void attempt(Operation operation) {
		try {
			operation.run();
			error.accept("");
		} catch (Exception invalid) {
			error.accept(invalid.getMessage() == null ? invalid.toString() : invalid.getMessage());
		}
	}

	@FunctionalInterface
	private interface Operation {
		void run() throws Exception;
	}
}
