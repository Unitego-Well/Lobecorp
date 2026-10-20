package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedHandle;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.client.photon.playback.PhotonProjectPreviewAccess;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil.Handle;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectPreviewUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 客户端线程上的瞬时控制，不写制作定义或历史；运行代次和句柄身份共同拒绝过期调用。
public class PhotonEffectNamedControlUtil {
	public static JsonObject control(Handle handle, JsonObject request) {
		if (handle.editor().getCurrentProject() != handle.project()) {
			throw new PhotonEffectCommandException(Code.PROJECT_CLOSED);
		}

		var runtime = handle.editor().runtime;
		var playback = runtime instanceof PhotonNamedAccess access ? access.lobecorp$namedRuntime() : null;
		if (playback == null || playback.closed() || !playback.controllerId().equals(PhotonEffectNamedUtil.uuid(request, "controllerId"))) {
			throw new PhotonEffectCommandException(Code.RUNTIME_UNAVAILABLE);
		}
		if (!playback.id().equals(PhotonEffectNamedUtil.uuid(request, "expectedRuntimeId"))) {
			throw new PhotonEffectCommandException(Code.RUNTIME_CONFLICT);
		}

		String target = PhotonEffectJsonUtil.optionalString(request, "target", "named");
		String action = PhotonEffectJsonUtil.string(request, "action");
		if (target.equals("project_test")) {
			projectTest(handle, playback, request, action);
		} else if (target.equals("named")) {
			named(playback, request, action);
		} else {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "target");
		}

