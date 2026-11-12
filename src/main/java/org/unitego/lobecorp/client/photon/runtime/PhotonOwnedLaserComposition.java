package org.unitego.lobecorp.client.photon.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/// 组合阶段以技能快照推进；延时单位为游戏 tick，局部动画不控制实例存在。
public class PhotonOwnedLaserComposition {
	/// 原激光局部进入与结束动画均为20 tick，缩放触发沿用同一作者相位标尺。
	public static final double AUTHORED_PHASE_TICKS = 20;

	public static CompoundTag serialize(Definition definition) {
		var tag = new CompoundTag();
		tag.putInt("version", definition.version());
		var cues = new ListTag();
		for (var cue : definition.cues()) {
			var item = new CompoundTag();
			item.putString("component", cue.component());
			item.putString("phase", cue.phase().name());
			item.putDouble("delay_ticks", cue.delayTicks());
			item.putString("animation", cue.animation());
			item.putString("binding", cue.binding().name());
			item.putString("stop_policy", cue.stopPolicy().name());
			item.putBoolean("scale_to_phase", cue.scaleToPhase());
			cues.add(item);
		}
		tag.put("cues", cues);
		var poses = new CompoundTag();
		definition.poses().forEach((name, pose) -> {
			var item = new CompoundTag();
			item.putFloat("x", pose.offset().x);
			item.putFloat("y", pose.offset().y);
			item.putFloat("z", pose.offset().z);
			item.putFloat("qx", pose.rotation().x);
			item.putFloat("qy", pose.rotation().y);
			item.putFloat("qz", pose.rotation().z);
			item.putFloat("qw", pose.rotation().w);
			var inputs = new CompoundTag();
			pose.inputs().forEach((key, values) -> {
				var array = new ListTag();
				values.forEach(value -> array.add(FloatTag.valueOf(value)));
				inputs.put(key, array);
			});
			item.put("inputs", inputs);
			poses.put(name, item);
		});
		tag.put("poses", poses);
		return tag;
	}

	public static Definition deserialize(CompoundTag tag) {
		var cues = new ArrayList<Cue>();
		for (var value : tag.getListOrEmpty("cues")) {
			var item = (CompoundTag) value;
			cues.add(new Cue(item.getStringOr("component", ""), Phase.valueOf(item.getStringOr("phase", "")),
					item.getDoubleOr("delay_ticks", 0), item.getStringOr("animation", ""),
					Binding.valueOf(item.getStringOr("binding", "")), StopPolicy.valueOf(item.getStringOr("stop_policy", "")), item.getBooleanOr("scale_to_phase", false)));
		}
		var poses = new LinkedHashMap<String, Pose>();
		var poseTag = tag.getCompoundOrEmpty("poses");
		for (var name : poseTag.keySet()) {
			var item = poseTag.getCompoundOrEmpty(name);
			var inputs = new LinkedHashMap<String, List<Float>>();
			var inputTag = item.getCompoundOrEmpty("inputs");
			for (var key : inputTag.keySet()) {
				var values = new ArrayList<Float>();
				for (var value : inputTag.getListOrEmpty(key)) {
					values.add(value.asFloat().orElseThrow());
				}
				inputs.put(key, values);
			}
			poses.put(name, new Pose(new Vector3f(item.getFloatOr("x", 0), item.getFloatOr("y", 0), item.getFloatOr("z", 0)),
					new Quaternionf(item.getFloatOr("qx", 0), item.getFloatOr("qy", 0), item.getFloatOr("qz", 0), item.getFloatOr("qw", 1)), inputs));
		}
		return new Definition(tag.getIntOr("version", 0), cues, poses);
	}

