package org.unitego.lobecorp.util.photon.model;

import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 只准备与播放时间无关的面角/三角形/材质分组，姿态更新不重新三角化。
public class PhotonModelTopologyUtil {
	/// 投影几何容差按该面的坐标跨度缩放，不作为全模型焊接阈值。
	private static final double RELATIVE_EPSILON = 1e-10;

	public record Corner(int vertex, int face, int corner) {
	}

	public record Group(int part, String material, List<Corner> corners, List<Integer> indices) {
		public Group {
			corners = List.copyOf(corners);
			indices = List.copyOf(indices);
		}
	}

	public static final class Prepared {
		private final PhotonModelDefinition definition;
		private final List<Group> groups;
		private final Map<String, PhotonModelAssetUtil.MaterialBlueprint> materials;
		private List<Integer> groupOrder;

		public Prepared(PhotonModelDefinition definition, List<Group> groups) {
			this.definition = definition;
			this.groups = List.copyOf(groups);
			var blueprints = new LinkedHashMap<String, PhotonModelAssetUtil.MaterialBlueprint>();
			definition.materials().forEach((id, material) -> {
				if (!material.materialData().isEmpty()) {
					blueprints.put(id, new PhotonModelAssetUtil.MaterialBlueprint(material.materialData()));
				}
			});
			materials = Map.copyOf(blueprints);
		}

		public PhotonModelDefinition definition() {
			return definition;
		}

		public List<Group> groups() {
			return groups;
		}

		/// 首次由输出准备阶段计算；仅缓存成功结果，不参与值相等契约。
		public List<Integer> groupOrder() {
			if (groupOrder == null) {
				var order = new ArrayList<Integer>();

				for (int index = 0; index < groups.size(); index++) {
					order.add(index);
				}

				order.sort(Comparator.comparing(index -> {
					var material = definition.materials().get(groups.get(index).material());
					return material != null && material.overlay();
				}));
				groupOrder = List.copyOf(order);
			}

			return groupOrder;
		}

		/// 蓝图随定义释放，不保存材质实例或GPU状态。
		public CompoundTag materialData(String id) throws IOException {
			return materials.get(id).copy();
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Prepared value && definition.equals(value.definition) && groups.equals(value.groups);
		}

		@Override
		public int hashCode() {
			return 31 * definition.hashCode() + groups.hashCode();
		}

		@Override
		public String toString() {
			return "Prepared[definition=" + definition + ", groups=" + groups + "]";
		}
	}

	private record GroupKey(int part, String material) {
	}

	private static class GroupBuilder {
		private final List<Corner> corners = new ArrayList<>();
		private final List<Integer> indices = new ArrayList<>();
	}

	public static Prepared prepare(PhotonModelDefinition definition) throws IOException {
		checkCancelled();
		var parts = new LinkedHashMap<String, Integer>();

		for (int index = 0; index < definition.parts().size(); index++) {
			parts.put(definition.parts().get(index).id(), index);
		}

		var builders = new LinkedHashMap<GroupKey, GroupBuilder>();

		for (int faceIndex = 0; faceIndex < definition.faces().size(); faceIndex++) {
			checkCancelled();
			var face = definition.faces().get(faceIndex);
			var key = new GroupKey(parts.get(face.partId()), face.material());
			var group = builders.computeIfAbsent(key, _ -> new GroupBuilder());
			int start = group.corners.size();

			for (int index = 0; index < face.vertices().size(); index++) {
				group.corners.add(new Corner(face.vertices().get(index), faceIndex, index));
			}

			for (int index : triangulate(definition.vertices(), face.vertices())) {
				group.indices.add(start + index);
			}
		}

		var groups = new ArrayList<Group>();
		builders.forEach((key, builder) -> groups.add(new Group(key.part(), key.material(), builder.corners, builder.indices)));
		return new Prepared(definition, groups);
	}

