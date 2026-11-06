package org.unitego.lobecorp.client.photon.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.mixin.PhotonBenchMixinPlugin;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector2;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;
import org.unitego.lobecorp.util.photon.model.PhotonModelCodec;
import org.unitego.lobecorp.util.photon.model.PhotonModelGeometryUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;
import org.unitego.lobecorp.util.photon.model.PhotonPackedVertexUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonBenchWorkloadUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhotonModelPoseBufferTest {
	@Test
	public void publicFrameCaptureKeepsEveryCornerAndRejectsMutableArrays() throws Exception {
		var frame = sample("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]],
				"normals":[[0,0,1],[0,0,1],[0,0,1]]}],
				"parts":[{"id":"main","opacity":0.5}]}
				""", 0.7f);
		var buffer = new PhotonModelPoseBuffer();
		buffer.capture(frame);
		assertEquals(frame.opacity(), buffer.opacity());
		assertEquals(frame.groups().getFirst().opacity(), buffer.groupOpacity(0));
		assertEquals(frame.groups().getFirst().vertices().size(), frame.prepared().groups().getFirst().corners().size());
		assertEquals(new Vector3(1, 0, 0), new Vector3(buffer.attribute(0, 1, 0), buffer.attribute(0, 1, 1),
				buffer.attribute(0, 1, 2)));
		assertEquals(new Vector3(0, 0, 1), new Vector3(buffer.attribute(0, 1, 3), buffer.attribute(0, 1, 4),
				buffer.attribute(0, 1, 5)));
		assertEquals(new Vector2(1, 0), new Vector2(buffer.attribute(0, 1, 6), buffer.attribute(0, 1, 7)));
		var snapshot = Objects.requireNonNull(buffer.frame());
		assertEquals(frame, snapshot);
		assertThrows(UnsupportedOperationException.class, () -> snapshot.groups().clear());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.groups().getFirst().vertices().clear());
		buffer.beginFrame(frame.prepared(), 0.2f, 1);
		buffer.beginGroup(0, 3, 0.3f);
		for (int corner = 0; corner < 3; corner++) {
			buffer.vertex(9, 8, 7, 0, 1, 0, 0.2f, 0.3f);
		}
		buffer.endGroup();
		buffer.endFrame(new Vector3(9, 8, 7), new Vector3(9, 8, 7), null);
		assertEquals(new Vector3(1, 0, 0), snapshot.groups().getFirst().vertices().get(1).position());
		assertEquals(new Vector3(0, 0, 1), snapshot.groups().getFirst().vertices().get(1).normal());
		assertEquals(new Vector2(1, 0), snapshot.groups().getFirst().vertices().get(1).uv());
		assertEquals(0.7f, snapshot.opacity());
		assertEquals(0.5f, snapshot.groups().getFirst().opacity());
		assertEquals(new Vector3(9, 8, 7),
				Objects.requireNonNull(buffer.frame()).groups().getFirst().vertices().get(1).position());
	}

	@Test
	public void primitiveSubmissionMatchesPublicFrameSubmissionVertexForVertex() throws Exception {
		var frame = sample("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0],[1,1,0]],
				"faces":[{"vertices":[0,1,3,2],"uv":[[0,0],[1,0],[1,1],[0,1]],
				"normals":[[0,0,1],[0,0,1],[0,0,1],[0,0,1]]}],
				"parts":[{"id":"main","insideOut":true,"shading":true,"opacity":0.4}]}
				""", 0.6f);
		var buffer = new PhotonModelPoseBuffer();
		buffer.capture(frame);
		for (var world : List.of(
				new Matrix4f().rotationXYZ(0.2f, -0.3f, 0.4f).scale(-2, 0.5f, 3).translate(4, -2, 7),
				new Matrix4f().rotationXYZ(0.2f, -0.3f, 0.4f).scale(0, 0.5f, 3).translate(4, -2, 7))) {
			var expected = new Collector();
			var actual = new Collector();
			int expectedTriangles = PhotonModelGeometryUtil.write(expected, frame, 0, world, 0xFF90A0C0,
					0x00F000F0, new PhotonModelGeometryUtil.NormalCache());
			int actualTriangles = PhotonModelGeometryUtil.writePrimitive(actual, buffer, 0, world, 0xFF90A0C0,
					0x00F000F0, new PhotonModelGeometryUtil.NormalCache());
			assertEquals(expectedTriangles, actualTriangles);
			assertTrue(expectedTriangles > 0);
			assertEquals(expected.vertices, actual.vertices);
			assertEquals(expected.positions, actual.positions);
			assertEquals(expected.normals, actual.normals);
			assertEquals(expected.uvs, actual.uvs);
			assertEquals(expected.colors, actual.colors);
			assertEquals(expected.lights, actual.lights);
		}
	}

	@Test
	public void sinkCornerOrderAndExpandedQuadOrderStayDistinct() throws Exception {
		var frame = sample("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]],
				"normals":[[0,0,1],[0,0,1],[0,0,1]]}],
				"parts":[{"id":"main"}]}
				""", 1);
		var buffer = buffer(frame);
		var collector = new Collector();
		assertEquals(1, PhotonModelGeometryUtil.writePrimitive(collector, buffer, 0, new Matrix4f(), -1, 0));
		assertEquals(4, collector.vertices);
		assertEquals(collector.positions.get(2), collector.positions.get(3));
	}

	private static PhotonModelPoseBuffer buffer(PhotonModelPoseSampler.Frame frame) {
		var buffer = new PhotonModelPoseBuffer();
		var prepared = frame.prepared();
		buffer.beginFrame(prepared, frame.opacity(), prepared.groups().size());
		buffer.beginGroup(0, frame.groups().getFirst().vertices().size(), frame.groups().getFirst().opacity());

		for (var vertex : frame.groups().getFirst().vertices()) {
			buffer.vertex(vertex.position().x(), vertex.position().y(), vertex.position().z(), vertex.normal().x(),
					vertex.normal().y(), vertex.normal().z(), vertex.uv().x(), vertex.uv().y());
		}

		buffer.endGroup();
		buffer.endFrame(frame.min(), frame.max(), frame.timings());
		return buffer;
	}

	@Test
	public void independentBuffersDoNotShareArraysAcrossRevisions() throws Exception {
		var first = sample("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}]}
				""", 1);
		var second = sample("""
				{"version":1,"vertices":[[0,0,0],[2,0,0],[0,2,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
				"parts":[{"id":"main","transform":{"translation":[5,0,0]}}]}
				""", 0.5f);
		var left = new PhotonModelPoseBuffer();
		var right = new PhotonModelPoseBuffer();
		left.capture(first);
		right.capture(second);
		var leftBefore = left.frame();
		var rightBefore = right.frame();
		right.capture(first);
		assertEquals(leftBefore, left.frame());
		assertEquals(first, right.frame());
		assertNotSame(leftBefore, rightBefore);
		assertEquals(1f, left.attribute(0, 1, 0));
		left.capture(second);
		assertEquals(1f, right.attribute(0, 1, 0));
		assertEquals(first, leftBefore);
		assertEquals(second, rightBefore);
		assertEquals(first, right.frame());
		assertEquals(second, left.frame());
		var leftFrame = Objects.requireNonNull(leftBefore);
		var rightFrame = Objects.requireNonNull(rightBefore);
		assertNotSame(leftFrame.groups().getFirst().vertices().get(1).position(),
				rightFrame.groups().getFirst().vertices().get(1).position());
	}

	@Test
	public void failedCandidateKeepsPreviousFrameAndNeverPublishesPartialState() throws Exception {
		var frame = sample("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}]}
				""", 1);
		var buffer = new PhotonModelPoseBuffer();
		buffer.capture(frame);
		var previous = buffer.frame();
		buffer.beginFrame(frame.prepared(), 0.5f, 1);
		buffer.beginGroup(0, 3, 0.5f);
		buffer.vertex(9, 9, 9, 0, 0, 1, 0, 0);
		buffer.failCandidate();
		assertThrows(IllegalStateException.class, buffer::endGroup);
		assertEquals(previous, buffer.frame());
		assertFalse(buffer.complete());
		assertEquals(1f, buffer.opacity());
		assertEquals(1f, buffer.groupOpacity(0));
		assertEquals(new Vector3(0, 0, 0),
				Objects.requireNonNull(buffer.frame()).groups().getFirst().vertices().getFirst().position());
		assertEquals(new Vector3(1, 1, 0), Objects.requireNonNull(buffer.frame()).max());
		assertEquals(frame, buffer.frame());
	}

	@Test
	public void sinkFailureCannotLeaveAReusableHalfFrame() throws Exception {
		var frame = sample("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}]}
				""", 1);
		var buffer = new PhotonModelPoseBuffer();
		buffer.beginFrame(frame.prepared(), 1, 1);
		buffer.beginGroup(0, 3, 1);
		assertThrows(IllegalStateException.class, buffer::endGroup);
		assertNull(buffer.frame());
	}

	@Test
	public void unchangedInputKeepsPublishedFrameIdentityAndFailedInputCannotBeReused() throws Exception {
		var definition = PhotonModelCodec.read("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}]}
				""");
		var prepared = PhotonModelTopologyUtil.prepare(definition);
		var sampler = new PhotonModelPoseSampler(prepared);
		var buffer = new PhotonModelPoseBuffer();
		var input = new PhotonModelPoseSampler.Input(0, "", true, 1, 1, Anchor.NONE, 1, Map.of());

		try (var workspace = sampler.newWorkspace()) {
			sampler.sampleInto(input, false, workspace, buffer);
			var first = buffer.frame();
			sampler.sampleInto(input, false, workspace, buffer);
			var second = buffer.frame();
			assertEquals(first, second);
			assertNotSame(first, second);
			buffer.beginFrame(prepared, 1, prepared.groups().size());
			buffer.beginGroup(0, prepared.groups().getFirst().corners().size(), 1);
			buffer.failCandidate();
			assertThrows(IllegalStateException.class, buffer::endGroup);
			assertEquals(first, buffer.frame());
		}
	}

	@Test
	public void cancelledSamplingDoesNotPublishPartialPoseAndWorkspaceRemainsReusable() throws Exception {
		var definition = PhotonModelCodec.read("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}]}
				""");
		var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
		var input = new PhotonModelPoseSampler.Input(0, "", true, 1, 1, Anchor.NONE, 1, Map.of());
		var buffer = new PhotonModelPoseBuffer();

		try (var workspace = sampler.newWorkspace()) {
			sampler.sampleInto(input, false, workspace, buffer);
			var published = buffer.frame();
			Thread.currentThread().interrupt();

			try {
				assertThrows(java.util.concurrent.CancellationException.class,
						() -> sampler.sampleInto(input, false, workspace, buffer));
			} finally {
				assertTrue(Thread.interrupted());
			}

			assertEquals(published, buffer.frame());
			sampler.sampleInto(input, false, workspace, buffer);
			assertEquals(published, buffer.frame());
		}
	}

	@Test
	public void failedCandidateAfterPublishedFrameDoesNotHideTheOldPublicSnapshot() throws Exception {
		var frame = sample("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}]}
				""", 1);
		var buffer = new PhotonModelPoseBuffer();
		buffer.capture(frame);
		var published = Objects.requireNonNull(buffer.frame());
		buffer.beginFrame(frame.prepared(), 0.25f, 1);
		buffer.failCandidate();
		assertEquals(published, buffer.frame());
	}

	@Test
	public void groupOpacityBuffersPublishOnlyCompleteCandidatesAndReuseCapacity() throws Exception {
		var first = sample("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
				"parts":[{"id":"main","opacity":0.2}]}
				""", 0.6f);
		var second = sample("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
				"parts":[{"id":"main","opacity":0.8}]}
				""", 0.4f);
		var buffer = new PhotonModelPoseBuffer();
		buffer.capture(first);
		var published = Objects.requireNonNull(buffer.frame());
		var publishedOpacity = published.groups().getFirst().opacity();
		buffer.beginFrame(first.prepared(), 0.9f, 1);
		buffer.beginGroup(0, 3, 0.7f);
		buffer.failCandidate();
		assertEquals(publishedOpacity, buffer.groupOpacity(0));
		assertEquals(published, buffer.frame());

		buffer.beginFrame(second.prepared(), second.opacity(), 1);
		buffer.beginGroup(0, 3, second.groups().getFirst().opacity());
		for (var vertex : second.groups().getFirst().vertices()) {
			buffer.vertex(vertex.position().x(), vertex.position().y(), vertex.position().z(), vertex.normal().x(),
					vertex.normal().y(), vertex.normal().z(), vertex.uv().x(), vertex.uv().y());
		}
		buffer.endGroup();
		buffer.endFrame(second.min(), second.max(), second.timings());
		var replacement = Objects.requireNonNull(buffer.frame());
		assertEquals(second.groups().getFirst().opacity(), buffer.groupOpacity(0));
		assertEquals(second.groups().getFirst().opacity(), replacement.groups().getFirst().opacity());
		assertEquals(publishedOpacity, published.groups().getFirst().opacity());
		assertNotSame(published, replacement);

		buffer.beginFrame(second.prepared(), 0.3f, 1);
		buffer.beginGroup(0, 3, 0.1f);
		for (var vertex : second.groups().getFirst().vertices()) {
			buffer.vertex(vertex.position().x(), vertex.position().y(), vertex.position().z(), vertex.normal().x(),
					vertex.normal().y(), vertex.normal().z(), vertex.uv().x(), vertex.uv().y());
		}
		buffer.endGroup();
		buffer.endFrame(second.min(), second.max(), second.timings());
		assertEquals(second.groups().getFirst().opacity(), replacement.groups().getFirst().opacity());
		assertEquals(0.1f, buffer.groupOpacity(0));
	}

	@Test
	public void largeGridAtAndAboveLegacySnapshotBudgetWritesEveryVertex() throws Exception {
		for (boolean extraFace : List.of(false, true)) {
			var frame = gridFrame(extraFace);
			var buffer = new PhotonModelPoseBuffer();
			buffer.capture(frame);
			var frameOutput = new CountConsumer();
			var primitiveOutput = new CountConsumer();
			int triangles = PhotonModelGeometryUtil.write(frameOutput, frame, 0, new Matrix4f(), -1, 0);
			int primitiveTriangles = PhotonModelGeometryUtil.writePrimitive(primitiveOutput, buffer, 0,
					new Matrix4f(), -1, 0);
			assertEquals(triangles, primitiveTriangles);
			assertEquals(triangles * 4, frameOutput.vertices);
			assertEquals(frameOutput.vertices, primitiveOutput.vertices);

			if (extraFace) {
				assertTrue(frameOutput.vertices > PhotonPackedVertexUtil.MAX_VERTICES);
			} else {
				assertEquals(PhotonPackedVertexUtil.MAX_VERTICES, frameOutput.vertices);
			}
		}
	}

	@Test
	public void largeGridSampleIntoMatchesImmutableSampleAndPrimitiveExpansion() throws Exception {
		var input = gridInput();

		for (boolean extraFace : List.of(false, true)) {
			var sampler = gridSampler(extraFace);
			var expected = sampler.sample(input, false);
			var buffer = new PhotonModelPoseBuffer();

			try (var workspace = sampler.newWorkspace()) {
				sampler.sampleInto(input, false, workspace, buffer);
			}

			assertEquals(expected, buffer.frame());
			var world = new Matrix4f().rotationXYZ(0.2f, -0.3f, 0.4f).scale(-2, 0.5f, 3)
					.translate(4, -2, 7);
			var expectedOutput = new CountConsumer();
			var primitiveOutput = new CountConsumer();
			int triangles = PhotonModelGeometryUtil.write(expectedOutput, expected, 0, world, 0xFF90A0C0,
					0x00F000F0, new PhotonModelGeometryUtil.NormalCache());
			int primitiveTriangles = PhotonModelGeometryUtil.writePrimitive(primitiveOutput, buffer, 0, world,
					0xFF90A0C0, 0x00F000F0, new PhotonModelGeometryUtil.NormalCache());
			assertEquals(triangles, primitiveTriangles);
			assertEquals(triangles * 4, expectedOutput.vertices);
			assertEquals(expectedOutput.vertices, primitiveOutput.vertices);

			if (extraFace) {
				assertTrue(primitiveOutput.vertices > PhotonPackedVertexUtil.MAX_VERTICES);
			} else {
				assertEquals(PhotonPackedVertexUtil.MAX_VERTICES, primitiveOutput.vertices);
			}
		}
	}

	@Test
	public void bufferBuilderMixinRequiresTheExplicitPackedProperty() {
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

	private static PhotonModelPoseSampler.Frame gridFrame(boolean extraFace) throws Exception {
		return gridSampler(extraFace).sample(gridInput(), false);
	}

	private static PhotonModelPoseSampler gridSampler(boolean extraFace) throws Exception {
		var asset = PhotonBenchWorkloadUtil.asset(64);

		if (extraFace) {
			var faces = asset.getAsJsonArray("faces");
			faces.add(faces.get(0).deepCopy());
		}

		var definition = PhotonModelCodec.read(asset);
		return new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
	}

	private static PhotonModelPoseSampler.Input gridInput() {
		return new PhotonModelPoseSampler.Input(0, "", true, 1, 1, Anchor.NONE, 0.65f, Map.of());
	}

	private static PhotonModelPoseSampler.Frame sample(String asset, float opacity) throws Exception {
		var definition = PhotonModelCodec.read(asset);
		var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
		return sampler.sample(new PhotonModelPoseSampler.Input(0, "", true, 1, 1, Anchor.NONE, opacity, Map.of()), false);
	}

	@NullMarked
	private static final class Collector implements VertexConsumer {
		private final List<Vector3> positions = new ArrayList<>();
		private final List<Vector3> normals = new ArrayList<>();
		private final List<Vector2> uvs = new ArrayList<>();
		private final List<List<Float>> colors = new ArrayList<>();
		private final List<Integer> lights = new ArrayList<>();
		private int vertices;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			positions.add(new Vector3(x, y, z));
			vertices++;
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			return setColor(red / 255f, green / 255f, blue / 255f, alpha / 255f);
		}

		@Override
		public VertexConsumer setColor(int color) {
			return setColor(color >>> 16 & 255, color >>> 8 & 255, color & 255, color >>> 24);
		}

		@Override
		public VertexConsumer setColor(float red, float green, float blue, float alpha) {
			colors.add(List.of(red, green, blue, alpha));
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			uvs.add(new Vector2(u, v));
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return setLight(u | v << 16);
		}

		@Override
		public VertexConsumer setLight(int light) {
			lights.add(light);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			normals.add(new Vector3(x, y, z));
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}
	}

	@NullMarked
	private static final class CountConsumer implements VertexConsumer {
		private int vertices;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			vertices++;
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer setColor(int color) {
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer setColor(float red, float green, float blue, float alpha) {
			return this;
		}

		@Override
		public VertexConsumer setLight(int light) {
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}
	}
}
