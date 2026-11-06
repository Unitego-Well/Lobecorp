package org.unitego.lobecorp.util.photon.model;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Rotation;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.Frame;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.Input;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.PartOverride;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonModelSamplingScratchTest {
    private static final String TRIANGLE = """
            {"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
            "faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]],
            "normals":[[0,0,1],[0,0,1],[0,0,1]]}]%s}
            """;
    private static final String RIG = """
            ,"parts":[{"id":"main","transform":{"translation":[0,0,3]}}],
            "bones":[{"id":"child","parentId":"root","bind":{"translation":[0,1,0]}},
            {"id":"root","bind":{"translation":[1,0,0]}}],
            "weights":{"0":[{"boneId":"child","value":2}],
            "1":[{"boneId":"root","value":1},{"boneId":"child","value":3}]},
            "morphs":[{"id":"rise","weight":0.25,"deltas":{"0":[0,0,2]}}],
            "clips":[{"id":"move","duration":10,"loop":true,
            "morphTracks":{"rise":[{"time":0,"value":0},{"time":10,"value":1}]}}]
            """;

    @Test
    public void resetsParentChildPivotAndTrsWithoutChangingEarlierFrames() throws Exception {
        var definition = PhotonModelCodec.read("""
                {"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
                "faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]],"partId":"child"}],
                "parts":[{"id":"child","parentId":"root","pivot":[1,0,0],
                "transform":{"translation":[0,2,0],"scale":[2,1,1]}},
                {"id":"root","transform":{"translation":[10,0,0]}}]}
                """);
        var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
        var base = input(Map.of(), Map.of(), Map.of());
        var first = sampler.sample(base, false);
        assertEquals(new Vector3(9, 2, 0), position(first, 0));
        assertEquals(new Vector3(11, 2, 0), position(first, 1));
        var changed = input(Map.of(), Map.of(
                "root", new PartOverride(new Vector3(20, 0, 0), new Rotation(0, 0, 1, 0), null),
                "child", new PartOverride(null, null, new Vector3(-1, 1, 1))), Map.of());
        var second = sampler.sample(changed, false);
        assertEquals(new Vector3(18, -2, 0), position(second, 0));
        assertEquals(new Vector3(19, -2, 0), position(second, 1));
        assertEquals(new Vector3(9, 2, 0), position(first, 0));
        assertEquals(new PhotonModelPoseSampler(sampler.prepared()).sample(base, false), first);
        assertEquals(first, sampler.sample(base, false));
        assertThrows(UnsupportedOperationException.class, () -> first.groups().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.groups().getFirst().vertices().clear());
    }

    @Test
    public void resetsBoneHierarchyAndNormalizedWeightsWhileLeavingUnskinnedVerticesAlone() throws Exception {
        var sampler = sampler(RIG);
        var base = input(Map.of(), Map.of(), Map.of());
        var first = sampler.sample(base, false);
        var changed = new Input(5, "move", false, 1, 1, Anchor.NONE, 1, Map.of(), Map.of(), Map.of(
                "root", new PartOverride(new Vector3(3, 0, 0), null, null),
                "child", new PartOverride(new Vector3(0, 5, 0), null, null)));
        var second = sampler.sample(changed, false);
        assertEquals(List.of(new Vector3(2, 4, 4), new Vector3(3, 3, 3), new Vector3(0, 1, 3)),
                second.groups().getFirst().vertices().stream().map(PhotonModelPoseSampler.Vertex::position).toList());
        assertEquals(new Vector3(0, 0, 3.5f), position(first, 0));
        assertEquals(first, sampler.sample(base, false));
        assertEquals(new PhotonModelPoseSampler(sampler.prepared()).sample(changed, false), second);
    }

    @Test
    public void overwritesMorphWeightsAcrossOverridesClipsDefaultsAndZero() throws Exception {
        var sampler = sampler(RIG);
        var weighted = sampler.sample(input(Map.of("rise", 2f), Map.of(), Map.of()), false);
        assertEquals(7, position(weighted, 0).z());
        var zero = sampler.sample(input(Map.of("rise", 0f), Map.of(), Map.of()), false);
        assertEquals(3, position(zero, 0).z());
        var animated = sampler.sample(new Input(5, "move", false, 1, 1, Anchor.NONE, 1, Map.of()), false);
        assertEquals(4, position(animated, 0).z());
        var defaults = sampler.sample(input(Map.of(), Map.of(), Map.of()), false);
        assertEquals(3.5f, position(defaults, 0).z());
        assertEquals(7, position(weighted, 0).z());
        assertEquals(3, position(zero, 0).z());
        assertEquals(4, position(animated, 0).z());
        assertEquals(weighted, sampler.sample(input(Map.of("rise", 2f), Map.of(), Map.of()), false));
    }

    @Test
    public void preservesNegativeAndSingularPartNormalsAndRecoversIdentity() throws Exception {
        var sampler = sampler("");
        var base = input(Map.of(), Map.of(), Map.of());
        var first = sampler.sample(base, false);
        var negative = sampler.sample(input(Map.of(), Map.of("main",
                new PartOverride(null, null, new Vector3(-1, 2, 1))), Map.of()), false);
        assertEquals(new Vector3(-1, 0, 0), position(negative, 1));
        assertEquals(-1, negative.groups().getFirst().vertices().getFirst().normal().z());
        var collapsed = sampler.sample(input(Map.of(), Map.of("main",
                new PartOverride(null, null, new Vector3(0, 0, 1))), Map.of()), false);
        assertTrue(collapsed.groups().getFirst().vertices().stream().allMatch(vertex -> vertex.normal().equals(Vector3.ZERO)));
        assertEquals(first, sampler.sample(base, false));
        assertEquals(1, first.groups().getFirst().vertices().getFirst().normal().z());
    }

    @Test
    public void resetsNegativeAndSingularBoneMatricesBeforeTheNextSample() throws Exception {
        var sampler = sampler("""
                ,"bones":[{"id":"root"}],
                "weights":{"0":[{"boneId":"root","value":1}],"1":[{"boneId":"root","value":1}],
                "2":[{"boneId":"root","value":1}]}
                """);
        var base = input(Map.of(), Map.of(), Map.of());
        var first = sampler.sample(base, false);
        var negative = sampler.sample(input(Map.of(), Map.of(), Map.of("root",
                new PartOverride(null, null, new Vector3(-1, 2, 1)))), false);
        assertEquals(new Vector3(-1, 0, 0), position(negative, 1));
        assertEquals(-1, negative.groups().getFirst().vertices().getFirst().normal().z());
        var collapsed = sampler.sample(input(Map.of(), Map.of(), Map.of("root",
                new PartOverride(null, null, new Vector3(0, 0, 1)))), false);
        assertTrue(collapsed.groups().getFirst().vertices().stream().allMatch(vertex -> vertex.normal().equals(Vector3.ZERO)));
        assertEquals(first, sampler.sample(base, false));
    }

    @Test
    public void handlesEmptyGeometryAndAbsentMorphsOrSkinAcrossRepeatedSamples() throws Exception {
        var empty = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(PhotonModelCodec.read("{\"version\":1}")));
        var base = input(Map.of(), Map.of(), Map.of());
        var first = empty.sample(base, false);
        assertTrue(first.groups().isEmpty());
        assertEquals(Vector3.ZERO, first.min());
        assertEquals(Vector3.ZERO, first.max());
        assertEquals(first, empty.sample(base, false));
        var plain = sampler("");
        assertEquals(new Vector3(1, 0, 0), position(plain.sample(base, false), 1));
        assertEquals(plain.sample(base, false), plain.sample(base, false));
    }

    @Test
    public void keepsScratchIsolatedAcrossConcurrentThreadsAndRepeatedInputChanges() throws Exception {
        var sampler = sampler(RIG);
        var a = input(Map.of("rise", 2f), Map.of("main", new PartOverride(new Vector3(10, 0, 0), null, null)),
                Map.of("root", new PartOverride(new Vector3(3, 0, 0), null, null)));
        var b = input(Map.of("rise", -1f), Map.of(), Map.of("child",
                new PartOverride(new Vector3(0, 4, 0), null, null)));
        var expectedA = new PhotonModelPoseSampler(sampler.prepared()).sample(a, false);
        var expectedB = new PhotonModelPoseSampler(sampler.prepared()).sample(b, false);
        var barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                for (int index = 0; index < 32; index++) {
                    barrier.await(10, TimeUnit.SECONDS);
                    assertEquals(expectedA, sampler.sample(a, false));
                    assertEquals(expectedB, sampler.sample(b, false));
                }

                return sampler.sample(a, false);
            });
            var second = executor.submit(() -> {
                for (int index = 0; index < 32; index++) {
                    barrier.await(10, TimeUnit.SECONDS);
                    assertEquals(expectedB, sampler.sample(b, false));
                    assertEquals(expectedA, sampler.sample(a, false));
                }

                return sampler.sample(b, false);
            });
            assertEquals(expectedA, first.get(30, TimeUnit.SECONDS));
            assertEquals(expectedB, second.get(30, TimeUnit.SECONDS));
        }

        assertEquals(expectedA, sampler.sample(a, false));
        assertEquals(expectedB, sampler.sample(b, false));
    }

    private static PhotonModelPoseSampler sampler(String extra) throws Exception {
        return new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(PhotonModelCodec.read(TRIANGLE.formatted(extra))));
    }

    private static Input input(Map<String, Float> morphs, Map<String, PartOverride> parts, Map<String, PartOverride> bones) {
        return new Input(0, "", false, 1, 1, Anchor.NONE, 1, morphs, parts, bones);
    }

    private static Vector3 position(Frame frame, int corner) {
        return frame.groups().getFirst().vertices().get(corner).position();
    }
}