	private static List<Integer> triangulate(List<Vector3> vertices, List<Integer> face) throws IOException {
		var projected = project(vertices, face);
		var x = projected[0];
		var y = projected[1];
		double span = 0;

		for (int index = 0; index < x.length; index++) {
			span = Math.max(span, Math.max(Math.abs(x[index] - x[0]), Math.abs(y[index] - y[0])));
		}

		double epsilon = RELATIVE_EPSILON * span * span;
		double area = 0;

		for (int index = 0; index < face.size(); index++) {
			int next = (index + 1) % face.size();
			area += x[index] * y[next] - x[next] * y[index];
		}

		if (Math.abs(area) <= epsilon) {
			throw new IOException("模型多边形没有稳定的投影平面。");
		}

		double sign = Math.signum(area);
		validateEdges(x, y, epsilon, RELATIVE_EPSILON * span);
		var remaining = new ArrayList<Integer>();

		for (int index = 0; index < face.size(); index++) {
			remaining.add(index);
		}

		var result = new ArrayList<Integer>();

		while (remaining.size() > 3) {
			checkCancelled();
			boolean removed = false;

			for (int index = 0; index < remaining.size(); index++) {
				int a = remaining.get((index + remaining.size() - 1) % remaining.size());
				int b = remaining.get(index);
				int c = remaining.get((index + 1) % remaining.size());

				if (cross(x, y, a, b, c) * sign <= epsilon || containsPoint(x, y, remaining, a, b, c, sign, epsilon)) {
					continue;
				}

				result.addAll(List.of(a, b, c));
				remaining.remove(index);
				removed = true;
				break;
			}

			if (!removed) {
				throw new IOException("模型多边形不能无损三角化，可能退化或自相交。");
			}
		}

		if (cross(x, y, remaining.get(0), remaining.get(1), remaining.get(2)) * sign <= epsilon) {
			throw new IOException("模型多边形包含退化的剩余三角形。");
		}

		result.addAll(remaining);
		return List.copyOf(result);
	}

	private static void validateEdges(double[] x, double[] y, double epsilon, double lengthEpsilon) throws IOException {
		for (int a = 0; a < x.length; a++) {
			checkCancelled();
			int b = (a + 1) % x.length;

			for (int c = a + 1; c < x.length; c++) {
				int d = (c + 1) % x.length;

				if (a == c || a == d || b == c || b == d) {
					continue;
				}

				double abC = cross(x, y, a, b, c);
				double abD = cross(x, y, a, b, d);
				double cdA = cross(x, y, c, d, a);
				double cdB = cross(x, y, c, d, b);

				if (abC * abD < -epsilon * epsilon && cdA * cdB < -epsilon * epsilon
						|| Math.abs(abC) <= epsilon && within(x, y, a, b, c, lengthEpsilon)
						|| Math.abs(abD) <= epsilon && within(x, y, a, b, d, lengthEpsilon)
						|| Math.abs(cdA) <= epsilon && within(x, y, c, d, a, lengthEpsilon)
						|| Math.abs(cdB) <= epsilon && within(x, y, c, d, b, lengthEpsilon)) {
					throw new IOException("模型多边形边界自相交或重叠。");
				}
			}
		}
	}

	private static boolean within(double[] x, double[] y, int a, int b, int p, double epsilon) {
		return x[p] >= Math.min(x[a], x[b]) - epsilon && x[p] <= Math.max(x[a], x[b]) + epsilon
				&& y[p] >= Math.min(y[a], y[b]) - epsilon && y[p] <= Math.max(y[a], y[b]) + epsilon;
	}

	private static boolean containsPoint(double[] x, double[] y, List<Integer> remaining,
	                                     int a, int b, int c, double sign, double epsilon) {
		for (int point : remaining) {
			if (point == a || point == b || point == c) {
				continue;
			}

			if (cross(x, y, a, b, point) * sign >= -epsilon
					&& cross(x, y, b, c, point) * sign >= -epsilon
					&& cross(x, y, c, a, point) * sign >= -epsilon) {
				return true;
			}
		}

		return false;
	}

	private static double cross(double[] x, double[] y, int a, int b, int c) {
		return (x[b] - x[a]) * (y[c] - y[a]) - (y[b] - y[a]) * (x[c] - x[a]);
	}

	private static void checkCancelled() throws InterruptedIOException {
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedIOException("模型拓扑准备已取消。");
		}
	}

	private static double[][] project(List<Vector3> vertices, List<Integer> face) {
		double nx = 0;
		double ny = 0;
		double nz = 0;

		for (int index = 0; index < face.size(); index++) {
			var a = vertices.get(face.get(index));
			var b = vertices.get(face.get((index + 1) % face.size()));
			nx += (a.y() - b.y()) * (a.z() + b.z());
			ny += (a.z() - b.z()) * (a.x() + b.x());
			nz += (a.x() - b.x()) * (a.y() + b.y());
		}

		int omit = Math.abs(nx) >= Math.abs(ny) && Math.abs(nx) >= Math.abs(nz) ? 0 : Math.abs(ny) >= Math.abs(nz) ? 1 : 2;
		var result = new double[2][face.size()];

		for (int index = 0; index < face.size(); index++) {
			var value = vertices.get(face.get(index));
			result[0][index] = omit == 0 ? value.y() : value.x();
			result[1][index] = omit == 2 ? value.y() : value.z();
		}

		for (int index = face.size() - 1; index >= 0; index--) {
			result[0][index] -= result[0][0];
			result[1][index] -= result[1][0];
		}

		return result;
	}
}
