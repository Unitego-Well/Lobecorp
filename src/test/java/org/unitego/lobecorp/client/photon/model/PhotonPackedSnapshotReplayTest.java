package org.unitego.lobecorp.client.photon.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput.GeometrySnapshot;
import org.unitego.lobecorp.mixin.PhotonBenchMixinPlugin;
import org.unitego.lobecorp.util.photon.model.PhotonPackedVertexUtil;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 仅实例化静态快照与测试 sink，不创建 Photon 输出、FML 宿主或渲染上下文。
public class PhotonPackedSnapshotReplayTest {
	@Test
	public void disabledPackedReplayKeepsTheCompleteLegacySequence() {
		var snapshot = snapshot(vertices(), lights());
		var original = new Collector();
		snapshot.replay(original);
		var sink = new Sink(false, null);
		snapshot.replay(sink, false);
		assertEquals(20, original.calls.size());
		assertEquals(original.calls, sink.calls);
		assertEquals(0, sink.attempts);
		assertEquals(0, snapshot.packedBytes());
	}

	@Test
	public void rejectedSinkIsTriedOnceBeforeCompleteLegacyReplay() {
		var snapshot = snapshot(vertices(), lights());
		var original = new Collector();
		snapshot.replay(original);
		var sink = new Sink(false, null);
		snapshot.replay(sink, true);
		assertEquals(1, sink.attempts);
		assertEquals(original.calls, sink.calls);
		assertTrue(sink.copies.isEmpty());
	}

	@Test
	public void acceptedSinkReusesPackedBytesAndCanReleaseAndRebuildItsCache() {
		var vertices = vertices();
		var lights = lights();
		var snapshot = snapshot(vertices, lights);
		var sink = new Sink(true, null);
		var expected = PhotonPackedVertexUtil.pack(vertices, lights, lights.length);
		assertEquals(0, snapshot.packedBytes());
		snapshot.replay(sink, true);
		assertEquals(1, sink.attempts);
		assertEquals(4, sink.vertexCount);
		assertTrue(sink.calls.isEmpty());
		assertArrayEquals(expected, sink.copies.getFirst());
		assertEquals(128, snapshot.packedBytes());
		snapshot.replay(sink, true);
		assertEquals(2, sink.attempts);
		assertArrayEquals(sink.copies.getFirst(), sink.copies.get(1));
		assertTrue(sink.calls.isEmpty());
		snapshot.discardPacked();
		assertEquals(0, snapshot.packedBytes());
		snapshot.replay(sink, true);
		assertEquals(3, sink.attempts);
		assertArrayEquals(expected, sink.copies.get(2));
		assertEquals(128, snapshot.packedBytes());
		snapshot.discardPacked();
		assertEquals(0, snapshot.packedBytes());
	}

	@Test
	public void nonFiniteSnapshotSkipsTheSinkAndPreservesLegacyReplay() {
		var vertices = vertices();
		vertices[5] = Float.NaN;
		var snapshot = snapshot(vertices, lights());
		var original = new Collector();
		snapshot.replay(original);
		var sink = new Sink(true, null);
		snapshot.replay(sink, true);
		assertEquals(0, sink.attempts);
		assertEquals(original.calls, sink.calls);
		assertEquals(0, snapshot.packedBytes());
	}

	@Test
	public void sinkFailurePropagatesWithoutMixingLegacyWrites() {
		var snapshot = snapshot(vertices(), lights());
		var failure = new IllegalStateException("sink_reserved_failure");
		var sink = new Sink(false, failure);
		assertSame(failure, assertThrows(IllegalStateException.class, () -> snapshot.replay(sink, true)));
		assertEquals(1, sink.attempts);
		assertTrue(sink.calls.isEmpty());
		assertTrue(sink.copies.isEmpty());
	}

	@Test
	public void fourFullSnapshotsFitTheOutputBudgetButTheFifthDoesNot() {
		int count = PhotonPackedVertexUtil.MAX_VERTICES;
		var snapshot = snapshot(new float[count * 12], new int[count]);
		assertTrue(GeometrySnapshot.withinPackedBudget(List.of(snapshot, snapshot, snapshot, snapshot)));
		assertFalse(GeometrySnapshot.withinPackedBudget(List.of(snapshot, snapshot, snapshot, snapshot, snapshot)));
		assertEquals(0, snapshot.packedBytes());
	}

