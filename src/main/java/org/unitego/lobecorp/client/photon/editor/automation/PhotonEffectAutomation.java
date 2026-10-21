package org.unitego.lobecorp.client.photon.editor.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import net.minecraft.client.Minecraft;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import org.unitego.lobecorp.client.photon.editor.PhotonPreviewTimeAccess;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectEditUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectFileUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectJsonUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectSchemaUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectResourceUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectResourceAuditUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectViewUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectNamedUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectNamedControlUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil.Handle;
import xyz.langyo.minecraft.mcp.common.ControlModeHelper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class PhotonEffectAutomation {
	/// 排队和复制校验允许等待的时间；取消状态不能再进入提交。
	private static final long QUEUE_TIMEOUT_SECONDS = 8;
	/// 单次请求上限为 2 MiB，限制客户端线程的解析和快照开销。
	private static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;
	/// 原生粒子定位需要从头模拟，限制一次最多定位到 60 秒（1200 tick）。
	public static final long MAX_SEEK_TICKS = 1200;
	private static final String OPERATION_SCHEMA = """
			{
			  "add_emitter":{"projectKind":"fx","required":["type"],"optional":["name","parameters"],"parameters":"原生对象 data 字段的局部替换；type 从 emitterTypes 选择"},
			  "remove_emitter":{"projectKind":"fx","required":["objectId"]},
			  "set_parameter":{"projectKind":"fx","required":["objectId","path","value"],"path":"相对 parameters 的 JSON Pointer；只允许已有字段，保留原生 NBT 类型"},
			  "set_timeline":{"projectKind":"fx","required":["timeline"],"timeline":"effect_state 返回的原生时间轴 JSON 格式；时间单位 tick"},
			  "set_model_asset":{"projectKind":"model","required":["asset"],"asset":"当前version=1完整JSON资产；保持需要保留的未知字段"},
			  "patch_model_asset":{"projectKind":"model","required":["patch"],"patch":"JSON对象递归合并，列表整体替换；未涉及字段保留"},
			  "edit_model_value":{"projectKind":"model或fx内嵌模型","required":["path","value"],"optional":["objectId"],"path":"已有模型JSON字段的JSON Pointer，例如/vertices/0或/faces/0/uv/2；fx需objectId；身份字段须使用完整引用事务"},
			  "set_model_playback":{"projectKind":"model","required":["values"],"values":"enabled/duration/startDelay/looping/paused/clip/playbackSpeed/animationOffset的局部设置"},
			  "import_model_obj":{"projectKind":"model","required":["text"],"optional":["flipV"],"text":"OBJ文本；保留几何、分组、面角UV和法线，其他能力不作为完整工程备份"},
			  "set_model_renderer":{"projectKind":"model","required":["snbt"],"snbt":"原生renderer复合标签局部合并，保留未涉及字段"},
			  "set_project_data":{"required":["snbt"],"snbt":"完整 snapshotSnbt，仅支持当前原生 FX 工程字段；必须精确往返"}
			}
			""";

	private enum Phase {
		QUEUED, RUNNING, COMMITTING, CANCELLED, DONE
	}

	@FunctionalInterface
	@SuppressWarnings("UnnecessaryModifier")
	private interface Action extends AutoCloseable {
		public JsonObject run() throws Exception;

		@Override
		public default void close() throws Exception {
		}
	}

	private static class Request implements Runnable {
		private final String command;
		private final JsonObject arguments;
		private final boolean writes;
		private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.QUEUED);
		private final CompletableFuture<JsonObject> result = new CompletableFuture<>();

		private Request(String command, JsonObject arguments, boolean writes) {
			this.command = command;
			this.arguments = arguments.deepCopy();
			this.writes = writes;
		}

		private void checkControl() {
			if (writes && !ControlModeHelper.isMcpControlMode()) {
				throw new PhotonEffectCommandException(Code.CONTROL_REQUIRED);
			}
		}

		@Override
		public void run() {
			if (!phase.compareAndSet(Phase.QUEUED, Phase.RUNNING)) {
				return;
			}

			Action action = null;

			try {
				checkControl();
				action = prepare(command, arguments);

				if (!phase.compareAndSet(Phase.RUNNING, Phase.COMMITTING)) {
					return;
				}

				checkControl();
				result.complete(PhotonEffectJsonUtil.success(action.run()));
			} catch (IllegalArgumentException | IndexOutOfBoundsException | IllegalStateException failure) {
				result.complete(PhotonEffectJsonUtil.failure(new PhotonEffectCommandException(Code.INVALID_ARGUMENT, String.valueOf(failure.getMessage()))));
			} catch (Exception failure) {
				result.complete(PhotonEffectJsonUtil.failure(failure));
			} catch (Error failure) {
				result.complete(PhotonEffectJsonUtil.failure(failure));
				throw failure;
			} finally {
				if (action != null) {
					try {
						action.close();
					} catch (Exception cleanupFailure) {
						Lobecorp.LOGGER.error(cleanupFailure.getMessage(), cleanupFailure);
					}
				}

				phase.set(Phase.DONE);
			}
		}

		private boolean cancel() {
			while (true) {
				var current = phase.get();

				if (current != Phase.QUEUED && current != Phase.RUNNING) {
					return false;
				}

				if (phase.compareAndSet(current, Phase.CANCELLED)) {
					result.complete(PhotonEffectJsonUtil.failure(new PhotonEffectCommandException(Code.CLIENT_TIMEOUT)));
					return true;
				}
			}
		}
	}

	public static JsonObject execute(String command, JsonObject arguments, boolean writes) {
		if (arguments.toString().getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
			return PhotonEffectJsonUtil.failure(new PhotonEffectCommandException(Code.INVALID_ARGUMENT));
		}

		var request = new Request(command, arguments, writes);
		var minecraft = Minecraft.getInstance();

		if (minecraft.isSameThread()) {
			request.run();
			return request.result.join();
		}

		minecraft.execute(request);

		try {
			return request.result.get(QUEUE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (TimeoutException failure) {
			if (request.cancel()) {
				return request.result.join();
			}

			// 已进入提交不能伪报取消；等待实际结果，防止调用方超时后出现无声写入。
			return request.result.join();
		} catch (InterruptedException failure) {
			request.cancel();
			Thread.currentThread().interrupt();
			return request.result.join();
		} catch (ExecutionException failure) {
			return PhotonEffectJsonUtil.failure(failure.getCause());
		}
	}

	private static Action prepare(String command, JsonObject request) throws Exception {
		return switch (command) {
			case "effect_capabilities" -> {
				var capabilities = capabilities();
				yield () -> capabilities;
			}
			case "effect_state" -> {
				var state = PhotonEffectProjectUtil.state(request);
				yield () -> state;
			}
			case "effect_create" -> {
				var kind = PhotonEffectJsonUtil.string(request, "kind");
				var name = PhotonEffectJsonUtil.optionalString(request, "name", "");
				yield () -> PhotonEffectProjectUtil.describe(PhotonEffectProjectUtil.create(kind, name));
			}
			case "effect_open" -> {
				var prepared = PhotonEffectProjectUtil.prepareOpen(request);
				yield prepared::commit;
			}
			case "effect_resources" -> {
				var resources = PhotonEffectResourceUtil.list(request);
				yield () -> resources;
			}
			case "effect_resource_read" -> {
				var resource = PhotonEffectResourceUtil.read(request);
				yield () -> resource;
			}
			case "effect_resource_write" -> resourceAction(PhotonEffectResourceUtil.prepareWrite(request));
			case "effect_texture_import" -> resourceAction(PhotonEffectResourceUtil.prepareTexture(request));
			case "effect_view_state" -> {
				var view = PhotonEffectViewUtil.describe(handle(request));
				yield () -> view;
			}
			case "effect_view" -> {
				var prepared = PhotonEffectViewUtil.prepare(handle(request), request);
				yield prepared::commit;
			}
			case "effect_edit_batch" -> {
				var prepared = PhotonEffectEditUtil.prepare(request);
				yield prepared::commit;
			}
			case "effect_preview" -> preview(handle(request), request);
			case "effect_resource_audit" -> {
				var report = PhotonEffectResourceAuditUtil.audit(request);
				yield () -> report;
			}
			case "effect_animation_state" -> {
				var state = PhotonEffectNamedUtil.state(handle(request), request.has("controllerId") ? PhotonEffectNamedUtil.uuid(request, "controllerId") : null);
				yield () -> state;
			}
			case "effect_animation_edit" -> {
				var prepared = PhotonEffectNamedUtil.prepare(handle(request), request);
				yield new Action() {
					@Override
					public JsonObject run() {
						return prepared.commit();
					}

					@Override
					public void close() {
						prepared.close();
					}
				};
			}
			case "effect_animation_control" -> () -> PhotonEffectNamedControlUtil.control(handle(request), request);
			case "effect_save" -> fileAction(PhotonEffectFileUtil.prepareSave(handle(request), request));
			case "effect_export" -> fileAction(PhotonEffectFileUtil.prepareExport(handle(request), request));
			case "effect_history" -> history(handle(request), request);
			default -> throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION, command);
		};
	}

	private static Action fileAction(PhotonEffectFileUtil.Prepared prepared) {
		return new Action() {
			@Override
			public JsonObject run() throws Exception {
				return prepared.commit();
			}

			@Override
			public void close() throws Exception {
				prepared.close();
			}
		};
	}

	private static Action resourceAction(PhotonEffectResourceUtil.Prepared prepared) {
		return new Action() {
			@Override
			public JsonObject run() throws Exception {
				return prepared.commit();
			}

			@Override
			public void close() throws Exception {
				prepared.close();
			}
		};
	}

	private static Handle handle(JsonObject request) {
		return PhotonEffectProjectUtil.require(PhotonEffectJsonUtil.string(request, "projectId"));
	}

	private static Action history(Handle handle, JsonObject request) {
		var action = PhotonEffectJsonUtil.string(request, "action");

		if (!"undo".equals(action) && !"redo".equals(action)) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, action);
		}

		return () -> {
			if ("undo".equals(action)) {
				handle.editor().historyView.undo();
			} else {
				handle.editor().historyView.redo();
			}

			return PhotonEffectProjectUtil.describe(handle);
		};
	}

	private static Action preview(Handle handle, JsonObject request) {
		var action = PhotonEffectJsonUtil.string(request, "action");
		double time = "seek".equals(action) ? PhotonEffectJsonUtil.number(request, "time") : 0;

		if (!Double.isFinite(time) || time < 0 || time > MAX_SEEK_TICKS || !"play".equals(action) && !"pause".equals(action)
				&& !"stop".equals(action) && !"seek".equals(action)) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, action);
		}

		return () -> {
			var editor = handle.editor();

			switch (action) {
				case "play" -> editor.sceneView.particleManager.play();
				case "pause" -> editor.sceneView.particleManager.pause();
				case "stop" -> editor.timelineView.stop();
				case "seek" -> {
					var view = PhotonEffectViewUtil.prepare(handle, new JsonObject());
					editor.sceneView.particleManager.pause();
					editor.sceneView.simulateTo((long) time);
					((PhotonPreviewTimeAccess) editor.sceneView.particleManager).lobecorp$seekFraction((float) (time - (long) time));
					view.commit();
				}
				default -> throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
			}

			return PhotonEffectProjectUtil.describe(handle);
		};
	}

	private static JsonObject capabilities() {
		var result = new JsonObject();
		result.addProperty("protocolVersion", 1);
		result.add("operations", JsonParser.parseString(OPERATION_SCHEMA));
		for (var key : List.of("set_model_asset", "patch_model_asset", "edit_model_value", "set_model_playback", "set_model_renderer", "import_model_obj")) {
			var operation = result.getAsJsonObject("operations").getAsJsonObject(key);
			operation.addProperty("projectKind", "model_or_fx");
			operation.addProperty("objectIdRequiredFor", "fx");
		}
		result.add("animationOperations", PhotonEffectNamedUtil.operations());
		result.addProperty("animationProtocolVersion", 1);
		result.addProperty("namedAuthoringAvailable", true);
		result.addProperty("namedRuntimeAvailable", true);
		result.addProperty("modelAuthoringAvailable", true);
		result.addProperty("modelFileAcceptancePending", true);
		result.addProperty("namedSeekSemantics", "authored_cursor_without_particle_resimulation");
		result.add("namedWritableTrackTypes", JsonParser.parseString("[\"activator\",\"control\",\"animation\",\"group\"]"));
		var parameterTypes = new JsonArray();
		for (var parameter : PhotonNamedLibrary.ParameterType.values()) {
			var type = new JsonObject();
			type.addProperty("name", parameter.name());
			type.addProperty("channels", parameter.channels());
			parameterTypes.add(type);
		}
		result.add("animationParameterTypes", parameterTypes);
		result.addProperty("timeUnit", "tick");
		result.addProperty("editWorkflow", "effect_preview pause → effect_state → effect_edit_batch；原生 FX 时间轴播放会更新持久变换，播放中 revision 可变化");
		result.addProperty("maxSeekTicks", MAX_SEEK_TICKS);
		result.addProperty("maxOperations", PhotonEffectEditUtil.MAX_OPERATIONS);
		result.addProperty("maxRequestBytes", MAX_REQUEST_BYTES);
		result.addProperty("queueTimeoutSeconds", QUEUE_TIMEOUT_SECONDS);
		var roots = new JsonArray();
		PhotonEffectFileUtil.roots().forEach(root -> roots.add(root.toString()));
		result.add("allowedRoots", roots);
		var types = new JsonArray();

		for (var entry : PhotonRegistries.FX_OBJECTS.entries()) {
			var type = new JsonObject();
			type.addProperty("type", entry.getKey());
			var object = entry.getValue().create();
			var data = object.serializeWrapper();
			type.addProperty("writable", data != null && !PhotonEditorProjectGuardUtil.containsTag(data));

			if (data != null) {
				var metadata = PhotonEffectSchemaUtil.describe(object.getClass(), data.getCompoundOrEmpty("data"));
				metadata.addProperty("valueRole", "default");
				type.add("configurationMetadata", metadata);
				type.add("defaultParameters", PhotonEffectJsonUtil.json(data.getCompoundOrEmpty("data")));
				type.add("parameterSchema", PhotonEffectJsonUtil.schema(data.getCompoundOrEmpty("data")));
			}

			var bindings = new JsonArray();

			for (var binding : entry.getValue().runtimeBindings()) {
				var field = new JsonObject();
				field.addProperty("path", binding.path);
				field.addProperty("labelKey", binding.labelKey);
				field.addProperty("type", binding.type.name());
				bindings.add(field);
			}

			type.add("runtimeBindings", bindings);
			types.add(type);
		}

		result.add("emitterTypes", types);
		result.addProperty("customRuntimeAvailable", false);
		result.addProperty("modelProjectsAvailable", false);
		var tracks = new JsonArray();
		PhotonRegistries.TIMELINE_TRACKS.keys().forEach(tracks::add);
		result.add("timelineTypes", tracks);
		var templates = new JsonArray();

		for (var type : PhotonRegistries.TIMELINE_TRACKS.values()) {
			var track = type.create();


			templates.add(PhotonEffectJsonUtil.json(Timeline.writeTrack(track, Platform.getFrozenRegistry())));
		}

		result.add("timelineTemplates", templates);
		return result;
	}
}
