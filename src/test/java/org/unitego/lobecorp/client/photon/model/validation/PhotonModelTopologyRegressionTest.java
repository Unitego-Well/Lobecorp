package org.unitego.lobecorp.client.photon.model.validation;

import org.junit.jupiter.api.Test;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Bone;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Face;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Weight;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Edge;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Primitive;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelLoopCutUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelTopologyUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelTopologyUtil.MergeMode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 拓扑编辑的数据不变量回归，不启动游戏或编辑器。
public class PhotonModelTopologyRegressionTest {


	@Test
	public void mergeModesAndWeights() {
		for (var mode : MergeMode.values()) {
			var asset = quad();
			PhotonModelTopologyUtil.merge(asset, selected(0, 1), mode, 1.01f);
			asset.validate();
			check(asset.vertices.size() == 3 && asset.faces.size() == 1, mode + " reduced topology");
			var expected = new Vector3f(switch (mode) {
				case FIRST -> 0f;
				case LAST -> 1f;
				default -> 0.5f;
			}, 0, 0);
			check(asset.vertices.stream().anyMatch(expected::equals), mode + " target position");
			check(asset.faces.getFirst().vertices.length == 3, mode + " resulting triangle");
		}

		var asset = quad();
		bone(asset, "a");
		bone(asset, "b");
		asset.weights.put(0, List.of(weight("a", 2f)));
		asset.weights.put(1, List.of(weight("b", 1f)));
		asset.weights.put(3, List.of(weight("a", 1f)));
		PhotonModelTopologyUtil.merge(asset, selected(0, 1), MergeMode.CENTER, 0);
		asset.validate();
		check(asset.weights.get(0).size() == 2, "merged bone influences");
		check(asset.weights.get(0).stream().allMatch(value -> value.value == 0.5f), "averaged bone weights");
		check(asset.weights.get(2).getFirst().boneId.equals("a"), "untouched vertex weight remapped");
		check(asset.weights.keySet().stream().allMatch(index -> index < asset.vertices.size()), "weight index bounds");

		var reversed = quad();
		PhotonModelTopologyUtil.merge(reversed, selected(1, 0), MergeMode.FIRST, 0);
		check(reversed.vertices.stream().anyMatch(new Vector3f(1, 0, 0)::equals), "first follows selection order");
	}

	@Test
	public void mergeUVSeam() {
		var asset = quad();
		asset.faces.clear();
		asset.vertices.add(new Vector3f(asset.vertices.getFirst()));
		face(asset, 0, 1, 2);
		face(asset, 4, 2, 3);
		asset.faces.getLast().uv[0].set(5, 7);
		asset.faces.getFirst().uv[0].set(0, 0);
		PhotonModelTopologyUtil.merge(asset, selected(0, 4), MergeMode.DISTANCE, 0.001f);
		asset.validate();
		check(asset.vertices.size() == 4 && asset.faces.size() == 2, "distance weld duplicated seam vertex");
		check(asset.faces.getFirst().vertices[0] == asset.faces.getLast().vertices[0], "shared welded index");
		check(asset.faces.getFirst().uv[0].equals(new Vector2f(0, 0))
				&& asset.faces.getLast().uv[0].equals(new Vector2f(5, 7)), "per-corner UV seam preserved");
	}

	@Test
	public void collapseConnectedEdges() {
		var asset = quad();
		PhotonModelTopologyUtil.collapse(asset, new LinkedHashSet<>(List.of(Edge.of(0, 1))));
		asset.validate();
		check(asset.faces.size() == 1 && asset.faces.getFirst().vertices.length == 3, "quad edge collapses to triangle");
		check(asset.vertices.getFirst().equals(new Vector3f(0.5f, 0, 0)), "collapse midpoint");

		var connected = quad();
		PhotonModelTopologyUtil.collapse(connected, new LinkedHashSet<>(List.of(Edge.of(0, 1), Edge.of(1, 2))));
		connected.validate();
		check(connected.vertices.size() == 2 && connected.faces.isEmpty(), "connected collapsed edges form one group");
		var triangle = quad();
		triangle.faces.clear();
		face(triangle, 0, 1, 2);
		PhotonModelTopologyUtil.collapse(triangle, new LinkedHashSet<>(List.of(Edge.of(0, 1))));
		triangle.validate();
		check(triangle.faces.isEmpty(), "collapsed degenerate triangle removed");
	}

