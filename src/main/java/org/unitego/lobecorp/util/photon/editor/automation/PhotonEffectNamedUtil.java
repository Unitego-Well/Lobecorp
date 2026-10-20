package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import com.lowdragmc.photon.gui.editor.FXProject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import org.unitego.lobecorp.client.photon.editor.PhotonNamedAuthoringSession;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonProjectPreviewAccess;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil.Handle;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil.Document;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedCodecUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.UUID;

/// 界面制作会话的结构化适配；MCP调度、控制门禁和文件保护沿用现有入口。
public class PhotonEffectNamedUtil {
	public static JsonObject operations() {
		return JsonParser.parseString("""
				{
				  "add":{"required":["name"]},
				  "duplicate":{"required":["animationId","name"]},
				  "rename":{"required":["animationId","name"],"identity":"保留UUID并同步本库名称引用"},
				  "remove":{"required":["animationId"],"references":"仍有名称或UUID引用时拒绝"},
				  "move":{"required":["animationId","index"],"index":"非负整数，最终列表下标"},
				  "replace_definition":{"required":["animationId","snbt"],"snbt":"完整定义原树，含参数/绑定/标记/轨道；身份和名称不变，调用方保留未知字段"},
				  "patch_definition":{"required":["animationId","snbt"],"snbt":"局部定义字段，保留其他原树；列表字段整体替换"},
				  "patch_project_preview":{"required":["animationId","snbt"],"snbt":"按工程测试UUID局部编辑；不创建测试，不进入发布FX"},
				  "set_default":{"required":["animation"],"animation":"名称或UUID，空字符串表示手动播放"},
				  "set_rules":{"required":["snbt"],"snbt":"仅含rules列表的复合标签，完整替换触发规则"},
				  "set_project_preview":{"required":["snbt"],"snbt":"完整工程测试定义，不进入发布FX"},
				  "clear_project_preview":{}
				}
				""").getAsJsonObject();
	}

	public record Prepared(Handle handle, UUID controller, PhotonNamedAuthoringSession session,
	                       Document before, boolean dryRun) implements AutoCloseable {
		public JsonObject commit() {
			var proposed = session.document();
			try {
				session.validate(session.revision());
				if (!dryRun) {
					session.commit(session.revision());
				}
			} catch (IllegalStateException conflict) {
				throw new PhotonEffectCommandException(Code.REVISION_CONFLICT, conflict.getMessage());
			}

			var result = state(handle, controller);
			result.addProperty("dryRun", dryRun);
			result.addProperty("changed", !before.equals(proposed));
			if (dryRun) {
				result.addProperty("proposedLibrarySnbt", proposed.library().toString());
				var preview = proposed.preview();
				result.add("proposedProjectPreviewSnbt", preview == null ? JsonNull.INSTANCE
						: new JsonPrimitive(preview.toString()));
			}

			return result;
		}

		@Override
		public void close() {
			session.cancel();
		}
	}

