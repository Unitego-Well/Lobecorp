package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

/// 相机附近逐格地面环形场；准备工作按列预算分摊，GPU按最终位置复用。
public class HordeNearTerrain {
	/// 近景半径64格；每刻只准备1024列，不随虚拟单位数量增加。
	private static final int SIDE = 129, PREPARE_COLUMNS = 1024, WORDS = 4;
	private static final float[] values = new float[SIDE * SIDE * WORDS];
	private static final boolean[] invalid = new boolean[SIDE * SIDE];
	private static HordeDataTexture texture;
	private static GpuBuffer parameters;
	private static ClientLevel world;
	private static long preparedTick = Long.MIN_VALUE;
	private static int cursor;
	private static boolean invalidated;

	public static void prepare() {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		if (world != level) {
			clear();
			world = level;
		}
		if (level == null) {
			return;
		}
		if (texture == null) {
			Arrays.fill(values, Float.NaN);
			texture = new HordeDataTexture("Horde near terrain", values);
			parameters = RenderSystem.getDevice().createBuffer(() -> "Horde near terrain parameters",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
		}
		flushInvalidated();
		if (preparedTick == level.getGameTime()) {
			return;
		}
		preparedTick = level.getGameTime();
		var camera = minecraft.gameRenderer.getMainCamera().position();
		int minX = (int) Math.floor(camera.x) - SIDE / 2;
		int minZ = (int) Math.floor(camera.z) - SIDE / 2;
		var memory = MemoryUtil.memAlloc(16);
		try {
			memory.putInt(minX).putInt(minZ).putInt(SIDE).putInt(WORDS).flip();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(), memory);
		} finally {
			MemoryUtil.memFree(memory);
		}

		int start = cursor, count = Math.min(PREPARE_COLUMNS, SIDE * SIDE - start);
		for (int i = start; i < start + count; i++) {
			int x = minX + Math.floorMod(i % SIDE - minX, SIDE);
			int z = minZ + Math.floorMod(i / SIDE - minZ, SIDE);
			var sample = HordeGroundCache.sample(x, z);
			values[i * WORDS] = sample == null ? Float.NaN : (float) sample.height();
			values[i * WORDS + 1] = sample != null && sample.passable() ? 1 : 0;
			values[i * WORDS + 2] = x;
			values[i * WORDS + 3] = z;
		}
		texture.update(start * WORDS, values, start * WORDS, count * WORDS);
		cursor = (start + count) % (SIDE * SIDE);
	}

	public static void invalidate(int x, int z) {
		int index = Math.floorMod(z, SIDE) * SIDE + Math.floorMod(x, SIDE);
		if (texture == null || values[index * WORDS + 2] != x || values[index * WORDS + 3] != z) {
			return;
		}
		invalid[index] = true;
		invalidated = true;
	}

	private static void flushInvalidated() {
		if (!invalidated) {
			return;
		}
		invalidated = false;
		for (int row = 0; row < SIDE; row++) {
			boolean changed = false;
			for (int column = 0; column < SIDE; column++) {
				int index = row * SIDE + column;
				if (invalid[index]) {
					values[index * WORDS] = Float.NaN;
					invalid[index] = false;
					changed = true;
				}
			}
			if (changed) {
				texture.update(row * SIDE * WORDS, values, row * SIDE * WORDS, SIDE * WORDS);
			}
		}
	}

	public static GpuTextureView view() {
		return texture.texture().getTextureView();
	}

	public static GpuBufferSlice parameters() {
		return parameters.slice();
	}

	public static void clear() {
		if (texture != null) {
			texture.close();
			texture = null;
		}
		if (parameters != null) {
			parameters.close();
			parameters = null;
		}
		world = null;
		preparedTick = Long.MIN_VALUE;
		cursor = 0;
		Arrays.fill(invalid, false);
		invalidated = false;
	}
}
