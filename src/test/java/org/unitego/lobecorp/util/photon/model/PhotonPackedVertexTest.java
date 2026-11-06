package org.unitego.lobecorp.util.photon.model;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 数值检查使用普通 JVM；原生字节对照还依赖 LWJGL／Tracy 本机库，不创建窗口或 GPU 上下文。
public class PhotonPackedVertexTest {
	@Test
	public void matchesNativeBufferBuilderBytesIncludingDistinctVertexLights() {
		var vertices = vertices();
		int[] lights = {0xABCD1234, 0x00F000F0, 0x13572468, 0xFFFFFFFF};
		var packed = PhotonPackedVertexUtil.pack(vertices, lights, 4);
		assertNotNull(packed);
		assertArrayEquals(nativeBytes(vertices, lights), packed);
		var bytes = ByteBuffer.wrap(packed).order(ByteOrder.nativeOrder());

		for (int index = 0; index < lights.length; index++) {
			int offset = index * PhotonPackedVertexUtil.VERTEX_STRIDE;
			assertEquals((short) lights[index], bytes.getShort(offset + 24));
			assertEquals((short) (lights[index] >>> 16), bytes.getShort(offset + 26));
			assertEquals(0, bytes.get(offset + 31));
		}
	}

	@Test
	public void preservesNegativeZeroAndUsesNativeColorAndNormalTruncation() {
		var packed = PhotonPackedVertexUtil.pack(vertices(), new int[4], 4);
		assertNotNull(packed);
		var bytes = ByteBuffer.wrap(packed).order(ByteOrder.nativeOrder());
		assertEquals(Float.floatToRawIntBits(-0.0F), bytes.getInt(0));
		assertEquals(Float.floatToRawIntBits(-0.0F), bytes.getInt(16));
		assertEquals(25, Byte.toUnsignedInt(bytes.get(12)));
		assertEquals(127, Byte.toUnsignedInt(bytes.get(13)));
		assertEquals(62, Byte.toUnsignedInt(bytes.get(14)));
		assertEquals(63, Byte.toUnsignedInt(bytes.get(15)));
		assertEquals(-127, bytes.get(28));
		assertEquals(-63, bytes.get(29));
		assertEquals(127, bytes.get(30));
	}

	@Test
	public void boundsEachPayloadAndRejectsInvalidCountsOrArrays() {
		int count = PhotonPackedVertexUtil.MAX_VERTICES;
		var packed = PhotonPackedVertexUtil.pack(new float[count * 12], new int[count], count);
		assertNotNull(packed);
		assertEquals((long) count * PhotonPackedVertexUtil.VERTEX_STRIDE, packed.length);
		assertTrue(packed.length <= PhotonPackedVertexUtil.MAX_CACHED_BYTES);
		assertTrue(PhotonPackedVertexUtil.validPayload(packed, count));

		for (int invalid : new int[]{-4, 0, 1, 3, count + 4, Integer.MAX_VALUE}) {
			assertNull(PhotonPackedVertexUtil.pack(new float[48], new int[4], invalid));
			assertFalse(PhotonPackedVertexUtil.validPayload(new byte[128], invalid));
		}

		assertNull(PhotonPackedVertexUtil.pack(null, new int[4], 4));
		assertNull(PhotonPackedVertexUtil.pack(new float[48], null, 4));
		assertNull(PhotonPackedVertexUtil.pack(new float[47], new int[4], 4));
		assertNull(PhotonPackedVertexUtil.pack(new float[48], new int[3], 4));
		assertFalse(PhotonPackedVertexUtil.validPayload(null, 4));
		assertFalse(PhotonPackedVertexUtil.validPayload(new byte[127], 4));
		assertFalse(PhotonPackedVertexUtil.validPayload(new byte[129], 4));
		assertFalse(PhotonPackedVertexUtil.validPayload(new byte[128], 8));
		assertTrue(PhotonPackedVertexUtil.validPayload(new byte[128], 4));
	}

	@Test
	public void rejectsEveryNonFiniteActiveValueButIgnoresUnusedArrayTail() {
		var vertices = Arrays.copyOf(vertices(), 49);
		var original = vertices.clone();

		for (int index = 0; index < 48; index++) {
			for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
				vertices[index] = invalid;
				assertNull(PhotonPackedVertexUtil.pack(vertices, new int[4], 4));
			}

			vertices[index] = original[index];
		}

