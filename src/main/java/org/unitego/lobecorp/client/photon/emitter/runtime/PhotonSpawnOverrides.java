package org.unitego.lobecorp.client.photon.emitter.runtime;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 传给生成实例的参数快照；null 表示沿用模板，向量按值保存。
 */
public record PhotonSpawnOverrides(String targetName, @Nullable Integer lifetime, @Nullable Vector3f size,
                                   @Nullable Vector4f color, @Nullable Float speed, @Nullable Float beamLength,
                                   @Nullable Float beamWidth, float playbackSpeed) {
	public PhotonSpawnOverrides {
		if (targetName == null || !Float.isFinite(playbackSpeed) || playbackSpeed < 0) {
			throw new IllegalArgumentException();
		}

		if (lifetime != null && lifetime < 0) {
			throw new IllegalArgumentException();
		}

		requireFinite(speed, false);
		requireFinite(beamLength, true);
		requireFinite(beamWidth, true);
		if (size != null) {
			if (!size.isFinite() || size.x < 0 || size.y < 0 || size.z < 0) {
				throw new IllegalArgumentException();
			}

			size = new Vector3f(size);
		}

		if (color != null) {
			if (!color.isFinite() || color.x < 0 || color.y < 0 || color.z < 0 || color.w < 0
					|| color.x > 1 || color.y > 1 || color.z > 1 || color.w > 1) {
				throw new IllegalArgumentException();
			}

			color = new Vector4f(color);
		}
	}

	@Override
	@Nullable
	public Vector3f size() {
		return size == null ? null : new Vector3f(size);
	}

	@Override
	@Nullable
	public Vector4f color() {
		return color == null ? null : new Vector4f(color);
	}

	private static void requireFinite(@Nullable Float value, boolean nonNegative) {
		if (value != null && (!Float.isFinite(value) || nonNegative && value < 0)) {
			throw new IllegalArgumentException();
		}
	}
}

