package org.unitego.lobecorp.util.photon.model;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector2;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.IPrimitivePoseSink;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.Input;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonModelCompiledSamplingTest {
	private static final String MODEL = """
			{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
			"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]],"normals":[[0,0,1],[0,0,1],[0,0,1]]}],
			"parts":[{"id":"main","opacity":0.5}],
			"clips":[{"id":"move","duration":10,"opacityKeys":[{"time":0,"value":1},{"time":10,"value":0.5}]}]}
			""";

	@Test
	public void sampleIntoEmitsOriginalCornerOrderAndMatchesLegacyFrame() throws Exception {
		var sampler = sampler();
		var input = new Input(5, "move", false, 1, 1, Anchor.NONE, 1, Map.of());
		var expected = sampler.sample(input, false);
		var sink = new RecordingSink();
		var workspace = sampler.newWorkspace();

		try (workspace) {
			sampler.sampleInto(input, false, workspace, sink);
		}

		assertEquals(expected.opacity(), sink.opacity);
		assertEquals(expected.min(), sink.min);
		assertEquals(expected.max(), sink.max);
		assertNull(sink.timings);
		assertEquals(expected.groups().size(), sink.groups.size());
		assertEquals(expected.groups().getFirst().vertices().size(), sink.groups.getFirst().vertices.size());
		assertEquals(expected.groups().getFirst().opacity(), sink.groups.getFirst().opacity);

		for (int index = 0; index < expected.groups().getFirst().vertices().size(); index++) {
			var vertex = expected.groups().getFirst().vertices().get(index);
			var primitive = sink.groups.getFirst().vertices.get(index);
			assertEquals(vertex.position(), new Vector3(primitive.x, primitive.y, primitive.z));
			assertEquals(vertex.normal(), new Vector3(primitive.nx, primitive.ny, primitive.nz));
			assertEquals(vertex.uv(), new Vector2(primitive.u, primitive.v));
		}
	}

	@Test
	public void clipLookupUsesExistingIndexAndReturnsNullForUnknown() throws Exception {
		var sampler = sampler();
		var move = sampler.clip("move");
		assertNotNull(move);
		assertEquals("move", move.id());
		assertNull(sampler.clip(""));
		assertNull(sampler.clip("missing"));
	}

	@Test
	public void workspaceRejectsWrongOwnerThreadClosedAndReentrantUseThenRecoversAfterFailure() throws Exception {
		var sampler = sampler();
		var other = sampler();
		var workspace = sampler.newWorkspace();
		var input = new Input(0, "", false, 1, 1, Anchor.NONE, 1, Map.of());
		var sink = new RecordingSink();

		assertThrows(IllegalArgumentException.class, () -> other.sampleInto(input, false, workspace, sink));
		try (var executor = Executors.newSingleThreadExecutor()) {
			var wrongThread = executor.submit(() -> assertThrows(IllegalStateException.class,
					() -> sampler.sampleInto(input, false, workspace, sink)));
			wrongThread.get(10, TimeUnit.SECONDS);
			var wrongClose = executor.submit(() -> assertThrows(IllegalStateException.class, workspace::close));
			wrongClose.get(10, TimeUnit.SECONDS);
		}

		var failure = new Input(0, "missing", false, 1, 1, Anchor.NONE, 1, Map.of());
		assertThrows(IllegalArgumentException.class, () -> sampler.sampleInto(failure, false, workspace, sink));
		sampler.sampleInto(input, false, workspace, sink);
		assertFalse(sink.frames.isEmpty());

		var reentrant = new IPrimitivePoseSink() {
			@Override
			public void beginFrame(PhotonModelTopologyUtil.Prepared prepared, float opacity, int groupCount) {
				assertThrows(IllegalStateException.class, () -> sampler.sampleInto(input, false, workspace, sink));
			}

			@Override
			public void beginGroup(int groupIndex, int vertexCount, float opacity) {
			}

			@Override
			public void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
			}

			@Override
			public void endGroup() {
			}

			@Override
			public void endFrame(Vector3 min, Vector3 max, PhotonModelPoseSampler.Timings timings) {
			}
		};
		sampler.sampleInto(input, false, workspace, reentrant);
		sampler.sampleInto(input, false, workspace, sink);
		workspace.close();
		assertThrows(IllegalStateException.class, () -> sampler.sampleInto(input, false, workspace, sink));
	}

	@Test
	public void failedSampleNeverCallsSuccessfulEndFrameThenWorkspaceRecovers() throws Exception {
		var sampler = sampler();
		var workspace = sampler.newWorkspace();
		var ended = new AtomicBoolean(false);
		var sink = new IPrimitivePoseSink() {
			@Override
			public void beginFrame(PhotonModelTopologyUtil.Prepared prepared, float opacity, int groupCount) {
			}

			@Override
			public void beginGroup(int groupIndex, int vertexCount, float opacity) {
			}

			@Override
			public void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
			}

			@Override
			public void endGroup() {
			}

			@Override
			public void endFrame(Vector3 min, Vector3 max, PhotonModelPoseSampler.Timings timings) {
				ended.set(true);
			}
		};

		assertThrows(IllegalArgumentException.class, () -> sampler.sampleInto(
				new Input(0, "missing", false, 1, 1, Anchor.NONE, 1, Map.of()), false, workspace, sink));
		assertFalse(ended.get());
		sampler.sampleInto(new Input(0, "", false, 1, 1, Anchor.NONE, 1, Map.of()), false, workspace, sink);
		assertTrue(ended.get());
		workspace.close();
	}

	private static PhotonModelPoseSampler sampler() throws Exception {
		return new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(PhotonModelCodec.read(MODEL)));
	}

	private record Primitive(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
	}

	private record Group(float opacity, List<Primitive> vertices) {
	}

	private static final class RecordingSink implements IPrimitivePoseSink {
		private final List<Group> groups = new ArrayList<>();
		private final List<Boolean> frames = new ArrayList<>();
		private float opacity;
		private float groupOpacity;
		private List<Primitive> vertices;
		private Vector3 min;
		private Vector3 max;
		private PhotonModelPoseSampler.Timings timings;

		@Override
		public void beginFrame(PhotonModelTopologyUtil.Prepared prepared, float opacity, int groupCount) {
			this.opacity = opacity;
			groups.clear();
		}

		@Override
		public void beginGroup(int groupIndex, int vertexCount, float opacity) {
			vertices = new ArrayList<>(vertexCount);
			groupOpacity = opacity;
		}

		@Override
		public void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
			vertices.add(new Primitive(x, y, z, nx, ny, nz, u, v));
		}

		@Override
		public void endGroup() {
			groups.add(new Group(groupOpacity, vertices));
			vertices = null;
		}

		@Override
		public void endFrame(Vector3 min, Vector3 max, PhotonModelPoseSampler.Timings timings) {
			this.min = min;
			this.max = max;
			this.timings = timings;
			frames.add(true);
		}
	}
}
