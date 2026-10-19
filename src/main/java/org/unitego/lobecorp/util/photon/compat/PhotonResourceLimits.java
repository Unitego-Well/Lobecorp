package org.unitego.lobecorp.util.photon.compat;

/// 读取预算按单个源文件或整包计算；与可播放能力无关。
public record PhotonResourceLimits(long sourceBytes, long unpackedBytes, long nbtHeapBytes,
                                   int depth, int entries, int jsonNodes) {
	/// 原始文件64MiB、整包展开128MiB，另限制NBT对象估算、递归与条目。
	public static final PhotonResourceLimits DEFAULT = new PhotonResourceLimits(
			64L << 20, 128L << 20, 128L << 20, 128, 4096, 1_000_000);

	public PhotonResourceLimits {
		if (sourceBytes <= 0 || sourceBytes >= Integer.MAX_VALUE || unpackedBytes <= 0 || nbtHeapBytes <= 0
				|| depth <= 0 || entries <= 0 || jsonNodes <= 0) {
			throw new IllegalArgumentException("资源读取预算必须为正且可由数组表示。");
		}
	}
}
