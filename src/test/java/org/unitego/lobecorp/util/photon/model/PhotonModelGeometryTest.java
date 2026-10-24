package org.unitego.lobecorp.util.photon.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
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

	@Test
	public void matchesNativeNormalsAcrossTranslationRotationScaleAndSingularChanges() {
		var cache = new PhotonModelGeometryUtil.NormalCache();
		var rotated = new Matrix4f().rotationXYZ(0.3f, -0.7f, 0.4f);
		var genericRotation = new Matrix4f(rotated).assume(Matrix4fc.PROPERTY_AFFINE);
		for (var world : List.of(new Matrix4f(), new Matrix4f().translation(7, -4, 9),
				new Matrix4f().scaling(2), new Matrix4f().translation(30, -8, 11).scale(2),
				rotated, genericRotation, new Matrix4f().rotationXYZ(0.2f, 0.5f, -0.3f).scale(2, 3, 0.5f),
				new Matrix4f().scaling(-2, 3, 0.5f), new Matrix4f().scaling(1, 1, 0),
				new Matrix4f().scaling(0), new Matrix4f().scaling(2))) {
			var expected = world.determinant3x3() == 0 ? new Matrix3f().zero() : world.normal(new Matrix3f());
			assertEquals(expected, cache.update(world));
		}
	}

	@Test
	public void keepsNormalMatricesOwnedByEachOutputCache() {
		var first = new PhotonModelGeometryUtil.NormalCache();
		var second = new PhotonModelGeometryUtil.NormalCache();
		var firstNormal = first.update(new Matrix4f().scaling(2, 3, 4));
		var expected = new Matrix3f(firstNormal);
		var secondNormal = second.update(new Matrix4f().rotationXYZ(0.2f, 0.4f, 0.6f));
		assertNotSame(firstNormal, secondNormal);
		second.update(new Matrix4f().scaling(0));
		assertEquals(expected, firstNormal);
	}

	@Test
	public void cachedSubmissionPreservesVerticesNormalsColorsUvsAndSingularFallback() throws Exception {
		var worlds = List.of(new Matrix4f().scaling(2), new Matrix4f().translation(9, -3, 5).scale(2),
				new Matrix4f().rotationXYZ(0.4f, -0.2f, 0.7f).scale(2, 0.5f, 3),
				new Matrix4f().scaling(-1, 2, 3), new Matrix4f().scaling(1, 1, 0),
				new Matrix4f().scaling(0), new Matrix4f());
		for (boolean shading : List.of(false, true)) {
			for (boolean insideOut : List.of(false, true)) {
				var definition = PhotonModelCodec.read("""
						{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
						"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
						"parts":[{"id":"main","shading":%s,"insideOut":%s,"doubleSided":true}]}
						""".formatted(shading, insideOut));
				var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
				var cache = new PhotonModelGeometryUtil.NormalCache();
				for (float opacity : List.of(1f, 0.3f)) {
					var frame = sampler.sample(new PhotonModelPoseSampler.Input(0, "", true, 1, 1,
							Anchor.NONE, opacity, Map.of()), false);
					for (var world : worlds) {
						var original = new Collector();
						var cached = new Collector();
						int triangles = PhotonModelGeometryUtil.write(original, frame, 0, world, 0xFF90A0C0, 0x00F000F0);
						assertEquals(triangles, PhotonModelGeometryUtil.write(cached, frame, 0, world, 0xFF90A0C0,
								0x00F000F0, cache));
						assertEquals(original.positions, cached.positions);
						assertEquals(original.normals, cached.normals);
						assertEquals(original.uvs, cached.uvs);
						assertEquals(original.colors, cached.colors);
						assertEquals(original.alphas, cached.alphas);
						assertEquals(original.light, cached.light);
					}
				}
			}
		}
	}

	private static class Collector implements VertexConsumer {
		private final List<Vector3> positions = new ArrayList<>();
		private final List<Vector3> normals = new ArrayList<>();
		private final List<List<Float>> uvs = new ArrayList<>();
		private final List<Integer> alphas = new ArrayList<>();
		private final List<List<Integer>> colors = new ArrayList<>();
		private int light;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			positions.add(new Vector3(x, y, z));
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			alphas.add(a);
			colors.add(List.of(r, g, b, a));
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
