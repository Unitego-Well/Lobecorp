package org.unitego.lobecorp.client.photon.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
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
