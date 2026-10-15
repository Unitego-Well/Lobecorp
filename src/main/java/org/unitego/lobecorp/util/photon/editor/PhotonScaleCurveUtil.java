package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.property.ScalePropertyType;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves;
import org.joml.Vector2f;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;

public class PhotonScaleCurveUtil {
	public static boolean supports(AnimatedProperty property) {
		return property != null && property.type() == ScalePropertyType.INSTANCE;
	}

	public static int addLinkedKey(AnimatedProperty property, int axis, float time, float value, IntSupplier insertPrimary) {
		if (!supports(property)) {
			return insertPrimary.getAsInt();
		}

		var values = new float[property.channelCount()];
		float delta = value - AnimatedProperty.sampleChannel(property.channel(axis), time);

		if (!Float.isFinite(time) || time < 0 || !Float.isFinite(delta)) {
			return -1;
		}

		for (int component = 0; component < property.channelCount(); component++) {
			if (findKey(property, component, time) >= 0) {
				return -1;
			}

			values[component] = property.type().clampValue(AnimatedProperty.sampleChannel(property.channel(component), time) + delta);

			if (!Float.isFinite(values[component])) {
				return -1;
			}
		}

		var before = property.snapshotChannels();

		try {
			int inserted = insertPrimary.getAsInt();

			if (inserted < 0) {
				property.restoreChannels(before);
				return -1;
			}

			for (int component = 0; component < property.channelCount(); component++) {
				if (component != axis && property.addKey(component, time, values[component]) < 0) {
					property.restoreChannels(before);
					return -1;
				}
			}

			return inserted;
		} catch (RuntimeException error) {
			property.restoreChannels(before);
			throw error;
		}
	}

	public static Set<Long> linkedKeys(AnimatedProperty property, Set<Long> selected) {
		var result = new LinkedHashSet<>(selected);

		if (!supports(property)) {
			return result;
		}

		for (long id : selected) {
			int axis = axis(id), index = index(id);

			if (axis < 0 || axis >= property.channelCount() || index < 0 || index >= property.keyCount(axis)) {
				continue;
			}

			float time = property.key(axis, index).x;

			for (int component = 0; component < property.channelCount(); component++) {
				int match = findKey(property, component, time);

				if (match >= 0) {
					result.add(encode(component, match));
				}
			}
		}

		return result;
	}

	public static Map<Long, Vector2f> dragOrigins(AnimatedProperty property, Set<Long> selected,
	                                              int axis, int index, int handle) {
		if (!supports(property)) {
			return Map.of();
		}

		var source = handle == 0 ? new LinkedHashSet<>(selected) : new LinkedHashSet<Long>();
		source.add(encode(axis, index));
		var result = new LinkedHashMap<Long, Vector2f>();

		for (long id : linkedKeys(property, source)) {
			if (axis(id) < 0 || axis(id) >= property.channelCount() || index(id) < 0 || index(id) >= property.keyCount(axis(id))) {
				continue;
			}

			var point = point(property, axis(id), index(id), handle);

			if (point != null && Float.isFinite(point.x) && Float.isFinite(point.y)) {
				result.put(id, new Vector2f(point));
			}
		}

		return result;
	}

	public static boolean moveLinked(AnimatedProperty property, ECBCurves[] snapshot,
	                                 Map<Long, Vector2f> origins, int axis, int index, int handle) {
		if (!supports(property)) {
			return false;
		}

		var origin = origins.get(encode(axis, index));
		var current = point(property, axis, index, handle);

		if (origin == null || current == null || origins.size() <= 1) {
			return false;
		}

		float timeDelta = current.x - origin.x, valueDelta = current.y - origin.y;
		property.restoreChannels(snapshot);

		if (!Float.isFinite(timeDelta) || !Float.isFinite(valueDelta)) {
			return true;
		}

		var bounds = deltaBounds(property, origins, handle);

		if (bounds[0] > bounds[1]) {
			return true;
		}

		timeDelta = Math.clamp(timeDelta, bounds[0], bounds[1]);
		var moved = new LinkedHashMap<Long, Vector2f>();

		for (var entry : origins.entrySet()) {
			float time = entry.getValue().x + timeDelta, value = entry.getValue().y + valueDelta;

			if (handle == 0) {
				value = property.type().clampValue(value);
			}

			if (!Float.isFinite(time) || !Float.isFinite(value)) {
				return true;
			}

			moved.put(entry.getKey(), new Vector2f(time, value));
		}

		moved.forEach((id, value) -> move(property, axis(id), index(id), handle, value));
		return true;
	}

	private static float[] deltaBounds(AnimatedProperty property, Map<Long, Vector2f> origins, int handle) {
		float lower = -Float.MAX_VALUE, upper = Float.MAX_VALUE;

		for (var entry : origins.entrySet()) {
			int axis = axis(entry.getKey()), index = index(entry.getKey());
			var origin = entry.getValue();
			lower = Math.max(lower, -origin.x);

			if (handle == 1) {
				upper = Math.min(upper, property.key(axis, index).x - origin.x);
				continue;
			}

			if (handle == 2) {
				lower = Math.max(lower, property.key(axis, index).x - origin.x);
				continue;
			}

			if (index > 0 && !origins.containsKey(encode(axis, index - 1))) {
				lower = Math.max(lower, property.key(axis, index - 1).x + PhotonCurveEditUtil.KEY_GAP - origin.x);
			}

			if (index + 1 < property.keyCount(axis) && !origins.containsKey(encode(axis, index + 1))) {
				upper = Math.min(upper, property.key(axis, index + 1).x - PhotonCurveEditUtil.KEY_GAP - origin.x);
			}
		}

		return new float[]{lower, upper};
	}

	private static void move(AnimatedProperty property, int axis, int index, int handle, Vector2f value) {
		if (handle == 1) {
			property.setInHandle(axis, index, value.x, value.y);
			return;
		}

		if (handle == 2) {
			property.setOutHandle(axis, index, value.x, value.y);
			return;
		}

		property.moveKey(axis, index, value.x, value.y);
	}

	private static Vector2f point(AnimatedProperty property, int axis, int index, int handle) {
		if (handle == 1) {
			return property.inHandle(axis, index);
		}

		return handle == 2 ? property.outHandle(axis, index) : property.key(axis, index);
	}

	private static int findKey(AnimatedProperty property, int axis, float time) {
		for (int index = 0; index < property.keyCount(axis); index++) {
			if (property.key(axis, index).x == time) {
				return index;
			}
		}

		return -1;
	}

	private static long encode(int axis, int index) {
		return (long) axis << 32 | index & 0xffffffffL;
	}

	private static int axis(long id) {
		return (int) (id >>> 32);
	}

	private static int index(long id) {
		return (int) id;
	}
}
