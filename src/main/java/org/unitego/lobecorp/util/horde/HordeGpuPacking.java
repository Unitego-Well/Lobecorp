package org.unitego.lobecorp.util.horde;

/// 路线只上传六个有效浮点字；两份相对高度共用一个32位字。
public class HordeGpuPacking {
	/// CPU路线保留原布局，避免改变粒子与调试采样的输入契约。
	public static final int SOURCE_ROUTE_WORDS = 16;
	public static final int ROUTE_WORDS = 6;
	/// 高度步长1/8格，最大舍入误差1/16格，可覆盖合法维度的全高度差。
	public static final int HEIGHT_SCALE = 8;

	public static float[] route(float[] source, int nodes) {
		int lookup = source.length - nodes * SOURCE_ROUTE_WORDS;
		var packed = new float[nodes * ROUTE_WORDS + lookup];
		copyRoute(source, packed, 0, nodes);
		System.arraycopy(source, nodes * SOURCE_ROUTE_WORDS, packed, nodes * ROUTE_WORDS, lookup);
		return packed;
	}

	public static void copyRoute(float[] source, float[] target, int first, int last) {
		for (int node = first; node < last; node++) {
			System.arraycopy(source, node * SOURCE_ROUTE_WORDS, target, node * ROUTE_WORDS, ROUTE_WORDS);
		}
	}

	/// 首尾按双样本边界扩展，保留与相邻脏节点共用字中的另一份高度。
	public static void heights(float[] source, int[] target, int first, int last) {
		for (int word = first / 2; word < Math.ceilDiv(last, 2); word++) {
			int sample = word * 2;
			int low = height(source[sample]);
			int high = sample + 1 < source.length ? height(source[sample + 1]) : 0;
			target[word] = (low & 0xffff) | ((high & 0xffff) << 16);
		}
	}

	private static int height(float value) {
		int encoded = Math.round(value * HEIGHT_SCALE);
		if (!Float.isFinite(value) || encoded < Short.MIN_VALUE || encoded > Short.MAX_VALUE) {
			throw new IllegalArgumentException("群体相对地形高度超出压缩范围：" + value);
		}

		return encoded;
	}
}
