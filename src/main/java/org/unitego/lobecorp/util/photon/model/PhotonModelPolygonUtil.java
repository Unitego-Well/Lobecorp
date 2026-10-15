package org.unitego.lobecorp.util.photon.model;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/// 保持面角索引的平面简单多边形三角化；不对失败输入使用可能越界的扇形回退。
public class PhotonModelPolygonUtil {
	/// 几何计算相对模型尺寸的误差容限。
	private static final double EPSILON = 1.0e-6;
	/// 导入平面多边形允许的相对离面距离。
	private static final double PLANAR_EPSILON = 1.0e-5;

	public static int[] triangulate(List<Vector3f> positions, int[] face) {
		var normal = normal(positions, face);
		int droppedAxis = Math.abs(normal.x) >= Math.abs(normal.y)
				? (Math.abs(normal.x) >= Math.abs(normal.z) ? 0 : 2)
				: (Math.abs(normal.y) >= Math.abs(normal.z) ? 1 : 2);
		int count = face.length;
		double[] x = new double[count];
		double[] y = new double[count];
		double scale = 0d;
		var origin = positions.get(face[0]);

		for (int i = 0; i < count; i++) {
			var position = positions.get(face[i]);
			var relative = new Vector3f(position).sub(origin);
			x[i] = relative.get((droppedAxis + 1) % 3);
			y[i] = relative.get((droppedAxis + 2) % 3);
			scale = Math.max(scale, Math.max(Math.abs(x[i]), Math.abs(y[i])));
		}

		PhotonModelValidationUtil.require(scale > 0d && Double.isFinite(scale), "polygon.size");
		double epsilon = EPSILON * scale * scale;

		for (int index : face) {
			var relative = new Vector3f(positions.get(index)).sub(origin);
			PhotonModelValidationUtil.require(Math.abs(relative.dot(normal)) <= PLANAR_EPSILON * scale,
					"polygon.planarity");
		}

		checkIntersections(x, y, epsilon);
		return clipEars(x, y, epsilon);
	}

	public static Vector3f normal(List<Vector3f> positions, int[] face) {
		double nx = 0d;
		double ny = 0d;
		double nz = 0d;

		for (int i = 0; i < face.length; i++) {
			var a = positions.get(face[i]);
			var b = positions.get(face[(i + 1) % face.length]);
			nx += (double) (a.y - b.y) * (a.z + b.z);
			ny += (double) (a.z - b.z) * (a.x + b.x);
			nz += (double) (a.x - b.x) * (a.y + b.y);
		}

		double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
		PhotonModelValidationUtil.require(length > 0d && Double.isFinite(length), "polygon.normal");
		return new Vector3f((float) (nx / length), (float) (ny / length), (float) (nz / length));
	}

	private static int[] clipEars(double[] x, double[] y, double epsilon) {
		double area = 0d;
		var indices = new ArrayList<Integer>();

		for (int i = 0; i < x.length; i++) {
			int next = (i + 1) % x.length;
			area += x[i] * y[next] - x[next] * y[i];
			indices.add(i);
		}

		PhotonModelValidationUtil.require(Math.abs(area) > epsilon, "polygon.area");
		double winding = Math.signum(area);
		int[] triangles = new int[(x.length - 2) * 3];
		int output = 0;

		while (indices.size() > 3) {
			boolean found = false;

			for (int i = 0; i < indices.size(); i++) {
				int a = indices.get((i + indices.size() - 1) % indices.size());
				int b = indices.get(i);
				int c = indices.get((i + 1) % indices.size());

				if (!isEar(x, y, indices, a, b, c, winding, epsilon)) {
					continue;
				}

				triangles[output++] = a;
				triangles[output++] = b;
				triangles[output++] = c;
				indices.remove(i);
				found = true;
				break;
			}

			PhotonModelValidationUtil.require(found, "polygon.triangulation");
		}

		triangles[output++] = indices.get(0);
		triangles[output++] = indices.get(1);
		triangles[output] = indices.get(2);
		return triangles;
	}

	private static boolean isEar(double[] x, double[] y, List<Integer> indices, int a, int b, int c,
	                             double winding, double epsilon) {
		if (cross(x[a], y[a], x[b], y[b], x[c], y[c]) * winding <= epsilon) {
			return false;
		}

		for (int index : indices) {
			if (index == a || index == b || index == c) {
				continue;
			}

			if (cross(x[a], y[a], x[b], y[b], x[index], y[index]) * winding >= -epsilon
					&& cross(x[b], y[b], x[c], y[c], x[index], y[index]) * winding >= -epsilon
					&& cross(x[c], y[c], x[a], y[a], x[index], y[index]) * winding >= -epsilon) {
				return false;
			}
		}

		return true;
	}

	private static void checkIntersections(double[] x, double[] y, double epsilon) {
		for (int i = 0; i < x.length; i++) {
			int nextI = (i + 1) % x.length;

			for (int j = i + 1; j < x.length; j++) {
				int nextJ = (j + 1) % x.length;

				if (nextI == j || nextJ == i) {
					continue;
				}

				double a = cross(x[i], y[i], x[nextI], y[nextI], x[j], y[j]);
				double b = cross(x[i], y[i], x[nextI], y[nextI], x[nextJ], y[nextJ]);
				double c = cross(x[j], y[j], x[nextJ], y[nextJ], x[i], y[i]);
				double d = cross(x[j], y[j], x[nextJ], y[nextJ], x[nextI], y[nextI]);
				boolean proper = Math.signum(a) != Math.signum(b) && Math.signum(c) != Math.signum(d);
				boolean touching = Math.abs(a) <= epsilon && onSegment(x[i], y[i], x[nextI], y[nextI], x[j], y[j])
						|| Math.abs(b) <= epsilon && onSegment(x[i], y[i], x[nextI], y[nextI], x[nextJ], y[nextJ])
						|| Math.abs(c) <= epsilon && onSegment(x[j], y[j], x[nextJ], y[nextJ], x[i], y[i])
						|| Math.abs(d) <= epsilon && onSegment(x[j], y[j], x[nextJ], y[nextJ], x[nextI], y[nextI]);
				PhotonModelValidationUtil.require(!proper && !touching, "polygon.intersection");
			}
		}
	}

	private static boolean onSegment(double ax, double ay, double bx, double by, double px, double py) {
		return px >= Math.min(ax, bx) && px <= Math.max(ax, bx)
				&& py >= Math.min(ay, by) && py <= Math.max(ay, by);
	}

	private static double cross(double ax, double ay, double bx, double by, double cx, double cy) {
		return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
	}
}
