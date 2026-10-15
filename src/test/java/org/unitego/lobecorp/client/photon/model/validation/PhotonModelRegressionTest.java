package org.unitego.lobecorp.client.photon.model.validation;

import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Quaternionf;
import org.unitego.lobecorp.client.photon.model.editor.PhotonModelPartTransform;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Primitive;

import java.util.LinkedHashSet;
import java.util.Set;

import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Bone;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Clip;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Face;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Morph;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Material;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Transform;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.TransformKey;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.ValueKey;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Weight;
import org.unitego.lobecorp.util.photon.model.PhotonModelIOUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelCullUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelPolygonUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil.SampleParameters;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 不启动游戏的模型交换、固定端点和动画组合回归测试。
public class PhotonModelRegressionTest {
	/// 浮点动画与交换误差的检查容差。
	private static final float EPSILON = 0.0001f;

	@Test
	public void objSeamsAndDefaultPose() {
		var asset = PhotonModelIOUtil.readObj("""
				v 0 0 0
				v 2 0 0
				v 2 1 0
				v 0 1 0
				vt 0 0
				vt 1 0
				vt 1 1
				vt 0 1
				vt 0.25 0.75
				o beam
				usemtl core
				f -4/1 -3/2 -2/3
				f -4/5 -2/3 -1/4
				""", false);
		check(asset.vertices.size() == 4);
		check(asset.faces.getFirst().vertices[0] == asset.faces.getLast().vertices[0]);
		equal(0f, asset.faces.getFirst().uv[0].x);
		equal(0.25f, asset.faces.getLast().uv[0].x);
		equal(2f, asset.referenceWidth);
		var beam = asset.parts.stream().filter(part -> part.id.equals("beam")).findFirst().orElseThrow();
		beam.transform.translation.x = 3f;
		var morph = new Morph();
		morph.id = "wide";
		morph.weight = 0.5f;
		morph.deltas.put(0, new Vector3f(2f, 0f, 0f));
		asset.morphs.add(morph);
		var exported = PhotonModelIOUtil.readObj(PhotonModelIOUtil.writeObj(asset, false), false);
		equal(4f, exported.vertices.get(exported.faces.getFirst().vertices[0]).x);
		equal(0.25f, exported.faces.getLast().uv[0].x);
		check(exported.faces.stream().allMatch(face -> face.partId.equals("beam") && face.material.equals("core")));
		check(exported.morphs.isEmpty());
		var flipped = PhotonModelIOUtil.readObj(PhotonModelIOUtil.writeObj(asset, true), true);
		equal(0.75f, flipped.faces.getLast().uv[0].y);
	}

	@Test
	public void strictJsonAndCopy() {
		var asset = triangle();
		var bone = new Bone();
		bone.id = "root";
		asset.bones.add(bone);
		asset.weights.put(1, List.of(weight("root", 1f)));
		var morph = new Morph();
		morph.id = "bend";
		morph.deltas.put(1, new Vector3f(0f, 0f, 2f));
		asset.morphs.add(morph);
		var decoded = PhotonModelIOUtil.readJson(PhotonModelIOUtil.writeJson(asset));
		equal(1f, decoded.weights.get(1).getFirst().value);
		equal(2f, decoded.morphs.getFirst().deltas.get(1).z);
		var copy = decoded.copy();
		copy.vertices.getFirst().x = 7f;
		copy.morphs.getFirst().deltas.get(1).z = 8f;
		equal(0f, decoded.vertices.getFirst().x);
		equal(2f, decoded.morphs.getFirst().deltas.get(1).z);
		rejects(() -> PhotonModelIOUtil.readJson("{\"version\":2}"));
		rejects(() -> PhotonModelIOUtil.readJson("{\"version\":1,\"version\":1}"));
		var cycle = decoded.copy();
		cycle.parts.getFirst().parentId = cycle.parts.getFirst().id;
		rejects(cycle::validate);
		var badBind = decoded.copy();
		badBind.bones.getFirst().bind.scale.x = 0f;
		rejects(badBind::validate);
	}

