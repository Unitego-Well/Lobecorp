package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.EditAction;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;
import com.lowdragmc.photon.client.fx.timeline.Track;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelPlaybackConfig;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil.Handle;
import org.unitego.lobecorp.util.photon.model.PhotonModelIOUtil;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class PhotonEffectEditUtil {
	/// 防止单个工具调用在客户端主线程累计无界编辑工作。
	public static final int MAX_OPERATIONS = 256;

	public record Prepared(Handle handle, CompoundTag before, CompoundTag after, boolean dryRun) {
		public JsonObject commit() {
			JsonObject result;

			if (!dryRun && !before.equals(after)) {
				PhotonEffectProjectUtil.apply(handle, after);

				try {
					result = PhotonEffectProjectUtil.describe(handle);
					handle.editor().historyView.pushHistory(Component.literal("MCP"),
							EditAction.of(() -> PhotonEffectProjectUtil.apply(handle, after),
									() -> PhotonEffectProjectUtil.apply(handle, before)), false);
					result.addProperty("undoCount", handle.editor().historyView.getUndoStack().size());
					result.addProperty("redoCount", handle.editor().historyView.getRedoStack().size());
				} catch (RuntimeException failure) {
					PhotonEffectProjectUtil.apply(handle, before);
					throw failure;
				}
			} else {
				result = PhotonEffectProjectUtil.describe(handle);
			}
			result.addProperty("dryRun", dryRun);
			result.addProperty("changed", !before.equals(after));

			if (dryRun) {
				result.addProperty("proposedSnapshotIncluded", result.has("snapshotSnbt"));

				if (result.has("snapshotSnbt")) {
					result.addProperty("proposedSnapshotSnbt", after.toString());
				}
			}

			return result;
		}
	}

	public static Prepared prepare(JsonObject request) throws Exception {
		var handle = PhotonEffectProjectUtil.require(PhotonEffectJsonUtil.string(request, "projectId"));

		if (!PhotonEffectProjectUtil.revision(handle.project()).equals(PhotonEffectJsonUtil.string(request, "expectedRevision"))) {
			throw new PhotonEffectCommandException(Code.REVISION_CONFLICT);
		}

		var operations = PhotonEffectJsonUtil.array(request, "operations");

		if (operations.isEmpty() || operations.size() > MAX_OPERATIONS) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "operations");
		}

		var before = PhotonEffectProjectUtil.snapshot(handle.project());
		var copy = PhotonEffectProjectUtil.copy(handle.project());

		for (var value : operations) {
			if (!value.isJsonObject()) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "operations");
			}

			applyOperation(copy, value.getAsJsonObject());
		}

		validate(copy);
		return new Prepared(handle, before, PhotonEffectProjectUtil.snapshot(copy), PhotonEffectJsonUtil.bool(request, "dryRun", false));
	}

	private static void applyOperation(IProject project, JsonObject operation) throws Exception {
		switch (PhotonEffectJsonUtil.string(operation, "op")) {
			case "add_emitter" -> addEmitter(fx(project), operation);
			case "remove_emitter" -> removeEmitter(fx(project), operation);
			case "set_parameter" -> setParameter(fx(project), operation);
			case "set_model_asset" -> replaceAsset(project, operation, PhotonModelIOUtil.readJson(
					PhotonEffectJsonUtil.object(operation, "asset").toString()));
			case "patch_model_asset" -> patchAsset(project, operation);
			case "set_model_playback" -> setPlayback(project, operation);
			case "set_timeline" -> setTimeline(fx(project), operation);
			case "set_project_data" -> {
				var data = TagParser.parseCompoundFully(PhotonEffectJsonUtil.string(operation, "snbt"));
				PhotonEffectProjectUtil.decode(project, data);

				if (!data.equals(PhotonEffectProjectUtil.snapshot(project))) {
					throw new PhotonEffectCommandException(Code.INVALID_DATA, "snbt");
				}
			}
			default ->
					throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION, PhotonEffectJsonUtil.string(operation, "op"));
		}
	}

	private static FXProject fx(IProject project) {
		if (!(project instanceof FXProject fx)) {
			throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION);
		}

		return fx;
	}

	private static IFXObject object(FXProject project, JsonObject operation) {
		var id = UUID.fromString(PhotonEffectJsonUtil.string(operation, "objectId"));
		return project.getFx().getFxData().objects().stream().filter(object -> object.id().equals(id)).findFirst()
				.orElseThrow(() -> new PhotonEffectCommandException(Code.INVALID_ARGUMENT, id.toString()));
	}

	private static void addEmitter(FXProject project, JsonObject operation) {
		var typeName = PhotonEffectJsonUtil.string(operation, "type");
		var type = PhotonRegistries.FX_OBJECTS.get(typeName);

		if (type == null) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, typeName);
		}

		var object = type.create();
		object.setName(PhotonEffectJsonUtil.optionalString(operation, "name", typeName));
		var objects = project.getFx().getFxData().objects();
		objects.add(object);

		if (operation.has("parameters")) {
			var wrapper = object.serializeWrapper();

			if (wrapper == null) {
				throw new PhotonEffectCommandException(Code.INVALID_DATA);
			}

			var data = wrapper.getCompoundOrEmpty("data");
			var json = PhotonEffectJsonUtil.json(data).getAsJsonObject();

			for (var entry : PhotonEffectJsonUtil.object(operation, "parameters").entrySet()) {
				if (!json.has(entry.getKey())) {
					throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, entry.getKey());
				}

				json.add(entry.getKey(), entry.getValue().deepCopy());
			}

			wrapper.put("data", PhotonEffectJsonUtil.tag(json, data));
			replaceObject(project, object, wrapper, null);
		}
	}

	private static void removeEmitter(FXProject project, JsonObject operation) {
		var removed = object(project, operation);
		var objects = project.getFx().getFxData().objects();
		objects.remove(removed);
		objects.forEach(object -> object.transform()._setInternalChildID(object.transform()._getInternalChildID().stream()
				.filter(id -> !id.equals(removed.id())).toList()));
	}

	private static void setParameter(FXProject project, JsonObject operation) {
		var object = object(project, operation);
		var wrapper = object.serializeWrapper();

		if (wrapper == null || !operation.has("value") || operation.get("value").isJsonNull()) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
		}

		var data = wrapper.getCompoundOrEmpty("data");
		var json = PhotonEffectJsonUtil.json(data);
		var path = PhotonEffectJsonUtil.string(operation, "path");
		PhotonEffectJsonUtil.patch(json, path, operation.get("value"), true);
		wrapper.put("data", PhotonEffectJsonUtil.tag(json, data));
		replaceObject(project, object, wrapper, path);
	}

	private static void replaceObject(FXProject project, IFXObject original, CompoundTag wrapper, @Nullable String editedPath) {
		var replacement = IFXObject.deserializeWrapper(wrapper);

		if (replacement == null || !replacement.id().equals(original.id())
				|| replacement.getFXObjectType() != original.getFXObjectType()
				|| !matchesNativeModuleDefaults(wrapper, Objects.requireNonNull(replacement.serializeWrapper()), editedPath)) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA);
		}

		var objects = project.getFx().getFxData().objects();
		objects.set(objects.indexOf(original), replacement);
	}

	/// 原生模块开关可补齐或省略模块内部字段；开关值及模块外数据仍必须完整往返。
	private static boolean matchesNativeModuleDefaults(CompoundTag requested, CompoundTag actual, @Nullable String path) {
		if (requested.equals(actual)) {
			return true;
		}

		if (path == null || !path.endsWith("/_enable")) {
			return false;
		}

		var normalized = requested.copy();
		var requestedParent = normalized.getCompoundOrEmpty("data");
		var actualParent = actual.getCompoundOrEmpty("data");
		var segments = path.split("/");

		for (int index = 1; index < segments.length - 2; index++) {
			var key = segments[index].replace("~1", "/").replace("~0", "~");

			if (!(requestedParent.get(key) instanceof CompoundTag requestedChild)
					|| !(actualParent.get(key) instanceof CompoundTag actualChild)) {
				return false;
			}

			requestedParent = requestedChild;
			actualParent = actualChild;
		}

		var key = segments[segments.length - 2].replace("~1", "/").replace("~0", "~");

		if (!(requestedParent.get(key) instanceof CompoundTag requestedModule)
				|| !(actualParent.get(key) instanceof CompoundTag actualModule)
				|| requestedModule.get("_enable") == null
				|| !Objects.equals(requestedModule.get("_enable"), actualModule.get("_enable"))) {
			return false;
		}

		requestedParent.put(key, actualModule.copy());
		return normalized.equals(actual);
	}

	private static PhotonModelAsset asset(IProject project, JsonObject operation) {
		if (project instanceof PhotonModelProject model) {
			return model.session().asset();
		}

		if (object(fx(project), operation) instanceof PhotonModelEmitter model) {
			return model.getAsset();
		}

		throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION);
	}

	private static void replaceAsset(IProject project, JsonObject operation, PhotonModelAsset asset) {
		asset.validate();

		if (project instanceof PhotonModelProject model) {
			model.session().restore(asset);
			return;
		}

		if (object(fx(project), operation) instanceof PhotonModelEmitter model) {
			model.setAsset(asset);
			return;
		}

		throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION);
	}

	private static void patchAsset(IProject project, JsonObject operation) {
		if (!operation.has("value")) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "value");
		}

		var json = JsonParser.parseString(PhotonModelIOUtil.writeJson(asset(project, operation)));
		PhotonEffectJsonUtil.patch(json, PhotonEffectJsonUtil.string(operation, "path"), operation.get("value"), false);
		replaceAsset(project, operation, PhotonModelIOUtil.readJson(json.toString()));
	}

	private static void setPlayback(IProject project, JsonObject operation) {
		if (!(project instanceof PhotonModelProject model)) {
			throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION);
		}

		var data = PhotonEffectProjectUtil.snapshot(model);
		var playback = data.getCompoundOrEmpty("playback");
		var json = PhotonEffectJsonUtil.json(playback).getAsJsonObject();

		for (var entry : PhotonEffectJsonUtil.object(operation, "playback").entrySet()) {
			if (!json.has(entry.getKey())) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, entry.getKey());
			}

			json.add(entry.getKey(), entry.getValue().deepCopy());
		}

		var replacement = PhotonEffectJsonUtil.tag(json, playback);
		data.put("playback", replacement);
		PhotonEffectProjectUtil.decode(model, data);
	}

	private static void setTimeline(FXProject project, JsonObject operation) {
		var timeline = project.getFx().getFxData().timeline();
		var original = timeline.serializeNBT(Platform.getFrozenRegistry());
		var data = (CompoundTag) PhotonEffectJsonUtil.tag(PhotonEffectJsonUtil.object(operation, "timeline"), original);
		timeline.deserializeNBT(Platform.getFrozenRegistry(), data);

		if (!PhotonEffectJsonUtil.json(data).equals(PhotonEffectJsonUtil.json(timeline.serializeNBT(Platform.getFrozenRegistry())))) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA, "timeline");
		}
	}

	private static void validateParents(List<IFXObject> objects) {
		var parents = new HashMap<UUID, UUID>();
		objects.forEach(object -> parents.put(object.id(), object.transform()._getInternalParentID()));

		for (var id : parents.keySet()) {
			var seen = new HashSet<UUID>();
			var current = id;

			while (current != null && !current.equals(FXRuntime.ROOT_UUID)) {
				if (!seen.add(current) || !parents.containsKey(current)) {
					throw new PhotonEffectCommandException(Code.INVALID_DATA, current.toString());
				}

				current = parents.get(current);
			}
		}
	}

	private static void validateTracks(List<Track> tracks, HashSet<UUID> ids) {
		for (var track : tracks) {
			var target = track.targetId();

			if (target != null && !ids.contains(target)) {
				throw new PhotonEffectCommandException(Code.INVALID_DATA, target.toString());
			}

			for (var clip : track.clips()) {
				if (!Double.isFinite(clip.start()) || clip.start() < 0 || !Double.isFinite(clip.duration())
						|| clip.duration() < 0 || !Float.isFinite(clip.speed()) || clip.speed() < 0
						|| clip.targetId() != null && !ids.contains(clip.targetId())) {
					throw new PhotonEffectCommandException(Code.INVALID_DATA);
				}
			}
		}
	}

	public static void validate(IProject project) {
		if (project instanceof PhotonModelProject model) {
			model.session().asset().validate();
			model.playback().validate(model.session().asset());
			PhotonEffectSchemaUtil.validate(PhotonModelPlaybackConfig.class, PhotonEffectProjectUtil.snapshot(model).getCompoundOrEmpty("playback"));
			return;
		}

		var fx = fx(project);
		var data = fx.getFx().getFxData();
		var ids = new HashSet<UUID>();

		for (var object : data.objects()) {
			var wrapper = object.serializeWrapper();

			if (FXRuntime.ROOT_UUID.equals(object.id()) || !ids.add(object.id()) || wrapper == null) {
				throw new PhotonEffectCommandException(Code.INVALID_DATA);
			}

			PhotonEffectSchemaUtil.validate(object.getClass(), wrapper.getCompoundOrEmpty("data"));

			if (object instanceof PhotonModelEmitter model) {
				model.getAsset().validate();
				PhotonModelPlaybackConfig.from(model.config).validate(model.getAsset());
			}
		}

		validateParents(data.objects());
		ids.add(FXRuntime.ROOT_UUID);
		validateTracks(data.timeline().leafTracks(true), ids);

		for (var object : data.objects()) {
			if (!(object instanceof PhotonAnimationController controller)) {
				continue;
			}

			for (var animation : controller.library().animations) {
				validateTracks(animation.leafTracks(true), ids);

				for (var binding : animation.bindings) {
					if (!ids.contains(binding.target)) {
						throw new PhotonEffectCommandException(Code.INVALID_DATA, binding.target.toString());
					}
				}
			}
		}
	}
}
