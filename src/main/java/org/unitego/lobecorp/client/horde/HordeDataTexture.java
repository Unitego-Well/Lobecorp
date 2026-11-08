package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.function.IntUnaryOperator;

/// RGBA8保存原始32位字；整数直接提交，不经过可能改变NaN位模式的浮点转换。
public class HordeDataTexture implements AutoCloseable {
	private static final int ROW_WIDTH = 1024;
	private final DynamicTexture texture;

	public HordeDataTexture(String label, float[] values) {
		this(label, values.length, index -> Float.floatToRawIntBits(values[index]));
	}

	public HordeDataTexture(String label, int[] values) {
		this(label, values.length, index -> values[index]);
	}

	private HordeDataTexture(String label, int length, IntUnaryOperator words) {
		int width = Math.clamp(length, 1, ROW_WIDTH);
		int height = Math.ceilDiv(Math.max(1, length), width);
		if (height > RenderSystem.getDevice().getMaxTextureSize()) {
			throw new IllegalArgumentException("群体数据纹理超过设备尺寸：" + label);
		}

		var image = new NativeImage(width, height, true);
		try {
			for (int i = 0; i < length; i++) {
				image.setPixelABGR(i % width, i / width, words.applyAsInt(i));
			}
			texture = new DynamicTexture(() -> label, image);
		} catch (RuntimeException exception) {
			image.close();
			throw exception;
		}
	}

	public void update(int first, float[] values, int source, int length) {
		updateWords(first, length, index -> Float.floatToRawIntBits(values[source + index]));
	}

	public void update(int first, int[] values, int source, int length) {
		updateWords(first, length, index -> values[source + index]);
	}

	private void updateWords(int first, int length, IntUnaryOperator words) {
		if (length <= 0) {
			return;
		}

		var image = texture.getPixels();
		int width = image.getWidth();
		for (int copied = 0; copied < length; ) {
			int at = first + copied;
			int x = at % width;
			int y = at / width;
			int count = Math.min(width - x, length - copied);
			for (int i = 0; i < count; i++) {
				image.setPixelABGR(x + i, y, words.applyAsInt(copied + i));
			}
			copied += count;
		}

		// 覆盖区只分首段、完整行矩形和尾段三次提交，避免逐行产生碎片命令。
		int x = first % width;
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		int copied = 0;
		if (x != 0) {
			int firstCount = Math.min(width - x, length);
			encoder.writeToTexture(texture.getTexture(), image, 0, 0, x, first / width, firstCount, 1, x, first / width);
			copied = firstCount;
		}
		int rows = (length - copied) / width;
		if (rows > 0) {
			int y = (first + copied) / width;
			encoder.writeToTexture(texture.getTexture(), image, 0, 0, 0, y, width, rows, 0, y);
			copied += rows * width;
		}
		if (copied < length) {
			int tailY = (first + copied) / width;
			encoder.writeToTexture(texture.getTexture(), image, 0, 0, 0, tailY, length - copied, 1, 0, tailY);
		}
	}

	public DynamicTexture texture() {
		return texture;
	}

	@Override
	public void close() {
		texture.close();
	}
}