	public static Prepared prepare(Handle handle, JsonObject request) {
		if (!PhotonEffectProjectUtil.revision(handle.project()).equals(PhotonEffectJsonUtil.string(request, "expectedRevision"))) {
			throw new PhotonEffectCommandException(Code.REVISION_CONFLICT);
		}

		var operations = PhotonEffectJsonUtil.array(request, "operations");
		if (operations.isEmpty() || operations.size() > PhotonEffectEditUtil.MAX_OPERATIONS) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "operations");
		}

		var controller = uuid(request, "controllerId");
		var session = new PhotonNamedAuthoringSession(handle.editor(), controller);
		var before = session.document();
		try {
			for (var raw : operations) {
				if (!raw.isJsonObject()) {
					throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "operations");
				}

				session.edit(session.revision(), source -> apply(source, raw.getAsJsonObject()));
			}

			session.validate(session.revision());
			return new Prepared(handle, controller, session, before, PhotonEffectJsonUtil.bool(request, "dryRun", false));
		} catch (RuntimeException failure) {
			session.cancel();
			throw failure;
		}
	}

	/// 纯原树操作可由其他制作客户端复用；全量SNBT替换必须携带要保留的未知字段。
	public static Document apply(Document source, JsonObject operation) {
		return switch (PhotonEffectJsonUtil.string(operation, "op")) {
			case "add" -> PhotonNamedAuthoringUtil.add(source, PhotonEffectJsonUtil.string(operation, "name"));
			case "duplicate" ->
					PhotonNamedAuthoringUtil.duplicate(source, uuid(operation, "animationId"), PhotonEffectJsonUtil.string(operation, "name"));
			case "rename" ->
					PhotonNamedAuthoringUtil.rename(source, uuid(operation, "animationId"), PhotonEffectJsonUtil.string(operation, "name"));
			case "remove" -> PhotonNamedAuthoringUtil.remove(source, uuid(operation, "animationId"));
			case "move" -> PhotonNamedAuthoringUtil.move(source, uuid(operation, "animationId"), index(operation));
			case "replace_definition" ->
					PhotonNamedAuthoringUtil.replaceDefinition(source, uuid(operation, "animationId"), snbt(operation, "snbt"));
			case "set_default" ->
					PhotonNamedAuthoringUtil.defaultAnimation(source, PhotonEffectJsonUtil.string(operation, "animation"));
			case "patch_definition" ->
					PhotonNamedAuthoringUtil.patchDefinition(source, uuid(operation, "animationId"), snbt(operation, "snbt"));
			case "patch_project_preview" ->
					PhotonNamedAuthoringUtil.patchPreview(source, uuid(operation, "animationId"), snbt(operation, "snbt"));
			case "set_rules" -> {
				var value = snbt(operation, "snbt");
				if (value.size() != 1 || !(value.get("rules") instanceof ListTag rules)) {
					throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "snbt.rules");
				}

				yield PhotonNamedAuthoringUtil.rules(source, rules);
			}
			case "set_project_preview" -> new Document(source.library(), snbt(operation, "snbt"));
			case "clear_project_preview" -> new Document(source.library(), null);
			default ->
					throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION, PhotonEffectJsonUtil.string(operation, "op"));
		};
	}

	public static JsonObject state(Handle handle, @Nullable UUID controllerId) {
		if (handle.editor().getCurrentProject() != handle.project()) {
			throw new PhotonEffectCommandException(Code.PROJECT_CLOSED);
		}
		if (!(handle.project() instanceof FXProject project)) {
			throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION);
		}

		var result = new JsonObject();
		result.addProperty("projectId", handle.id());
		result.addProperty("revision", PhotonEffectProjectUtil.revision(project));
		result.addProperty("undoCount", handle.editor().historyView.getUndoStack().size());
		result.addProperty("redoCount", handle.editor().historyView.getRedoStack().size());
		var preview = ((PhotonProjectPreviewAccess) project).lobecorp$projectPreview();
		result.add("projectPreviewSnbt", preview == null ? JsonNull.INSTANCE : new JsonPrimitive(preview.toString()));
		var controllers = new JsonArray();
		for (var object : project.getFx().getFxData().objects()) {
			if (!(object instanceof PhotonNamedController controller) || controllerId != null && !controller.id().equals(controllerId)) {
				continue;
			}

			controllers.add(describe(handle, controller));
		}
		if (controllerId != null && controllers.size() != 1) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "controllerId");
		}

		result.add("controllers", controllers);
		return result;
	}

	private static JsonObject describe(Handle handle, PhotonNamedController controller) {
		var result = new JsonObject();
		var raw = controller.definition();
		result.addProperty("controllerId", controller.id().toString());
		result.addProperty("name", controller.getName());
		result.addProperty("librarySnbt", raw.toString());
		result.addProperty("writable", !PhotonEditorProjectGuardUtil.containsExtensions(handle.project()));
		try {
			var library = PhotonNamedCodecUtil.read(raw);
			result.addProperty("defaultAnimation", library.defaultAnimation());
			result.add("rules", PhotonEffectJsonUtil.json(raw.getListOrEmpty("rules")));
			var animations = new JsonArray();
			for (var definition : library.animations()) {
				var entry = new JsonObject();
				entry.addProperty("animationId", definition.id().toString());
				entry.addProperty("name", definition.name());
				entry.addProperty("duration", definition.duration());
				entry.addProperty("autoDuration", definition.autoDuration());
				entry.addProperty("loop", definition.loop());
				entry.addProperty("priority", definition.priority());
				entry.addProperty("completion", definition.completion().name());
				entry.addProperty("trackCount", definition.tracks().size());
				entry.add("parameters", PhotonEffectJsonUtil.json(definition.source().getListOrEmpty("parameters")));
				entry.add("bindings", PhotonEffectJsonUtil.json(definition.source().getListOrEmpty("bindings")));
				entry.add("markers", PhotonEffectJsonUtil.json(definition.source().getListOrEmpty("markers")));
				animations.add(entry);
			}

			result.add("animations", animations);
		} catch (IOException invalid) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA, invalid.getMessage());
		}

		var runtime = handle.editor().runtime;
		var playback = runtime instanceof PhotonNamedAccess access ? access.lobecorp$namedRuntime() : null;
		result.add("runtime", playback != null && playback.controllerId().equals(controller.id())
				? PhotonEffectNamedControlUtil.describe(playback, raw) : JsonNull.INSTANCE);
		return result;
	}

	public static CompoundTag snbt(JsonObject request, String key) {
		try {
			return TagParser.parseCompoundFully(PhotonEffectJsonUtil.string(request, key));
		} catch (CommandSyntaxException invalid) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA, key + ": " + invalid.getMessage());
		}
	}

	public static UUID uuid(JsonObject request, String key) {
		var text = PhotonEffectJsonUtil.string(request, key);
		try {
			var id = UUID.fromString(text);
			if (!id.toString().equalsIgnoreCase(text)) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, key);
			}

			return id;
		} catch (IllegalArgumentException invalid) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, key);
		}
	}

	private static int index(JsonObject request) {
		double value = PhotonEffectJsonUtil.number(request, "index");
		if (value != Math.rint(value) || value < 0 || value > Integer.MAX_VALUE) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "index");
		}

		return (int) value;
	}
}
