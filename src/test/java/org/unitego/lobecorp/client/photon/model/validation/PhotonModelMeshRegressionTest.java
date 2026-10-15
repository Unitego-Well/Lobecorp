package org.unitego.lobecorp.client.photon.model.validation;

import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Face;
import org.unitego.lobecorp.util.photon.model.PhotonModelPolygonUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelPrimitiveUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelPrimitiveUtil.Parameters;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelPrimitiveUtil.Shape;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 参数化基本体回归，不启动游戏。
public class PhotonModelMeshRegressionTest {


	@Test
	public void defaultShapes() {
		var parameters = Parameters.defaults();
		for (var shape : Shape.values()) {
			var mesh = mesh(shape, parameters);
			int columns = parameters.segments();
			int rows = parameters.rings();
			int expectedVertices = switch (shape) {
				case CYLINDER -> columns * (rows + 1);
				case CONE -> columns * rows + 1;
				case RING -> columns * rows;
				case SPHERE -> columns * (rows - 1) + 2;
				case ARC -> (columns + 1) * 2;
				case CYLINDRICAL_PATCH, SPHERICAL_PATCH -> (columns + 1) * (rows + 1);
			};
			int expectedFaces = switch (shape) {
				case CYLINDER -> columns * rows + 2;
				case CONE -> columns * rows + 1;
				case ARC -> columns;
				default -> columns * rows;
			};
			check(mesh.vertices.size() == expectedVertices, shape + " vertex count");
			check(mesh.faces.size() == expectedFaces, shape + " face count");
			check(mesh.materials.isEmpty(), shape + " does not create materials");
			check(mesh.vertices.stream().distinct().count() == mesh.vertices.size(), shape + " welded vertices");
			checkNormals(mesh, shape, parameters);
			checkUVBounds(mesh);
		}
	}

	@Test
	public void closedSurfacesAndPoles() {
		var parameters = new Parameters(0.5f, 0.3f, 1f, 360f, 180f, 12, 6);
		for (var shape : Shape.values()) {
			var mesh = mesh(shape, parameters);
			if (shape == Shape.ARC || shape == Shape.CYLINDRICAL_PATCH) {
				continue;
			}

			var edgeUses = new HashMap<Long, Integer>();
			for (var face : mesh.faces) {
				for (int corner = 0; corner < face.vertices.length; corner++) {
					int a = face.vertices[corner];
					int b = face.vertices[(corner + 1) % face.vertices.length];
					long edge = (long) Math.min(a, b) << Integer.SIZE | Integer.toUnsignedLong(Math.max(a, b));
					edgeUses.merge(edge, 1, Integer::sum);
				}
			}

			check(edgeUses.values().stream().allMatch(count -> count == 2), shape + " watertight edges");
			if (shape == Shape.SPHERE || shape == Shape.SPHERICAL_PATCH) {
				long triangles = mesh.faces.stream().filter(face -> face.vertices.length == 3).count();
				check(triangles == parameters.segments() * 2L, shape + " pole triangles");
			}

			checkSeam(mesh, 0);
			if (shape == Shape.RING) {
				checkSeam(mesh, 1);
			}
		}

		var ribbon = mesh(Shape.ARC, parameters);
		check(ribbon.vertices.size() == parameters.segments() * 2, "closed ribbon shared seam");
		var cylinder = mesh(Shape.CYLINDRICAL_PATCH, parameters);
		check(cylinder.vertices.size() == parameters.segments() * (parameters.rings() + 1),
				"closed cylindrical patch shared seam");
	}

	@Test
	public void subdivisionBoundaries() {
		for (var shape : Shape.values()) {
			var minimal = new Parameters(0.5f, 0.3f, 1f, 270f, 180f, 3, shape == Shape.RING ? 3 : 2);
			var coarse = mesh(shape, minimal);
			checkNormals(coarse, shape, minimal);
			var dense = new Parameters(0.5f, 0.3f, 1f, 270f, 120f,
					PhotonModelPrimitiveUtil.MAX_SEGMENTS, PhotonModelPrimitiveUtil.MAX_RINGS);
			mesh(shape, dense);
		}

		// 奇数分段避免仅覆盖正交方向或与半圈对齐的采样。
		var odd = new Parameters(1.25f, 0.4f, 2.5f, 237f, 127f, 17, 9);
		for (var shape : Shape.values()) {
			checkNormals(mesh(shape, odd), shape, odd);
		}
	}

