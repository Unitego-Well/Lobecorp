package org.unitego.lobecorp.client.photon.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput.GeometrySnapshot;
import org.unitego.lobecorp.util.photon.model.PhotonModelGeometryUtil.NormalCache;
import org.unitego.lobecorp.util.photon.model.PhotonPackedVertexUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonBenchWorkloadUtil;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;
import org.unitego.lobecorp.util.photon.model.PhotonModelCodec;
import org.unitego.lobecorp.util.photon.model.PhotonModelGeometryUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 稳定帧快照必须与原始 CPU 写入逐顶点一致，包括法线、颜色、UV、亮度和光照。
public class PhotonModelOutputSnapshotTest {
	@Test
	public void replayMatchesOriginalSubmission() throws Exception {
		var definition = PhotonModelCodec.read("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
				"parts":[{"id":"main","opacity":0.5}]}
				""");
		var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
		var frame = sampler.sample(new PhotonModelPoseSampler.Input(0, "", true, 1, 1,
				Anchor.NONE, 0.5f, Map.of()), false);
		var world = new Matrix4f().rotationXYZ(0.3f, -0.2f, 0.4f).scale(2, 0.5f, 3)
				.translate(4, -2, 7);
		int color = 0xFF90A0C0;
		int light = 0x00F000F0;
		var recorder = new PhotonModelOutput.SnapshotRecorder();
		int triangles = PhotonModelGeometryUtil.write(recorder, frame, 0, world, color, light);
		recorder.finish();
		var snapshot = new PhotonModelOutput.GeometrySnapshot(world, color, light,
				recorder.vertices(), recorder.lights(), recorder.count(), triangles);
		var expected = new Collector();
		assertEquals(triangles, PhotonModelGeometryUtil.write(expected, frame, 0, world, color, light));
		var replayed = new Collector();
		snapshot.replay(replayed);
		assertEquals(expected.positions, replayed.positions);
		assertEquals(expected.normals, replayed.normals);
		assertEquals(expected.uvs, replayed.uvs);
		assertEquals(expected.colors, replayed.colors);
		assertEquals(expected.alphas, replayed.alphas);
		assertEquals(expected.light, replayed.light);
	}


	@Test
	public void stableMissSubmitsImmediatelyAndKeepsAReusableSnapshot() throws Exception {
		var frame = gridFrame(1, false);
		var world = new Matrix4f().rotationXYZ(0.3f, -0.2f, 0.4f).scale(2, 0.5f, 3).translate(4, -2, 7);
		var expected = new SignatureConsumer();
		int triangles = PhotonModelGeometryUtil.write(expected, frame, 0, world, 0xB78090A0, 0x00F000A0, new NormalCache());
		var actual = new SignatureConsumer();
		var snapshot = GeometrySnapshot.captureAndSubmit(actual, frame, 0, world, 0xB78090A0, 0x00F000A0, new NormalCache());
		assertNotNull(snapshot);
		assertEquals(triangles, snapshot.triangles());
		assertTrue(expected.vertices > 0);
		assertEquals(expected.vertices, actual.vertices);
		assertEquals(expected.signature, actual.signature);
		assertEquals(0, snapshot.packedBytes());
		var replayed = new SignatureConsumer();
		snapshot.replay(replayed);
		assertEquals(expected.vertices, replayed.vertices);
		assertEquals(expected.signature, replayed.signature);
	}

	@Test
	public void exactSnapshotBudgetStillSubmitsAndCaches() throws Exception {
		var frame = gridFrame(64, false);
		var world = new Matrix4f();
		var expected = new SignatureConsumer();
		PhotonModelGeometryUtil.write(expected, frame, 0, world, -1, 0x00F000F0, new NormalCache());
		assertEquals(PhotonPackedVertexUtil.MAX_VERTICES, expected.vertices);
		var actual = new SignatureConsumer();
		var snapshot = GeometrySnapshot.captureAndSubmit(actual, frame, 0, world, -1, 0x00F000F0, new NormalCache());
		assertNotNull(snapshot);
		assertEquals(expected.vertices, actual.vertices);
		assertEquals(expected.signature, actual.signature);
	}

	@Test
	public void oversizedStableMissWritesEveryTimeWithoutCaching() throws Exception {
		var frame = gridFrame(64, true);
		var world = new Matrix4f().scale(-2, 0.5f, 3);
		var expected = new SignatureConsumer();
		PhotonModelGeometryUtil.write(expected, frame, 0, world, 0x9F123456, 0x003000F0, new NormalCache());
		assertTrue(expected.vertices > PhotonPackedVertexUtil.MAX_VERTICES);
		var cache = new NormalCache();

		for (int attempt = 0; attempt < 2; attempt++) {
			var actual = new SignatureConsumer();
			assertNull(GeometrySnapshot.captureAndSubmit(actual, frame, 0, world, 0x9F123456, 0x003000F0, cache));
			assertEquals(expected.vertices, actual.vertices);
			assertEquals(expected.signature, actual.signature);
		}
	}

	private static PhotonModelPoseSampler.Frame gridFrame(int grid, boolean extraFace) throws Exception {
		var asset = PhotonBenchWorkloadUtil.asset(grid);

		if (extraFace) {
			var faces = asset.getAsJsonArray("faces");
			faces.add(faces.get(0).deepCopy());
		}

		var definition = PhotonModelCodec.read(asset);
		var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
		return sampler.sample(new PhotonModelPoseSampler.Input(0, "", true, 1, 1, Anchor.NONE, 0.65f, Map.of()), false);
	}

	/// 逐项保留调用顺序与浮点原始位的签名，避免大网格对照再保存两份顶点集合。
	@NullMarked
	private static class SignatureConsumer implements VertexConsumer, IPhotonPackedVertexSink {
		private int vertices;
		private long signature = 0xcbf29ce484222325L;

		private void mix(int value) {
			signature = (signature ^ value) * 0x100000001b3L;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			vertices++;
			mix(1);
			mix(Float.floatToRawIntBits(x));
			mix(Float.floatToRawIntBits(y));
			mix(Float.floatToRawIntBits(z));
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
			mix(2);
			mix(Float.floatToRawIntBits(red));
			mix(Float.floatToRawIntBits(green));
			mix(Float.floatToRawIntBits(blue));
			mix(Float.floatToRawIntBits(alpha));
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			mix(3);
			mix(Float.floatToRawIntBits(u));
			mix(Float.floatToRawIntBits(v));
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			mix(4);
			mix(u);
			mix(v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return setLight(u | v << 16);
		}

		@Override
		public VertexConsumer setLight(int light) {
			mix(5);
			mix(light);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			mix(6);
			mix(Float.floatToRawIntBits(x));
			mix(Float.floatToRawIntBits(y));
			mix(Float.floatToRawIntBits(z));
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			mix(7);
			mix(Float.floatToRawIntBits(width));
			return this;
		}

		@Override
		public boolean lobecorp$tryAppendPhotonSnapshot(byte[] packed, int vertexCount) {
			throw new AssertionError("录制当次不能进入实验整块提交。");
		}
	}

	private static final class Collector implements VertexConsumer {
		private final List<Vector3> positions = new ArrayList<>();
		private final List<Vector3> normals = new ArrayList<>();
		private final List<List<Float>> uvs = new ArrayList<>();
		private final List<List<Integer>> colors = new ArrayList<>();
		private final List<Integer> alphas = new ArrayList<>();
		private int light;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			positions.add(new Vector3(x, y, z));
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			colors.add(List.of(red, green, blue, alpha));
			alphas.add(alpha);
			return this;
		}

		@Override
		public VertexConsumer setColor(int color) {
			return setColor(color >>> 16 & 255, color >>> 8 & 255, color & 255, color >>> 24);
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			uvs.add(List.of(u, v));
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			light = u | v << 16;
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
}
