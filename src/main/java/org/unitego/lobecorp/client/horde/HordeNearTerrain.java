package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import org.unitego.lobecorp.config.HordePathVariant;

/// 相机附近逐格地面环形场；准备工作按列预算分摊，GPU按最终位置复用。
public class HordeNearTerrain {
	/// 近景半径64格；每刻只准备1024列，不随虚拟单位数量增加。
	private static final int SIDE = 129, PREPARE_COLUMNS = 1024, WORDS = 2;
	/// 高度最低有效位保存可通行标记，NaN仍表示未知；另一字保存XZ低16位标签。
	private static final int UNKNOWN = Float.floatToRawIntBits(Float.NaN);
	private static final int[] values = new int[SIDE * SIDE * WORDS];
	private static final int[] sampleX = new int[SIDE * SIDE], sampleZ = new int[SIDE * SIDE];
	private static final BitSet changed = new BitSet(SIDE * SIDE);
	private static final BitSet invalid = new BitSet(SIDE * SIDE);
	private static ByteBuffer parameterMemory;
	private static int remainingBudget;
	private static HordeDataTexture texture;
	private static GpuBuffer parameters;
	private static ClientLevel world;
	private static long preparedTick = Long.MIN_VALUE;
	private static int cursor;
	private static boolean invalidated;
	private static boolean hasWindow;
	private static int previousMinX, previousMinZ;

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
			Arrays.fill(values, UNKNOWN);
			texture = new HordeDataTexture("Horde near terrain", values);
			parameters = RenderSystem.getDevice().createBuffer(() -> "Horde near terrain parameters",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
		}
		boolean newTick = preparedTick != level.getGameTime();
		if (newTick) {
			remainingBudget = PREPARE_COLUMNS;
			preparedTick = level.getGameTime();
		}
		var camera = minecraft.gameRenderer.getMainCamera().position();
		int minX = (int) Math.floor(camera.x) - SIDE / 2;
		int minZ = (int) Math.floor(camera.z) - SIDE / 2;
		// 跳出整个窗口时清空标签，不能让压缩坐标在长距离传送后命中旧槽。
		if (hasWindow && (Math.abs((long) minX - previousMinX) >= SIDE
				|| Math.abs((long) minZ - previousMinZ) >= SIDE)) {
			Arrays.fill(values, UNKNOWN);
			invalid.clear();
			invalidated = false;
			texture.update(0, values, 0, values.length);
			cursor = 0;
		}
		boolean parametersChanged = !hasWindow || minX != previousMinX || minZ != previousMinZ;
		previousMinX = minX;
		previousMinZ = minZ;
		hasWindow = true;
		if (parametersChanged) {
			if (parameterMemory == null) {
				parameterMemory = MemoryUtil.memAlloc(16);
			}
			parameterMemory.clear();
			parameterMemory.putInt(minX).putInt(minZ).putInt(SIDE).putInt(WORDS).flip();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(), parameterMemory);
		}

		// 先处理窗口迁移，避免将本刻预算花在已离开的窗口；同刻新失效仍立即隐藏。
		if (HordePathVariant.optimized()) {
			refreshInvalidated();
		} else {
			flushInvalidated();
		}
		if (!newTick) {
			return;
		}

		int start = cursor, count = Math.min(remainingBudget, SIDE * SIDE - start);
		int firstChanged = -1, lastChanged = -1;
		for (int i = start; i < start + count; i++) {
			int x = minX + Math.floorMod(i % SIDE - minX, SIDE);
			int z = minZ + Math.floorMod(i / SIDE - minZ, SIDE);
			var sample = HordeGroundCache.sample(x, z);
			int height = sample == null ? UNKNOWN
					: (Float.floatToRawIntBits((float) sample.height()) & ~1) | (sample.passable() ? 1 : 0);
			int tag = (x & 0xffff) | ((z & 0xffff) << 16);
			int at = i * WORDS;
			if (values[at] == height && values[at + 1] == tag && sampleX[i] == x && sampleZ[i] == z) {
				continue;
			}
			values[at] = height;
			values[at + 1] = tag;
			sampleX[i] = x;
			sampleZ[i] = z;
			if (firstChanged < 0) {
				firstChanged = i;
			}
			lastChanged = i;
		}
		if (firstChanged >= 0) {
			int firstWord = firstChanged * WORDS;
			texture.update(firstWord, values, firstWord, (lastChanged - firstChanged + 1) * WORDS);
		}
		remainingBudget -= count;
		cursor = (start + count) % (SIDE * SIDE);
	}

	public static void invalidate(int x, int z) {
		int index = Math.floorMod(z, SIDE) * SIDE + Math.floorMod(x, SIDE);
		if (texture == null || sampleX[index] != x || sampleZ[index] != z) {
			return;
		}
		invalid.set(index);
		invalidated = true;
	}

	/// 已失效列优先重采；预算外列先隐藏旧样本，下一刻继续，不等待整窗游标。
	private static void refreshInvalidated() {
		if (!invalidated) {
			return;
		}
		changed.clear();
		int budget = remainingBudget;
		invalidated = false;
		for (int i = invalid.nextSetBit(0); i >= 0; i = invalid.nextSetBit(i + 1)) {
			int height = UNKNOWN;
			if (budget > 0) {
				var sample = HordeGroundCache.sample(sampleX[i], sampleZ[i]);
				height = sample == null ? UNKNOWN : (Float.floatToRawIntBits((float) sample.height()) & ~1)
						| (sample.passable() ? 1 : 0);
				invalid.clear(i);
				budget--;
			} else {
				invalidated = true;
			}
			if (values[i * WORDS] != height) {
				values[i * WORDS] = height;
				changed.set(i);
			}
		}
		remainingBudget = budget;
		for (int first = changed.nextSetBit(0); first >= 0;) {
			int last = changed.nextClearBit(first);
			texture.update(first * WORDS, values, first * WORDS, (last - first) * WORDS);
			first = changed.nextSetBit(last);
		}
	}

	private static void flushInvalidated() {
		if (!invalidated) {
			return;
		}
		invalidated = false;
		for (int first = invalid.nextSetBit(0); first >= 0;) {
			int last = invalid.nextClearBit(first);
			for (int i = first; i < last; i++) {
				values[i * WORDS] = UNKNOWN;
			}
			texture.update(first * WORDS, values, first * WORDS, (last - first) * WORDS);
			first = invalid.nextSetBit(last);
		}
		invalid.clear();
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
		if (parameterMemory != null) {
			MemoryUtil.memFree(parameterMemory);
			parameterMemory = null;
		}
		world = null;
		preparedTick = Long.MIN_VALUE;
		cursor = 0;
		invalid.clear();
		invalidated = false;
		hasWindow = false;
	}
}
