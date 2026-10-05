package org.unitego.lobecorp.util.photon.emitter;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.emitter.PhotonEmitterSpawnShape;

/**
 * 采样本地空间的位置；球和圆按体积或面积均匀分布。
 */
public class PhotonEmitterSpawnShapeUtil {
	public static Vector3f sample(PhotonEmitterSpawnShape shape, RandomSource random) {
		if (shape.type == null || !shape.size.isFinite() || !shape.offset.isFinite()
				|| shape.size.x < 0 || shape.size.y < 0 || shape.size.z < 0) {
			throw new IllegalArgumentException();
		}

		var position = switch (shape.type) {
			case POINT -> new Vector3f();
			case BOX ->
					new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1);
			case SPHERE -> sphere(random);
			case CIRCLE -> circle(random);
		};
		return position.mul(shape.size).add(shape.offset);
	}

	private static Vector3f sphere(RandomSource random) {
		var z = random.nextFloat() * 2 - 1;
		var angle = random.nextFloat() * Mth.TWO_PI;
		var radius = (float) Math.cbrt(random.nextFloat());
		var ring = (float) Math.sqrt(1 - z * z);
		return new Vector3f(Mth.cos(angle) * ring, z, Mth.sin(angle) * ring).mul(radius);
	}

	private static Vector3f circle(RandomSource random) {
		var angle = random.nextFloat() * Mth.TWO_PI;
		var radius = (float) Math.sqrt(random.nextFloat());
		return new Vector3f(Mth.cos(angle) * radius, 0, Mth.sin(angle) * radius);
	}
}

