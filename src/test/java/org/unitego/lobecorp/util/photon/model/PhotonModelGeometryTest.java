package org.unitego.lobecorp.util.photon.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonModelGeometryTest {
	@Test
	public void keepsFiniteGeometricNormalsWhenWorldScaleFlattensOneAxis() throws Exception {
		var definition = PhotonModelCodec.read("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}]}
				""");
		var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
		var frame = sampler.sample(new PhotonModelPoseSampler.Input(0, "", true, 1, 1, Anchor.NONE, 1, Map.of()), false);
		var consumer = new Collector();
		PhotonModelGeometryUtil.write(consumer, frame, 0, new Matrix4f().scaling(1, 1, 0), -1, 0);
		assertEquals(4, consumer.normals.size());
		assertTrue(consumer.normals.stream().allMatch(normal -> normal.z() == 1 && normal.x() == 0 && normal.y() == 0));
	}

	@Test
	public void emitsCameraRelativeQuadTrianglesWithOriginalUvsAndOpacity() throws Exception {
		var definition = PhotonModelCodec.read("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
				"parts":[{"id":"main","opacity":0.5}]}
				""");
		var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
		var frame = sampler.sample(new PhotonModelPoseSampler.Input(0, "", true, 1, 1, Anchor.NONE, 0.5f, Map.of()), false);
		var consumer = new Collector();
		assertEquals(1, PhotonModelGeometryUtil.write(consumer, frame, 0, new Matrix4f().translation(2, 3, 4), -1, 0x00F000F0));
		assertEquals(4, consumer.positions.size());
		assertEquals(new Vector3(2, 3, 4), consumer.positions.getFirst());
		assertEquals(consumer.positions.get(2), consumer.positions.get(3));
		assertEquals(List.of(0f, 1f), consumer.uvs.get(2));
		assertEquals(63, consumer.alphas.getFirst());
		assertTrue(consumer.normals.stream().allMatch(normal -> normal.z() == 1));
		assertEquals(0x00F000F0, consumer.light);
	}

	private static class Collector implements VertexConsumer {
		private final List<Vector3> positions = new ArrayList<>();
		private final List<Vector3> normals = new ArrayList<>();
		private final List<List<Float>> uvs = new ArrayList<>();
		private final List<Integer> alphas = new ArrayList<>();
		private int light;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			positions.add(new Vector3(x, y, z));
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			alphas.add(a);
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
