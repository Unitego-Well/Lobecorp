package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.Signal;
import com.lowdragmc.photon.client.fx.timeline.SignalTrack;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.property.PositionPropertyType;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.gui.editor.FXProjectEffectExecutor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelClip;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelClipTrack;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedPlan;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Event;
import org.unitego.lobecorp.registry.client.photon.LcPhotonModelOutputTypes;
import org.unitego.lobecorp.util.photon.model.PhotonModelClipUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// B 命名执行真实契约检查；必须在已初始化 Minecraft/Photon 注册表的客户端 bench 环境执行。
public final class PhotonNamedExecutionContractCheck {
	private static final String MODEL_JSON = """
			{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
			"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
			"parts":[{"id":"main"}],
			"clips":[{"id":"grow","duration":10,"loop":true,
			"partTracks":{"main":[{"time":0,"transform":{}},{"time":10,"transform":{"translation":[0,2,0]}}]}}]}
			""";

	private final Map<String, JsonObject> results = new LinkedHashMap<>();

	private PhotonNamedExecutionContractCheck() {
	}

	public static JsonObject verify() {
		var check = new PhotonNamedExecutionContractCheck();
		check.run("event_insertion_order", check::eventInsertionOrder);
		check.run("uuid_replacement_owner_cleanup", check::uuidReplacementOwnerCleanup);
		check.run("timeline_begin_tick_frame_stop_mutable", check::timelineBeginTickFrameStopMutable);
		var report = new JsonObject();
		report.addProperty("schema", "photon-named-execution-contract-v1");
		report.addProperty("passed", check.results.values().stream().allMatch(value -> value.get("passed").getAsBoolean()));
		var cases = new JsonArray();
		check.results.values().forEach(cases::add);
		report.add("cases", cases);
		return report;
	}

	private void eventInsertionOrder() throws Exception {
		PhotonNamedRuntime playback = null;
		FXRuntime runtime = null;
		JsonObject actual = new JsonObject();

		try {
			var target = new EmptyFXObject();
			var low = namedDefinition(target, "low", 1, 1f, "low-signal", 1);
			var high = namedDefinition(target, "high", 9, 9f, "high-signal", 1);
			var library = new CompoundTag();
			library.putInt("version", 6);
			var animations = new ListTag();
			animations.add(low);
			animations.add(high);
			library.put("animations", animations);
			var controller = new PhotonNamedController();
			controller.definition(library);
			runtime = new FXRuntime(new FXData(new ArrayList<>(List.of(target, controller)), new Timeline()));
			playback = new PhotonNamedRuntime(runtime, controller);
			var notifications = new ArrayList<String>();
			playback.listener(notification -> {
				if (notification.event() == Event.SIGNAL) {
					notifications.add(notification.name());
				}
			});
			playback.play("high", -1, true, Map.of());
			playback.play("low", -1, true, Map.of());
			var root = (FXObject) runtime.root;

			for (int tick = 0; tick < 64 && notifications.size() < 2; tick++) {
				root.getOnUpdateTick().run();
				root.getOnUpdateFrame().accept(0f);
			}

			var expected = List.of("high-signal", "low-signal");
			require(notifications.equals(expected), "信号顺序必须保持插入顺序，实际=" + notifications);
			require(Math.abs(target.transform().localPosition().x - 9f) < 0.0001f,
					"高优先级属性必须获胜，实际=" + target.transform().localPosition());
			require(playback.failure() == null, "命名事件运行失败：" + playback.failure());
			actual.addProperty("actualOrder", String.join(",", notifications));
			actual.addProperty("expectedOrder", "high-signal,low-signal");
			actual.addProperty("insertionOrder", "high-signal,low-signal");
			actual.addProperty("lowPriority", 1);
			actual.addProperty("highPriority", 9);
			actual.addProperty("winningPositionX", target.transform().localPosition().x);
			actual.addProperty("expectedWinningX", 9f);
		} finally {
			var cleanup = new JsonObject();
			var failures = new JsonArray();
			if (playback != null) {
				cleanup(playback, failures);
			}
			cleanup(runtime, failures);
			cleanup.addProperty("completed", failures.isEmpty());
			cleanup.add("failures", failures);
			actual.add("cleanup", cleanup);
			actual.addProperty("cleanupCompleted", failures.isEmpty());
		}
		pass("event_insertion_order", "高优先级先插入、低优先级后插入时，事件保持插入顺序，且高优先级属性值获胜", actual);
	}

