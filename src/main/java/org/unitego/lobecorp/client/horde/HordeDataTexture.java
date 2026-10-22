package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;

/// 通过 RGBA8 无损保存浮点位模式；只使用整数纹素读取，不执行颜色过滤。
public class HordeDataTexture implements AutoCloseable {
	private static final int ROW_WIDTH = 1024;
	private final DynamicTexture texture;

	public HordeDataTexture(String label, float[] values) {
		int width = Math.clamp(values.length, 1, ROW_WIDTH);
		int height = Math.ceilDiv(Math.max(1, values.length), width);
		if (height > RenderSystem.getDevice().getMaxTextureSize()) {
			throw new IllegalArgumentException("群体数据纹理超过设备尺寸：" + label);
		}

		var image = new NativeImage(width, height, true);
		try {
			for (int i = 0; i < values.length; i++) {
				image.setPixelABGR(i % width, i / width, Float.floatToRawIntBits(values[i]));
			}
			texture = new DynamicTexture(() -> label, image);
		} catch (RuntimeException exception) {
			image.close();
			throw exception;
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
