package org.unitego.lobecorp.util.photon.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import net.minecraft.world.level.CardinalLighting;

import javax.annotation.Nullable;

/// 向原生Photon QUADS批次提交三角形，重复末角以保留其透明排序路径。
public class PhotonModelGeometryUtil {
	private static final float COLOR_CHANNEL_MAX = 255;

	public static int write(VertexConsumer output, PhotonModelPoseSampler.Frame frame, int groupIndex,
	                        Matrix4f world, int color, int light) {
		return write(output, frame, groupIndex, world, color, light, null);
	}

	public static int write(VertexConsumer output, PhotonModelPoseSampler.Frame frame, int groupIndex,
	                        Matrix4f world, int color, int light, @Nullable NormalCache cache) {
		var topology = frame.prepared().groups().get(groupIndex);
		var group = frame.groups().get(groupIndex);
		var part = frame.prepared().definition().parts().get(topology.part());
		// 热循环只读取调用局部矩阵，不持有缓存中可变矩阵的别名。
		var normalMatrix = cache == null ? new Matrix3f() : new Matrix3f(cache.update(world));
		float determinant = cache == null ? world.determinant3x3() : cache.determinant;

		if (cache == null) {
			if (determinant == 0) {
				normalMatrix.zero();
			} else {
				world.normal(normalMatrix);
			}
		}

		var position = new Vector3f();
		var normal = new Vector3f();
		var indices = topology.indices();

		for (int triangle = 0; triangle < indices.size(); triangle += 3) {
			Vector3f fallback = null;

			for (int corner = 0; corner < 4; corner++) {
				int offset = Math.min(corner, 2);
				int ordered = part.insideOut() && offset != 0 ? 3 - offset : offset;
				var vertex = group.vertices().get(indices.get(triangle + ordered));
				var point = vertex.position();
				var direction = vertex.normal();
				world.transformPosition(position.set(point.x(), point.y(), point.z()));
				normalMatrix.transform(normal.set(direction.x(), direction.y(), direction.z()));
				normal.mul(Math.signum(determinant));

				if (normal.lengthSquared() > 0 && normal.isFinite()) {
					normal.normalize();
				} else {
					if (fallback == null) {
						var a = point(group.vertices().get(indices.get(triangle)), world);
						var b = point(group.vertices().get(indices.get(triangle + (part.insideOut() ? 2 : 1))), world);
						var c = point(group.vertices().get(indices.get(triangle + (part.insideOut() ? 1 : 2))), world);
						fallback = b.sub(a).cross(c.sub(a));

						if (fallback.isFinite() && fallback.lengthSquared() > 0) {
							fallback.normalize();
						} else {
							fallback.zero();
						}
					}

					normal.set(fallback);
				}

				float shade = part.shading() ? shade(normal) : 1;
				float alpha = (color >>> 24) / COLOR_CHANNEL_MAX * frame.opacity() * group.opacity();
				output.addVertex(position.x, position.y, position.z).setUv(vertex.uv().x(), vertex.uv().y())
						.setColor(((color >>> 16) & 0xFF) / COLOR_CHANNEL_MAX * shade,
								((color >>> 8) & 0xFF) / COLOR_CHANNEL_MAX * shade,
								(color & 0xFF) / COLOR_CHANNEL_MAX * shade, alpha)
						.setLight(light).setNormal(normal.x, normal.y, normal.z);
			}
		}

		return indices.size() / 3;
	}

	/// 单个模型输出在客户端绘制时独占；返回的矩阵只读借用，不保存在延迟绘制任务中。
	public static class NormalCache {
		private final Matrix3f linear = new Matrix3f();
		private final Matrix3f normal = new Matrix3f();
		private boolean valid;
		private int properties;
		private float determinant;

		public Matrix3fc update(Matrix4f world) {
			int nextProperties = world.properties() & Matrix4fc.PROPERTY_ORTHONORMAL;

			if (!valid || properties != nextProperties || !matches(world)) {
				linear.set(world);
				determinant = world.determinant3x3();

				if (determinant == 0) {
					normal.zero();
				} else {
					world.normal(normal);
				}

				properties = nextProperties;
				valid = true;
			}

			return normal;
		}

		private boolean matches(Matrix4f world) {
			return same(linear.m00(), world.m00()) && same(linear.m01(), world.m01())
					&& same(linear.m02(), world.m02()) && same(linear.m10(), world.m10())
					&& same(linear.m11(), world.m11()) && same(linear.m12(), world.m12())
					&& same(linear.m20(), world.m20()) && same(linear.m21(), world.m21())
					&& same(linear.m22(), world.m22());
		}

		private static boolean same(float first, float second) {
			return Float.floatToRawIntBits(first) == Float.floatToRawIntBits(second);
		}
	}

	private static Vector3f point(PhotonModelPoseSampler.Vertex vertex, Matrix4f world) {
		var value = vertex.position();
		return world.transformPosition(new Vector3f(value.x(), value.y(), value.z()));
	}

	private static float shade(Vector3f normal) {
		var lighting = CardinalLighting.DEFAULT;
		return normal.x * normal.x * (normal.x < 0 ? lighting.west() : lighting.east())
				+ normal.y * normal.y * (normal.y < 0 ? lighting.down() : lighting.up())
				+ normal.z * normal.z * (normal.z < 0 ? lighting.north() : lighting.south());
	}
}