		var result = PhotonEffectNamedUtil.state(handle, playback.controllerId());
		result.addProperty("action", action);
		result.addProperty("target", target);
		return result;
	}

	private static void named(PhotonNamedRuntime runtime, JsonObject request, String action) {
		switch (action) {
			case "play" -> {
				allowed(request, Set.of("name", "duration", "restart", "inputs"));
				var inputs = inputs(request);
				double duration = request.has("duration") ? PhotonEffectJsonUtil.number(request, "duration") : -1;
				runtime.play(PhotonEffectJsonUtil.string(request, "name"), duration,
						PhotonEffectJsonUtil.bool(request, "restart", false), inputs);
			}
			case "pause", "resume", "stop", "seek", "parameters", "duration" -> {
				allowed(request, switch (action) {
					case "seek" -> Set.of("handleId", "time");
					case "parameters" -> Set.of("handleId", "inputs");
					case "duration" -> Set.of("handleId", "duration");
					default -> Set.of("handleId");
				});
				var handle = requireHandle(runtime, request);
				if (runtime.ownsPreview(handle) || handle.controlled()) {
					throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "target");
				}

				switch (action) {
					case "pause" -> runtime.pause(handle, true);
					case "resume" -> runtime.pause(handle, false);
					case "stop" -> runtime.stop(handle);
					case "seek" -> runtime.seek(handle, PhotonEffectJsonUtil.number(request, "time"));
					case "parameters" -> {
						PhotonEffectJsonUtil.object(request, "inputs");
						runtime.parameters(handle, inputs(request));
					}
					case "duration" -> runtime.duration(handle, PhotonEffectJsonUtil.number(request, "duration"));
					default -> throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "action");
				}
			}
			case "trigger", "signal" -> {
				allowed(request, Set.of("channel", "name", "dataSnbt"));
				String channel = PhotonEffectJsonUtil.optionalString(request, "channel", "");
				String name = PhotonEffectJsonUtil.optionalString(request, "name", "");
				var data = request.has("dataSnbt") ? PhotonEffectNamedUtil.snbt(request, "dataSnbt") : new CompoundTag();
				if (action.equals("trigger")) {
					runtime.trigger(channel, name, data);
				} else {
					runtime.signal(channel, name, data);
				}
			}
			default -> throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "action");
		}
	}

	private static void projectTest(Handle handle, PhotonNamedRuntime runtime, JsonObject request, String action) {
		if (!Set.of("play", "seek", "stop").contains(action)) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "action");
		}

		allowed(request, action.equals("play") || action.equals("seek") ? Set.of("handleId", "time") : Set.of("handleId"));
		if (action.equals("play")) {
			var existing = runtime.playbacks().stream().filter(runtime::ownsPreview).findFirst().orElse(null);
			if (existing != null || request.has("handleId")) {
				if (existing == null || requireHandle(runtime, request) != existing) {
					throw new PhotonEffectCommandException(Code.HANDLE_EXPIRED);
				}
			}

			if (!(handle.project() instanceof FXProject project)) {
				throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION);
			}
			var source = ((PhotonProjectPreviewAccess) project).lobecorp$projectPreview();
			if (source == null) {
				throw new PhotonEffectCommandException(Code.INVALID_DATA, "projectPreview");
			}

			try {
				runtime.preview(PhotonProjectPreviewUtil.read(source), request.has("time") ? PhotonEffectJsonUtil.number(request, "time") : 0);
			} catch (IOException invalid) {
				throw new PhotonEffectCommandException(Code.INVALID_DATA, invalid.getMessage());
			}
			return;
		}

		var current = requireHandle(runtime, request);
		if (!runtime.ownsPreview(current)) {
			throw new PhotonEffectCommandException(Code.HANDLE_EXPIRED);
		}

		if (action.equals("stop")) {
			runtime.stopPreview();
		} else {
			runtime.seekPreview(PhotonEffectJsonUtil.number(request, "time"));
		}
	}

	private static PhotonNamedHandle requireHandle(PhotonNamedRuntime runtime, JsonObject request) {
		var id = PhotonEffectNamedUtil.uuid(request, "handleId");
		return runtime.playbacks().stream().filter(handle -> handle.id().equals(id) && !handle.disposed()).findFirst()
				.orElseThrow(() -> new PhotonEffectCommandException(Code.HANDLE_EXPIRED));
	}

	public static Map<String, List<Float>> inputs(JsonObject request) {
		if (!request.has("inputs")) {
			return Map.of();
		}

		var result = new LinkedHashMap<String, List<Float>>();
		for (var entry : PhotonEffectJsonUtil.object(request, "inputs").entrySet()) {
			if (!entry.getValue().isJsonArray()) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "inputs." + entry.getKey());
			}

			var values = new ArrayList<Float>();
			for (var value : entry.getValue().getAsJsonArray()) {
				if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Float.isFinite(value.getAsFloat())) {
					throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "inputs." + entry.getKey());
				}

				values.add(value.getAsFloat());
			}
			result.put(entry.getKey(), List.copyOf(values));
		}

		return Map.copyOf(result);
	}

	private static void allowed(JsonObject request, Set<String> fields) {
		var base = Set.of("projectId", "controllerId", "expectedRuntimeId", "target", "action");
		for (var key : request.keySet()) {
			if (!base.contains(key) && !fields.contains(key)) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, key);
			}
		}
	}

	public static JsonObject describe(PhotonNamedRuntime runtime, CompoundTag authored) {
		var result = new JsonObject();
		result.addProperty("runtimeId", runtime.id().toString());
		result.addProperty("available", !runtime.closed());
		result.addProperty("revision", runtime.revision());
		result.addProperty("libraryMatchesAuthored", runtime.library().source().equals(authored));
		var failure = runtime.failure();
		result.add("failure", failure == null ? JsonNull.INSTANCE : new JsonPrimitive(failure));
		var handles = new JsonArray();
		for (var handle : runtime.playbacks()) {
			var entry = new JsonObject();
			entry.addProperty("handleId", handle.id().toString());
			entry.addProperty("animationId", handle.definition().id().toString());
			entry.addProperty("name", handle.definition().name());
			entry.addProperty("state", handle.clock().state().name());
			entry.addProperty("disposed", handle.disposed());
			entry.addProperty("controlled", handle.controlled());
			entry.addProperty("projectTest", runtime.ownsPreview(handle));
			entry.addProperty("duration", handle.clock().duration());
			entry.addProperty("authoredTime", handle.authoredTime(0));
			entry.addProperty("progress", handle.progress(0));
			entry.addProperty("loops", handle.loops(0));
			var parameters = new JsonObject();
			handle.parameters().forEach((id, values) -> {
				var channels = new JsonArray();
				values.forEach(channels::add);
				parameters.add(id.toString(), channels);
			});
			entry.add("parameters", parameters);
			handles.add(entry);
		}

		result.add("handles", handles);
		return result;
	}
}
