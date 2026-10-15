package org.unitego.lobecorp.util.photon.model.editor;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Face;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Weight;
import org.unitego.lobecorp.util.photon.model.PhotonModelPolygonUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PhotonModelEditUtil {
	/// 圆形基础网格的默认分段数，控制建模细度。
	public static final int RADIAL_SEGMENTS = 16;
	/// 单位基础网格的半径与半边长。
	private static final float UNIT_HALF = 0.5f;
	/// 圆环单位外半径下的默认管半径。
	private static final float RING_TUBE_RADIUS = 0.15f;

	public enum Primitive {
		PLANE, CUBE, CYLINDER, CONE, RING
	}

	public enum SelectionMode {
		PART, VERTEX, EDGE, FACE
	}

	public record Edge(int first, int second) {
		public static Edge of(int first, int second) {
			return first < second ? new Edge(first, second) : new Edge(second, first);
		}
	}

	public static void addPrimitive(PhotonModelAsset asset, Primitive primitive) {
		var part = new Part();
		String stem = primitive.name().toLowerCase(Locale.ROOT);
		var existingIds = asset.parts.stream().map(existing -> existing.id).toList();
		part.id = stem;
		int suffix = 1;

		while (existingIds.contains(part.id)) {
			part.id = stem + "_" + suffix++;
		}
		asset.parts.add(part);

		switch (primitive) {
			case PLANE -> plane(asset, part.id);
			case CUBE -> cube(asset, part.id);
			case CYLINDER, CONE -> radial(asset, part.id, primitive == Primitive.CONE);
			case RING -> ring(asset, part.id);
		}
	}

	private static void plane(PhotonModelAsset asset, String part) {
		int base = asset.vertices.size();
		asset.vertices.addAll(List.of(new Vector3f(-UNIT_HALF, 0, 0), new Vector3f(UNIT_HALF, 0, 0),
				new Vector3f(UNIT_HALF, 0, 1), new Vector3f(-UNIT_HALF, 0, 1)));
		face(asset, part, base, base + 1, base + 2, base + 3);
	}

	private static void cube(PhotonModelAsset asset, String part) {
		int base = asset.vertices.size();

		for (int z = 0; z < 2; z++) {
			for (int y = 0; y < 2; y++) {
				for (int x = 0; x < 2; x++) {
					asset.vertices.add(new Vector3f(x - UNIT_HALF, y - UNIT_HALF, z));
				}
			}
		}

		int firstFace = asset.faces.size();
		face(asset, part, base, base + 2, base + 3, base + 1);
		face(asset, part, base + 4, base + 5, base + 7, base + 6);
		face(asset, part, base, base + 1, base + 5, base + 4);
		face(asset, part, base + 2, base + 6, base + 7, base + 3);
		face(asset, part, base, base + 4, base + 6, base + 2);
		face(asset, part, base + 1, base + 3, base + 7, base + 5);

		for (var face : asset.faces.subList(firstFace, asset.faces.size())) {
			var normal = PhotonModelPolygonUtil.normal(asset.vertices, face.vertices);

			for (int corner = 0; corner < face.vertices.length; corner++) {
				var vertex = asset.vertices.get(face.vertices[corner]);
				face.uv[corner].set(normal.x == 0 ? vertex.x + UNIT_HALF : vertex.z,
						normal.y == 0 ? UNIT_HALF - vertex.y : vertex.z);
			}
		}
	}

	private static void radial(PhotonModelAsset asset, String part, boolean cone) {
		int base = asset.vertices.size();

		for (int end = 0; end < (cone ? 1 : 2); end++) {
			for (int segment = 0; segment < RADIAL_SEGMENTS; segment++) {
				double angle = Math.TAU * segment / RADIAL_SEGMENTS;
				float radius = UNIT_HALF;
				asset.vertices.add(new Vector3f((float) Math.cos(angle) * radius,
						(float) Math.sin(angle) * radius, end));
			}
		}

		int[] start = new int[RADIAL_SEGMENTS];
		int[] end = new int[RADIAL_SEGMENTS];
		int apex = asset.vertices.size();

		if (cone) {
			asset.vertices.add(new Vector3f(0, 0, 1));
		}

		for (int i = 0; i < RADIAL_SEGMENTS; i++) {
			int next = (i + 1) % RADIAL_SEGMENTS;

			if (cone) {
				face(asset, part, base + i, base + next, apex);
			} else {
				face(asset, part, base + i, base + next, base + next + RADIAL_SEGMENTS, base + i + RADIAL_SEGMENTS);
			}

			start[RADIAL_SEGMENTS - i - 1] = base + i;
			end[i] = base + i + RADIAL_SEGMENTS;
		}

		face(asset, part, start);

		if (!cone) {
			face(asset, part, end);
		}
	}

	private static void ring(PhotonModelAsset asset, String part) {
		int base = asset.vertices.size();

		for (int i = 0; i < RADIAL_SEGMENTS; i++) {
			double angle = Math.TAU * i / RADIAL_SEGMENTS;

			for (int j = 0; j < RADIAL_SEGMENTS; j++) {
				double tube = Math.TAU * j / RADIAL_SEGMENTS;
				double radius = UNIT_HALF - RING_TUBE_RADIUS + RING_TUBE_RADIUS * Math.cos(tube);
				asset.vertices.add(new Vector3f((float) (radius * Math.cos(angle)),
						(float) (radius * Math.sin(angle)), (float) (RING_TUBE_RADIUS * Math.sin(tube))));
			}
		}

		for (int i = 0; i < RADIAL_SEGMENTS; i++) {
			for (int j = 0; j < RADIAL_SEGMENTS; j++) {
				int nextI = (i + 1) % RADIAL_SEGMENTS;
				int nextJ = (j + 1) % RADIAL_SEGMENTS;
				face(asset, part, base + i * RADIAL_SEGMENTS + j, base + nextI * RADIAL_SEGMENTS + j,
						base + nextI * RADIAL_SEGMENTS + nextJ);
				face(asset, part, base + i * RADIAL_SEGMENTS + j, base + nextI * RADIAL_SEGMENTS + nextJ,
						base + i * RADIAL_SEGMENTS + nextJ);
			}
		}
	}

	private static void face(PhotonModelAsset asset, String part, int... vertices) {
		var face = new Face();
		face.partId = part;
		face.vertices = vertices;
		face.uv = new Vector2f[vertices.length];

		if (vertices.length == 4) {
			face.uv = new Vector2f[]{new Vector2f(0, 1), new Vector2f(0, 0),
					new Vector2f(1, 0), new Vector2f(1, 1)};
		} else {
			for (int i = 0; i < vertices.length; i++) {
				double angle = Math.TAU * i / vertices.length;
				face.uv[i] = new Vector2f(UNIT_HALF + (float) Math.cos(angle) * UNIT_HALF,
						UNIT_HALF + (float) Math.sin(angle) * UNIT_HALF);
			}
		}

		face.normals = new Vector3f[0];
		asset.faces.add(face);
	}

	public static Set<Integer> selectedVertices(PhotonModelAsset asset, String partId, Set<Integer> vertices, Set<Integer> faces,
	                                            SelectionMode mode) {
		if (mode == SelectionMode.VERTEX || mode == SelectionMode.EDGE) {
			return new LinkedHashSet<>(vertices);
		}

		var result = new LinkedHashSet<Integer>();

		for (int i = 0; i < asset.faces.size(); i++) {
			var face = asset.faces.get(i);

			if (mode == SelectionMode.PART ? face.partId.equals(partId) : faces.contains(i)) {
				for (int vertex : face.vertices) {
					result.add(vertex);
				}
			}
		}

		return result;
	}

	public static Vector3f center(PhotonModelAsset asset, Set<Integer> selected) {
		var center = new Vector3f();

		for (int index : selected) {
			center.add(asset.vertices.get(index));
		}

		return selected.isEmpty() ? center : center.div(selected.size());
	}

	/// 重定位部件轴心并补偿基础平移，保持基础姿态及子部件的世界矩阵不变。
	/// 现有动画轨道不重写，后续动画采样使用新轴心。
	public static void movePartPivot(PhotonModelAsset asset, String partId, Vector3f newPivot) {
		var part = asset.parts.stream().filter(current -> current.id.equals(partId)).findFirst().orElseThrow();
		var pivot = new Vector3f(newPivot);
		var delta = new Vector3f(pivot).sub(part.pivot);
		var compensation = new Matrix4f().rotation(new Quaternionf(part.transform.rotation).normalize())
				.scale(part.transform.scale).transformDirection(new Vector3f(delta)).sub(delta);
		part.transform.translation.add(compensation);
		part.pivot.set(pivot);
	}

	public static void transformVertices(PhotonModelAsset asset, Set<Integer> selected, Set<Integer> selectedFaces,
	                                     Vector3f pivot, Vector3f translation, Quaternionf rotation, Vector3f scale) {
		var transform = new Matrix4f().translate(pivot).translate(translation).rotate(rotation).scale(scale)
				.translate(new Vector3f(pivot).negate());
		transformVertices(asset, selected, selectedFaces, transform);
	}

	/// 直接应用绑定空间仿射矩阵，保留部件逆变换产生的剪切分量。
	public static void transformVertices(PhotonModelAsset asset, Set<Integer> selected, Set<Integer> selectedFaces,
	                                     Matrix4f transform) {
		triangulatePartialFaces(asset, selected, selectedFaces);

		for (int vertex : selected) {
			asset.vertices.get(vertex).mulPosition(transform);
		}

		clearNormals(asset, selected);
	}

	private static void triangulatePartialFaces(PhotonModelAsset asset, Set<Integer> selected, Set<Integer> selectedFaces) {
		var result = new ArrayList<Face>();
		var remapped = new LinkedHashSet<Integer>();

		for (int index = 0; index < asset.faces.size(); index++) {
			var face = asset.faces.get(index);
			int count = 0;

			for (int vertex : face.vertices) {
				if (selected.contains(vertex)) {
					count++;
				}
			}

			if (count == 0 || count == face.vertices.length || face.vertices.length == 3) {
				if (selectedFaces.contains(index)) {
					remapped.add(result.size());
				}

				result.add(face);
				continue;
			}

			int[] triangles = PhotonModelPolygonUtil.triangulate(asset.vertices, face.vertices);

			for (int triangle = 0; triangle < triangles.length; triangle += 3) {
				var copy = new Face();
				copy.partId = face.partId;
				copy.material = face.material;
				copy.vertices = new int[3];
				copy.uv = new Vector2f[3];

				for (int corner = 0; corner < 3; corner++) {
					int oldCorner = triangles[triangle + corner];
					copy.vertices[corner] = face.vertices[oldCorner];
					copy.uv[corner] = new Vector2f(face.uv[oldCorner]);
				}

				if (selectedFaces.contains(index)) {
					remapped.add(result.size());
				}

				result.add(copy);
			}
		}

		asset.faces = result;
		selectedFaces.clear();
		selectedFaces.addAll(remapped);
	}

	private static void clearNormals(PhotonModelAsset asset, Set<Integer> selected) {
		for (var face : asset.faces) {
			for (int index : face.vertices) {
				if (selected.contains(index)) {
					face.normals = new Vector3f[0];
					break;
				}
			}
		}
	}

	public static void deleteFaces(PhotonModelAsset asset, Set<Integer> selectedFaces) {
		for (int i = asset.faces.size() - 1; i >= 0; i--) {
			if (selectedFaces.contains(i)) {
				asset.faces.remove(i);
			}
		}
	}

	public static void deleteVertices(PhotonModelAsset asset, Set<Integer> selectedVertices) {
		var faces = new LinkedHashSet<Integer>();

		for (int i = 0; i < asset.faces.size(); i++) {
			for (int vertex : asset.faces.get(i).vertices) {
				if (selectedVertices.contains(vertex)) {
					faces.add(i);
				}
			}
		}

		deleteFaces(asset, faces);
		var remap = new HashMap<Integer, Integer>();
		var retained = new ArrayList<Vector3f>();

		for (int old = 0; old < asset.vertices.size(); old++) {
			if (!selectedVertices.contains(old)) {
				remap.put(old, retained.size());
				retained.add(asset.vertices.get(old));
			}
		}

		asset.vertices = retained;
		asset.faces.forEach(face -> {
			for (int corner = 0; corner < face.vertices.length; corner++) {
				face.vertices[corner] = remap.get(face.vertices[corner]);
			}
		});
		var weights = new HashMap<Integer, List<Weight>>();
		asset.weights.forEach((vertex, influences) -> {
			if (remap.containsKey(vertex)) {
				weights.put(remap.get(vertex), influences);
			}
		});
		asset.weights = weights;
		asset.morphs.forEach(morph -> {
			var deltas = new HashMap<Integer, Vector3f>();
			morph.deltas.forEach((vertex, delta) -> {
				if (remap.containsKey(vertex)) {
					deltas.put(remap.get(vertex), delta);
				}
			});
			morph.deltas = deltas;
		});
	}

	public static void duplicateFaces(PhotonModelAsset asset, Set<Integer> selectedFaces, String partId) {
		var remap = new HashMap<Integer, Integer>();
		var originals = selectedFaces.stream().map(asset.faces::get).toList();

		for (var original : originals) {
			var copy = new Face();
			copy.vertices = new int[original.vertices.length];
			copy.uv = new Vector2f[original.vertices.length];
			copy.normals = new Vector3f[0];
			copy.partId = partId;
			copy.material = original.material;

			for (int i = 0; i < original.vertices.length; i++) {
				int old = original.vertices[i];
				copy.vertices[i] = remap.computeIfAbsent(old, index -> {
					asset.vertices.add(new Vector3f(asset.vertices.get(index)));
					int added = asset.vertices.size() - 1;

					if (asset.weights.containsKey(index)) {
						asset.weights.put(added, new ArrayList<>(asset.weights.get(index).stream()
								.map(Weight::copy).toList()));
					}

					return added;
				});
				copy.uv[i] = new Vector2f(original.uv[i]);
			}

			asset.faces.add(copy);
		}
	}

	public static void extrude(PhotonModelAsset asset, Set<Integer> selectedFaces, float distance) {
		var originals = selectedFaces.stream().map(asset.faces::get).toList();

		for (var original : originals) {
			if (original.vertices.length < 3) {
				continue;
			}

			var normal = PhotonModelPolygonUtil.normal(asset.vertices, original.vertices).mul(distance);
			int[] raised = new int[original.vertices.length];

			for (int i = 0; i < raised.length; i++) {
				raised[i] = asset.vertices.size();
				int source = original.vertices[i];
				asset.vertices.add(new Vector3f(asset.vertices.get(source)).add(normal));

				if (asset.weights.containsKey(source)) {
					asset.weights.put(raised[i], new ArrayList<>(asset.weights.get(source).stream().map(Weight::copy).toList()));
				}
			}

			var cap = original.copy();
			cap.vertices = raised;
			cap.normals = new Vector3f[0];
			asset.faces.add(cap);

			for (int i = 0; i < raised.length; i++) {
				int next = (i + 1) % raised.length;
				face(asset, original.partId, original.vertices[i], original.vertices[next], raised[next], raised[i]);
			}
		}

		deleteFaces(asset, selectedFaces);
	}

	public static void projectUV(PhotonModelAsset asset, Set<Integer> selectedFaces, int axis) {
		for (int index : selectedFaces) {
			var face = asset.faces.get(index);
			face.uv = new Vector2f[face.vertices.length];

			for (int corner = 0; corner < face.vertices.length; corner++) {
				var vertex = asset.vertices.get(face.vertices[corner]);
				face.uv[corner] = switch (axis) {
					case 0 -> new Vector2f(vertex.get(1), vertex.get(2));
					case 1 -> new Vector2f(vertex.x, vertex.z);
					default -> new Vector2f(vertex.x, vertex.y);
				};
			}
		}
	}

	public static List<Integer> partFaces(PhotonModelAsset asset, String partId) {
		var result = new ArrayList<Integer>();

		for (int i = 0; i < asset.faces.size(); i++) {
			if (asset.faces.get(i).partId.equals(partId)) {
				result.add(i);
			}
		}

		return result;
	}
}
