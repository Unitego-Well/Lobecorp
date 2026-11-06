package org.unitego.lobecorp.util.photon.model;

import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.Input;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.PartOverride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PhotonModelPoseLookupTest {
	private static final String MODEL = """
			{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
			"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
			"parts":[{"id":"main","transform":{"translation":[0,0,3]}}],
			"morphs":[{"id":"rise","weight":0,"deltas":{"0":[0,0,2]}}],
			"clips":[{"id":"move","duration":10,"morphTracks":{"rise":[{"time":0,"value":0},{"time":10,"value":1}]}}]}
			""";

	@Test
	public void acceptsKnownPartAndMorphThenRejectsUnknownIdsWithoutChangingMessage() throws Exception {
		var sampler = sampler();
		var known = new Input(5, "move", false, 1, 1, Anchor.NONE, 1, Map.of("rise", 1f),
				Map.of("main", new PartOverride(new Vector3(10, 0, 0), null, null)));
		var frame = sampler.sample(known, false);
		// 部件覆盖替换TRS；rise=1使顶点先沿z增加2，随后应用覆盖平移x=10、z=0。
		assertEquals(new Vector3(10, 0, 2), frame.groups().getFirst().vertices().getFirst().position());
		var morphFailure = assertThrows(IllegalArgumentException.class, () -> sampler.sample(
				new Input(0, "", false, 1, 1, Anchor.NONE, 1, Map.of("missing", 1f)), false));
		assertEquals("模型形状键不存在：missing", morphFailure.getMessage());
		var partFailure = assertThrows(IllegalArgumentException.class, () -> sampler.sample(
				new Input(0, "", false, 1, 1, Anchor.NONE, 1, Map.of(),
						Map.of("missing", new PartOverride(Vector3.ZERO, null, null))), false));
		assertEquals("模型部件不存在：missing", partFailure.getMessage());
	}

	@Test
	public void repeatsKnownLookupSamplesDeterministicallyAndKeepsConcurrentScratchIsolation() throws Exception {
		var sampler = sampler();
		var input = new Input(5, "move", false, 1, 1, Anchor.NONE, 1, Map.of("rise", 1f),
				Map.of("main", new PartOverride(new Vector3(10, 0, 0), null, null)));
		var expected = sampler.sample(input, false);
		assertEquals(expected, sampler.sample(input, false));
		assertEquals(expected, new PhotonModelPoseSampler(sampler.prepared()).sample(input, false));
		var barrier = new CyclicBarrier(2);

		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> {
				for (int index = 0; index < 16; index++) {
					barrier.await(10, TimeUnit.SECONDS);
					assertEquals(expected, sampler.sample(input, false));
				}

				return sampler.sample(input, false);
			});
			var second = executor.submit(() -> {
				for (int index = 0; index < 16; index++) {
					barrier.await(10, TimeUnit.SECONDS);
					assertEquals(expected, sampler.sample(input, false));
				}

				return sampler.sample(input, false);
			});
			assertEquals(expected, first.get(30, TimeUnit.SECONDS));
			assertEquals(expected, second.get(30, TimeUnit.SECONDS));
		}
	}

	private static PhotonModelPoseSampler sampler() throws Exception {
		return new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(PhotonModelCodec.read(MODEL)));
	}
}
