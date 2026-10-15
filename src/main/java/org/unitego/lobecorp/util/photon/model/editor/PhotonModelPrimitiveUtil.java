package org.unitego.lobecorp.util.photon.model.editor;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Face;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Part;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

import java.util.Arrays;
import java.util.Locale;

/// 生成共享几何顶点与独立面角 UV 的基本体，不创建材质槽。
public class PhotonModelPrimitiveUtil {
	/// 交互建模的分段上限，限制一次新增的网格规模。
	public static final int MAX_SEGMENTS = 256;
	public static final int MAX_RINGS = 128;
	public static final int MIN_SEGMENTS = 3;
	/// 模型单位的可编辑尺寸范围，避免零面积与极端浮点误差。
	public static final float MIN_SIZE = 0.0001f;
	public static final float MAX_SIZE = 10000f;
	public static final float FULL_ANGLE = 360f;
	public static final float HALF_ANGLE = 180f;
	/// 角度单位为度；小于此范围的曲面难以稳定拾取。
	public static final float MIN_ANGLE = 0.1f;
	private static final float DEFAULT_RADIUS = 0.5f;
	private static final float DEFAULT_WIDTH = 0.3f;
	private static final float DEFAULT_HEIGHT = 1f;
	private static final float DEFAULT_VERTICAL_ANGLE = 90f;
	private static final int DEFAULT_RINGS = 8;

	public enum Shape {
		CYLINDER, CONE, RING, SPHERE, ARC, CYLINDRICAL_PATCH, SPHERICAL_PATCH
	}

	/// 尺寸使用模型单位，角度使用度。圆环 radius 为中心线半径，width 为管直径；
	/// 弧带 width 为带宽。球面片 verticalAngle 为关于赤道对称的纬度总跨度。
	/// segments 为周向分段；rings 为高度、纬度或圆环管截面的分段。
	public record Parameters(float radius, float width, float height, float angle, float verticalAngle,
	                         int segments, int rings) {
		public static Parameters defaults() {
			return new Parameters(DEFAULT_RADIUS, DEFAULT_WIDTH, DEFAULT_HEIGHT, HALF_ANGLE,
					DEFAULT_VERTICAL_ANGLE, PhotonModelEditUtil.RADIAL_SEGMENTS, DEFAULT_RINGS);
		}
	}

	/// 先独立生成并校验，成功后才追加到目标资产；调用者负责历史事务及材质分配。
	public static Part add(PhotonModelAsset asset, Shape shape, Parameters parameters) {
		validate(shape, parameters);
		var mesh = new PhotonModelAsset();
		mesh.parts.clear();
		var part = new Part();
		String stem = shape.name().toLowerCase(Locale.ROOT);
		var existingIds = asset.parts.stream().map(existing -> existing.id).toList();
		part.id = stem;
		int suffix = 1;

		while (existingIds.contains(part.id)) {
			part.id = stem + "_" + suffix++;
		}

		mesh.parts.add(part);
		switch (shape) {
			case CYLINDER, CONE -> radial(mesh, part.id, parameters, shape == Shape.CONE);
			case RING -> ring(mesh, part.id, parameters);
			case SPHERE, SPHERICAL_PATCH -> sphere(mesh, part.id, parameters, shape == Shape.SPHERE);
			case ARC -> arc(mesh, part.id, parameters);
			case CYLINDRICAL_PATCH -> cylindricalPatch(mesh, part.id, parameters);
		}

		try {
			mesh.validate();
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException(PhotonModelTextUtil.key("primitive.invalid"), exception);
		}

		int offset = asset.vertices.size();
		for (var face : mesh.faces) {
			for (int corner = 0; corner < face.vertices.length; corner++) {
				face.vertices[corner] += offset;
			}
		}

		asset.parts.add(part);
		asset.vertices.addAll(mesh.vertices);
		asset.faces.addAll(mesh.faces);
		return part;
	}

