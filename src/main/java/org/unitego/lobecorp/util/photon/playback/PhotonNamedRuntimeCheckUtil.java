package org.unitego.lobecorp.util.photon.playback;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.property.PositionPropertyType;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.registry.photon.client.LcPhotonPlaybackTypes;
import org.unitego.lobecorp.client.photon.playback.PhotonChildWindows;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonCallTrack;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildClip;
import com.lowdragmc.photon.gui.editor.FXProjectEffectExecutor;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 客户端原生注册环境中的内存检查；不读取为可播放原件、不解除文件门禁。
public class PhotonNamedRuntimeCheckUtil {
	public record Result(boolean passed, String detail) {
	}

	public static Result inspectFixture() {
		PhotonNamedRuntime playback = null;

		try {
			var target = new EmptyFXObject();
			target.transform().localPosition(new Vector3f(1, 0, 0));
			var library = new CompoundTag();
			library.putInt("version", 6);
			library.putString("defaultAnimation", "base");
			var animations = new ListTag();
			animations.add(definition(target, "base", "ADD", 5, 0));
			animations.add(definition(target, "overlay", "MULTIPLY", 2, 1));
			library.put("animations", animations);
			var rule = new CompoundTag();
			rule.putString("event", "TRIGGER");
			rule.putString("channel", "fixture");
			rule.putString("name", "pulse");
			rule.putString("target", "base");
			rule.putDouble("duration", -1);
			var input = new CompoundTag();
			input.putString("target", "amplitude");
			input.putString("source", "");
			input.putBoolean("follow", false);
			var values = new ListTag();
			values.add(FloatTag.valueOf(9));
			input.put("value", values);
			var inputs = new ListTag();
			inputs.add(input);
			rule.put("inputs", inputs);
			var rules = new ListTag();
			rules.add(rule);
			library.put("rules", rules);
			var controller = new PhotonNamedController();
			controller.definition(library);
			var codec = LcPhotonPlaybackTypes.CONTROLLER.codec();
			var tag = codec.encodeStart(NbtOps.INSTANCE, controller).getOrThrow();
			var copied = (PhotonNamedController) codec.parse(NbtOps.INSTANCE, tag).getOrThrow();

			if (!library.equals(copied.definition()) || copied.definition() == controller.definition()) {
				throw new IllegalStateException("命名控制器原生编码或实例隔离失败。");
			}

			copied.definition().putString("defaultAnimation", "changed");

			if (!library.equals(controller.definition())) {
				throw new IllegalStateException("命名库可变出口污染原定义。");
			}

			var runtime = new FXRuntime(new FXData(new ArrayList<>(List.of(target, controller)), new Timeline()));
			var root = (FXObject) runtime.root;
			var previousTick = root.getOnUpdateTick();
			var previousFrame = root.getOnUpdateFrame();
			var previousRetention = target.getKeepAlive();
			playback = new PhotonNamedRuntime(runtime, controller);
			playback.begin();
			requireX(target, 6);
			var overlay = playback.play("overlay", -1, true, Map.of());
			root.getOnUpdateFrame().accept(0f);
			requireX(target, 12);
			root.getOnUpdateFrame().accept(0f);
			requireX(target, 12);
			playback.stop(overlay);
			root.getOnUpdateFrame().accept(0f);
			requireX(target, 6);
			playback.playback("base").parameters(Map.of("amplitude", List.of(7f)));
			root.getOnUpdateFrame().accept(0f);
			requireX(target, 8);
			playback.trigger("fixture", "pulse", new CompoundTag());
			root.getOnUpdateFrame().accept(0f);
			requireX(target, 10);

			for (int tick = 0; tick < 25; tick++) {
				root.getOnUpdateTick().run();
				root.getOnUpdateFrame().accept(0f);
			}

			requireX(target, 10);

			if (playback.failure() != null) {
				throw new IllegalStateException(playback.failure());
			}

			playback.close();
			requireX(target, 1);

			if (root.getOnUpdateTick() != previousTick || root.getOnUpdateFrame() != previousFrame
					|| target.getKeepAlive() != previousRetention) {
				throw new IllegalStateException("命名播放关闭后原生回调或保留条件未恢复。");
			}

			return new Result(true, "原生控制器编码、基础值恢复、优先级混合、重复帧不累加、形参、触发规则、HOLD与关闭恢复通过；未代替完整资源验收");
		} catch (Exception failure) {
			return new Result(false, failure.toString());
		} finally {
			if (playback != null) {
				playback.close();
			}
		}
	}

