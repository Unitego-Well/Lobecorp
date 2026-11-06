package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonElement;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.ObjMeshParser;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.obj.ObjGeometry;
import net.neoforged.neoforge.client.model.obj.ObjTokenizer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Face;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector2;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil.Corner;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil.Group;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/// CPU 比较：本项目验证原面与拓扑，Photon 验证公开 mesh，Neo 仅验证公开分组和输入接收。
/// Neo 不公开原面/顶点；这些测试不覆盖完整 bake、MTL 资源加载、atlas、GPU 或资源缓存生命周期。
/// 不捕获注册/静态初始化错误，不伪造宿主，也不将空 mesh 或跳过夹具记为成功。
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PhotonObjLoaderComparisonTest {
	private static final float EPSILON = 1e-6f;
	private static final String VERTICES = """
			v 0 0 0
			v 2 0 0
			v 0 2 0
			""";
	private static final String TRIANGLE = VERTICES + "f 1 2 3\n";
	private static final String ATTRIBUTES = """
			vt 0.125 0.25
			vt 0.75 0.25
			vt 0.125 0.875
			vn 0 0 1
			""";
	private static final String QUAD = """
			v 0 0 0
			v 2 0 0
			v 2 2 0
			v 0 2 0
			f 1 2 3 4
			""";
	private static final String CONCAVE = """
			v 0 0 0
			v 3 0 0
			v 3 3 0
			v 2 1 0
			v 0 3 0
			f 1 2 3 4 5
			""";

	@Test
	@Order(1)
	public void ordinaryLfTriangleMustProduceGeometry() {
		assertTriangleRoutes(TRIANGLE);
	}

	@Test
	@Order(2)
	public void ordinaryCrLfTriangleMustProduceGeometry() {
		assertTriangleRoutes(TRIANGLE.replace("\n", "\r\n"));
	}

	@Test
	public void projectResolvesPositiveAndNegativeCornerIndicesAndFlipV() throws Exception {
		var positive = project(VERTICES + ATTRIBUTES + "f 1/1/1 2/2/1 3/3/1\n", false);
		var negative = project(VERTICES + ATTRIBUTES + "f -3/-3/-1 -2/-2/-1 -1/-1/-1\n", false);
		var face = positive.definition().faces().getFirst();
		assertEquals(positive.definition().faces(), negative.definition().faces());
		assertEquals(positive.groups(), negative.groups());
		assertEquals(List.of(0, 1, 2), face.vertices());
		assertEquals(List.of(new Vector2(0.125f, 0.25f), new Vector2(0.75f, 0.25f),
				new Vector2(0.125f, 0.875f)), face.uv());
		assertEquals(List.of(new Vector3(0, 0, 1), new Vector3(0, 0, 1), new Vector3(0, 0, 1)), face.normals());
		var flipped = project(VERTICES + ATTRIBUTES + "f 1/1/1 2/2/1 3/3/1\n", true);
		assertEquals(new Vector2(0.125f, 0.75f), flipped.definition().faces().getFirst().uv().getFirst());
		assertEquals(face.normals(), flipped.definition().faces().getFirst().normals());
	}


	@Test
	public void projectDefaultsMissingUvsLikeExplicitZeroUvsForBothFlipOptions() throws Exception {
		var explicit = VERTICES + "vt 0 0\nf 1/1 2/1 3/1\n";

		for (boolean flipV : List.of(false, true)) {
			var asset = PhotonModelObjUtil.read(TRIANGLE, flipV);
			var prepared = PhotonModelTopologyUtil.prepare(PhotonModelCodec.read(asset));
			var face = prepared.definition().faces().getFirst();
			var expected = new Vector2(0, flipV ? 1 : 0);
			assertEquals(List.of(expected, expected, expected), face.uv());
			assertEquals(project(explicit, flipV).definition().faces(), prepared.definition().faces());
			assertEquals(3, asset.getAsJsonArray("faces").get(0).getAsJsonObject().getAsJsonArray("uv").size());
			assertTrue(face.normals().isEmpty());
			assertEquals(2, projectArea(prepared), EPSILON);
		}
	}

	@Test
	public void projectRejectsMixedMissingUvsForBothFlipOptions() {
		for (boolean flipV : List.of(false, true)) {
			for (var face : List.of("f 1/1 2 3\n", "f 1 2/1 3\n", "f 1/1 2/1 3\n")) {
				var failure = assertThrows(IOException.class,
						() -> project(VERTICES + "vt 0 0\n" + face, flipV), face);
				assertInstanceOf(IllegalArgumentException.class, failure.getCause());
				assertEquals("同一面混用了缺失的UV或法线。", failure.getCause().getMessage());
			}
		}
	}

	@Test
	public void photonResolvesCornerAttributesAndFlipVAfterTriangleGate() throws Exception {
		var positive = photon(VERTICES + ATTRIBUTES + "f 1/1/1 2/2/1 3/3/1\n", false);
		var negative = photon(VERTICES + ATTRIBUTES + "f -3/-3/-1 -2/-2/-1 -1/-1/-1\n", false);
		assertArrayEquals(positive.geometry(), negative.geometry(), EPSILON);
		assertArrayEquals(positive.attributes(), negative.attributes(), EPSILON);
		assertArrayEquals(positive.indices(), negative.indices());
		assertArrayEquals(new float[]{0.125f, 0.25f, 1, 0.75f, 0.25f, 1, 0.125f, 0.875f, 1},
				positive.attributes(), EPSILON);
		assertArrayEquals(new float[]{0, 0, 0, 0, 0, 1, 2, 0, 0, 0, 0, 1, 0, 2, 0, 0, 0, 1},
				positive.geometry(), EPSILON);
		assertArrayEquals(new int[]{0, 1, 2}, positive.indices());
		var flipped = photon(VERTICES + ATTRIBUTES + "f 1/1/1 2/2/1 3/3/1\n", true);
		assertArrayEquals(new float[]{0.125f, 0.75f, 1, 0.75f, 0.75f, 1, 0.125f, 0.125f, 1},
				flipped.attributes(), EPSILON);
		assertArrayEquals(positive.geometry(), flipped.geometry(), EPSILON);
	}

	@Test
	public void projectRetainsOriginalFacesAndTriangulatesConcavity() throws Exception {
		var quad = project(QUAD, false);
		assertEquals(List.of(0, 1, 2, 3), quad.definition().faces().getFirst().vertices());
		assertEquals(4, quad.groups().getFirst().corners().size());
		assertEquals(4, projectArea(quad), EPSILON);
		var concave = project(CONCAVE, false);
		assertEquals(List.of(0, 1, 2, 3, 4), concave.definition().faces().getFirst().vertices());
		assertEquals(9, concave.groups().getFirst().indices().size());
		assertEquals(6, projectArea(concave), EPSILON);
	}

	@Test
	public void photonProducesQuadPairAndConcaveTrianglesAfterTriangleGate() throws Exception {
		var quad = photon(QUAD, false);
		assertEquals(2, quad.triangleCount());
		assertTrue(quad.quadPaired(0));
		assertFalse(quad.quadPaired(1));
		assertEquals(4, meshArea(quad), EPSILON);
		var concave = photon(CONCAVE, false);
		assertEquals(3, concave.triangleCount());
		assertEquals(6, meshArea(concave), EPSILON);

		for (int triangle = 0; triangle < concave.triangleCount(); triangle++) {
			assertFalse(concave.quadPaired(triangle));
		}
	}

	@Test
	public void projectRetainsPartMaterialDirectivesAndOriginalVertexIdentity() throws Exception {
		var text = VERTICES + """
				v 0 0 0
				mtllib comparison.mtl
				o body
				g front
				usemtl red
				f 1 2 3
				g back
				usemtl blue
				f 4 3 2
				""";
		var prepared = project(text, false);
		var model = prepared.definition();
		assertEquals(4, model.vertices().size());
		assertEquals(model.vertices().get(0), model.vertices().get(3));
		assertEquals(List.of(0, 1, 2), model.faces().get(0).vertices());
		assertEquals(List.of(3, 2, 1), model.faces().get(1).vertices());
		assertEquals(List.of("front", "back"), model.faces().stream().map(Face::partId).toList());
		assertEquals(List.of("red", "blue"), prepared.groups().stream().map(Group::material).toList());
		assertEquals(Set.of("red", "blue"), model.materials().keySet());
		assertEquals(List.of("mtllib comparison.mtl", "o body", "g front", "g back"),
				model.source().getAsJsonArray("objDirectives").asList().stream().map(JsonElement::getAsString).toList());
		assertEquals(List.of(0, 1, 2), prepared.groups().get(0).corners().stream().map(Corner::vertex).toList());
		assertEquals(List.of(3, 2, 1), prepared.groups().get(1).corners().stream().map(Corner::vertex).toList());
	}

	@Test
	public void photonMergesNamedPartsAndIgnoresMaterialLibraryAfterTriangleGate() throws Exception {
		var plain = photon(VERTICES + "f 1 2 3\nf 1 3 2\n", false);
		var named = photon(VERTICES + """
				mtllib comparison.mtl
				o body
				g front
				usemtl red
				f 1 2 3
				g back
				usemtl blue
				f 1 3 2
				""", false);
		assertEquals(2, named.triangleCount());
		assertEquals(6, named.vertexCount());
		assertArrayEquals(plain.geometry(), named.geometry(), EPSILON);
		assertArrayEquals(plain.attributes(), named.attributes(), EPSILON);
		assertArrayEquals(plain.indices(), named.indices());
		// 公开输出是同一个扁平 mesh；不据此宣称保留了原面名、材质名或伴生骨骼索引。
	}

	@Test
	public void scientificNotationAndUnitHomogeneousCoordinatesProduceExactProjectGeometry() throws Exception {
		var text = "v 0e0 0 0 1\nv 2e0 0 0 1\nv 0 2E+0 0 1\nf 1 2 3\n";
		assertEquals(project(TRIANGLE, false).definition().vertices(), project(text, false).definition().vertices());
		assertEquals(2, projectArea(project(text, false)), EPSILON);
	}

	@Test
	public void nonUnitHomogeneousCoordinateIsRejectedByProjectButUsedRawByPhoton() throws Exception {
		var text = "v 0 0 0 2\nv 2e0 0 0 2\nv 0 2E+0 0 2\nf 1 2 3\n";
		assertThrows(IOException.class, () -> project(text, false));
		var mesh = photon(text, false);
		assertEquals(1, mesh.triangleCount());
		assertEquals(2, meshArea(mesh), EPSILON);
		assertEquals(2, mesh.geometry()[PhotonMesh.geometryOffset(1)], EPSILON);
	}

	@Test
	public void inlineCommentWorksInProjectButNeoTokenizerKeepsCommentTokens() throws Exception {
		var text = VERTICES + "f 1 2 3 # face\n";
		assertEquals(2, projectArea(project(text, false)), EPSILON);

		try (var tokenizer = new ObjTokenizer(input("f 1 2 3 # face\n"))) {
			assertArrayEquals(new String[]{"f", "1", "2", "3", "#", "face"}, tokenizer.readAndSplitLine(true));
			assertNull(tokenizer.readAndSplitLine(true));
		}

		assertThrows(NumberFormatException.class, () -> neo(text));
		var mesh = photon(text, false);
		assertEquals(1, mesh.triangleCount());
		assertEquals(2, meshArea(mesh), EPSILON);
	}

	@Test
	public void continuationIsJoinedByNeoButRejectedByProject() throws Exception {
		var continuedFace = "f 1 2 \\\n3\n";

		try (var tokenizer = new ObjTokenizer(input(continuedFace))) {
			assertArrayEquals(new String[]{"f", "1", "2", "3"}, tokenizer.readAndSplitLine(true));
			assertNull(tokenizer.readAndSplitLine(true));
		}

		assertEquals(Set.of(""), neo(VERTICES + continuedFace).getRootComponentNames());
		assertThrows(IOException.class, () -> project(VERTICES + continuedFace, false));
		// Photon 没有续行合并；先证明基础输入可用，再断言该夹具无三角形。
		assertTrue(photon(VERTICES + continuedFace, false).isEmpty());
	}

	@Test
	public void invalidIndicesAreRejectedByProjectAndSkippedByPhotonAfterTriangleGate() throws Exception {
		for (var face : List.of("f 0 2 3\n", "f 99 2 3\n", "f -4 2 3\n")) {
			var text = VERTICES + face;
			assertThrows(IOException.class, () -> project(text, false), face);
			var mesh = photon(text, false);
			assertTrue(mesh.isEmpty(), face);
			assertEquals(0, mesh.indices().length, face);
		}
	}

	@Test
	public void photonCanDiscardBadCornerInsteadOfRejectingWholeFaceAfterTriangleGate() throws Exception {
		var text = VERTICES + "f 1 2 99 3\n";
		assertThrows(IOException.class, () -> project(text, false));
		var mesh = photon(text, false);
		assertEquals(1, mesh.triangleCount());
		assertEquals(2, meshArea(mesh), EPSILON);
	}

	@Test
	public void degenerateFaceIsRejectedByProjectButRetainedByPhotonAfterTriangleGate() throws Exception {
		var text = "v 0 0 0\nv 1 0 0\nv 2 0 0\nf 1 2 3\n";
		assertThrows(IOException.class, () -> project(text, false));
		var mesh = photon(text, false);
		assertEquals(1, mesh.triangleCount());
		assertEquals(0, meshArea(mesh), EPSILON);
		assertEquals(1, mesh.geometry()[5], EPSILON);
	}

	@Test
	public void neoExposesObjectGroupNamesButNotVerifiedFaceGeometry() throws Exception {
		var geometry = neo(VERTICES + "o body\ng front\nf 1 2 3\ng back\nf -3 -1 -2\n");
		assertEquals(Set.of("body"), geometry.getRootComponentNames());
		assertEquals(List.of("body"), geometry.getRootParts().stream().map(ObjGeometry.ModelObject::name).toList());
		assertEquals(Set.of("body", "body/front", "body/back"), geometry.getConfigurableComponentNames());
		assertThrows(NoSuchElementException.class, () -> neo(VERTICES + "usemtl red\nf 1 2 3\n"));
		// 不调用 mtllib/ObjLoader：材质查找需要真实资源宿主，不伪造 Minecraft 全局。
	}

	@Test
	public void neoInputAcceptanceDoesNotProveFaceValidityOrTriangulation() throws Exception {
		for (var text : List.of(TRIANGLE, QUAD, CONCAVE,
				VERTICES + ATTRIBUTES + "f -3/-3/-1 -2/-2/-1 -1/-1/-1\n",
				"v 0 0 0 2\nv 2e0 0 0 2\nv 0 2E+0 0 2\nf 1 2 3\n",
				VERTICES + "f 0 2 99\n", "v 0 0 0\nv 1 0 0\nv 2 0 0\nf 1 2 3\n")) {
			assertEquals(Set.of(""), neo(text).getRootComponentNames(), text);
		}
		// 无公开顶点/面访问器；接收越界或退化输入只表示 parse 未校验，绝不算正确几何。
	}


	private record ProductionObj(String name, int vertices, int faces, int triangles,
	                             List<String> parts, List<String> materials) {
	}

	@Test
	public void productionBeamObjsCompareUnmodifiedInputsAcrossAllRoutes() {
		var fixtures = List.of(
				new ProductionObj("laser_body", 200, 164, 328, List.of("body", "plasma", "core"), List.of("body", "plasma", "core")),
				new ProductionObj("dust_column", 160, 144, 288, List.of("front"), List.of("dust")),
				new ProductionObj("reverse_muzzle", 145, 252, 252, List.of("main"), List.of("smoke")),
				new ProductionObj("source_dust", 81, 80, 144, List.of("main"), List.of("smoke")));
		assertAll("四个生产 OBJ 原文矩阵", fixtures.stream().map(fixture -> () -> {
			var resource = "/assets/lobecorp/models/photon/the_queen_of_hatred/beam/" + fixture.name() + ".obj";
			String text;

			try (var stream = PhotonObjLoaderComparisonTest.class.getResourceAsStream(resource)) {
				assertNotNull(stream, "生产夹具必须存在：" + resource);
				text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			}

			assertAll(resource,
					() -> assertProductionProject(fixture, text),
					() -> assertProductionPhoton(fixture, text),
					() -> {
						assertFalse(text.lines().anyMatch(line -> line.startsWith("mtllib ")), resource);
						assertTrue(text.contains("usemtl " + fixture.materials().getFirst()), resource);
						var failure = assertThrows(NoSuchElementException.class, () -> neo(text),
								"Neo 对原文 usemtl 必须真实查库，不删除指令");
						assertTrue(failure.getMessage().contains(fixture.materials().getFirst()), failure.getMessage());
					});
		}));
	}

	private static void assertProductionProject(ProductionObj fixture, String text) throws IOException {
		var prepared = project(text, false);
		var model = prepared.definition();
		assertEquals(fixture.vertices(), model.vertices().size());
		assertEquals(fixture.faces(), model.faces().size());
		assertEquals(fixture.parts(), model.parts().stream().map(Part::id).toList());
		assertEquals(Set.copyOf(fixture.materials()), model.materials().keySet());
		assertEquals(fixture.materials(), prepared.groups().stream().map(Group::material).toList());
		assertEquals(fixture.triangles() * 3, prepared.groups().stream().mapToInt(group -> group.indices().size()).sum());

		for (var group : prepared.groups()) {
			for (var corner : group.corners()) {
				var face = model.faces().get(corner.face());
				assertEquals(face.vertices().get(corner.corner()).intValue(), corner.vertex());
				assertEquals(model.parts().get(group.part()).id(), face.partId());
				assertEquals(group.material(), face.material());
			}
		}
	}

	private static void assertProductionPhoton(ProductionObj fixture, String text) throws IOException {
		var mesh = photon(text, false);
		assertNotSame(PhotonMesh.EMPTY, mesh, fixture.name());
		assertFalse(mesh.isEmpty(), fixture.name());
		assertEquals(fixture.triangles(), mesh.triangleCount());
		assertEquals(fixture.triangles() * 3, mesh.indices().length);
		assertEquals(mesh.vertexCount() * PhotonMesh.FLOATS_PER_GEOMETRY, mesh.geometry().length);
		assertEquals(mesh.vertexCount() * PhotonMesh.FLOATS_PER_ATTRIBUTE, mesh.attributes().length);

		for (int index : mesh.indices()) {
			assertTrue(index >= 0 && index < mesh.vertexCount(), fixture.name());
		}

		for (float value : mesh.geometry()) {
			assertTrue(Float.isFinite(value), fixture.name());
		}

		for (float value : mesh.attributes()) {
			assertTrue(Float.isFinite(value), fixture.name());
		}
		// 仅断言原文产生的公开几何流；不将扁平 mesh 当作原面、材质名或骨骼索引。
	}

	private static void assertTriangleRoutes(String text) {
		assertAll(
				() -> {
					var prepared = project(text, false);
					assertEquals(List.of(new Vector3(0, 0, 0), new Vector3(2, 0, 0), new Vector3(0, 2, 0)),
							prepared.definition().vertices());
					assertEquals(List.of(0, 1, 2), prepared.definition().faces().getFirst().vertices());
					assertEquals(2, projectArea(prepared), EPSILON);
				},
				() -> {
					var geometry = neo(text);
					assertEquals(Set.of(""), geometry.getRootComponentNames());
					assertEquals(List.of(""), geometry.getRootParts().stream().map(ObjGeometry.ModelObject::name).toList());
				},
				() -> assertPhotonTriangle(parsePhoton(text, false)));
	}

	private static PhotonModelTopologyUtil.Prepared project(String text, boolean flipV) throws IOException {
		return PhotonModelTopologyUtil.prepare(PhotonModelCodec.read(PhotonModelObjUtil.read(text, flipV)));
	}

	private static ObjGeometry neo(String text) throws IOException {
		try (var tokenizer = new ObjTokenizer(input(text))) {
			return ObjGeometry.parse(tokenizer, new ObjGeometry.Settings(
					Identifier.fromNamespaceAndPath("lobecorp", "comparison/triangle.obj"), false, true, false, false, null));
		}
	}

	private static PhotonMesh photon(String text, boolean flipV) throws IOException {
		assertPhotonTriangle(parsePhoton(TRIANGLE, false));
		return parsePhoton(text, flipV);
	}

	private static PhotonMesh parsePhoton(String text, boolean flipV) throws IOException {
		try (var stream = input(text)) {
			return ObjMeshParser.parse(stream, flipV);
		}
	}

	private static ByteArrayInputStream input(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}

	private static void assertPhotonTriangle(PhotonMesh mesh) {
		var actual = "基础三角必须加载；actual vertices=" + mesh.vertexCount() + ", triangles=" + mesh.triangleCount()
				+ ", indices=" + Arrays.toString(mesh.indices()) + ", geometry=" + Arrays.toString(mesh.geometry());
		assertNotSame(PhotonMesh.EMPTY, mesh, actual);
		assertFalse(mesh.isEmpty(), actual);
		assertEquals(3, mesh.vertexCount(), actual);
		assertEquals(1, mesh.triangleCount(), actual);
		assertArrayEquals(new int[]{0, 1, 2}, mesh.indices(), actual);
		assertArrayEquals(new float[]{0, 0, 0, 0, 0, 1, 2, 0, 0, 0, 0, 1, 0, 2, 0, 0, 0, 1},
				mesh.geometry(), EPSILON, actual);
		assertEquals(2, meshArea(mesh), EPSILON);
	}

	private static double projectArea(PhotonModelTopologyUtil.Prepared prepared) {
		double area = 0;

		for (var group : prepared.groups()) {
			assertEquals(0, group.indices().size() % 3);

			for (int index = 0; index < group.indices().size(); index += 3) {
				var a = prepared.definition().vertices().get(group.corners().get(group.indices().get(index)).vertex());
				var b = prepared.definition().vertices().get(group.corners().get(group.indices().get(index + 1)).vertex());
				var c = prepared.definition().vertices().get(group.corners().get(group.indices().get(index + 2)).vertex());
				double twiceArea = (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
				assertTrue(twiceArea > 0, "本夹具的每个三角形应非退化且保持正绕序");
				area += twiceArea / 2;
			}
		}

		return area;
	}

	private static double meshArea(PhotonMesh mesh) {
		assertEquals(mesh.vertexCount() * PhotonMesh.FLOATS_PER_GEOMETRY, mesh.geometry().length);
		assertEquals(mesh.vertexCount() * PhotonMesh.FLOATS_PER_ATTRIBUTE, mesh.attributes().length);
		assertEquals(mesh.triangleCount() * 3, mesh.indices().length);
		double area = 0;

		for (int index = 0; index < mesh.indices().length; index += 3) {
			var offsets = new int[3];

			for (int corner = 0; corner < 3; corner++) {
				int vertex = mesh.indices()[index + corner];
				assertTrue(vertex >= 0 && vertex < mesh.vertexCount());
				offsets[corner] = PhotonMesh.geometryOffset(vertex);
			}

			var values = mesh.geometry();
			int a = offsets[0];
			int b = offsets[1];
			int c = offsets[2];
			double twiceArea = (values[b] - values[a]) * (values[c + 1] - values[a + 1])
					- (values[b + 1] - values[a + 1]) * (values[c] - values[a]);
			assertTrue(Double.isFinite(twiceArea) && twiceArea >= 0, "本夹具三角形须有限且保持正绕序");
			area += twiceArea / 2;
		}

		return area;
	}
}

