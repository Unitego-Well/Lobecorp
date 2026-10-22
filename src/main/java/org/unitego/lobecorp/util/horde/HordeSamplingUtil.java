package org.unitego.lobecorp.util.horde;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/// 与容器遍历顺序无关的种子采样；不保存逐个虚拟实体。
public class HordeSamplingUtil {
	private static final long MIX_FIRST = 0xbf58476d1ce4e5b9L;
	private static final long MIX_SECOND = 0x94d049bb133111ebL;

	public static long mix(long value) {
		value = (value ^ value >>> 30) * MIX_FIRST;
		value = (value ^ value >>> 27) * MIX_SECOND;
		return value ^ value >>> 31;
	}

	public static double unit(long seed, long index, long purpose) {
		return (mix(seed ^ mix(index) ^ purpose) >>> 11) * 0x1.0p-53;
	}

	public static double smooth(double value) {
		value = Math.clamp(value, 0, 1);
		return value * value * (3 - 2 * value);
	}

	public static double noise(long seed, long index, double time) {
		long cell = (long) Math.floor(time);
		double weight = smooth(time - cell);
		return (unit(seed, index ^ cell, 17) * (1 - weight)
				+ unit(seed, index ^ (cell + 1), 17) * weight) * 2 - 1;
	}

	public static double density(double width, double referenceDensity) {
		return referenceDensity * Math.sqrt(128 / width);
	}

	/// 无途经点时以两条平行路段构成环，途经点则按输入顺序闭合并平滑转弯。
	public static List<Vec3> loop(List<Vec3> input, double width) {
		var anchors = new ArrayList<>(input);
		if (anchors.size() > 2 && anchors.getFirst().distanceToSqr(anchors.getLast()) < 0.001) {
			anchors.removeLast();
		}
		if (anchors.size() == 2) {
			Vec3 side = anchors.getLast().subtract(anchors.getFirst()).cross(new Vec3(0, 1, 0)).normalize().scale(width * 2);
			if (side.lengthSqr() < 1) {
				throw new IllegalArgumentException("闭环需要不同的起终点或有效途经点");
			}
			anchors.add(anchors.get(1).add(side));
			anchors.add(anchors.getFirst().add(side));
		}
		var result = new ArrayList<Vec3>();
		for (int index = 0; index < anchors.size(); index++) {
			Vec3 a = anchors.get(Math.floorMod(index - 1, anchors.size()));
			Vec3 b = anchors.get(index);
			Vec3 c = anchors.get((index + 1) % anchors.size());
			Vec3 d = anchors.get((index + 2) % anchors.size());
			for (int sample = 0; sample < 16; sample++) {
				double t = sample / 16d;
				double t2 = t * t;
				double t3 = t2 * t;
				result.add(b.scale(2).add(c.subtract(a).scale(t))
						.add(a.scale(2).subtract(b.scale(5)).add(c.scale(4)).subtract(d).scale(t2))
						.add(b.scale(3).subtract(a).subtract(c.scale(3)).add(d).scale(t3)).scale(0.5));
			}
		}
		result.add(result.getFirst());
		return result;
	}

	public static float endpointAlpha(double station, double length, double speed, int fadeTicks) {
		double span = Math.max(0.001, speed * fadeTicks);
		return (float) (smooth(station / span) * smooth((length - station) / span));
	}

	public static double segmentDistanceSqr(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 delta = end.subtract(start);
		double progress = delta.lengthSqr() == 0 ? 0
				: Math.clamp(point.subtract(start).dot(delta) / delta.lengthSqr(), 0, 1);
		return point.distanceToSqr(start.add(delta.scale(progress)));
	}
}