	public static Result inspectProjectPreviewFixture() {
		PhotonNamedRuntime playback = null;

		try {
			var target = new EmptyFXObject();
			target.transform().localPosition(new Vector3f(1, 0, 0));
			var animation = definition(target, "base", "ADD", 5, 0);
			var animations = new ListTag();
			animations.add(animation);
			var library = new CompoundTag();
			library.putInt("version", 6);
			library.put("animations", animations);
			var controller = new PhotonNamedController();
			controller.definition(library);
			var runtime = new FXRuntime(new FXData(new ArrayList<>(List.of(target, controller)), new Timeline()));
			playback = new PhotonNamedRuntime(runtime, controller);
			var track = (PhotonCallTrack) LcPhotonPlaybackTypes.CALL.create();
			var clip = new PhotonChildClip(0, 10, 1);
			clip.animation = "base";
			clip.completion = PhotonChildClip.Completion.HOLD;
			track.clips().add(clip);
			var preview = definition(target, "preview", "ADD", 0, 0);
			preview.remove("bindings");
			var tracks = new ListTag();
			tracks.add(Timeline.writeTrack(track, Platform.getFrozenRegistry()));
			preview.put("tracks", tracks);
			var definition = PhotonProjectPreviewUtil.read(preview);
			var host = playback;
			requireRejected(() -> host.preview(definition, 0));
			((FXObject) runtime.root).setEffect(new FXProjectEffectExecutor(new TrackedDummyWorld()));
			requireRejected(() -> {
				try (var windows = new PhotonChildWindows(runtime, null)) {
					windows.evaluate(List.of(track), 0);
				}
			});
			playback.preview(definition, 5);
			requireX(target, 6);
			requireProgress(playback, 0.5);
			playback.seekPreview(8);
			requireProgress(playback, 0.8);
			playback.seekPreview(0);
			requireProgress(playback, 0);
			playback.stopPreview();
			requireX(target, 1);

			if (playback.playback("base") != null) {
				throw new IllegalStateException("工程测试停止后受控句柄未释放。");
			}

			clip.mode = PhotonChildClip.Mode.INDEPENDENT;
			clip.playbackDuration = 8;
			tracks.set(0, Timeline.writeTrack(track, Platform.getFrozenRegistry()));
			preview.put("tracks", tracks);
			playback.preview(PhotonProjectPreviewUtil.read(preview), 0);
			var independent = playback.playback("base");
			playback.stopPreview();

			if (independent == null || playback.playback("base") != independent || independent.clock().duration() != 8) {
				throw new IllegalStateException("独立工程调用没有保留自身时钟或句柄。");
			}

			playback.stop(independent);
			clip.mode = PhotonChildClip.Mode.CONTROLLED;
			clip.completion = PhotonChildClip.Completion.RELEASE;
			tracks.set(0, Timeline.writeTrack(track, Platform.getFrozenRegistry()));
			preview.put("tracks", tracks);
			playback.preview(PhotonProjectPreviewUtil.read(preview), 0);
			playback.seekPreview(10);
			requireX(target, 1);
			playback.close();
			return new Result(true, "工程测试调用的受控映射、回拖、停止、独立句柄、RELEASE及世界/普通场景拒绝通过；未替代工程GUI验收");
		} catch (Exception failure) {
			return new Result(false, failure.toString());
		} finally {
			if (playback != null) {
				playback.close();
			}
		}
	}

	private static void requireRejected(Runnable operation) {
		try {
			operation.run();
		} catch (IllegalArgumentException expected) {
			return;
		}

		throw new IllegalStateException("无效的工程调用来源未被拒绝。");
	}

	private static void requireProgress(PhotonNamedRuntime playback, double expected) {
		var handle = playback.playback("base");

		if (handle == null || Math.abs(handle.progress(0) - expected) > 0.00001) {
			throw new IllegalStateException("受控工程调用的归一化进度不符。");
		}
	}

	private static CompoundTag definition(EmptyFXObject target, String name, String blend, float amplitude, int priority) {
		var source = new CompoundTag();
		source.putString("id", UUID.randomUUID().toString());
		source.putString("name", name);
		source.putDouble("duration", 20);
		source.putBoolean("autoDuration", false);
		source.putString("completion", "HOLD");
		source.putInt("priority", priority);
		var parameter = new CompoundTag();
		parameter.putString("id", UUID.randomUUID().toString());
		parameter.putString("name", "amplitude");
		parameter.putString("type", "NUMBER");
		var defaults = new ListTag();
		defaults.add(FloatTag.valueOf(amplitude));
		parameter.put("defaults", defaults);
		var parameters = new ListTag();
		parameters.add(parameter);
		source.put("parameters", parameters);
		var track = (AnimationTrack) PhotonRegistries.TIMELINE_TRACKS.get("animation").create();
		track.targetId(target.id());
		track.properties().add(PositionPropertyType.INSTANCE.create(target));
		var tracks = new ListTag();
		tracks.add(Timeline.writeTrack(track, Platform.getFrozenRegistry()));
		source.put("tracks", tracks);
		var binding = new CompoundTag();
		binding.putString("target", target.id().toString());
		binding.putString("property", PositionPropertyType.INSTANCE.key());
		binding.putString("blend", blend);
		binding.putString("source", "PARAMETER");
		binding.putString("parameter", "amplitude");
		binding.putInt("component", 0);
		var bindings = new ListTag();
		bindings.add(binding);
		source.put("bindings", bindings);
		return source;
	}

	private static void requireX(FXObject object, float expected) {
		float actual = object.transform().localPosition().x;

		if (Math.abs(actual - expected) > 0.0001f) {
			throw new IllegalStateException("命名混合坐标不符：" + actual + " != " + expected);
		}
	}
}