	public static Definition defaults() {
		var cues = new ArrayList<Cue>();
		for (var name : List.of("circle", "circle2", "circle3", "light_ball", "accumulate_particle")) {
			cues.add(new Cue(name, Phase.CHARGE, 0, "charge_start", Binding.ROOT, StopPolicy.NATURAL));
			cues.add(new Cue(name, Phase.RECOVERY, 0, "charge_end", Binding.ROOT, StopPolicy.NATURAL));
		}
		cues.add(new Cue("light_ball", Phase.OUTPUT, 0, "laser_start", Binding.ROOT, StopPolicy.NATURAL));
		for (var name : PhotonOwnedLaser.BEAM_COMPONENTS) {
			cues.add(new Cue(name, Phase.OUTPUT, 0, "laser_start", Binding.BEAM, StopPolicy.NATURAL));
			cues.add(new Cue(name, Phase.RECOVERY, 0, "laser_end", Binding.BEAM, StopPolicy.NATURAL));
		}
		cues.add(new Cue("launch_spikes", Phase.OUTPUT, 10, "", Binding.BEAM, StopPolicy.IMMEDIATE, true));
		for (var name : PhotonOwnedLaser.BEAM_COMPONENTS) {
			if (name.equals("launch_spikes")) {
				cues.add(new Cue(name, Phase.RECOVERY, 0, "", Binding.BEAM, StopPolicy.IMMEDIATE));
			} else {
				if (!List.of("laser_body", "dust_column", "source_dust").contains(name)) {
					cues.add(new Cue(name, Phase.RECOVERY, 0, "", Binding.BEAM, StopPolicy.NATURAL));
				}
				cues.add(new Cue(name, Phase.RECOVERY, 20, "", Binding.BEAM, StopPolicy.IMMEDIATE, true));
			}
		}
		for (var name : List.of("circle", "circle2", "circle3", "light_ball", "accumulate_particle")) {
			cues.add(new Cue(name, Phase.RECOVERY, 20, "", Binding.ROOT, StopPolicy.IMMEDIATE, true));
		}
		return new Definition(1, cues);
	}

	public enum Phase {CHARGE, OUTPUT, RECOVERY}

	public enum Binding {ROOT, BEAM}

	public enum StopPolicy {NATURAL, IMMEDIATE}

	public record Cue(String component, Phase phase, double delayTicks, String animation, Binding binding,
	                  StopPolicy stopPolicy, boolean scaleToPhase) {
		public Cue(String component, Phase phase, double delayTicks, String animation, Binding binding, StopPolicy stopPolicy) {
			this(component, phase, delayTicks, animation, binding, stopPolicy, false);
		}

		public Cue {
			Objects.requireNonNull(component);
			Objects.requireNonNull(phase);
			Objects.requireNonNull(animation);
			Objects.requireNonNull(binding);
			Objects.requireNonNull(stopPolicy);
			if (component.isBlank() || !Double.isFinite(delayTicks) || delayTicks < 0) {
				throw new IllegalArgumentException("无效组合触发");
			}
		}
	}

	public record Pose(Vector3f offset, Quaternionf rotation, Map<String, List<Float>> inputs) {
		public Pose {
			offset = new Vector3f(offset);
			rotation = new Quaternionf(rotation);
			var copy = new LinkedHashMap<String, List<Float>>();
			inputs.forEach((key, value) -> copy.put(key, List.copyOf(value)));
			inputs = Map.copyOf(copy);
			if (!offset.isFinite() || !Float.isFinite(rotation.x) || !Float.isFinite(rotation.y)
					|| !Float.isFinite(rotation.z) || !Float.isFinite(rotation.w) || rotation.lengthSquared() == 0) {
				throw new IllegalArgumentException("无效组成件变换");
			}
			rotation.normalize();
		}

		@Override
		public Vector3f offset() {
			return new Vector3f(offset);
		}

		@Override
		public Quaternionf rotation() {
			return new Quaternionf(rotation);
		}
	}

	public record Definition(int version, List<Cue> cues, Map<String, Pose> poses) {
		public Definition(int version, List<Cue> cues) {
			this(version, cues, Map.of());
		}

		public Definition {
			if (version != 1) {
				throw new IllegalArgumentException("不支持的组合版本：" + version);
			}
			cues = List.copyOf(cues);
			poses = Map.copyOf(poses);
		}
	}
}
