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

	/// 只读局部姿态视图；C 的紧凑缓冲与公开 Frame 都通过同一提交数学写出。
	public interface LocalPoseView {
		PhotonModelTopologyUtil.Prepared prepared();

		float opacity();

		float groupOpacity(int groupIndex);

		/// attribute: 0..2 position, 3..5 normal, 6..7 uv.
		float attribute(int groupIndex, int cornerIndex, int attribute);
	}

	public static int write(VertexConsumer output, PhotonModelPoseSampler.Frame frame, int groupIndex,
	                        Matrix4f world, int color, int light) {
		return write(output, frame, groupIndex, world, color, light, null);
	}

	public static int write(VertexConsumer output, PhotonModelPoseSampler.Frame frame, int groupIndex,
	                        Matrix4f world, int color, int light, @Nullable NormalCache cache) {
		return writeView(output, new FramePoseView(frame), groupIndex, world, color, light, cache);
	}

	public static int writePrimitive(VertexConsumer output, LocalPoseView pose, int groupIndex,
	                                 Matrix4f world, int color, int light, @Nullable NormalCache cache) {
		return writeView(output, pose, groupIndex, world, color, light, cache);
	}

	public static int writePrimitive(VertexConsumer output, LocalPoseView pose, int groupIndex,
	                                 Matrix4f world, int color, int light) {
		return writePrimitive(output, pose, groupIndex, world, color, light, null);
	}

	private static int writeView(VertexConsumer output, LocalPoseView pose, int groupIndex,
	                             Matrix4f world, int color, int light, @Nullable NormalCache cache) {
		var topology = pose.prepared().groups().get(groupIndex);
		var part = pose.prepared().definition().parts().get(topology.part());
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
				int source = indices.get(triangle + ordered);
				world.transformPosition(position.set(pose.attribute(groupIndex, source, 0),
						pose.attribute(groupIndex, source, 1), pose.attribute(groupIndex, source, 2)));
				normalMatrix.transform(normal.set(pose.attribute(groupIndex, source, 3),
						pose.attribute(groupIndex, source, 4), pose.attribute(groupIndex, source, 5)));
				normal.mul(Math.signum(determinant));

				if (normal.lengthSquared() > 0 && normal.isFinite()) {
					normal.normalize();
				} else {
					if (fallback == null) {
						var a = point(pose, groupIndex, indices.get(triangle), world);
						var b = point(pose, groupIndex, indices.get(triangle + (part.insideOut() ? 2 : 1)), world);
						var c = point(pose, groupIndex, indices.get(triangle + (part.insideOut() ? 1 : 2)), world);
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
				float alpha = (color >>> 24) / COLOR_CHANNEL_MAX * pose.opacity() * pose.groupOpacity(groupIndex);
				output.addVertex(position.x, position.y, position.z)
						.setUv(pose.attribute(groupIndex, source, 6), pose.attribute(groupIndex, source, 7))
						.setColor(((color >>> 16) & 0xFF) / COLOR_CHANNEL_MAX * shade,
								((color >>> 8) & 0xFF) / COLOR_CHANNEL_MAX * shade,
								(color & 0xFF) / COLOR_CHANNEL_MAX * shade, alpha)
						.setLight(light).setNormal(normal.x, normal.y, normal.z);
			}
		}

		return indices.size() / 3;
	}

	private record FramePoseView(PhotonModelPoseSampler.Frame frame) implements LocalPoseView {

		@Override
		public PhotonModelTopologyUtil.Prepared prepared() {
			return frame.prepared();
		}

		@Override
		public float opacity() {
			return frame.opacity();
		}

		@Override
		public float groupOpacity(int groupIndex) {
			return frame.groups().get(groupIndex).opacity();
		}

		@Override
		public float attribute(int groupIndex, int cornerIndex, int attribute) {
			var vertex = frame.groups().get(groupIndex).vertices().get(cornerIndex);

			return switch (attribute) {
				case 0 -> vertex.position().x();
				case 1 -> vertex.position().y();
				case 2 -> vertex.position().z();
				case 3 -> vertex.normal().x();
				case 4 -> vertex.normal().y();
				case 5 -> vertex.normal().z();
				case 6 -> vertex.uv().x();
				case 7 -> vertex.uv().y();
				default -> throw new IllegalArgumentException("未知局部姿态属性：" + attribute);
			};
		}
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

	private static Vector3f point(LocalPoseView pose, int groupIndex, int cornerIndex, Matrix4f world) {
		return world.transformPosition(new Vector3f(pose.attribute(groupIndex, cornerIndex, 0),
				pose.attribute(groupIndex, cornerIndex, 1), pose.attribute(groupIndex, cornerIndex, 2)));
	}

	private static float shade(Vector3f normal) {
		var lighting = CardinalLighting.DEFAULT;
		return normal.x * normal.x * (normal.x < 0 ? lighting.west() : lighting.east())
				+ normal.y * normal.y * (normal.y < 0 ? lighting.down() : lighting.up())
				+ normal.z * normal.z * (normal.z < 0 ? lighting.north() : lighting.south());
	}
}
