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