	private void uuidReplacementOwnerCleanup() throws Exception {
		PhotonModelOutput oldModel = null;
		PhotonModelOutput newModel = null;
		FXRuntime runtime = null;
		JsonObject actual = new JsonObject();

		try {
			oldModel = model();
			newModel = model();
			var targetId = oldModel.id();
			newModel.transform()._setInternalID(targetId);
			var track = modelTrack(targetId, "grow");
			var target = new PhotonNamedPlan.ModelClipTarget(track, targetId);
			var state = new PhotonModelClipUtil.ContributionState();
			var otherState = new PhotonModelClipUtil.ContributionState();
			var owner = new Object();
			var otherOwner = new Object();
			runtime = new FXRuntime(new FXData(new ArrayList<>(List.of(oldModel)), new Timeline()));
			otherState.apply(otherOwner, runtime, List.of(target), 3, 1, 0);
			var oldOtherPoint = Objects.requireNonNull(oldModel.sampleLocal(0)).groups().getFirst().vertices().getFirst().position();
			require(oldOtherPoint.y() > 0, "旧实例 otherOwner 独立贡献必须非零，实际=" + oldOtherPoint);
			state.apply(owner, runtime, List.of(target), 5, 2, 0);
			var oldOwnerPoint = Objects.requireNonNull(oldModel.sampleLocal(0)).groups().getFirst().vertices().getFirst().position();
			require(oldOwnerPoint.y() > 0, "旧实例 owner 叠加后必须非零，实际=" + oldOwnerPoint);
			require(!oldOwnerPoint.equals(oldOtherPoint), "owner 高优先级必须覆盖 otherOwner，实际=" + oldOwnerPoint);
			runtime.objects.put(targetId, newModel);
			state.apply(owner, runtime, List.of(target), 5, 2, 0);
			var newPoint = Objects.requireNonNull(newModel.sampleLocal(0)).groups().getFirst().vertices().getFirst().position();
			var oldAfterOwnerClear = Objects.requireNonNull(oldModel.sampleLocal(0)).groups().getFirst().vertices().getFirst().position();
			require(oldAfterOwnerClear.equals(oldOtherPoint), "旧实例应只保留 otherOwner 的独立姿态，实际=" + oldAfterOwnerClear);
			require(newPoint.y() > 0, "新实例必须获得当前 owner 贡献，实际=" + newPoint);
			otherState.apply(otherOwner, runtime, List.of(target), 3, 1, 0);
			var newOther = Objects.requireNonNull(newModel.sampleLocal(0)).groups().getFirst().vertices().getFirst().position();
			require(newOther.equals(oldOwnerPoint), "新实例 owner 必须继续按 priority 获胜，实际=" + newOther);
			state.clear(owner);
			var newAfterOwnerClear = Objects.requireNonNull(newModel.sampleLocal(0)).groups().getFirst().vertices().getFirst().position();
			require(newAfterOwnerClear.equals(oldOtherPoint), "清理被测 owner 后新实例应精确保留 otherOwner 姿态，实际=" + newAfterOwnerClear);
			otherState.clear(otherOwner);
			var allCleared = Objects.requireNonNull(newModel.sampleLocal(0)).groups().getFirst().vertices().getFirst().position();
			require(allCleared.y() == 0, "全部 owner 清理后新实例必须归零，实际=" + allCleared);
			actual.addProperty("targetId", targetId.toString());
			actual.addProperty("oldOwnerBefore", oldOwnerPoint.toString());
			actual.addProperty("oldOtherBefore", oldOtherPoint.toString());
			actual.addProperty("oldAfterOwnerClear", oldAfterOwnerClear.toString());
			actual.addProperty("newApplied", newPoint.toString());
			actual.addProperty("newOtherApplied", newOther.toString());
			actual.addProperty("newAfterOwnerClear", newAfterOwnerClear.toString());
			actual.addProperty("allCleared", allCleared.toString());
		} finally {
			var cleanup = new JsonObject();
			var failures = new JsonArray();
			cleanup(runtime, failures);
			cleanup(oldModel, failures);
			cleanup(newModel, failures);
			cleanup.addProperty("completed", failures.isEmpty());
			cleanup.add("failures", failures);
			actual.add("cleanup", cleanup);
			actual.addProperty("cleanupCompleted", failures.isEmpty());
		}
		pass("uuid_replacement_owner_cleanup", "同 UUID 替换后旧实例 owner 贡献清除，新实例接收当前贡献；其他 owner 保留后独立清理", actual);
	}

