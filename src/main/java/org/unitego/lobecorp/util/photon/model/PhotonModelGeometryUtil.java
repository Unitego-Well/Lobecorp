package org.unitego.lobecorp.util.photon.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.world.level.CardinalLighting;

/// 向原生Photon QUADS批次提交三角形，重复末角以保留其透明排序路径。
public class PhotonModelGeometryUtil {
	private static final float COLOR_CHANNEL_MAX = 255;

	public static int write(VertexConsumer output, PhotonModelPoseSampler.Frame frame, int groupIndex,
	                        Matrix4f world, int color, int light) {
		var topology = frame.prepared().groups().get(groupIndex);
		var group = frame.groups().get(groupIndex);
		var part = frame.prepared().definition().parts().get(topology.part());
		float determinant = world.determinant3x3();
		var normalMatrix = determinant == 0 ? new Matrix3f().zero() : world.normal(new Matrix3f());
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