	private static void validate(Shape shape, Parameters parameters) {
		if (shape == null || parameters == null) {
			throw new IllegalArgumentException(PhotonModelTextUtil.key("primitive.invalid"));
		}

		boolean valid = size(parameters.radius) && size(parameters.width) && size(parameters.height)
				&& angle(parameters.angle, FULL_ANGLE) && angle(parameters.verticalAngle, HALF_ANGLE)
				&& parameters.segments >= MIN_SEGMENTS && parameters.segments <= MAX_SEGMENTS
				&& parameters.rings >= 1 && parameters.rings <= MAX_RINGS;
		valid &= switch (shape) {
			case ARC, RING -> parameters.radius - parameters.width / 2f >= MIN_SIZE;
			case SPHERE, SPHERICAL_PATCH -> parameters.rings >= 2;
			default -> true;
		};
		valid &= shape != Shape.RING || parameters.rings >= MIN_SEGMENTS;

		if (!valid) {
			throw new IllegalArgumentException(PhotonModelTextUtil.key("primitive.invalid"));
		}
	}

	private static boolean size(float value) {
		return Float.isFinite(value) && value >= MIN_SIZE && value <= MAX_SIZE;
	}

	private static boolean angle(float value, float maximum) {
		return Float.isFinite(value) && value >= MIN_ANGLE && value <= maximum;
	}

	private static void radial(PhotonModelAsset mesh, String part, Parameters parameters, boolean cone) {
		int columns = parameters.segments;
		int rows = parameters.rings;
		int[][] indices = new int[rows + 1][columns];

		for (int row = 0; row <= rows; row++) {
			float fraction = (float) row / rows;
			float radius = parameters.radius * (cone ? 1f - fraction : 1f);
			float height = parameters.height * fraction;

			if (cone && row == rows) {
				int pole = vertex(mesh, 0, 0, height);
				Arrays.fill(indices[row], pole);
				continue;
			}

			for (int column = 0; column < columns; column++) {
				double angle = Math.TAU * column / columns;
				indices[row][column] = polarVertex(mesh, radius, angle, height);
			}
		}

		gridFaces(mesh, part, indices, columns, rows, true);
		cap(mesh, part, indices[0], parameters.radius, false);

		if (!cone) {
			cap(mesh, part, indices[rows], parameters.radius, true);
		}
	}

	private static void cap(PhotonModelAsset mesh, String part, int[] indices, float radius, boolean top) {
		int[] vertices = new int[indices.length];
		Vector2f[] uv = new Vector2f[indices.length];

		for (int corner = 0; corner < indices.length; corner++) {
			int source = top ? corner : indices.length - corner - 1;
			vertices[corner] = indices[source];
			var position = mesh.vertices.get(vertices[corner]);
			uv[corner] = new Vector2f(0.5f + position.x / (radius * 2f),
					0.5f - position.y / (radius * 2f));
		}

		face(mesh, part, vertices, uv);
	}

	private static void cylindricalPatch(PhotonModelAsset mesh, String part, Parameters parameters) {
		boolean closed = parameters.angle == FULL_ANGLE;
		int columns = parameters.segments;
		int rows = parameters.rings;
		int[][] indices = new int[rows + 1][closed ? columns : columns + 1];

		for (int row = 0; row <= rows; row++) {
			for (int column = 0; column < indices[row].length; column++) {
				double angle = Math.toRadians(parameters.angle) * column / columns;
				indices[row][column] = polarVertex(mesh, parameters.radius, angle,
						parameters.height * row / rows);
			}
		}

		gridFaces(mesh, part, indices, columns, rows, closed);
	}

	private static void sphere(PhotonModelAsset mesh, String part, Parameters parameters, boolean full) {
		float horizontal = full ? FULL_ANGLE : parameters.angle;
		float vertical = full ? HALF_ANGLE : parameters.verticalAngle;
		boolean closed = horizontal == FULL_ANGLE;
		int columns = parameters.segments;
		int rows = parameters.rings;
		int[][] indices = new int[rows + 1][closed ? columns : columns + 1];

		for (int row = 0; row <= rows; row++) {
			double latitude = Math.toRadians(vertical) * ((double) row / rows - 0.5d);
			float height = (float) Math.sin(latitude) * parameters.radius;

			if (vertical == HALF_ANGLE && (row == 0 || row == rows)) {
				int pole = vertex(mesh, 0, 0, height);
				Arrays.fill(indices[row], pole);
				continue;
			}

			float radius = (float) Math.cos(latitude) * parameters.radius;
			for (int column = 0; column < indices[row].length; column++) {
				double longitude = Math.toRadians(horizontal) * column / columns;
				indices[row][column] = polarVertex(mesh, radius, longitude, height);
			}
		}

		gridFaces(mesh, part, indices, columns, rows, closed);
	}