	private void timelineBeginTickFrameStopMutable() throws Exception {
		PhotonModelOutput model = null;
		FXRuntime runtime = null;
		JsonObject actual = new JsonObject();

		try {
			model = model();
			var timeline = new Timeline();
			var track = modelTrack(model.id(), "grow");
			timeline.tracks().add(track);
			runtime = new FXRuntime(new FXData(new ArrayList<>(List.of(model)), timeline));
			var player = runtime.timelinePlayer;
			player.setAudioDispatch(false);
			player.setSignalDispatch(false);
			player.begin(new FXProjectEffectExecutor(new TrackedDummyWorld()));
			player.tick(5);
			player.tick(0);
			player.frame(0);
			var first = Objects.requireNonNull(model.sampleLocal(0)).groups().getFirst().vertices().getFirst().position();
			track.mute(true);
			player.tick(0);
			player.frame(0);
			var muted = Objects.requireNonNull(model.sampleLocal(0)).groups().getFirst().vertices().getFirst().position();
			track.mute(false);
			player.tick(0);
			player.frame(0);
			var restored = Objects.requireNonNull(model.sampleLocal(0)).groups().getFirst().vertices().getFirst().position();
			player.stop();
			var stopped = Objects.requireNonNull(model.sampleLocal(0)).groups().getFirst().vertices().getFirst().position();
			require(first.y() > 0, "主 Timeline 首帧必须产生模型姿态，实际=" + first);
			require(muted.y() == 0, "静音可变轨道必须移除贡献，实际=" + muted);
			require(restored.y() > 0, "恢复轨道后必须重新产生姿态，实际=" + restored);
			require(stopped.y() == 0, "stop 后必须清除贡献，实际=" + stopped);
			actual.addProperty("first", first.toString());
			actual.addProperty("muted", muted.toString());
			actual.addProperty("restored", restored.toString());
			actual.addProperty("stopped", stopped.toString());
		} finally {
			var cleanup = new JsonObject();
			var failures = new JsonArray();
			cleanup(runtime, failures);
			cleanup(model, failures);
			cleanup.addProperty("completed", failures.isEmpty());
			cleanup.add("failures", failures);
			actual.add("cleanup", cleanup);
			actual.addProperty("cleanupCompleted", failures.isEmpty());
		}
		pass("timeline_begin_tick_frame_stop_mutable", "主 Timeline begin/tick/frame/stop 与可变静音轨道一致，贡献清理可复验", actual);
	}

	private void run(String name, Body body) {
		try {
			body.run();
		} catch (Throwable failure) {
			var result = new JsonObject();
			result.addProperty("name", name);
			result.addProperty("passed", false);
			result.addProperty("failure", failure.toString());
			var stack = new JsonArray();
			for (var frame : failure.getStackTrace()) {
				stack.add(frame.toString());
			}
			result.add("stack", stack);
			results.put(name, result);
		}
	}

	private void pass(String name, String detail, JsonObject actual) {
		var result = new JsonObject();
		result.addProperty("name", name);
		result.addProperty("passed", !actual.has("cleanupCompleted") || actual.get("cleanupCompleted").getAsBoolean());
		result.addProperty("detail", detail);
		result.add("actual", actual);
		results.put(name, result);
	}

	private static void cleanup(PhotonNamedRuntime playback, JsonArray failures) {
		try {
			playback.close();
		} catch (Throwable failure) {
			failures.add("playback.close: " + failure);
		}
	}

	private static void cleanup(FXRuntime runtime, JsonArray failures) {
		if (runtime == null) {
			return;
		}
		try {
			runtime.destroy(true);
		} catch (Throwable failure) {
			failures.add("runtime.destroy: " + failure);
		}
	}

	private static void cleanup(PhotonModelOutput model, JsonArray failures) {
		if (model == null) {
			return;
		}
		try {
			model.remove(true);
		} catch (Throwable failure) {
			failures.add("model.remove: " + failure);
		}
	}

	private static PhotonModelOutput model() throws Exception {
		var model = new PhotonModelOutput();
		model.config.setAssetSnapshot(StringTag.valueOf(MODEL_JSON));
		model.prepare();
		model.reset();
		model.measure(true);
		return model;
	}

	private static PhotonModelClipTrack modelTrack(UUID targetId, String animation) {
		var track = (PhotonModelClipTrack) LcPhotonModelOutputTypes.MODEL_ANIMATION.create();
		track.targetId(targetId);
		var clip = new PhotonModelClip(0, 10, 1);
		clip.animation = animation;
		clip.loop = true;
		clip.completion = PhotonModelClip.Completion.HOLD;
		track.clips().add(clip);
		return track;
	}

	private static CompoundTag namedDefinition(EmptyFXObject target, String name, int priority, float positionX, String signal, double time) {
		var source = new CompoundTag();
		source.putString("id", UUID.randomUUID().toString());
		source.putString("name", name);
		source.putDouble("duration", 20);
		source.putBoolean("autoDuration", false);
		source.putString("completion", "HOLD");
		source.putInt("priority", priority);
		var track = (AnimationTrack) PhotonRegistries.TIMELINE_TRACKS.get("animation").create();
		track.targetId(target.id());
		var property = PositionPropertyType.INSTANCE.create(target);
		property.putKey(0, 0, positionX);
		property.putKey(1, 0, 0);
		property.putKey(2, 0, 0);
		track.properties().add(property);
		var tracks = new ListTag();
		tracks.add(Timeline.writeTrack(track, Platform.getFrozenRegistry()));
		var signalTrack = (SignalTrack) PhotonRegistries.TIMELINE_TRACKS.get("signal").create();
		signalTrack.displayName(signal);
		signalTrack.signals().add(new Signal(time, signal));
		tracks.add(Timeline.writeTrack(signalTrack, Platform.getFrozenRegistry()));
		source.put("tracks", tracks);
		return source;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private interface Body {
		void run() throws Exception;
	}

}