	@Test
	public void packedBufferMixinRequiresTheExplicitEnabledProperty() {
		var property = PhotonPackedVertexUtil.ENABLE_PROPERTY;
		var previous = System.getProperty(property);
		var plugin = new PhotonBenchMixinPlugin();
		var target = "com.mojang.blaze3d.vertex.BufferBuilder";
		var mixin = "org.unitego.lobecorp.mixin.minecraft.client.BufferBuilderMixin";

		try {
			System.clearProperty(property);
			assertFalse(plugin.shouldApplyMixin(target, mixin));
			System.setProperty(property, "false");
			assertFalse(plugin.shouldApplyMixin(target, mixin));
			System.setProperty(property, "true");
			assertTrue(plugin.shouldApplyMixin(target, mixin));
		} finally {
			if (previous == null) {
				System.clearProperty(property);
			} else {
				System.setProperty(property, previous);
			}
		}
	}

	private static GeometrySnapshot snapshot(float[] vertices, int[] lights) {
		return new GeometrySnapshot(new Matrix4f(), -1, 0, vertices, lights, lights.length, lights.length / 4);
	}

	private static float[] vertices() {
		return new float[]{
				-0.0F, 1.25F, -2.5F, -1.0F, -0.5F, 1.0F, -0.0F, 0.75F, 0.1F, 0.5F, 1.0F, 0.25F,
				1.0F, -0.0F, 3.0F, 0.0F, 1.0F, 0.0F, 0.25F, 1.0F, 1.0F, 0.0F, 0.2F, 1.0F,
				-1.0F, 2.0F, Float.MIN_VALUE, -1.0F, 0.0F, 0.5F, 1.5F, -0.25F, 0.3F, 0.7F, 0.9F, 0.0F,
				2.0F, 4.0F, -1.0F, 0.25F, -0.25F, 0.75F, 1.0F, 1.0F, 0.5F, 0.25F, 0.75F, 0.5F
		};
	}

	private static int[] lights() {
		return new int[]{0xABCD1234, 0x00F000F0, 0x13572468, 0xFFFFFFFF};
	}

	private record Call(String operation, List<?> values) {
	}

	@NullMarked
	private static class Collector implements VertexConsumer {
		protected final List<Call> calls = new ArrayList<>();

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			calls.add(new Call("position", List.of(x, y, z)));
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			return setColor(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
		}

		@Override
		public VertexConsumer setColor(int color) {
			return setColor(color >>> 16 & 255, color >>> 8 & 255, color & 255, color >>> 24);
		}

		@Override
		public VertexConsumer setColor(float red, float green, float blue, float alpha) {
			calls.add(new Call("color", List.of(red, green, blue, alpha)));
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			calls.add(new Call("uv", List.of(u, v)));
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			calls.add(new Call("overlay", List.of(u, v)));
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return setLight(u | v << 16);
		}

		@Override
		public VertexConsumer setLight(int light) {
			calls.add(new Call("light", List.of(light)));
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			calls.add(new Call("normal", List.of(x, y, z)));
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			calls.add(new Call("line_width", List.of(width)));
			return this;
		}
	}

	private static class Sink extends Collector implements IPhotonPackedVertexSink {
		private final boolean accepted;
		private final RuntimeException failure;
		private final List<byte[]> copies = new ArrayList<>();
		private int attempts;
		private int vertexCount;

		private Sink(boolean accepted, RuntimeException failure) {
			this.accepted = accepted;
			this.failure = failure;
		}

		@Override
		public boolean lobecorp$tryAppendPhotonSnapshot(byte[] packed, int vertexCount) {
			attempts++;

			if (failure != null) {
				throw failure;
			}

			if (!accepted) {
				return false;
			}

			this.vertexCount = vertexCount;
			copies.add(packed.clone());
			return true;
		}
	}
}
