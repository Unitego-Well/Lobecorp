package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.gui.editor.FXProject;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.StringTag;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceJsonUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import org.unitego.lobecorp.util.photon.model.PhotonModelCodec;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelObjUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelJsonEditUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedTrackAuthoringUtil;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/// 模型工程与MCP使用同一原树事务；几何、面角UV、材质和动画仍按当前模型协议解析。
public class PhotonEffectModelAuthoringUtil {
	public static void apply(IProject project, JsonObject operation) throws Exception {
		if (!(project instanceof PhotonModelProject model)) {
			applyEmbedded(project, operation);
			return;
		}
		var data = model.document();
		String action = PhotonEffectJsonUtil.string(operation, "op");
		if (action.equals("set_model_renderer")) {
			var patch = PhotonEffectNamedUtil.snbt(operation, "snbt");
			data.put("renderer", PhotonNamedTrackAuthoringUtil.merge(data.getCompoundOrEmpty("renderer"), patch));
		} else if (action.equals("set_model_playback")) {
			var values = PhotonEffectJsonUtil.object(operation, "values");
			var allowed = Set.of("enabled", "duration", "startDelay", "looping", "paused", "clip", "playbackSpeed", "animationOffset");
			var current = data.getCompoundOrEmpty("playback");
			for (var entry : values.entrySet()) {
				if (!allowed.contains(entry.getKey())) {
					throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, entry.getKey());
				}
				var template = current.get(entry.getKey());
				if (template == null) {
					var defaults = PersistedParser.serializeNBT(model.model().config, Platform.getFrozenRegistry());
					template = defaults.get(entry.getKey());
				}
				var changed = PhotonEffectJsonUtil.tag(entry.getValue(), template);
				if (current.contains(entry.getKey()) || !changed.equals(template)) {
					current.put(entry.getKey(), changed);
				}
			}
			data.put("playback", current);
		} else {
			var asset = data.get("asset");
			var current = switch (asset) {
				case ByteArrayTag bytes ->
						PhotonResourceJsonUtil.read(bytes.getAsByteArray(), PhotonResourceLimits.DEFAULT).getAsJsonObject();
				case StringTag(var text) ->
						PhotonResourceJsonUtil.read(text, PhotonResourceLimits.DEFAULT).getAsJsonObject();
				case null, default -> throw new PhotonEffectCommandException(Code.INVALID_DATA, "asset");
			};
			var candidate = switch (action) {
				case "edit_model_value" ->
						PhotonModelJsonEditUtil.set(current, PhotonEffectJsonUtil.string(operation, "path"), operation.get("value"));
				case "import_model_obj" ->
						PhotonModelObjUtil.read(PhotonEffectJsonUtil.string(operation, "text"), PhotonEffectJsonUtil.bool(operation, "flipV", false));
				case "set_model_asset" -> PhotonEffectJsonUtil.object(operation, "asset").deepCopy();
				case "patch_model_asset" -> merge(current, PhotonEffectJsonUtil.object(operation, "patch"));
				default -> throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION, action);
			};
			PhotonModelTopologyUtil.prepare(PhotonModelCodec.read(candidate));
			var json = candidate.toString();
			if (!candidate.equals(current)) {
				data.put("asset", asset instanceof StringTag ? StringTag.valueOf(json) : new ByteArrayTag(json.getBytes(StandardCharsets.UTF_8)));
			}
		}
		model.restore(data);
	}

	private static void applyEmbedded(IProject project, JsonObject operation) throws Exception {
		if (!(project instanceof FXProject fx)) {
			throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION);
		}
		var id = PhotonEffectNamedUtil.uuid(operation, "objectId");
		var target = fx.getFx().getFxData().objects().stream().filter(value -> value.id().equals(id)).findFirst()
				.orElseThrow(() -> new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "objectId"));
		if (!(target instanceof PhotonModelOutput model)) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "objectId");
		}
		var temporary = new PhotonModelProject();
		var data = temporary.document();
		var config = PersistedParser.serializeNBT(model.config, Platform.getFrozenRegistry());
		data.put("asset", model.config.assetSnapshot());
		data.put("playback", config.copy());
		data.put("renderer", PersistedParser.serializeNBT(model.config.modelRenderer, Platform.getFrozenRegistry()));
		temporary.restore(data);
		apply(temporary, operation);
		PersistedParser.deserializeNBT(PersistedParser.serializeNBT(temporary.model().config, Platform.getFrozenRegistry()), model.config, Platform.getFrozenRegistry());
		model.config.setAssetSnapshot(temporary.model().config.assetSnapshot());
	}

	private static JsonObject merge(JsonObject source, JsonObject patch) {
		var result = source.deepCopy();
		for (var entry : patch.entrySet()) {
			if (entry.getValue().isJsonNull()) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, entry.getKey());
			}
			result.add(entry.getKey(), entry.getValue().isJsonObject() && result.has(entry.getKey()) && result.get(entry.getKey()).isJsonObject()
					? merge(result.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject()) : entry.getValue().deepCopy());
		}
		return result;
	}
}