	@Test
	public void rejectedParametersPreserveAsset() {
		var asset = mesh(Shape.SPHERE, Parameters.defaults());
		int vertices = asset.vertices.size();
		int faces = asset.faces.size();
		int parts = asset.parts.size();
		var firstVertex = asset.vertices.getFirst();
		var invalid = new Parameters[]{
				new Parameters(Float.NaN, 0.3f, 1f, 180f, 90f, 16, 8),
				new Parameters(0f, 0.3f, 1f, 180f, 90f, 16, 8),
				new Parameters(0.5f, 0.3f, 1f, 180f, 90f, 2, 8),
				new Parameters(0.5f, 0.3f, 1f, 180f, 90f, 16, 0),
				new Parameters(0.5f, 1f, 1f, 180f, 90f, 16, 8),
				new Parameters(0.5f, 0.3f, 1f, 361f, 90f, 16, 8)
		};

		for (var parameters : invalid) {
			rejects(() -> PhotonModelPrimitiveUtil.add(asset, Shape.RING, parameters));
			check(asset.vertices.size() == vertices && asset.faces.size() == faces && asset.parts.size() == parts,
					"rejected parameters preserve counts");
			check(asset.vertices.getFirst() == firstVertex, "rejected parameters preserve existing objects");
		}

		var first = PhotonModelPrimitiveUtil.add(asset, Shape.SPHERE, Parameters.defaults());
		var second = PhotonModelPrimitiveUtil.add(asset, Shape.SPHERE, Parameters.defaults());
		check(!first.id.equals(second.id), "unique part ids");
		asset.validate();
	}

	private static PhotonModelAsset mesh(Shape shape, Parameters parameters) {
		var mesh = new PhotonModelAsset();
		try {
			PhotonModelPrimitiveUtil.add(mesh, shape, parameters);
			mesh.validate();
		} catch (RuntimeException exception) {
			throw new AssertionError(shape + " " + parameters, exception);
		}
		return mesh;
	}

	private static void checkNormals(PhotonModelAsset mesh, Shape shape, Parameters parameters) {
		for (var face : mesh.faces) {
			var normal = PhotonModelPolygonUtil.normal(mesh.vertices, face.vertices);
			var outward = switch (shape) {
				case ARC -> new Vector3f(0, 0, 1);
				case SPHERE, SPHERICAL_PATCH -> center(mesh, face);
				case RING -> ringOutward(mesh, face, parameters.radius());
				case CYLINDRICAL_PATCH -> center(mesh, face).mul(1, 1, 0);
				case CYLINDER, CONE -> radialOutward(mesh, face, parameters.height());
			};
			check(normal.dot(outward) > 0f, shape + " outward winding");
		}
	}

	private static Vector3f radialOutward(PhotonModelAsset mesh, Face face, float height) {
		var center = center(mesh, face);
		if (center.z == 0f) {
			return new Vector3f(0, 0, -1);
		}
		if (center.z == height) {
			return new Vector3f(0, 0, 1);
		}
		return center.mul(1, 1, 0);
	}

	private static Vector3f ringOutward(PhotonModelAsset mesh, Face face, float radius) {
		var normal = new Vector3f();
		for (int index : face.vertices) {
			var vertex = mesh.vertices.get(index);
			float length = (float) Math.hypot(vertex.x, vertex.y);
			normal.add(vertex.x - radius * vertex.x / length, vertex.y - radius * vertex.y / length, vertex.z);
		}
		return normal;
	}

	private static Vector3f center(PhotonModelAsset mesh, Face face) {
		var center = new Vector3f();
		for (int index : face.vertices) {
			center.add(mesh.vertices.get(index));
		}
		return center.div(face.vertices.length);
	}

	private static void checkUVBounds(PhotonModelAsset mesh) {
		for (var face : mesh.faces) {
			for (var uv : face.uv) {
				check(uv.x >= 0f && uv.x <= 1f && uv.y >= 0f && uv.y <= 1f, "UV bounds");
			}
		}
	}

	private static void checkSeam(PhotonModelAsset mesh, int axis) {
		var values = new HashMap<Integer, Set<Float>>();
		for (var face : mesh.faces) {
			for (int corner = 0; corner < face.vertices.length; corner++) {
				values.computeIfAbsent(face.vertices[corner], _ -> new HashSet<>()).add(face.uv[corner].get(axis));
			}
		}
		check(values.values().stream().anyMatch(set -> set.contains(0f) && set.contains(1f)),
				"shared geometry keeps UV seam on axis " + axis);
	}

	private static void rejects(Runnable action) {
		assertThrows(IllegalArgumentException.class, action::run);
	}

	private static void check(boolean condition, String message) {
		assertTrue(condition, message);
	}
}