	@Test
	public void dissolveCompatibleFaces() {
		var asset = strip();
		PhotonModelTopologyUtil.dissolve(asset, new LinkedHashSet<>(List.of(Edge.of(1, 4))));
		asset.validate();
		check(asset.faces.size() == 1 && asset.faces.getFirst().vertices.length == 6, "dissolve preserves boundary");
		check(Math.abs(area(asset) - 2f) < 0.00001f, "dissolve preserves surface area");
		checkPlanarUV(asset, 0.5f);

		var materials = strip();
		materials.faces.getLast().material = "other";
		rejects(() -> PhotonModelTopologyUtil.dissolve(materials, selectedEdge(1, 4)));
		check(materials.faces.size() == 2, "material rejection leaves faces");
		var seam = strip();
		seam.faces.getLast().uv[0].x += 1f;
		rejects(() -> PhotonModelTopologyUtil.dissolve(seam, selectedEdge(1, 4)));
		var boundary = strip();
		rejects(() -> PhotonModelTopologyUtil.dissolve(boundary, selectedEdge(0, 1)));
	}

	@Test
	public void faceCreation() {
		var asset = openPyramid();
		PhotonModelTopologyUtil.createFace(asset, PhotonModelAsset.DEFAULT_PART_ID, selected(0, 1, 2, 3));
		asset.validate();
		check(asset.faces.size() == 5 && asset.faces.getLast().vertices.length == 4, "ordered boundary face created");
		check(asset.faces.getLast().uv.length == 4, "created UV corners");
		rejects(() -> PhotonModelTopologyUtil.createFace(asset, PhotonModelAsset.DEFAULT_PART_ID, selected(0, 1, 2, 3)));
		var crossing = openPyramid();
		rejects(() -> PhotonModelTopologyUtil.createFace(crossing, PhotonModelAsset.DEFAULT_PART_ID, selected(0, 2, 1, 3)));
		check(crossing.faces.size() == 4, "invalid polygon does not append face");
	}

	@Test
	public void triangulationAttributes() {
		var asset = quad();
		var original = asset.faces.getFirst();
		original.material = "sample";
		original.normals = new Vector3f[]{new Vector3f(0, 0, 1), new Vector3f(0, 0, 1),
				new Vector3f(0, 0, 1), new Vector3f(0, 0, 1)};
		original.uv[2].set(3, 7);
		var snapshot = original.copy();
		PhotonModelTopologyUtil.triangulate(asset, List.of(0));
		asset.validate();
		check(asset.faces.size() == 2 && asset.vertices.size() == 4, "triangulation keeps geometry indices");

		for (var triangle : asset.faces) {
			check(triangle.material.equals("sample") && triangle.partId.equals(snapshot.partId), "triangle metadata");
			for (int corner = 0; corner < triangle.vertices.length; corner++) {
				int source = triangle.vertices[corner];
				check(triangle.uv[corner].equals(snapshot.uv[source]), "triangle corner UV");
				check(triangle.normals[corner].equals(snapshot.normals[source]), "triangle corner normal");
			}
		}
		check(Math.abs(area(asset) - 1f) < 0.00001f, "triangle area");
	}

