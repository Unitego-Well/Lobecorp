package org.unitego.lobecorp.util.photon.model;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/// 稳定帧实验的纯 CPU 顶点编码；不改变提交顺序，格式或数值不支持时交回原写入链。
public class PhotonPackedVertexUtil {
	/// 由输出集成读取的显式实验开关，缺省关闭；编码工具自身不读取全局状态。
	public static final String ENABLE_PROPERTY = "lobecorp.photon.packedSnapshot";
	/// 原生 Photon 粒子顶点包含位置、颜色、UV、光照、法线与一个填充字节。
	public static final int VERTEX_STRIDE = 32;
	/// 与稳定帧快照沿用同一顶点预算；超出时保留原链。
	public static final int MAX_VERTICES = 32768;
	/// 单个输出持有的全部 packed 快照总预算，由拥有缓存的输出负责执行。
	public static final int MAX_CACHED_BYTES = 4 * 1024 * 1024;
	private static final int SOURCE_STRIDE = 12;

	public static @Nullable byte[] pack(float[] vertices, int[] lights, int count) {
		if (!validCount(count) || vertices == null || lights == null
				|| vertices.length < (long) count * SOURCE_STRIDE || lights.length < count) {
			return null;
		}

		int sourceLength = count * SOURCE_STRIDE;

		for (int index = 0; index < sourceLength; index++) {
			if (!Float.isFinite(vertices[index])) {
				return null;
			}
		}

		var packed = ByteBuffer.allocate(count * VERTEX_STRIDE).order(ByteOrder.nativeOrder());

		for (int index = 0, offset = 0; index < count; index++, offset += SOURCE_STRIDE) {
			packed.putFloat(vertices[offset]).putFloat(vertices[offset + 1]).putFloat(vertices[offset + 2]);

			for (int channel = 0; channel < 4; channel++) {
				packed.put((byte) (int) (vertices[offset + 8 + channel] * 255.0F));
			}

			packed.putFloat(vertices[offset + 6]).putFloat(vertices[offset + 7]);
			packed.putShort((short) lights[index]).putShort((short) (lights[index] >>> 16));

			for (int axis = 0; axis < 3; axis++) {
				packed.put((byte) (int) (Math.clamp(vertices[offset + 3 + axis], -1.0F, 1.0F) * 127.0F));
			}

			packed.put((byte) 0);
		}

		return packed.array();
	}

	public static boolean validPayload(byte[] packed, int count) {
		long size = (long) count * VERTEX_STRIDE;
		return validCount(count) && packed != null && size == packed.length;
	}

	public static boolean supportsLayout(int stride, int initialMask, int[] offsets) {
		if (stride != VERTEX_STRIDE || initialMask != 0x36 || offsets == null || offsets.length != 32) {
			return false;
		}

		for (int index = 0; index < offsets.length; index++) {
			int expected = switch (index) {
				case 0 -> 0;
				case 1 -> 12;
				case 2 -> 16;
				case 4 -> 24;
				case 5 -> 28;
				default -> -1;
			};

			if (offsets[index] != expected) {
				return false;
			}
		}

		return true;
	}

	private static boolean validCount(int count) {
		return count > 0 && count <= MAX_VERTICES && count % 4 == 0;
	}
}