		assertArrayEquals(original, vertices);
		vertices[48] = Float.NaN;
		assertNotNull(PhotonPackedVertexUtil.pack(vertices, new int[5], 4));
	}

	@Test
	public void acceptsOnlyTheCompleteNativeParticleLayout() {
		var offsets = offsets();
		assertTrue(PhotonPackedVertexUtil.supportsLayout(32, 0x36, offsets));
		assertFalse(PhotonPackedVertexUtil.supportsLayout(28, 0x36, offsets));
		assertFalse(PhotonPackedVertexUtil.supportsLayout(32, 0x37, offsets));
		assertFalse(PhotonPackedVertexUtil.supportsLayout(32, 0x36, null));
		assertFalse(PhotonPackedVertexUtil.supportsLayout(32, 0x36, Arrays.copyOf(offsets, 6)));
		assertFalse(PhotonPackedVertexUtil.supportsLayout(32, 0x36, Arrays.copyOf(offsets, 33)));

		for (int index = 0; index < offsets.length; index++) {
			var changed = offsets.clone();
			changed[index]++;
			assertFalse(PhotonPackedVertexUtil.supportsLayout(32, 0x36, changed));
		}

		var reordered = offsets.clone();
		reordered[1] = 20;
		reordered[2] = 12;
		assertFalse(PhotonPackedVertexUtil.supportsLayout(32, 0x36, reordered));
	}

	private static float[] vertices() {
		return new float[]{
				-0.0F, 1.25F, -2.5F, -2.0F, -0.5F, 2.0F, -0.0F, 0.75F, 0.1F, 0.5F, 1.25F, 0.25F,
				1.0F, -0.0F, 3.0F, 0.0F, 1.0F, 0.0F, 0.25F, 1.0F, 1.0F, 0.0F, 0.2F, 1.0F,
				-1.0F, 2.0F, Float.MIN_VALUE, -1.0F, 0.0F, 0.5F, 1.5F, -0.25F, 0.3F, 0.7F, 0.9F, 0.0F,
				2.0F, 4.0F, -1.0F, 0.25F, -0.25F, 0.75F, 1.0F, 1.0F, 0.5F, 0.25F, 0.75F, 0.5F
		};
	}

	private static int[] offsets() {
		var offsets = new int[32];
		Arrays.fill(offsets, -1);
		offsets[0] = 0;
		offsets[1] = 12;
		offsets[2] = 16;
		offsets[4] = 24;
		offsets[5] = 28;
		return offsets;
	}

	private static byte[] nativeBytes(float[] vertices, int[] lights) {
		int count = lights.length;
		var format = VertexFormat.builder()
				.add("Position", VertexFormatElement.POSITION)
				.add("Color", VertexFormatElement.COLOR)
				.add("UV0", VertexFormatElement.UV0)
				.add("UV2", VertexFormatElement.UV2)
				.add("Normal", VertexFormatElement.NORMAL)
				.padding(1).build();
		int size = count * 32;

		try (var buffer = new ByteBufferBuilder(size)) {
			// 原生法线写入不碰填充字节；先清零原分配，避免把未初始化内存当成编码差异。
			MemoryUtil.memSet(buffer.reserve(size), 0, size);

			try (var cleared = Objects.requireNonNull(buffer.build())) {
				assertEquals(size, cleared.byteBuffer().remaining());
			}

			var builder = new BufferBuilder(buffer, VertexFormat.Mode.QUADS, format);

			for (int index = 0, offset = 0; index < count; index++, offset += 12) {
				builder.addVertex(vertices[offset], vertices[offset + 1], vertices[offset + 2])
						.setUv(vertices[offset + 6], vertices[offset + 7])
						.setColor(vertices[offset + 8], vertices[offset + 9], vertices[offset + 10], vertices[offset + 11])
						.setLight(lights[index])
						.setNormal(vertices[offset + 3], vertices[offset + 4], vertices[offset + 5]);
			}

			try (var mesh = builder.buildOrThrow()) {
				assertEquals(count, mesh.drawState().vertexCount());
				assertEquals(VertexFormat.Mode.QUADS, mesh.drawState().mode());
				var bytes = mesh.vertexBuffer().duplicate();
				var result = new byte[bytes.remaining()];
				bytes.get(result);
				return result;
			}
		}
	}
}