	@Test
	public void loopCutOpenBands() {
		for (int count : new int[]{1, 3}) {
			var asset = quad();
			var plan = PhotonModelLoopCutUtil.plan(asset, PhotonModelAsset.DEFAULT_PART_ID, Edge.of(0, 1), -1f, count);
			check(asset.vertices.size() == 4 && asset.faces.size() == 1, "planning is read only");
			check(plan.segments().size() == count, "open preview count");
			PhotonModelLoopCutUtil.apply(asset, plan);
			asset.validate();
			check(asset.vertices.size() == 4 + 2 * count && asset.faces.size() == count + 1, "uniform band topology");
			check(Math.abs(area(asset) - 1f) < 0.00001f, "uniform cut preserves area");
			checkPlanarUV(asset, 1f);
		}

		var positioned = quad();
		var plan = PhotonModelLoopCutUtil.plan(positioned, PhotonModelAsset.DEFAULT_PART_ID, Edge.of(0, 1), 0.25f, 1);
		check(plan.segments().getFirst().first().equals(new Vector3f(0.25f, 0, 0)), "quarter preview");
		PhotonModelLoopCutUtil.apply(positioned, plan);
		positioned.validate();
		check(positioned.vertices.stream().filter(vertex -> vertex.x == 0.25f).count() == 2, "quarter geometry");
		checkPlanarUV(positioned, 1f);
	}

	@Test
	public void loopCutClosedCube() {
		var asset = new PhotonModelAsset();
		PhotonModelEditUtil.addPrimitive(asset, Primitive.CUBE);
		String part = asset.parts.getLast().id;
		var plan = PhotonModelLoopCutUtil.plan(asset, part, Edge.of(0, 1), -1f, 1);
		check(plan.segments().size() == 4, "closed ring preview");
		PhotonModelLoopCutUtil.apply(asset, plan);
		asset.validate();
		check(asset.vertices.size() == 12 && asset.faces.size() == 10, "cube closed ring counts");
		var uses = new HashMap<Edge, Integer>();
		for (var face : asset.faces) {
			for (int i = 0; i < face.vertices.length; i++) {
				uses.merge(Edge.of(face.vertices[i], face.vertices[(i + 1) % face.vertices.length]), 1, Integer::sum);
			}
		}
		check(uses.values().stream().allMatch(count -> count == 2), "cube ring remains watertight");
	}

	@Test
	public void loopCutSeams() {
		var asset = strip();
		for (var uv : asset.faces.getLast().uv) {
			uv.x += 10f;
		}
		var plan = PhotonModelLoopCutUtil.plan(asset, PhotonModelAsset.DEFAULT_PART_ID, Edge.of(0, 3), 0.25f, 1);
		PhotonModelLoopCutUtil.apply(asset, plan);
		asset.validate();
		check(asset.vertices.size() == 9 && asset.faces.size() == 4, "strip shares inserted seam vertex");
		int seam = -1;
		for (int index = 0; index < asset.vertices.size(); index++) {
			if (asset.vertices.get(index).equals(new Vector3f(1, 0.25f, 0))) {
				seam = index;
			}
		}
		check(seam >= 0, "inserted shared seam found");
		var values = new HashSet<Float>();
		for (var face : asset.faces) {
			for (int corner = 0; corner < face.vertices.length; corner++) {
				if (face.vertices[corner] == seam) {
					values.add(face.uv[corner].x);
					check(face.uv[corner].y == 0.25f, "seam V interpolation");
				}
			}
		}
		check(values.equals(new HashSet<>(List.of(0.5f, 10.5f))), "cut preserves independent UV seam values");
	}

