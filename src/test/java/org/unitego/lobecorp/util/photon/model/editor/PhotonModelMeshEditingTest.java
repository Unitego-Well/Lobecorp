package org.unitego.lobecorp.util.photon.model.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.photon.model.PhotonModelCodec;
import org.unitego.lobecorp.util.photon.model.PhotonModelWorkspaceEditUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Edge;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonModelMeshEditingTest {
    @Test
    public void implicitPartRemainsImplicitUntilItsTransformChanges() {
        var asset = JsonParser.parseString("""
                {"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
                "faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}]}
                """).getAsJsonObject();
        assertEquals("main", PhotonModelWorkspaceEditUtil.parts(asset).get(0).getAsJsonObject().get("id").getAsString());
        assertFalse(asset.has("parts"));
        var mesh = PhotonModelEditMesh.read(asset);
        assertEquals(asset, mesh.document());
        mesh.parts.getFirst().transform.translation.x = 2;
        var result = mesh.document();
        assertEquals(2, result.getAsJsonArray("parts").get(0).getAsJsonObject()
                .getAsJsonObject("transform").getAsJsonArray("translation").get(0).getAsFloat());
        assertDoesNotThrow(() -> PhotonModelCodec.read(result));
    }

    @Test
    public void loopCutRejectsChangedExtensionWithoutOverwritingIt() {
        var mesh = cube();
        var face = mesh.faces.getFirst();
        var plan = PhotonModelLoopCutUtil.plan(mesh, face.partId, Edge.of(face.vertices[0], face.vertices[1]), 0.5f, 1);
        face.source.addProperty("extension", "new_value");
        var before = mesh.document();
        assertThrows(IllegalArgumentException.class, () -> PhotonModelLoopCutUtil.apply(mesh, plan));
        assertEquals(before, mesh.document());
    }

    @Test
    public void skeletalRenamePreservesReferencesAndUnknownFields() {
        var asset = JsonParser.parseString("""
                {"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
                "faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
                "bones":[{"id":"root","extension":7},{"id":"child","parentId":"root"}],
                "weights":{"0":[{"boneId":"root","value":1,"extension":8}]},
                "morphs":[{"id":"shape","deltas":{"0":[0,0,1]},"extension":9}],
                "clips":[{"id":"clip","duration":20,"boneTracks":{"root":[{"time":0,"transform":{},"extension":10}]},
                "morphTracks":{"shape":[{"time":0,"value":1,"extension":11}]}}]}
                """).getAsJsonObject();
        PhotonModelWorkspaceEditUtil.renameNode(asset, "bones", "root", "renamed_root");
        PhotonModelWorkspaceEditUtil.renameNode(asset, "morphs", "shape", "renamed_shape");
        assertEquals("renamed_root", asset.getAsJsonArray("bones").get(1).getAsJsonObject().get("parentId").getAsString());
        var weight = asset.getAsJsonObject("weights").getAsJsonArray("0").get(0).getAsJsonObject();
        assertEquals("renamed_root", weight.get("boneId").getAsString());
        assertEquals(8, weight.get("extension").getAsInt());
        var clip = asset.getAsJsonArray("clips").get(0).getAsJsonObject();
        assertEquals(10, clip.getAsJsonObject("boneTracks").getAsJsonArray("renamed_root").get(0).getAsJsonObject().get("extension").getAsInt());
        assertEquals(11, clip.getAsJsonObject("morphTracks").getAsJsonArray("renamed_shape").get(0).getAsJsonObject().get("extension").getAsInt());
        assertDoesNotThrow(() -> PhotonModelCodec.read(asset));
    }

    @Test
    public void duplicatedImplicitPartKeepsItsAnimationTrack() {
        var asset = JsonParser.parseString("""
                {"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
                "faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
                "clips":[{"id":"clip","duration":20,"partTracks":{"main":[{"time":0,"transform":{},"extension":12}]}}]}
                """).getAsJsonObject();
        PhotonModelWorkspaceEditUtil.duplicatePart(asset, "main");
        String id = asset.getAsJsonArray("parts").get(1).getAsJsonObject().get("id").getAsString();
        var tracks = asset.getAsJsonArray("clips").get(0).getAsJsonObject().getAsJsonObject("partTracks");
        assertEquals(tracks.get("main"), tracks.get(id));
        assertNotSame(tracks.get("main"), tracks.get(id));
        assertDoesNotThrow(() -> PhotonModelCodec.read(asset));
    }

	@Test
	public void deletionKeepsUnchangedVertexNumbersAtTheirNewIndices() {
		var asset = JsonParser.parseString("""
				{"version":1,"vertices":[[0,0,0],[0.123456789,0,0],[1,0,0],[0,1,0]],
				"faces":[{"vertices":[1,2,3],"uv":[[0,0],[1,0],[0,1]]}]}
				""").getAsJsonObject();
		var mesh = PhotonModelEditMesh.read(asset);
		PhotonModelEditUtil.deleteVertices(mesh, Set.of(0));
		assertEquals(asset.getAsJsonArray("vertices").get(1), mesh.document().getAsJsonArray("vertices").get(0));
	}
    private static PhotonModelEditMesh cube() {
        var mesh = PhotonModelEditMesh.read(JsonParser.parseString("{\"version\":1,\"vertices\":[],\"faces\":[],\"extension\":{\"keep\":true}}").getAsJsonObject());
        PhotonModelEditUtil.addPrimitive(mesh, PhotonModelEditUtil.Primitive.CUBE);
        mesh.faces.forEach(face -> face.source.addProperty("extension", "keep"));
        return mesh;
    }

    @Test
    public void parameterizedPrimitivesProduceValidCurrentGeometry() {
        for (var shape : PhotonModelPrimitiveUtil.Shape.values()) {
            var mesh = PhotonModelEditMesh.read(JsonParser.parseString("{\"version\":1,\"vertices\":[],\"faces\":[]}").getAsJsonObject());
            PhotonModelPrimitiveUtil.add(mesh, shape, PhotonModelPrimitiveUtil.Parameters.defaults());
            assertDoesNotThrow(mesh::validate, shape.name());
            assertFalse(mesh.faces.isEmpty(), shape.name());
        }
    }

    @Test
    public void invalidPrimitiveLeavesCandidateUnchanged() {
        var mesh = cube();
        var before = mesh.document();
        assertThrows(IllegalArgumentException.class, () -> PhotonModelPrimitiveUtil.add(mesh, PhotonModelPrimitiveUtil.Shape.RING,
                new PhotonModelPrimitiveUtil.Parameters(0.1f, 1, 1, 180, 90, 16, 8)));
        assertEquals(before, mesh.document());
    }

    @Test
    public void extrusionPreservesMaterialAndUnknownFieldsOnCapAndSides() {
        var mesh = cube();
        var source = mesh.faces.getFirst();
        source.material = "surface";
        var asset = mesh.document();
        var materials = new JsonObject();
        materials.add("surface", new JsonObject());
        asset.add("materials", materials);
        mesh = PhotonModelEditMesh.read(asset);
        PhotonModelEditUtil.extrude(mesh, Set.of(0), 0.2f);
        mesh.validate();
        assertEquals(10, mesh.faces.size());
        for (var face : mesh.faces.subList(5, mesh.faces.size())) {
            assertEquals("surface", face.material);
            assertEquals("keep", face.source.get("extension").getAsString());
        }
        assertTrue(mesh.document().getAsJsonObject("extension").get("keep").getAsBoolean());
    }

    @Test
    public void triangulationRetainsEachFaceExtensionAndCornerUvs() {
        var mesh = cube();
        PhotonModelTopologyUtil.triangulate(mesh, List.of(0));
        mesh.validate();
        assertEquals(7, mesh.faces.size());
        assertTrue(mesh.faces.stream().allMatch(face -> face.source.get("extension").getAsString().equals("keep")));
        assertTrue(mesh.faces.stream().allMatch(face -> face.uv.length == face.vertices.length));
    }

    @Test
    public void uniformLoopCutCreatesSharedVerticesAndPreservesExtensions() {
        var mesh = cube();
        var seed = mesh.faces.getFirst().vertices;
        var plan = PhotonModelLoopCutUtil.plan(mesh, mesh.faces.getFirst().partId, Edge.of(seed[0], seed[1]), -1, 2);
        assertFalse(plan.segments().isEmpty());
        assertEquals(8, mesh.vertices.size());
        PhotonModelLoopCutUtil.apply(mesh, plan);
        mesh.validate();
        assertEquals(16, mesh.vertices.size());
        assertTrue(mesh.faces.stream().allMatch(face -> face.source.get("extension").getAsString().equals("keep")));
    }

    @Test
    public void loopCutRejectsStaleGeometryWithoutPartialChanges() {
        var mesh = cube();
        var seed = mesh.faces.getFirst().vertices;
        var plan = PhotonModelLoopCutUtil.plan(mesh, mesh.faces.getFirst().partId, Edge.of(seed[0], seed[1]), 0.5f, 1);
        mesh.vertices.get(seed[0]).add(0, 0, 0.1f);
        var before = mesh.document();
        assertThrows(IllegalArgumentException.class, () -> PhotonModelLoopCutUtil.apply(mesh, plan));
        assertEquals(before, mesh.document());
    }

    @Test
    public void centerMergeUsesSelectionOrderAndRejectsIncompatibleTopology() {
        var mesh = cube();
        var before = mesh.document();
        assertThrows(IllegalArgumentException.class, () -> PhotonModelTopologyUtil.merge(mesh, Set.of(0),
                PhotonModelTopologyUtil.MergeMode.CENTER, 0));
        assertEquals(before, mesh.document());
        var ordered = new LinkedHashSet<>(List.of(0, 1));
        PhotonModelTopologyUtil.merge(mesh, ordered, PhotonModelTopologyUtil.MergeMode.FIRST, 0);
        mesh.validate();
        assertEquals(7, mesh.vertices.size());
    }

    @Test
    public void vertexDeletionRemapsMorphDeltasWithoutDroppingUnknownData() {
        var asset = JsonParser.parseString("""
                {"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0],[0,0,1]],
                "faces":[{"vertices":[1,2,3],"uv":[[0,0],[1,0],[0,1]],"tag":"face"}],
                "morphs":[{"id":"shape","deltas":{"1":[0.123456789,0,0]},"tag":"morph"}]}
                """).getAsJsonObject();
        var mesh = PhotonModelEditMesh.read(asset);
        PhotonModelEditUtil.deleteVertices(mesh, Set.of(0));
        var result = mesh.document();
        assertEquals("morph", result.getAsJsonArray("morphs").get(0).getAsJsonObject().get("tag").getAsString());
        assertTrue(result.getAsJsonArray("morphs").get(0).getAsJsonObject().getAsJsonObject("deltas").has("0"));
        assertEquals(JsonParser.parseString("[0.123456789,0,0]"),
                result.getAsJsonArray("morphs").get(0).getAsJsonObject().getAsJsonObject("deltas").get("0"));
        assertDoesNotThrow(() -> PhotonModelCodec.read(result));
    }

    @Test
    public void movingPivotKeepsBaseGeometryInPlace() {
        var mesh = cube();
        var part = mesh.parts.getLast();
        part.transform.rotation.rotateZ(0.7f);
        part.transform.scale.set(2, 1, 1);
        var point = new Vector3f(mesh.vertices.getFirst());
        var before = new Matrix4f().translate(part.transform.translation).translate(part.pivot)
                .rotate(part.transform.rotation).scale(part.transform.scale).translate(new Vector3f(part.pivot).negate()).transformPosition(new Vector3f(point));
        PhotonModelEditUtil.movePartPivot(mesh, part.id, new Vector3f(0.2f, 0.3f, 0.4f));
        var after = new Matrix4f().translate(part.transform.translation).translate(part.pivot)
                .rotate(part.transform.rotation).scale(part.transform.scale).translate(new Vector3f(part.pivot).negate()).transformPosition(new Vector3f(point));
        assertEquals(0, before.distance(after), 1e-6);
    }
}
