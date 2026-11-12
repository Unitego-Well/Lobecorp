package org.unitego.lobecorp.util.photon.playback;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.photon.compat.PhotonLosslessNbtUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonProjectNbtUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDependencyUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

import java.io.IOException;
import java.util.UUID;

import org.unitego.lobecorp.util.photon.compat.PhotonInactiveLegacyFieldUtil;
import org.unitego.lobecorp.util.photon.runtime.PhotonLifetimeCycleUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonG1AuthoringOperationsTest {
	@Test
	public void nestedListEditsKeepIdentityUnknownFieldsAndOriginalDocument() throws Exception {
		var source = PhotonNamedAuthoringUtil.add(new PhotonNamedAuthoringUtil.Document(new CompoundTag(), null), "first");
		var id = PhotonNamedCodecUtil.read(source.library()).animations().getFirst().id();
		var group = TagParser.parseCompoundFully("{type:group,data:{children:[],future:'group'}}");
		var withGroup = PhotonNamedTrackAuthoringUtil.edit(source, id, false, List.of("tracks"), "insert", 0, -1, group);
		var track = TagParser.parseCompoundFully("{type:activator,data:{name:'old',clips:[],future:'track'}}");
		var path = List.<Object>of("tracks", 0, "data", "children");
		var populated = PhotonNamedTrackAuthoringUtil.edit(withGroup, id, false, path, "insert", 0, -1, track);
		var edited = PhotonNamedTrackAuthoringUtil.edit(populated, id, false, path, "patch", 0, -1,
				TagParser.parseCompoundFully("{data:{name:'new'}}"));
		var raw = PhotonNamedTrackAuthoringUtil.definition(edited, id, false);
		var actual = ((CompoundTag) raw.getListOrEmpty("tracks").getFirst()).getCompoundOrEmpty("data");
		assertEquals("group", actual.getStringOr("future", ""));
		var child = ((CompoundTag) actual.getListOrEmpty("children").getFirst()).getCompoundOrEmpty("data");
		assertEquals("track", child.getStringOr("future", ""));
		assertEquals("new", child.getStringOr("name", ""));
		assertTrue(PhotonNamedTrackAuthoringUtil.definition(source, id, false).getListOrEmpty("tracks").isEmpty());
		assertThrows(IllegalArgumentException.class, () -> PhotonNamedTrackAuthoringUtil.edit(populated, id, false, path, "remove", 7, -1, new CompoundTag()));
		assertEquals("old", ((CompoundTag) ((CompoundTag) PhotonNamedTrackAuthoringUtil.definition(populated, id, false).getListOrEmpty("tracks").getFirst())
				.getCompoundOrEmpty("data").getListOrEmpty("children").getFirst()).getCompoundOrEmpty("data").getStringOr("name", ""));
	}

	@Test
	public void deltaKeepsUnknownFieldsWhenObjectsMoveAndChange() throws Exception {
		var raw = TagParser.parseCompoundFully("{future:'root',objects:[{data:{transform:{id:'first'},name:'same',value:1,future:'first'}},{data:{transform:{id:'second'},name:'same',value:2,future:'second'}}]}");
		var before = TagParser.parseCompoundFully("{objects:[{data:{transform:{id:'first'},name:'same',value:1}},{data:{transform:{id:'second'},name:'same',value:2}}]}");
		var after = TagParser.parseCompoundFully("{objects:[{data:{transform:{id:'second'},name:'same',value:20}},{data:{transform:{id:'first'},name:'renamed',value:1}}]}");
		var merged = PhotonLosslessNbtUtil.changed(raw, before, after);
		assertEquals("root", merged.getStringOr("future", ""));
		var first = ((CompoundTag) merged.getListOrEmpty("objects").getFirst()).getCompoundOrEmpty("data");
		assertEquals("second", first.getStringOr("future", ""));
		assertEquals(20, first.getIntOr("value", 0));
		assertEquals(raw, PhotonLosslessNbtUtil.changed(raw, before, before));
	}



	@Test
	public void dependencyAuditKeepsSourceVersionsAndDoesNotInventBuiltinAcceptance() throws Exception {
		var root = source("assets/demo/a.json", "root", "{texture:'demo:textures/a.png'}");
		var first = source("assets/demo/textures/a.png", "pack_first", "{}");
		var second = source("assets/demo/textures/a.png", "source_second", "{}");
		var report = PhotonResourceDependencyUtil.resolve(List.of(root, first, second), List.of(root.key()), PhotonResourceLimits.DEFAULT);
		assertTrue(report.closed());
		assertEquals(2, report.versions().get(first.key()).size());
		assertEquals("pack_first", report.versions().get(first.key()).getFirst().origin());
		assertEquals("resolved", report.edges().getFirst().status());
		var builtin = source("builtin.json", "root", "{material:'built-in(material/white)'}");
		assertFalse(PhotonResourceDependencyUtil.resolve(List.of(builtin), List.of(builtin.key()), PhotonResourceLimits.DEFAULT).closed());
	}

	@Test
	public void dependencyAuditReportsCyclesAndMissingResources() throws Exception {
		var a = source("assets/demo/a.json", "a", "{next:'demo:b.json'}");
		var b = source("assets/demo/b.json", "b", "{next:'demo:a.json',missing:'demo:missing.png'}");
		var report = PhotonResourceDependencyUtil.resolve(List.of(a, b), List.of(a.key()), PhotonResourceLimits.DEFAULT);
		assertFalse(report.closed());
		assertFalse(report.cycles().isEmpty());
		assertTrue(report.edges().stream().anyMatch(edge -> edge.status().equals("unresolved")));
	}

	@Test
	public void fullValueInputReadKeepsUnregisteredKeysAndReturnsACopy() throws Exception {
		var raw = TagParser.parseCompoundFully("{fx:{future:'keep'},lobecorp_animation_preview:{version:6},future:[1,2,3]}");
		var input = TagValueInput.create(new ProblemReporter.Collector(), RegistryAccess.EMPTY, raw);
		var copy = PhotonProjectNbtUtil.read(input);
		assertEquals(raw, copy);
		copy.remove("future");
		assertTrue(raw.contains("future"));
	}

	@Test
	public void copyingChildTracksChangesWindowIdentityAndKeepsTargetAndUnknownData() throws Exception {
		var id = UUID.randomUUID().toString();
		var source = TagParser.parseCompoundFully("{type:lobecorp_child_animation,data:{target:'keep',clips:[{id:'" + id + "',animation:'charge',future:42}],future:'track'}}");
		var copy = PhotonTrackCopyUtil.copy(source);
		var clip = (CompoundTag) copy.getCompoundOrEmpty("data").getListOrEmpty("clips").getFirst();
		assertNotEquals(id, clip.getStringOr("id", ""));
		assertEquals("charge", clip.getStringOr("animation", ""));
		assertEquals(42, clip.getIntOr("future", 0));
		assertEquals("keep", copy.getCompoundOrEmpty("data").getStringOr("target", ""));
		assertEquals(id, ((CompoundTag) source.getCompoundOrEmpty("data").getListOrEmpty("clips").getFirst()).getStringOr("id", ""));
	}



	@Test
	public void inactiveLegacyDefaultsDoNotPermitActiveOrUnknownFields() throws Exception {
		var source = TagParser.parseCompoundFully("{'lobecorp$lifetimeCycleEnabled':0b,'lobecorp$lifetimeCycleTicks':20,'lobecorp$cycleOffsetX':0f}");
		for (var entry : source.entrySet()) {
			assertTrue(PhotonInactiveLegacyFieldUtil.allowed(source, entry.getKey(), entry.getValue()));
		}
		var active = source.copy();
		active.putBoolean("lobecorp$lifetimeCycleEnabled", true);
		assertFalse(PhotonInactiveLegacyFieldUtil.allowed(active, "lobecorp$lifetimeCycleEnabled", active.get("lobecorp$lifetimeCycleEnabled")));
		assertFalse(PhotonInactiveLegacyFieldUtil.allowed(source, "lobecorp$future", new CompoundTag()));
	}

	@Test
	public void loopPhaseKeepsNegativeOffsetsAndCycleBoundaries() {
		assertEquals(0, PhotonLifetimeCycleUtil.phase(20, 20));
		assertEquals(0.75f, PhotonLifetimeCycleUtil.phase(-5, 20));
		assertEquals(0.25f, PhotonLifetimeCycleUtil.phase(25, 20));
	}

	private static PhotonResourceDependencyUtil.Source source(String path, String origin, String json) {
		var document = new PhotonResourceDocument(origin, PhotonResourceDocument.Kind.JSON, "fixture", new byte[0], null,
				JsonParser.parseString(json), List.of());
		return new PhotonResourceDependencyUtil.Source(path, origin, document.sha256(), document);
	}
}
