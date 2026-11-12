package org.unitego.lobecorp.util.horde;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/// 固定线路构造与空间查询；不保存逐个虚拟实体。
public class HordeSamplingUtil {
	/// GPU势流最大横向位移，单位格；同时约束地面样本与绘制包围。
	public static final double MAX_VISUAL_OFFSET = 64;
	/// 与movingLocation一致的最大左右摆幅，单位格。
	private static final double MAX_VISUAL_SWAY = 1.5;
	private static final double LOOP_SAMPLE_SPACING = 2;
	private static final int MIN_LOOP_SAMPLES = 8;
	private static final int MAX_LOOP_SAMPLES = 64;
	private static final double CENTRIPETAL_ALPHA = 0.5;

	public static double groundSampleWidth(double width, double margin) {
		return width + 2 * Math.max(margin, MAX_VISUAL_OFFSET + MAX_VISUAL_SWAY);
	}

	public static double smooth(double value) {
		value = Math.clamp(value, 0, 1);
		return value * value * (3 - 2 * value);
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
			int samples = Math.max(MIN_LOOP_SAMPLES, Math.min(MAX_LOOP_SAMPLES,
					(int) Math.ceil(b.distanceTo(c) / LOOP_SAMPLE_SPACING)));
			for (int sample = 0; sample < samples; sample++) {
				result.add(catmullRom(a, b, c, d, sample / (double) samples));
			}
		}
		result.add(result.getFirst());
		return result;
	}

	/// 按输入顺序平滑连接各段，端点外推切线；不闭合，也不创建返程路段。
	public static List<Vec3> path(List<Vec3> input) {
		var anchors = new ArrayList<Vec3>();
		for (var point : input) {
			if (anchors.isEmpty() || anchors.getLast().distanceToSqr(point) > 0.0001) {
				anchors.add(point);
			}
		}

		if (anchors.size() < 2) {
			throw new IllegalArgumentException("线路需要至少两个不同的坐标");
		}

		var result = new ArrayList<Vec3>();
		for (int index = 0; index + 1 < anchors.size(); index++) {
			var b = anchors.get(index);
			var c = anchors.get(index + 1);
			var a = index == 0 ? b.scale(2).subtract(c) : anchors.get(index - 1);
			var d = index + 2 == anchors.size() ? c.scale(2).subtract(b) : anchors.get(index + 2);
			int samples = Math.clamp((int) Math.ceil(b.distanceTo(c) / LOOP_SAMPLE_SPACING),
					MIN_LOOP_SAMPLES, MAX_LOOP_SAMPLES);
			for (int sample = 0; sample < samples; sample++) {
				result.add(catmullRom(a, b, c, d, sample / (double) samples));
			}
		}

		result.add(anchors.getLast());
		return result;
	}

	private static Vec3 catmullRom(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double t) {
		double t0 = 0;
		double t1 = t0 + knot(a, b);
		double t2 = t1 + knot(b, c);
		double t3 = t2 + knot(c, d);
		double value = t1 + (t2 - t1) * Math.clamp(t, 0, 1);
		Vec3 a1 = interpolate(a, b, t0, t1, value);
		Vec3 a2 = interpolate(b, c, t1, t2, value);
		Vec3 a3 = interpolate(c, d, t2, t3, value);
		Vec3 b1 = interpolate(a1, a2, t0, t2, value);
		Vec3 b2 = interpolate(a2, a3, t1, t3, value);
		return interpolate(b1, b2, t1, t2, value);
	}

	private static Vec3 interpolate(Vec3 from, Vec3 to, double fromTime, double toTime, double time) {
		double weight = (time - fromTime) / (toTime - fromTime);
		return from.scale(1 - weight).add(to.scale(weight));
	}

	private static double knot(Vec3 from, Vec3 to) {
		return Math.max(0.0001, Math.pow(from.distanceTo(to), CENTRIPETAL_ALPHA));
	}

	public static double segmentDistanceSqr(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 delta = end.subtract(start);
		double progress = delta.lengthSqr() == 0 ? 0
				: Math.clamp(point.subtract(start).dot(delta) / delta.lengthSqr(), 0, 1);
		return point.distanceToSqr(start.add(delta.scale(progress)));
	}
}
