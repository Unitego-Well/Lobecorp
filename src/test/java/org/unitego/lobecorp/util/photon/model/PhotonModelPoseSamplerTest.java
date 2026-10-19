package org.unitego.lobecorp.util.photon.model;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.Input;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.PartOverride;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonModelPoseSamplerTest {
	@Test
	public void overridesOneTrsComponentWhileKeepingOtherClipComponents() throws Exception {
		var definition = PhotonModelCodec.read("""
				{"version":1,"vertices":[[1,0,0],[0,1,0],[0,0,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[1,0],[0,1],[0,0]]}],
				"parts":[{"id":"main","transform":{"translation":[0,2,0]}}],
				"clips":[{"id":"grow","duration":10,"loop":false,"partTracks":{"main":[
				{"time":0,"transform":{"translation":[0,2,0],"scale":[1,1,1]}},
				{"time":10,"transform":{"translation":[0,4,0],"scale":[3,3,3]}}]}}]}
				""");
		var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
		var before = sampler.sample(new Input(5, "grow", false, 1, 1, Anchor.NONE, 1, Map.of()), false);
		var input = new Input(5, "grow", false, 1, 1, Anchor.NONE, 1, Map.of(),
				Map.of("main", new PartOverride(new Vector3(5, 0, 0), null, null)));
		var overridden = sampler.sample(input, false);
		assertEquals(new Vector3(2, 3, 0), before.groups().getFirst().vertices().getFirst().position());
		assertEquals(new Vector3(7, 0, 0), overridden.groups().getFirst().vertices().getFirst().position());
		assertEquals(before.groups(), sampler.sample(new Input(5, "grow", false, 1, 1, Anchor.NONE, 1, Map.of()), false).groups());
		assertThrows(UnsupportedOperationException.class, () -> input.partOverrides().clear());
		assertThrows(IllegalArgumentException.class, () -> new PartOverride(new Vector3(Float.NaN, 0, 0), null, null));
		assertThrows(IllegalArgumentException.class, () -> sampler.sample(new Input(0, "", false, 1, 1, Anchor.NONE, 1,
				Map.of(), Map.of("missing", new PartOverride(Vector3.ZERO, null, null))), false));
	}

	@Test
	public void samplesHierarchyMorphAndIndependentClipTimes() throws Exception {
		var definition = PhotonModelCodec.read("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]],"partId":"child"}],
				"parts":[{"id":"child","parentId":"root","transform":{"translation":[0,2,0]}},
				{"id":"root","transform":{"translation":[1,0,0]}}],
				"morphs":[{"id":"rise","weight":0.25,"deltas":{"0":[0,0,2]}}],
				"clips":[{"id":"move","duration":10,"loop":true,
				"partTracks":{"child":[{"time":0,"transform":{"translation":[0,2,0]}},
				{"time":10,"transform":{"translation":[0,4,0]}}]},
				"morphTracks":{"rise":[{"time":0,"value":0},{"time":10,"value":1}]},
				"opacityKeys":[{"time":0,"value":1},{"time":10,"value":0}]}]}
				""");
		var prepared = PhotonModelTopologyUtil.prepare(definition);
		var sampler = new PhotonModelPoseSampler(prepared);
		var middle = sampler.sample(new Input(5, "move", false, 1, 1, Anchor.NONE, 1, Map.of()), true);
		assertEquals(new Vector3(1, 3, 1), middle.groups().getFirst().vertices().getFirst().position());
		assertEquals(0.5f, middle.opacity());
		assertNotNull(middle.timings());
		assertTrue(middle.timings().curvesNanos() >= 0 && middle.timings().deformationNanos() >= 0 && middle.timings().normalsNanos() >= 0);
		assertSame(prepared, middle.prepared());
		var end = sampler.sample(new Input(10, "move", false, 1, 1, Anchor.NONE, 1, Map.of()), false);
		assertEquals(new Vector3(1, 4, 2), end.groups().getFirst().vertices().getFirst().position());
		var loop = sampler.sample(new Input(10, "move", true, 1, 1, Anchor.NONE, 1, Map.of()), false);
		assertEquals(new Vector3(1, 2, 0), loop.groups().getFirst().vertices().getFirst().position());
		var base = sampler.sample(new Input(5, "", true, 1, 1, Anchor.NONE, 1, Map.of()), false);
		assertEquals(new Vector3(1, 2, 0.5f), base.groups().getFirst().vertices().getFirst().position());
		assertNull(base.timings());
		assertEquals(new Vector3(1, 3, 1), middle.groups().getFirst().vertices().getFirst().position());
		assertEquals(new Vector3(0, 0, 0), definition.vertices().getFirst());
		assertThrows(UnsupportedOperationException.class, () -> middle.groups().clear());
		assertThrows(UnsupportedOperationException.class, () -> middle.groups().getFirst().vertices().clear());
		assertThrows(IllegalArgumentException.class, () -> sampler.sample(new Input(0, "missing", true, 1, 1, Anchor.NONE, 1, Map.of()), false));
		assertThrows(IllegalArgumentException.class, () -> sampler.sample(new Input(0, "", true, 1, 1, Anchor.NONE, 1, Map.of("missing", 1f)), false));
	}

	@Test
	public void preservesStretchAnchorsWidthAndCollapsedNormals() throws Exception {
		var definition = PhotonModelCodec.read("""
				{"version":1,"referenceLength":2,"referenceWidth":1,"forwardAxis":0,
				"vertices":[[0,0,0],[2,0,0],[1,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0.5,1]],"normals":[[0,0,1],[0,0,1],[0,0,1]]}],
				"parts":[{"id":"main","stretch":{"enabled":true,"axis":0,"start":0.5,"end":1.5}}]}
				""");
		var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
		var start = sampler.sample(new Input(0, "", true, 4, 2, Anchor.START, 1, Map.of()), false);
		assertEquals(List.of(new Vector3(0, 0, 0), new Vector3(4, 0, 0), new Vector3(2, 2, 0)),
				start.groups().getFirst().vertices().stream().map(PhotonModelPoseSampler.Vertex::position).toList());
		var end = sampler.sample(new Input(0, "", true, 4, 2, Anchor.END, 1, Map.of()), false);
		assertEquals(new Vector3(2, 0, 0), end.groups().getFirst().vertices().get(1).position());
		var collapsed = sampler.sample(new Input(0, "", true, 0, 0, Anchor.START, 1, Map.of()), false);
		assertTrue(collapsed.groups().getFirst().vertices().stream().allMatch(vertex -> vertex.normal().equals(Vector3.ZERO)));
		assertEquals(new Vector3(0, 0, 0), start.groups().getFirst().vertices().getFirst().position());
	}

	@Test
	public void transformsAuthoredNormalsAndWindingTogether() throws Exception {
		var definition = PhotonModelCodec.read("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]],"normals":[[0,0,1],[0,0,1],[0,0,1]]}],
				"parts":[{"id":"main","transform":{"scale":[-1,2,1]}}]}
				""");
		var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
		var frame = sampler.sample(new Input(0, "", true, 1, 1, Anchor.NONE, 1, Map.of()), false);
		var normal = frame.groups().getFirst().vertices().getFirst().normal();
		assertEquals(-1, normal.z(), 1e-6);
		assertEquals(0, normal.x(), 1e-6);
		assertEquals(0, normal.y(), 1e-6);
	}

	@Test
	public void sharesPreparedDataAcrossConcurrentSamplesWithoutStateLeak() throws Exception {
		var definition = PhotonModelCodec.read("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
				"morphs":[{"id":"rise","deltas":{"0":[0,0,1]}}]}
				""");
		var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));

		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> sampler.sample(new Input(0, "", true, 1, 1, Anchor.NONE, 1, Map.of("rise", 2f)), false));
			var second = executor.submit(() -> sampler.sample(new Input(0, "", true, 1, 1, Anchor.NONE, 1, Map.of("rise", 7f)), false));
			assertEquals(2, first.get().groups().getFirst().vertices().getFirst().position().z());
			assertEquals(7, second.get().groups().getFirst().vertices().getFirst().position().z());
		}

		assertEquals(Vector3.ZERO, definition.vertices().getFirst());
	}

	@Test
	public void rejectsInvalidInputAndUnsupportedSkeletonWithoutDroppingDefinition() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> new Input(Float.NaN, "", true, 1, 1, Anchor.NONE, 1, Map.of()));
		assertThrows(IllegalArgumentException.class, () -> new Input(0, "", true, -1, 1, Anchor.NONE, 1, Map.of()));
		var definition = PhotonModelCodec.read("""
				{"version":1,"bones":[{"id":"root"}]}
				""");
		assertThrows(IOException.class, () -> new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition)));
		assertEquals("root", definition.bones().getFirst().id());
	}

	@Test
	public void preservesSubTickPrecisionAndCooperativelyCancels() throws Exception {
		var definition = PhotonModelCodec.read("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
				"morphs":[{"id":"rise","deltas":{"0":[0,0,100000000]}}],
				"clips":[{"id":"move","duration":10,"morphTracks":{"rise":[{"time":0,"value":0},{"time":10,"value":1}]}}]}
				""");
		var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
		var input = new Input(0.0000001f, "move", true, 1, 1, Anchor.NONE, 1, Map.of());
		var frame = sampler.sample(input, false);
		assertEquals(1, frame.groups().getFirst().vertices().getFirst().position().z(), 1e-5);

		try {
			Thread.currentThread().interrupt();
			assertThrows(InterruptedIOException.class, () -> PhotonModelTopologyUtil.prepare(definition));
			assertThrows(CancellationException.class, () -> sampler.sample(input, false));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			assertTrue(Thread.interrupted());
		}

		assertEquals(frame.groups(), sampler.sample(input, false).groups());
	}

	@Test
	public void samplesExistingModelOccurrencesWithoutChangingOriginals() throws Exception {
		var root = Path.of("").toAbsolutePath();

		while (root != null && !Files.isDirectory(root.resolve("src/main/resources/fxpacks"))) {
			root = root.getParent();
		}

		assertNotNull(root);
		int models = 0;

		for (var folder : List.of("src/main/resources/assets/lobecorp/fx", "src/main/resources/assets/lobecorp/models/photon",
				"src/main/resources/fxpacks")) {
			try (var paths = Files.walk(root.resolve(folder))) {
				for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
					var original = Files.readAllBytes(path);

					for (var binding : PhotonModelCodec.readEmbedded(PhotonResourceReader.read(path))) {
						var definition = binding.definition();
						var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));

						for (float time : List.of(0f, 5f, 100f)) {
							var frame = sampler.sample(new Input(time, "", true, definition.referenceLength(),
									definition.referenceWidth(), Anchor.START, 1, Map.of()), true);
							assertFalse(frame.groups().isEmpty(), binding.path());
							assertTrue(frame.min().x() <= frame.max().x(), binding.path());
							assertNotNull(frame.timings());
						}

						models++;
					}

					assertArrayEquals(original, Files.readAllBytes(path));
				}
			}
		}

		assertTrue(models > 0);
		System.out.println("R1_SAMPLED_MODEL_OCCURRENCES=" + models);
	}
}