	@Test
	public void loopCutRejections() {
		var crossPart = strip();
		var part = new Part();
		part.id = "other";
		crossPart.parts.add(part);
		crossPart.faces.getLast().partId = part.id;
		rejects(() -> PhotonModelLoopCutUtil.plan(crossPart, PhotonModelAsset.DEFAULT_PART_ID, Edge.of(0, 3), 0.25f, 1));
		var triangle = strip();
		triangle.faces.removeLast();
		face(triangle, 1, 2, 4);
		rejects(() -> PhotonModelLoopCutUtil.plan(triangle, PhotonModelAsset.DEFAULT_PART_ID, Edge.of(0, 3), 0.25f, 1));

		for (boolean changePosition : new boolean[]{true, false}) {
			var stale = quad();
			var plan = PhotonModelLoopCutUtil.plan(stale, PhotonModelAsset.DEFAULT_PART_ID, Edge.of(0, 1), 0.25f, 1);
			if (changePosition) {
				stale.vertices.getFirst().x -= 1f;
			} else {
				stale.faces.getFirst().uv[0].x += 1f;
			}
			rejects(() -> PhotonModelLoopCutUtil.apply(stale, plan));
			check(stale.vertices.size() == 4 && stale.faces.size() == 1, "stale plan does not partially apply");
		}
	}

	private static PhotonModelAsset quad() {
		var asset = new PhotonModelAsset();
		asset.vertices.addAll(List.of(new Vector3f(0, 0, 0), new Vector3f(1, 0, 0),
				new Vector3f(1, 1, 0), new Vector3f(0, 1, 0)));
		face(asset, 0, 1, 2, 3);
		return asset;
	}

	private static PhotonModelAsset strip() {
		var asset = new PhotonModelAsset();
		asset.vertices.addAll(List.of(new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), new Vector3f(2, 0, 0),
				new Vector3f(0, 1, 0), new Vector3f(1, 1, 0), new Vector3f(2, 1, 0)));
		face(asset, 0, 1, 4, 3);
		face(asset, 1, 2, 5, 4);
		for (var face : asset.faces) {
			for (var uv : face.uv) {
				uv.x *= 0.5f;
			}
		}
		return asset;
	}

	private static PhotonModelAsset openPyramid() {
		var asset = quad();
		asset.faces.clear();
		asset.vertices.add(new Vector3f(0.5f, 0.5f, 1));
		face(asset, 1, 0, 4);
		face(asset, 2, 1, 4);
		face(asset, 3, 2, 4);
		face(asset, 0, 3, 4);
		return asset;
	}

	private static void face(PhotonModelAsset asset, int... vertices) {
		var face = new Face();
		face.vertices = vertices;
		face.uv = new Vector2f[vertices.length];
		for (int i = 0; i < vertices.length; i++) {
			var position = asset.vertices.get(vertices[i]);
			face.uv[i] = new Vector2f(position.x, position.y);
		}
		asset.faces.add(face);
	}

	private static void bone(PhotonModelAsset asset, String id) {
		var bone = new Bone();
		bone.id = id;
		asset.bones.add(bone);
	}

	private static Weight weight(String bone, float value) {
		var weight = new Weight();
		weight.boneId = bone;
		weight.value = value;
		return weight;
	}

	private static LinkedHashSet<Integer> selected(Integer... vertices) {
		return new LinkedHashSet<>(Arrays.asList(vertices));
	}

	private static LinkedHashSet<Edge> selectedEdge(int first, int second) {
		return new LinkedHashSet<>(List.of(Edge.of(first, second)));
	}

	private static void checkPlanarUV(PhotonModelAsset asset, float scale) {
		for (var face : asset.faces) {
			for (int corner = 0; corner < face.vertices.length; corner++) {
				var position = asset.vertices.get(face.vertices[corner]);
				check(face.uv[corner].equals(new Vector2f(position.x * scale, position.y)), "planar corner UV");
			}
		}
	}

	private static float area(PhotonModelAsset asset) {
		float result = 0;
		for (var face : asset.faces) {
			for (int corner = 0; corner < face.vertices.length; corner++) {
				var a = asset.vertices.get(face.vertices[corner]);
				var b = asset.vertices.get(face.vertices[(corner + 1) % face.vertices.length]);
				result += a.x * b.y - a.y * b.x;
			}
		}
		return Math.abs(result) * 0.5f;
	}

	private static void rejects(Runnable action) {
		assertThrows(IllegalArgumentException.class, action::run);
	}

	private static void check(boolean condition, String message) {
		assertTrue(condition, message);
	}
}