	@Test
	public void concavePolygon() {
		var vertices = List.of(new Vector3f(0f, 0f, 0f), new Vector3f(3f, 0f, 0f),
				new Vector3f(3f, 3f, 0f), new Vector3f(2f, 1f, 0f), new Vector3f(0f, 3f, 0f));
		int[] indices = {0, 1, 2, 3, 4};
		int[] triangles = PhotonModelPolygonUtil.triangulate(vertices, indices);
		check(triangles.length == 9);
		float area = 0f;

		for (int i = 0; i < triangles.length; i += 3) {
			var a = vertices.get(indices[triangles[i]]);
			var b = vertices.get(indices[triangles[i + 1]]);
			var c = vertices.get(indices[triangles[i + 2]]);
			area += new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).length() / 2f;
		}

		equal(6f, area);
		var crossed = List.of(new Vector3f(0f, 0f, 0f), new Vector3f(3f, 2f, 0f),
				new Vector3f(3f, 0f, 0f), new Vector3f(0f, 3f, 0f));
		rejects(() -> PhotonModelPolygonUtil.triangulate(crossed, new int[]{0, 1, 2, 3}));
	}

	@Test
	public void renderSamplingPreservesGeometryAndDoesNotLeakPooledScratch() {
		var asset = triangle();
		var prepared = PhotonModelSamplerUtil.prepare(asset);
		var parameters = new SampleParameters();
		var full = PhotonModelSamplerUtil.sample(prepared, "", 0, parameters);
		var first = PhotonModelSamplerUtil.sampleForRender(prepared, "", 0, parameters);
		var saved = first.partMeshes().get("main").geometry().clone();
		check(first.mesh().isEmpty() && first.partVertices().isEmpty() && first.boneWorldMatrices().isEmpty());
		assertArrayEquals(full.partMeshes().get("main").geometry(), saved, EPSILON);
		parameters.width = 2f;
		PhotonModelSamplerUtil.sampleForRender(prepared, "", 1, parameters);
		assertArrayEquals(saved, first.partMeshes().get("main").geometry(), EPSILON);
		var material = new Material();
		asset.materials.put("core", material);
		asset.faces.getFirst().material = "core";
		prepared = PhotonModelSamplerUtil.prepare(asset);
		full = PhotonModelSamplerUtil.sample(prepared, "", 0, parameters);
		var grouped = PhotonModelSamplerUtil.sampleForRender(prepared, "", 0, parameters);
		check(grouped.partMeshes().isEmpty() && grouped.materialMeshes().size() == 1);
		assertArrayEquals(full.materialMeshes().values().iterator().next().geometry(),
				grouped.materialMeshes().values().iterator().next().geometry(), EPSILON);
	}

	@Test
	public void staticGeometryTimeKeepsOpacityIndependent() {
		var asset = triangle();
		var clip = new Clip();
		clip.id = "opacity";
		clip.duration = 10f;
		var start = new ValueKey();
		start.value = 1f;
		var end = new ValueKey();
		end.time = 10f;
		end.value = 0f;
		clip.opacityKeys.addAll(List.of(start, end));
		asset.clips.add(clip);
		var prepared = PhotonModelSamplerUtil.prepare(asset);
		equal(0f, PhotonModelSamplerUtil.geometryTime(prepared, clip.id, 5f));
		equal(0.5f, PhotonModelSamplerUtil.opacity(prepared, clip.id, 5f, false));
		var first = new TransformKey();
		var last = new TransformKey();
		last.time = 10f;
		clip.partTracks.put("main", List.of(first, last));
		prepared = PhotonModelSamplerUtil.prepare(asset);
		equal(5f, PhotonModelSamplerUtil.geometryTime(prepared, clip.id, 5f));
	}

	@Test
	public void jsonPreparedSnapshotSharesOnlyImmutableDefinition() {
		var asset = triangle();
		var material = new Material();
		material.texture = "minecraft:block/stone";
		asset.materials.put("core", material);
		var json = PhotonModelIOUtil.writeJson(asset);
		var first = PhotonModelIOUtil.prepareJson(json);
		check(first == PhotonModelIOUtil.prepareJson(json));
		check(PhotonModelIOUtil.textureDependencies(json).equals(List.of(material.texture)));
		assertThrows(UnsupportedOperationException.class, () -> PhotonModelIOUtil.textureDependencies(json).clear());
		var copy = PhotonModelIOUtil.readJson(json);
		copy.vertices.getFirst().x = 7f;
		var sampled = PhotonModelSamplerUtil.sample(first, "", 0, new SampleParameters());
		equal(0f, sampled.partVertices().get("main").getFirst().x);
		check(first != PhotonModelIOUtil.prepareJson(PhotonModelIOUtil.writeJson(copy)));
	}

	@Test
	public void backFaceCullingUsesWindingEyeAndMirror() {
		var asset = triangle();
		var mesh = PhotonModelSamplerUtil.sample(asset, "", 0, new SampleParameters()).mesh();
		var front = new Vector3f(0f, 0f, 1f);
		var back = new Vector3f(0f, 0f, -1f);
		check(!PhotonModelCullUtil.backFacing(mesh, 0, front, false));
		check(PhotonModelCullUtil.backFacing(mesh, 0, back, false));
		check(PhotonModelCullUtil.backFacing(mesh, 0, front, true));
		check(!PhotonModelCullUtil.backFacing(mesh, 0, back, true));
		check(!PhotonModelCullUtil.backFacing(mesh, 0, new Vector3f(Float.NaN), false));
		asset.parts.getFirst().insideOut = true;
		mesh = PhotonModelSamplerUtil.sample(asset, "", 0, new SampleParameters()).mesh();
		check(PhotonModelCullUtil.backFacing(mesh, 0, front, false));
	}

	@Test
	public void cachedJsonKeepsCopiesIsolated() {
		var source = triangle();
		var json = PhotonModelIOUtil.writeJson(source);
		source.vertices.getFirst().x = 8f;
		var first = PhotonModelIOUtil.readJson(json);
		first.vertices.getFirst().x = 7f;
		first.faces.getFirst().uv[0].x = 0.75f;
		var second = PhotonModelIOUtil.readJson(json);
		equal(0f, second.vertices.getFirst().x);
		equal(0f, second.faces.getFirst().uv[0].x);
		second.vertices.getFirst().x = -2f;
		var changed = PhotonModelIOUtil.writeJson(second);
		equal(-2f, PhotonModelIOUtil.readJson(changed).vertices.getFirst().x);
		equal(0f, PhotonModelIOUtil.readJson(json).vertices.getFirst().x);
		assertThrows(RuntimeException.class, () -> PhotonModelIOUtil.readJson(json + " null"));
	}

	@Test
	public void preparedSamplingKeepsSnapshotAndDynamicOverrides() {
		var source = triangle();
		var bone = new Bone();
		bone.id = "root";
		source.bones.add(bone);
		source.weights.put(1, List.of(weight(bone.id, 1f)));
		var morph = new Morph();
		morph.id = "bend";
		morph.deltas.put(1, new Vector3f(0f, 0f, 2f));
		source.morphs.add(morph);
		var prepared = PhotonModelSamplerUtil.prepare(source);
		var parameters = new SampleParameters();
		parameters.morphOverrides.put(morph.id, 0.6f);
		var transform = new Transform();
		transform.translation.set(2f, 3f, 4f);
		parameters.boneOverrides.put(bone.id, transform);
		parameters.width = 1.5f;
		var expected = PhotonModelSamplerUtil.sample(source, "", 0f, parameters);
		var actual = PhotonModelSamplerUtil.sample(prepared, "", 0f, parameters);
		assertArrayEquals(expected.mesh().geometry(), actual.mesh().geometry(), EPSILON);
		assertArrayEquals(expected.mesh().attributes(), actual.mesh().attributes(), EPSILON);
		assertArrayEquals(expected.mesh().indices(), actual.mesh().indices());
		source.vertices.getFirst().x = -5f;
		actual = PhotonModelSamplerUtil.sample(prepared, "", 0f, parameters);
		assertArrayEquals(expected.mesh().geometry(), actual.mesh().geometry(), EPSILON);
		var changed = PhotonModelSamplerUtil.sample(PhotonModelSamplerUtil.prepare(source), "", 0f, parameters);
		check(!changed.partVertices().get("main").getFirst().equals(actual.partVertices().get("main").getFirst()));
	}

	@Test
	public void preparedOpacityMatchesLoopAndClamping() {
		var source = triangle();
		var clip = new Clip();
		clip.id = "pulse";
		clip.duration = 10f;
		clip.loop = true;
		var start = new ValueKey();
		start.value = 0f;
		var middle = new ValueKey();
		middle.time = 5f;
		middle.value = 1f;
		var end = new ValueKey();
		end.time = 10f;
		end.value = 0f;
		clip.opacityKeys.addAll(List.of(start, middle, end));
		source.clips.add(clip);
		var prepared = PhotonModelSamplerUtil.prepare(source);
		var parameters = new SampleParameters();
		parameters.opacity = 0.5f;

		for (boolean loop : new boolean[]{false, true}) {
			parameters.loop = loop;

			for (float time : new float[]{0f, 2.5f, 5f, 10f, 12f, 20f}) {
				var sampled = PhotonModelSamplerUtil.sample(source, clip.id, time, parameters);
				equal(sampled.opacity(), parameters.opacity * PhotonModelSamplerUtil.opacity(prepared, clip.id, time, loop));
			}
		}

		middle.value = 0.2f;
		equal(1f, PhotonModelSamplerUtil.opacity(prepared, clip.id, 5f, false));
		equal(0.2f, PhotonModelSamplerUtil.opacity(PhotonModelSamplerUtil.prepare(source), clip.id, 5f, false));
	}

	@Test
	public void localLengthAndAnchors() {
		var asset = strip();
		var parameters = new SampleParameters();
		parameters.length = 16f;
		parameters.anchor = Anchor.START;
		var expanded = positions(asset, parameters);
		equal(0f, expanded.get(0).z);
		equal(2f, expanded.get(4).z);
		equal(14f, expanded.get(5).z);
		equal(16f, expanded.get(2).z);
		equal(2f, expanded.get(2).z - expanded.get(5).z);
		parameters.anchor = Anchor.END;
		var end = positions(asset, parameters);
		equal(10f, end.get(2).z);
		equal(-6f, end.get(0).z);
		parameters.length = 0f;
		parameters.width = 0f;
		parameters.anchor = Anchor.START;
		var collapsed = positions(asset, parameters);
		equal(4f, collapsed.get(2).z);
		equal(2f, collapsed.get(4).z);
		equal(2f, collapsed.get(5).z);
		check(collapsed.stream().allMatch(Vector3f::isFinite));
		equal(10f, asset.vertices.get(2).z);
		parameters.length = Float.POSITIVE_INFINITY;
		rejects(() -> positions(asset, parameters));
	}

	@Test
	public void capsAndCollapsedNormals() {
		var asset = strip();
		var head = new Part();
		head.id = "head";
		head.anchor = Anchor.START;
		var tail = new Part();
		tail.id = "tail";
		tail.anchor = Anchor.END;
		asset.parts.add(head);
		asset.parts.add(tail);
		var headFace = face(0, 1, 4);
		headFace.partId = head.id;
		var tailFace = face(2, 3, 5);
		tailFace.partId = tail.id;
		asset.faces.add(headFace);
		asset.faces.add(tailFace);
		var parameters = new SampleParameters();
		parameters.length = 16f;
		var model = PhotonModelSamplerUtil.sample(asset, "", 0f, parameters);
		equal(2f, model.partVertices().get("head").get(4).z);
		equal(14f, model.partVertices().get("tail").get(5).z);
		equal(16f, model.partVertices().get("tail").get(2).z);
		parameters.anchor = Anchor.END;
		model = PhotonModelSamplerUtil.sample(asset, "", 0f, parameters);
		equal(10f, model.partVertices().get("tail").get(2).z);
		parameters.length = 0f;
		parameters.width = 0f;
		model = PhotonModelSamplerUtil.sample(asset, "", 0f, parameters);

		for (float value : model.mesh().geometry()) {
			check(Float.isFinite(value));
		}

		for (float value : model.mesh().attributes()) {
			check(Float.isFinite(value));
		}
	}

	@Test
	public void partIsolation() {
		var asset = triangle();
		var other = new Part();
		other.id = "second";
		other.transform.translation.x = 5f;
		asset.parts.add(other);
		var secondFace = asset.faces.getFirst().copy();
		secondFace.partId = other.id;
		asset.faces.add(secondFace);
		var sampled = PhotonModelSamplerUtil.sample(asset, "", 0f, new SampleParameters());
		equal(0f, sampled.partVertices().get("main").getFirst().x);
		equal(5f, sampled.partVertices().get("second").getFirst().x);
		var override = new SampleParameters();
		var transform = new Transform();
		transform.translation.x = 9f;
		override.partOverrides.put("main", transform);
		var changed = PhotonModelSamplerUtil.sample(asset, "", 0f, override);
		equal(9f, changed.partVertices().get("main").getFirst().x);
		equal(5f, changed.partVertices().get("second").getFirst().x);
		equal(0f, sampled.partVertices().get("main").getFirst().x);
		var exported = PhotonModelIOUtil.readObj(PhotonModelIOUtil.writeObj(asset, false), false);
		check(exported.vertices.size() == 6);
	}

	@Test
	public void movingPartPivotPreservesBaseHierarchy() {
		var asset = triangle();
		var parent = asset.parts.getFirst();
		parent.pivot.set(-0.3f, 0.4f, 0.2f);
		parent.transform.translation.set(3f, -2f, 1f);
		parent.transform.rotation.rotationXYZ(0.25f, -0.5f, 0.75f);
		parent.transform.scale.set(2f, 0.5f, 1.25f);
		var child = new Part();
		child.id = "tip";
		child.parentId = parent.id;
		child.pivot.set(0.1f, -0.2f, 0.3f);
		child.transform.translation.set(1f, 0.5f, -0.25f);
		child.transform.rotation.rotationXYZ(-0.35f, 0.2f, 0.1f);
		child.transform.scale.set(-0.75f, 1.5f, 2f);
		asset.parts.add(child);
		var childFace = asset.faces.getFirst().copy();
		childFace.partId = child.id;
		asset.faces.add(childFace);
		var vertices = asset.vertices.stream().map(Vector3f::new).toList();
		var childBefore = child.copy();
		var before = PhotonModelSamplerUtil.basePartMatrices(asset);
		var parentPivot = new Vector3f(0.75f, -0.5f, 1.2f);
		PhotonModelEditUtil.movePartPivot(asset, parent.id, parentPivot);
		var after = PhotonModelSamplerUtil.basePartMatrices(asset);
		check(parent.pivot.equals(parentPivot, EPSILON));
		check(child.pivot.equals(childBefore.pivot, EPSILON));
		check(child.transform.translation.equals(childBefore.transform.translation, EPSILON));
		check(child.transform.rotation.equals(childBefore.transform.rotation, EPSILON));
		check(child.transform.scale.equals(childBefore.transform.scale, EPSILON));

		for (var part : asset.parts) {
			check(before.get(part.id).equals(after.get(part.id), EPSILON));
		}

		var childPivot = new Vector3f(-0.6f, 0.8f, -0.4f);
		PhotonModelEditUtil.movePartPivot(asset, child.id, childPivot);
		after = PhotonModelSamplerUtil.basePartMatrices(asset);
		check(child.pivot.equals(childPivot, EPSILON));

		for (var part : asset.parts) {
			check(before.get(part.id).equals(after.get(part.id), EPSILON));
		}

		for (int index = 0; index < vertices.size(); index++) {
			check(vertices.get(index).equals(asset.vertices.get(index), EPSILON));
		}
	}

	@Test
	public void movingPartPivotLeavesAnimationTracksUnchanged() {
		var asset = triangle();
		var part = asset.parts.getFirst();
		part.transform.translation.set(2f, -1f, 0.5f);
		part.transform.rotation.rotationXYZ(0.2f, 0.3f, -0.4f);
		part.transform.scale.set(2f, 0.75f, 1.5f);
		var clip = new Clip();
		clip.id = "turn";
		clip.loop = false;
		var start = new TransformKey();
		start.transform.translation.set(1f, -2f, 0.5f);
		start.transform.rotation.rotationZ(0.4f);
		start.transform.scale.set(1.5f, 0.7f, 2f);
		var end = new TransformKey();
		end.time = 20f;
		end.transform.translation.set(-1f, 2f, 1.5f);
		end.transform.rotation.rotationZ(1.2f);
		end.transform.scale.set(0.5f, 1.3f, 1f);
		var track = List.of(start, end);
		clip.partTracks.put(part.id, track);
		asset.clips.add(clip);
		var keysBefore = track.stream().map(TransformKey::copy).toList();
		var before = PhotonModelSamplerUtil.sample(asset, clip.id, 10f, new SampleParameters());
		var pivot = new Vector3f(0.5f, -0.25f, 0.75f);
		PhotonModelEditUtil.movePartPivot(asset, part.id, pivot);
		check(part.pivot.equals(pivot, EPSILON));
		check(asset.clips.getFirst() == clip);
		check(clip.partTracks.get(part.id) == track);
		check(track.getFirst() == start && track.getLast() == end);

		for (int index = 0; index < track.size(); index++) {
			var expected = keysBefore.get(index);
			var actual = track.get(index);
			equal(expected.time, actual.time);
			check(expected.transform.translation.equals(actual.transform.translation, EPSILON));
			check(expected.transform.rotation.equals(actual.transform.rotation, EPSILON));
			check(expected.transform.scale.equals(actual.transform.scale, EPSILON));
		}

		var after = PhotonModelSamplerUtil.sample(asset, clip.id, 10f, new SampleParameters());
		check(before.partVertices().get(part.id).getFirst().distance(after.partVertices().get(part.id).getFirst()) > EPSILON);
	}

	@Test
	public void nativePartTransformPreservesPivotAndSamplesHierarchy() {
		var asset = triangle();
		var parent = asset.parts.getFirst();
		parent.pivot.set(-0.3f, 0.4f, 0.2f);
		parent.transform.translation.set(3f, -2f, 1f);
		parent.transform.rotation.rotationXYZ(0.3f, -0.6f, 0.5f);
		parent.transform.scale.set(2f, 0.5f, 1.25f);
		var child = new Part();
		child.id = "tip";
		child.parentId = parent.id;
		child.pivot.set(0.1f, -0.2f, 0.3f);
		child.transform.translation.set(1f, 0.5f, -0.25f);
		child.transform.rotation.rotationXYZ(-0.35f, 0.2f, 0.1f);
		child.transform.scale.set(0.75f, 1.5f, 2f);
		asset.parts.add(child);
		var childFace = asset.faces.getFirst().copy();
		childFace.partId = child.id;
		asset.faces.add(childFace);
		var sourceVertices = asset.vertices.stream().map(Vector3f::new).toList();
		var rootRotation = new Quaternionf().rotationXYZ(-0.1f, 0.35f, 0.2f);
		var rootToWorld = new Matrix4f().translationRotateScale(new Vector3f(4f, -3f, 2f),
				rootRotation, new Vector3f(1.1f, 0.8f, 1.4f));
		var before = PhotonModelSamplerUtil.basePartMatrices(asset);
		var parentToWorld = new Matrix4f(rootToWorld).mul(before.get(parent.id));
		var parentRotation = new Quaternionf(rootRotation).mul(parent.transform.rotation).normalize();
		var pivotBefore = new Matrix4f(rootToWorld).mul(before.get(child.id)).transformPosition(new Vector3f(child.pivot));
		var localPosition = new Vector3f(child.pivot).add(child.transform.translation);
		var adapter = new PhotonModelPartTransform(child, parentToWorld, parentRotation);
		var localRotation = new Quaternionf().rotationXYZ(0.6f, -0.2f, 0.4f);
		var localScale = new Vector3f(0.6f, 1.75f, 1.2f);
		adapter.rotation(new Quaternionf(parentRotation).mul(localRotation));
		adapter.localScale(localScale);
		adapter.applyTo(child);
		var after = PhotonModelSamplerUtil.basePartMatrices(asset);
		var pivotAfter = new Matrix4f(rootToWorld).mul(after.get(child.id)).transformPosition(new Vector3f(child.pivot));
		check(pivotBefore.equals(pivotAfter, EPSILON));
		check(child.transform.rotation.equals(localRotation, EPSILON));
		check(child.transform.scale.equals(localScale, EPSILON));
		var sampled = PhotonModelSamplerUtil.sample(asset, "", 0f, new SampleParameters());

		for (int index = 0; index < sourceVertices.size(); index++) {
			var expected = new Vector3f(sourceVertices.get(index)).sub(child.pivot)
					.mul(localScale).rotate(localRotation).add(localPosition);
			parentToWorld.transformPosition(expected);
			var actual = rootToWorld.transformPosition(new Vector3f(sampled.partVertices().get(child.id).get(index)));
			check(expected.equals(actual, EPSILON));
		}

		var parentPosition = new Vector3f(-0.4f, 1.3f, 0.7f);
		var worldPosition = parentToWorld.transformPosition(new Vector3f(parentPosition));
		adapter.position(worldPosition);
		adapter.applyTo(child);
		after = PhotonModelSamplerUtil.basePartMatrices(asset);
		pivotAfter = new Matrix4f(rootToWorld).mul(after.get(child.id)).transformPosition(new Vector3f(child.pivot));
		check(worldPosition.equals(pivotAfter, EPSILON));
		check(child.transform.translation.equals(new Vector3f(parentPosition).sub(child.pivot), EPSILON));
		check(before.get(parent.id).equals(after.get(parent.id), EPSILON));
		var decoded = PhotonModelIOUtil.readJson(PhotonModelIOUtil.writeJson(asset));
		var decodedPart = decoded.parts.getLast();
		check(decodedPart.pivot.equals(child.pivot, EPSILON));
		check(decodedPart.transform.translation.equals(child.transform.translation, EPSILON));
		check(decodedPart.transform.rotation.equals(child.transform.rotation, EPSILON));
		check(decodedPart.transform.scale.equals(child.transform.scale, EPSILON));
		check(PhotonModelSamplerUtil.basePartMatrices(decoded).get(child.id).equals(after.get(child.id), EPSILON));

		for (int index = 0; index < sourceVertices.size(); index++) {
			check(sourceVertices.get(index).equals(asset.vertices.get(index), EPSILON));
			check(sourceVertices.get(index).equals(decoded.vertices.get(index), EPSILON));
		}
	}

	@Test
	public void bindAndMorphSkin() {
		var asset = triangle();
		var bone = new Bone();
		bone.id = "root";
		bone.bind.translation.x = 3f;
		asset.bones.add(bone);
		asset.weights.put(1, List.of(weight("root", 2f)));
		var neutral = positions(asset, new SampleParameters());
		equal(1f, neutral.get(1).x);
		var morph = new Morph();
		morph.id = "wide";
		morph.weight = 1f;
		morph.deltas.put(1, new Vector3f(1f, 0f, 0f));
		asset.morphs.add(morph);
		var parameters = new SampleParameters();
		var pose = bone.bind.copy();
		pose.rotation.rotationZ((float) (Math.PI / 2));
		parameters.boneOverrides.put("root", pose);
		var animated = positions(asset, parameters);
		equal(3f, animated.get(1).x);
		equal(-1f, animated.get(1).y);
		equal(0f, animated.getFirst().x);
		equal(0f, asset.vertices.getFirst().x);
		parameters.morphOverrides.put("wide", 0f);
		equal(-2f, positions(asset, parameters).get(1).y);
	}

	@Test
	public void hierarchicalSkin() {
		var asset = triangle();
		var parent = new Bone();
		parent.id = "root";
		parent.bind.translation.x = 3f;
		var child = new Bone();
		child.id = "tip";
		child.parentId = parent.id;
		child.bind.translation.x = 2f;
		asset.bones.add(parent);
		asset.bones.add(child);
		asset.weights.put(2, List.of(weight(child.id, 1f)));
		var parameters = new SampleParameters();
		var pose = parent.bind.copy();
		pose.translation.x = 4f;
		parameters.boneOverrides.put(parent.id, pose);
		var animated = positions(asset, parameters);
		equal(1f, animated.get(2).x);
		equal(1f, animated.get(2).y);
		equal(1f, animated.get(1).x);
	}

	@Test
	public void quaternionInterpolation() {
		var asset = triangle();
		var clip = new Clip();
		clip.id = "turn";
		clip.loop = false;
		var start = new TransformKey();
		start.transform.rotation.rotationZ((float) Math.toRadians(170));
		var end = new TransformKey();
		end.time = 20f;
		end.transform.rotation.rotationZ((float) Math.toRadians(-170));
		clip.partTracks.put("main", List.of(start, end));
		asset.clips.add(clip);
		var sampled = PhotonModelSamplerUtil.sample(asset, "turn", 10f, new SampleParameters());
		equal(-1f, sampled.partVertices().get("main").get(1).x);
		equal(0f, sampled.partVertices().get("main").get(1).y);
		var after = PhotonModelSamplerUtil.sample(asset, "turn", 30f, new SampleParameters());
		equal((float) Math.cos(Math.toRadians(-170)), after.partVertices().get("main").get(1).x);
	}

	@Test
	public void editorGeometry() {
		for (var primitive : Primitive.values()) {
			var model = new PhotonModelAsset();
			PhotonModelEditUtil.addPrimitive(model, primitive);
			model.validate();
			check(!model.faces.isEmpty());
		}

		var asset = new PhotonModelAsset();
		PhotonModelEditUtil.addPrimitive(asset, Primitive.CUBE);
		var bone = new Bone();
		bone.id = "root";
		asset.bones.add(bone);
		asset.weights.put(7, List.of(weight("root", 1f)));
		asset.faces.getFirst().material = "shell";
		var selectedFaces = new LinkedHashSet<>(Set.of(0));
		PhotonModelEditUtil.transformVertices(asset, Set.of(0), selectedFaces, new Vector3f(),
				new Vector3f(0f, 0f, 0.25f), new Quaternionf(), new Vector3f(1f));
		asset.validate();
		check(asset.faces.size() == 9);
		check(selectedFaces.size() == 2);
		check(asset.faces.getFirst().material.equals("shell"));
		equal(0.25f, asset.vertices.getFirst().z);
		PhotonModelEditUtil.deleteVertices(asset, Set.of(0));
		asset.validate();
		check(asset.vertices.size() == 7);
		equal(1f, asset.weights.get(6).getFirst().value);
	}

	private static PhotonModelAsset triangle() {
		var asset = new PhotonModelAsset();
		asset.vertices.addAll(List.of(new Vector3f(), new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f)));
		asset.faces.add(face(0, 1, 2));
		return asset;
	}

	private static PhotonModelAsset strip() {
		var asset = new PhotonModelAsset();
		asset.referenceLength = 10f;
		asset.referenceWidth = 2f;
		asset.vertices.addAll(List.of(new Vector3f(-1f, 0f, 0f), new Vector3f(1f, 0f, 0f),
				new Vector3f(1f, 0f, 10f), new Vector3f(-1f, 0f, 10f),
				new Vector3f(0f, 0f, 2f), new Vector3f(0f, 0f, 8f)));
		asset.faces.add(face(0, 1, 2, 3));
		var part = asset.parts.getFirst();
		part.stretch.enabled = true;
		part.stretch.start = 2f;
		part.stretch.end = 8f;
		return asset;
	}

	private static Face face(int... indices) {
		var face = new Face();
		face.vertices = indices;
		face.uv = new Vector2f[indices.length];

		for (int i = 0; i < indices.length; i++) {
			face.uv[i] = new Vector2f();
		}

		return face;
	}

	private static Weight weight(String bone, float value) {
		var weight = new Weight();
		weight.boneId = bone;
		weight.value = value;
		return weight;
	}

	private static List<Vector3f> positions(PhotonModelAsset asset, SampleParameters parameters) {
		return PhotonModelSamplerUtil.sample(asset, "", 0f, parameters).partVertices().get("main");
	}

	private static void rejects(Runnable action) {
		assertThrows(IllegalArgumentException.class, action::run);
	}

	private static void equal(float expected, float actual) {
		check(Float.isFinite(actual) && Math.abs(expected - actual) <= EPSILON);
	}

	private static void check(boolean condition) {
		assertTrue(condition);
	}
}