	private static void ring(PhotonModelAsset mesh, String part, Parameters parameters) {
		int columns = parameters.segments;
		int rows = parameters.rings;
		int[][] indices = new int[rows + 1][columns];
		float tubeRadius = parameters.width / 2f;

		for (int row = 0; row < rows; row++) {
			double tubeAngle = Math.TAU * row / rows;
			float radius = parameters.radius + tubeRadius * (float) Math.cos(tubeAngle);
			float height = tubeRadius * (float) Math.sin(tubeAngle);

			for (int column = 0; column < columns; column++) {
				indices[row][column] = polarVertex(mesh, radius, Math.TAU * column / columns, height);
			}
		}

		indices[rows] = indices[0];
		gridFaces(mesh, part, indices, columns, rows, true);
	}

	private static void arc(PhotonModelAsset mesh, String part, Parameters parameters) {
		int segments = parameters.segments;
		boolean closed = parameters.angle == FULL_ANGLE;
		int[][] indices = new int[2][closed ? segments : segments + 1];

		for (int row = 0; row < indices.length; row++) {
			float radius = parameters.radius + (row == 0 ? 0.5f : -0.5f) * parameters.width;
			for (int column = 0; column < indices[row].length; column++) {
				double angle = Math.toRadians(parameters.angle) * column / segments;
				indices[row][column] = polarVertex(mesh, radius, angle, 0);
			}
		}

		gridFaces(mesh, part, indices, segments, 1, closed);
	}

	/// 周向闭合仅复用几何索引，UV 在接缝两侧分别保留 0 和 1。
	private static void gridFaces(PhotonModelAsset mesh, String part, int[][] indices, int columns, int rows,
	                              boolean closed) {
		for (int row = 0; row < rows; row++) {
			float bottom = 1f - (float) row / rows;
			float top = 1f - (float) (row + 1) / rows;

			for (int column = 0; column < columns; column++) {
				int next = closed ? (column + 1) % columns : column + 1;
				int a = indices[row][column];
				int b = indices[row][next];
				int c = indices[row + 1][next];
				int d = indices[row + 1][column];
				float left = (float) column / columns;
				float right = (float) (column + 1) / columns;

				if (a == b) {
					face(mesh, part, new int[]{a, c, d}, new Vector2f[]{
							new Vector2f((left + right) / 2f, bottom), new Vector2f(right, top),
							new Vector2f(left, top)});
				} else if (c == d) {
					face(mesh, part, new int[]{a, b, c}, new Vector2f[]{
							new Vector2f(left, bottom), new Vector2f(right, bottom),
							new Vector2f((left + right) / 2f, top)});
				} else {
					face(mesh, part, new int[]{a, b, c, d}, new Vector2f[]{
							new Vector2f(left, bottom), new Vector2f(right, bottom),
							new Vector2f(right, top), new Vector2f(left, top)});
				}
			}
		}
	}

	private static int polarVertex(PhotonModelAsset mesh, float radius, double angle, float height) {
		return vertex(mesh, (float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius, height);
	}

	private static int vertex(PhotonModelAsset mesh, float x, float y, float z) {
		int index = mesh.vertices.size();
		mesh.vertices.add(new Vector3f(x, y, z));
		return index;
	}

	private static void face(PhotonModelAsset mesh, String part, int[] vertices, Vector2f[] uv) {
		var face = new Face();
		face.partId = part;
		face.vertices = vertices;
		face.uv = uv;
		mesh.faces.add(face);
	}
}
