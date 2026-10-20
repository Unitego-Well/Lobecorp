package org.unitego.lobecorp.util.photon.playback;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectNamedControlUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectNamedUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil.Document;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonNamedApiTest {
	@Test
	public void appliesStructuredOperationsToIndependentRawDocuments() throws IOException {
		var empty = new Document(new CompoundTag(), null);
		var first = PhotonEffectNamedUtil.apply(empty, json("{op:'add',name:'first'}"));
		var definition = PhotonNamedCodecUtil.read(first.library()).animations().getFirst();
		var id = definition.id();
		var copy = PhotonEffectNamedUtil.apply(first, json("{op:'duplicate',animationId:'" + id + "',name:'second'}"));
		assertEquals(1, PhotonNamedCodecUtil.read(first.library()).animations().size());
		var operation = json("{op:'replace_definition',animationId:'" + id + "'}");
		var source = definition.source();
		source.putBoolean("loop", true);
		source.putString("future", "keep");
		operation.addProperty("snbt", source.toString());
		var edited = PhotonEffectNamedUtil.apply(copy, operation);
		var renamed = PhotonEffectNamedUtil.apply(edited, json("{op:'rename',animationId:'" + id + "',name:'renamed'}"));
		var moved = PhotonEffectNamedUtil.apply(renamed, json("{op:'move',animationId:'" + id + "',index:1}"));
		var actual = PhotonNamedCodecUtil.read(moved.library()).animations().getLast();
		assertEquals(id, actual.id());
		assertTrue(actual.loop());
		assertEquals("keep", actual.source().getStringOr("future", ""));
		assertEquals("renamed", actual.name());
		assertTrue(empty.library().isEmpty());
		assertThrows(PhotonEffectCommandException.class, () -> PhotonEffectNamedUtil.apply(copy, json("{op:'move',animationId:'" + id + "',index:0.5}")));
	}

	@Test
	public void uuidReferencesBlockDeletionAndDoNotBecomeNameRewrites() throws IOException {
		var source = PhotonNamedAuthoringUtil.add(new Document(new CompoundTag(), null), "first");
		var id = PhotonNamedCodecUtil.read(source.library()).animations().getFirst().id();
		var referenced = PhotonEffectNamedUtil.apply(source, json("{op:'set_default',animation:'" + id + "'}"));
		assertEquals(1, PhotonNamedAuthoringUtil.references(referenced, id).size());
		assertThrows(IllegalArgumentException.class, () -> PhotonNamedAuthoringUtil.remove(referenced, id));
		var renamed = PhotonNamedAuthoringUtil.rename(referenced, id, "renamed");
		assertEquals(id.toString(), renamed.library().getStringOr("defaultAnimation", ""));
		var rules = json("{op:'set_rules'}");
		rules.addProperty("snbt", "{rules:[{event:'TRIGGER',target:'" + id + "',future:'keep'}]}");
		var withRules = PhotonEffectNamedUtil.apply(renamed, rules);
		assertEquals(2, PhotonNamedAuthoringUtil.references(withRules, id).size());
		assertEquals("keep", ((CompoundTag) withRules.library().getListOrEmpty("rules").getFirst()).getStringOr("future", ""));
	}

	@Test
	public void projectSnapshotsRetainTheProjectOnlyFieldAndItsGuard() throws IOException {
		var source = PhotonNamedAuthoringUtil.add(new Document(new CompoundTag(), null), "first");
		var preview = PhotonProjectPreviewUtil.create();
		preview.putString("future", "keep");
		var operation = json("{op:'set_project_preview'}");
		operation.addProperty("snbt", preview.toString());
		var edited = PhotonEffectNamedUtil.apply(source, operation);
		assertEquals(preview, edited.preview());
		var data = new CompoundTag();
		data.put(PhotonProjectPreviewUtil.PROJECT_FIELD, preview.copy());
		assertDoesNotThrow(() -> PhotonEditorProjectGuardUtil.requireSupportedProjectData(data));
		assertThrows(IllegalArgumentException.class, () -> PhotonEditorProjectGuardUtil.requireSupported(data));
		var nested = new CompoundTag();
		nested.put(PhotonProjectPreviewUtil.PROJECT_FIELD, preview.copy());
		data.put("unexpected", nested);
		assertThrows(IllegalArgumentException.class, () -> PhotonEditorProjectGuardUtil.requireSupportedProjectData(data));
		assertNull(PhotonEffectNamedUtil.apply(edited, json("{op:'clear_project_preview'}")).preview());
		var duplicateId = preview.copy();
		duplicateId.putString("id", PhotonNamedCodecUtil.read(source.library()).animations().getFirst().id().toString());
		assertThrows(IllegalArgumentException.class, () -> new Document(source.library(), duplicateId));
	}

	@Test
	public void rejectsMalformedPayloadsWithoutChangingTheSource() {
		var source = PhotonNamedAuthoringUtil.add(new Document(new CompoundTag(), null), "first");
		var snapshot = source.library();
		var badSnbt = json("{op:'set_project_preview',snbt:'{broken'}");
		assertEquals("INVALID_DATA", assertThrows(PhotonEffectCommandException.class, () -> PhotonEffectNamedUtil.apply(source, badSnbt)).code());
		assertEquals("UNSUPPORTED_OPERATION", assertThrows(PhotonEffectCommandException.class, () -> PhotonEffectNamedUtil.apply(source, json("{op:'unknown'}"))).code());
		assertThrows(PhotonEffectCommandException.class, () -> PhotonEffectNamedUtil.apply(source, json("{op:'set_rules',snbt:'{rules:1}'}")));
		assertEquals(snapshot, source.library());
		var inputs = PhotonEffectNamedControlUtil.inputs(json("{inputs:{gain:[1,2,3]}}"));
		assertEquals(3, inputs.get("gain").size());
		assertThrows(PhotonEffectCommandException.class, () -> PhotonEffectNamedControlUtil.inputs(json("{inputs:{gain:['wrong']}}")));
		assertThrows(PhotonEffectCommandException.class, () -> PhotonEffectNamedControlUtil.inputs(json("{inputs:{gain:[1e100]}}")));
		assertEquals("INVALID_ARGUMENT", assertThrows(PhotonEffectCommandException.class, () -> PhotonEffectNamedUtil.uuid(json("{id:'1-1-1-1-1'}"), "id")).code());
	}

	@Test
	public void patchesDefinitionAndPreviewWithoutReplacingTheirIdentityOrUnknownFields() throws IOException {
		var source = PhotonNamedAuthoringUtil.add(new Document(new CompoundTag(), null), "first");
		var definition = PhotonNamedCodecUtil.read(source.library()).animations().getFirst();
		var raw = definition.source();
		raw.putString("future", "keep");
		source = PhotonNamedAuthoringUtil.replaceDefinition(source, definition.id(), raw);
		var preview = PhotonProjectPreviewUtil.create();
		preview.putString("future", "preview_keep");
		var original = new Document(source.library(), preview);
		var operation = json("{op:'patch_definition',animationId:'" + definition.id() + "',snbt:'{duration:80d,loop:1b}'}");
		var edited = PhotonEffectNamedUtil.apply(original, operation);
		var actual = PhotonNamedCodecUtil.read(edited.library()).animations().getFirst();
		assertEquals(definition.id(), actual.id());
		assertEquals("first", actual.name());
		assertEquals(80, actual.duration());
		assertTrue(actual.loop());
		assertEquals("keep", actual.source().getStringOr("future", ""));
		assertEquals(raw, PhotonNamedCodecUtil.read(original.library()).animations().getFirst().source());
		var previewId = preview.getStringOr("id", "");
		var tested = PhotonEffectNamedUtil.apply(edited, json("{op:'patch_project_preview',animationId:'" + previewId + "',snbt:'{duration:40d}'}"));
		var actualPreview = tested.preview();
		assertNotNull(actualPreview);
		assertEquals(40, PhotonProjectPreviewUtil.read(actualPreview).duration());
		assertEquals("preview_keep", actualPreview.getStringOr("future", ""));
		assertEquals(edited.library(), tested.library());
		assertThrows(IllegalArgumentException.class, () -> PhotonEffectNamedUtil.apply(original,
				json("{op:'patch_definition',animationId:'" + definition.id() + "',snbt:'{name:renamed}'}")));
		assertThrows(IllegalArgumentException.class, () -> PhotonEffectNamedUtil.apply(original,
				json("{op:'patch_definition',animationId:'" + definition.id() + "',snbt:'{duration:-1d}'}")));
		assertThrows(IllegalArgumentException.class, () -> PhotonEffectNamedUtil.apply(original,
				json("{op:'patch_project_preview',animationId:'" + definition.id() + "',snbt:'{loop:1b}'}")));
		assertEquals(preview, original.preview());
	}

	private static JsonObject json(String source) {
		return JsonParser.parseString(source).getAsJsonObject();
	}
}
