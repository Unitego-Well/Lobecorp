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
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedPlan;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// B 命名执行真实契约检查；必须在已初始化 Minecraft/Photon 注册表的客户端 bench 环境执行。
public final class PhotonNamedExecutionContractCheck {

	private final Map<String, JsonObject> results = new LinkedHashMap<>();

	private PhotonNamedExecutionContractCheck() {
	}

	public static JsonObject verify() {
		var check = new PhotonNamedExecutionContractCheck();
		check.run("event_insertion_order", check::eventInsertionOrder);
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
