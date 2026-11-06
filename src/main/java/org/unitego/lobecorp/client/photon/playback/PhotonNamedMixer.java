package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Binding;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.ValueSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.unitego.lobecorp.client.photon.editor.PhotonAuthoringPose;

/// 贡献先收集后按优先级/启动顺序统一应用；每个属性在一次提交中只有一个最终写入。
public class PhotonNamedMixer {
	public record Key(UUID target, String property) {
	}

	private record Layer(int priority, long order, long sequence, PhotonValueContribution value) {
	}

	private static final Comparator<Layer> ORDER = Comparator.comparingInt(Layer::priority)
			.thenComparingLong(Layer::order).thenComparingLong(Layer::sequence);
	private final Map<Key, TargetState> targetStates = new LinkedHashMap<>();
	private long sequence;

	private static final class TargetState {
		private final PhotonPropertyTarget target;
		private final List<Layer> layers = new ArrayList<>();
		private final List<PhotonValueContribution> values = new ArrayList<>();
		private boolean active;

		private TargetState(PhotonPropertyTarget target) {
			this.target = target;
		}
	}

	/// 在原生时间轴求值前释放上一轮临时写入，随后才能读取该轮的新基础值。
	public void begin() {
		targetStates.values().forEach(state -> state.target.release());

		for (var state : targetStates.values()) {
			state.layers.clear();
			state.active = false;
		}

		sequence = 0;
	}

	public void add(FXObject target, AnimatedProperty property, double authoredTime, double authoredDuration, long loops, Binding binding,
	                List<Float> sourceValues, List<Float> scales, List<Float> offsets, int priority, long order) {
		var key = new Key(target.id(), property.type().key());
		var state = targetStates.computeIfAbsent(key, _ -> new TargetState(new PhotonPropertyTarget(target, property.type())));
		var resolved = state.target;
		var sampled = binding.source() == ValueSource.CURVE ? resolved.sample(property, authoredTime) : null;
		List<Float> values = sampled == null ? sourceValues : sampled.values();
		PhotonValueContribution.Function function = sampled == null ? null : sampled.function();

		if (binding.source() == ValueSource.CURVE && property.type().angular()) {
			values = floats(property.sample(authoredTime));
		}

		String path = property.type().path();

		if (binding.source() == ValueSource.CURVE && loops != 0 && (property.type().angular()
				|| path.contains("rotationOverLifetime.") || path.equals("config.startRotation"))) {
			var start = property.sample(0);
			var end = property.sample(authoredDuration);
			var shifts = new ArrayList<Float>();
			var shifted = new ArrayList<Float>();

			for (int axis = 0; axis < values.size(); axis++) {
				float shift = (end[axis] - start[axis]) * loops;
				shifts.add(shift);
				shifted.add(values.get(axis) + shift);
			}

			values = List.copyOf(shifted);

			if (function != null) {
				var original = function;
				function = (axis, time, random) -> original.sample(axis, time, random) + shifts.get(axis);
			}
		}

		var contribution = new PhotonValueContribution(binding.blend(), binding.component(), values, scales, offsets, function);
		state.active = true;
		state.layers.add(new Layer(priority, order, sequence++, contribution));
	}

	private static List<Float> floats(float[] values) {
		var result = new ArrayList<Float>();

		for (float value : values) {
			result.add(value);
		}

		return List.copyOf(result);
	}

	public void commit() {
		for (var state : targetStates.values()) {
			if (!state.active) {
				continue;
			}

			state.layers.sort(ORDER);
			state.values.clear();

			for (var layer : state.layers) {
				state.values.add(layer.value());
			}

			state.target.install(List.copyOf(state.values));
		}
	}

	public void clear() {
		begin();
		targetStates.clear();
	}

    public PhotonAuthoringPose authoredPose(FXObject object, PhotonAuthoringPose current) {
        var result = current;

        for (var state : targetStates.values()) {
            result = state.target.authoredPose(object, result);
        }

        return result;
    }
}
