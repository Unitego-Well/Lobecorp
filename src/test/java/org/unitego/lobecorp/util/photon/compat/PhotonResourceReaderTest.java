package org.unitego.lobecorp.util.photon.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import org.unitego.lobecorp.util.photon.model.PhotonModelFileValidationUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonResourceReaderTest {
	@TempDir
	public Path directory;

	@Test
	public void preservesUnknownNbtAndDefendsMutableAccessors() throws Exception {
		var tag = new CompoundTag();
		tag.putString("type", "future_model_type");
		tag.putString("future", "preserve");
		var data = nbt(tag, false);
		var path = Files.write(directory.resolve("unknown.fxproj"), data);
		var document = PhotonResourceReader.read(path);
		assertArrayEquals(data, document.bytes());
		assertEquals("preserve", document.nbt().getStringOr("future", ""));
		var decoded = document.nbt();
		decoded.remove("future");
		document.bytes()[0] = 0;
		assertEquals("preserve", document.nbt().getStringOr("future", ""));
		assertArrayEquals(data, document.bytes());
		assertFalse(PhotonResourceInspection.inspect(document).runtimeReady());
	}

	@Test
	public void acceptsCompressedRuntimeAndRejectsWrongContainer() throws Exception {
		var tag = new CompoundTag();
		tag.putString("type", "lobecorp_model_emitter");
		var path = Files.write(directory.resolve("model.mfx"), nbt(tag, true));
		var document = PhotonResourceReader.read(path);
		assertEquals(PhotonResourceDocument.Kind.MODEL_FX, document.kind());
		assertTrue(PhotonResourceInspection.isLegacyType(document.nbt().getStringOr("type", "")));
		Files.write(path, nbt(tag, false));
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path));
	}

	@Test
	public void rejectsTrailingNbtInsteadOfDroppingIt() throws Exception {
		var data = nbt(new CompoundTag(), false);
		var extra = Arrays.copyOf(data, data.length + 1);
		Files.write(directory.resolve("extra.fxproj"), extra);
		assertThrows(IOException.class, () -> PhotonResourceReader.read(directory.resolve("extra.fxproj")));
	}

	@Test
	public void enforcesSourceAndExpandedBudgets() throws Exception {
		var tag = new CompoundTag();
		tag.putString("large", "a".repeat(8192));
		var path = Files.write(directory.resolve("large.fx"), nbt(tag, true));
		var limits = new PhotonResourceLimits(512, 512, 1024, 8, 4, 100);
		assertTrue(Files.size(path) < limits.sourceBytes());
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path, limits));
		Files.write(directory.resolve("raw.bin"), new byte[513]);
		assertThrows(IOException.class, () -> PhotonResourceReader.read(directory.resolve("raw.bin"), limits));
	}

	@Test
	public void preservesArchiveUnknownEntriesAndModelDefinition() throws Exception {
		var unknown = new byte[]{0, 2, -1, 4};
		var tag = new CompoundTag();
		tag.putString("type", "lobecorp_model_emitter");
		var path = directory.resolve("models.mfxpack");
		archive(path, Map.of("assets/test/fx/probe.mfx", nbt(tag, true), "extra/future.bin", unknown));
		var original = Files.readAllBytes(path);
		var document = PhotonResourceReader.read(path);
		assertArrayEquals(original, document.bytes());
		assertEquals(2, document.entries().size());
		var binary = document.entries().stream().filter(entry -> entry.kind() == PhotonResourceDocument.Kind.BINARY).findFirst().orElseThrow();
		assertArrayEquals(unknown, binary.bytes());
		assertTrue(document.entries().stream().anyMatch(entry -> entry.kind() == PhotonResourceDocument.Kind.MODEL_FX));
	}

	@Test
	public void rejectsTraversalAndTruncatedArchive() throws Exception {
		var path = directory.resolve("unsafe.fxpack");
		archive(path, Map.of("../outside.bin", new byte[]{1}));
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path));
		archive(path, Map.of("safe.bin", new byte[]{1}));
		var bytes = Files.readAllBytes(path);
		Files.write(path, Arrays.copyOf(bytes, bytes.length - 22));
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path));
	}

	@Test
	public void archiveBudgetsApplyAcrossEntries() throws Exception {
		var path = directory.resolve("many.fxpack");
		var contents = new LinkedHashMap<String, byte[]>();
		contents.put("first.bin", new byte[400]);
		contents.put("second.bin", new byte[400]);
		archive(path, contents);
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path, new PhotonResourceLimits(4096, 700, 4096, 8, 5, 100)));
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path, new PhotonResourceLimits(4096, 4096, 4096, 8, 1, 100)));
	}

	@Test
	public void rejectsDuplicateJsonAndExcessiveComplexity() throws Exception {
		var limits = PhotonResourceLimits.DEFAULT;
		assertThrows(IOException.class, () -> PhotonResourceJsonUtil.read("{\"value\":1,\"value\":2}", limits));
		assertThrows(IOException.class, () -> PhotonResourceJsonUtil.read("{\"array\":[[[[1]]]]}", new PhotonResourceLimits(4096, 4096, 4096, 2, 10, 100)));
		assertThrows(IOException.class, () -> PhotonResourceJsonUtil.read("[1,2,3]", new PhotonResourceLimits(4096, 4096, 4096, 10, 10, 2)));
		assertThrows(IOException.class, () -> PhotonResourceJsonUtil.read("{\"value\":1} trailing", limits));
	}

	@Test
	public void rejectsInvalidUtf8InEmbeddedModel() throws Exception {
		var tag = new CompoundTag();
		tag.putByteArray("assetJson", new byte[]{'{', '"', -1, '"', ':', '1', '}'});
		var path = Files.write(directory.resolve("invalid.fxproj"), nbt(tag, false));
		assertThrows(IOException.class, () -> PhotonResourceInspection.inspect(PhotonResourceReader.read(path)));
	}

	@Test
	public void indexesEmbeddedModelAndKeepsReadinessSeparate() throws Exception {
		var asset = "{\"version\":1,\"vertices\":[],\"faces\":[],\"parts\":[{}],\"morphs\":[{}],\"future\":true}";
		var tag = new CompoundTag();
		tag.putByteArray("assetJson", asset.getBytes(StandardCharsets.UTF_8));
		tag.putBoolean("lobecorp$fixedRendering", false);
		tag.putString("type", "lobecorp_model_emitter");
		tag.putString("target", "943473cc-da2d-4ed4-9c35-2bac5e3dea6b");
		tag.putString("material", "file(assets/test/material.nbt)");
		var path = Files.write(directory.resolve("model.fxproj"), nbt(tag, false));
		var report = PhotonResourceInspection.inspect(PhotonResourceReader.read(path));
		assertEquals(1, report.models().getFirst().morphs());
		assertEquals(1, report.models().getFirst().parts());
		assertEquals(1, report.identities().size());
		assertEquals(1, report.references().size());
		assertEquals(1, report.legacyFields().size());
		assertEquals(1, report.types().get("lobecorp_model_emitter"));
		assertFalse(report.runtimeReady());
		assertEquals(asset, new String(PhotonResourceReader.read(path).nbt().getByteArray("assetJson").orElseThrow(), StandardCharsets.UTF_8));
	}

	@Test
	public void directoryPayloadCannotBypassArchiveBudget() throws Exception {
		var path = directory.resolve("directory.fxpack");
		archive(path, Map.of("hidden/", new byte[1024]));
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path));
	}

	@Test
	public void fileGuardStillBlocksLegacyAndAllowsPlainNativeData() throws Exception {
		var tag = new CompoundTag();
		tag.putString("type", "particle_emitter");
		var path = Files.write(directory.resolve("native.fxproj"), nbt(tag, false));
		assertFalse(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		tag.putBoolean("lobecorp$fixedRendering", false);
		Files.write(path, nbt(tag, false));
		assertFalse(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		tag.putInt("lobecorp$fixedRendering", 0);
		Files.write(path, nbt(tag, false));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		tag.putBoolean("lobecorp$fixedRendering", false);
		tag.putBoolean("lobecorp$unrecognized", false);
		Files.write(path, nbt(tag, false));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		tag.remove("lobecorp$unrecognized");
		tag.putBoolean("lobecorp$fixedRendering", true);
		Files.write(path, nbt(tag, false));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		assertFalse(PhotonEditorProjectGuardUtil.containsFileExtensions(directory.resolve("model.mfxproj").toFile()));
		archive(directory.resolve("legacy.fxpack"), Map.of("assets/test/fx/effect.fx", nbt(tag, true)));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(directory.resolve("legacy.fxpack").toFile()));
	}

	@Test
	public void interruptedReadDoesNotClearCancellation() throws Exception {
		var path = Files.write(directory.resolve("cancel.bin"), new byte[64]);
		Thread.currentThread().interrupt();

		try {
			assertThrows(IOException.class, () -> PhotonResourceReader.read(path));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	public void modelFileAdmissionChecksCurrentContainerAssetAndPlaybackWithoutMutation() throws Exception {
		var project = modelProject();
		var before = project.copy();
		var path = directory.resolve("current.mfxproj");
		Files.write(path, nbt(project, false));
		assertFalse(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		assertEquals(before, project);
		assertEquals(before, PhotonResourceReader.read(path).nbt());

		project.getCompoundOrEmpty("meta").putString("version", "2.0");
		Files.write(path, nbt(project, false));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		project = modelProject();
		project.getCompoundOrEmpty("data").putString("asset", "{\"version\":2,\"vertices\":[],\"faces\":[]}");
		Files.write(path, nbt(project, false));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		project = modelProject();
		project.getCompoundOrEmpty("data").getCompoundOrEmpty("playback").putInt("looping", 1);
		Files.write(path, nbt(project, false));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		project = modelProject();
		project.getCompoundOrEmpty("data").getCompoundOrEmpty("renderer").putBoolean("lobecorp$unrecognized", false);
		Files.write(path, nbt(project, false));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
	}

	@Test
	public void modelDefinitionsAndPacksRejectMissingIdentityAndUnclosedSources() throws Exception {
		var wrapper = new CompoundTag();
		wrapper.putString("type", "lobecorp_model_emitter");
		var emitter = new CompoundTag();
		emitter.putInt("version", 0);
		var transform = new CompoundTag();
		transform.putString("id", "00000000-0000-0000-0000-000000000001");
		emitter.put("transform", transform);
		var config = new CompoundTag();
		config.putString("source", "EMBEDDED");
		config.putString("assetJson", "{\"version\":1,\"vertices\":[],\"faces\":[]}");
		config.put("modelRenderer", new CompoundTag());
		emitter.put("config", config);
		wrapper.put("data", emitter);
		var objects = new ListTag();
		objects.add(wrapper);
		var data = new CompoundTag();
		data.put("objects", objects);
		var definition = new CompoundTag();
		definition.putInt("version", PhotonModelFileValidationUtil.DEFINITION_VERSION);
		definition.put("fxData", data);
		var path = directory.resolve("model.mfx");
		Files.write(path, nbt(definition, true));
		assertFalse(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		var pack = directory.resolve("model.mfxpack");
		archive(pack, Map.of("assets/test/fx/model.mfx", nbt(definition, true)));
		assertFalse(PhotonEditorProjectGuardUtil.containsFileExtensions(pack.toFile()));
		transform.put("id", new IntArrayTag(new int[]{0, 0, 0, 1}));
		Files.write(path, nbt(definition, true));
		assertFalse(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		config.putString("source", "FILE");
		Files.write(path, nbt(definition, true));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		archive(pack, Map.of("assets/test/fx/model.mfx", nbt(definition, true)));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(pack.toFile()));
		config.putString("source", "EMBEDDED");
		transform.putString("id", "invalid");
		Files.write(path, nbt(definition, true));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
	}

	@Test
	public void assetOnlyModelProjectKeepsOptionalGroupsAbsentAndRejectsWrongTypes() throws Exception {
		var project = modelProject();
		var data = project.getCompoundOrEmpty("data");
		data.remove("playback");
		data.remove("renderer");
		var original = project.copy();
		var path = directory.resolve("asset-only.mfxproj");
		Files.write(path, nbt(project, false));
		assertFalse(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		assertEquals(original, PhotonResourceReader.read(path).nbt());
		assertEquals(original, project);
		data.putInt("playback", 0);
		Files.write(path, nbt(project, false));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		data.remove("playback");
		data.putString("renderer", "invalid");
		Files.write(path, nbt(project, false));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
	}

	private static CompoundTag modelProject() {
		var result = new CompoundTag();
		var meta = new CompoundTag();
		meta.putString("version", "1.0");
		meta.putString("suffix", ".mfxproj");
		result.put("meta", meta);
		var data = new CompoundTag();
		data.put("asset", StringTag.valueOf("{\"version\":1,\"vertices\":[],\"faces\":[],\"future\":\"preserve\"}"));
		data.put("playback", new CompoundTag());
		data.put("renderer", new CompoundTag());
		data.putString("future", "preserve");
		result.put("data", data);
		return result;
	}

	@Test
	public void nativeDisabledOcclusionDoesNotAdmitEnabledMalformedOrUnknownFields() {
		var renderer = new CompoundTag();
		var occlusion = new CompoundTag();
		renderer.put("lobecorp$occlusion", occlusion);
		occlusion.putBoolean("_enable", false);
		assertFalse(PhotonEditorProjectGuardUtil.containsTag(renderer));
		occlusion.putBoolean("_enable", true);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(renderer));
		occlusion.putInt("_enable", 0);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(renderer));
		occlusion.putBoolean("_enable", false);
		occlusion.putBoolean("future", false);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(renderer));
		occlusion.remove("future");
		occlusion.putBoolean("enable", true);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(renderer));
		occlusion.remove("enable");
		occlusion.putFloat("alphaCutoff", Float.NaN);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(renderer));
		occlusion.putFloat("alphaCutoff", 0.99f);
		assertFalse(PhotonEditorProjectGuardUtil.containsTag(renderer));
	}

	@Test
	public void fixedRendererAdmissionKeepsOriginalDataAndUnknownFieldProtection() {
		for (var type : new String[]{"particle_emitter", "lobecorp_model_emitter"}) {
			var wrapper = renderedEmitter(type);
			var config = wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config");
			if (type.equals("lobecorp_model_emitter")) {
				config.put("modelRenderer", fixedRenderer());
			}

			var before = wrapper.copy();
			assertFalse(PhotonEditorProjectGuardUtil.containsTag(wrapper));
			assertEquals(before, wrapper);
			config.getCompoundOrEmpty("renderer").putBoolean("lobecorp$unrecognized", false);
			assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		}
	}

	@Test
	public void fixedRendererAdmissionRejectsWrongTypesShapesAndConflictingToggles() {
		for (var key : new String[]{"lobecorp$fixedRendering", "lobecorp$viewDepthSort"}) {
			for (Tag value : new Tag[]{ByteTag.valueOf((byte) 2), IntTag.valueOf(1), StringTag.valueOf("true")}) {
				var wrapper = renderedEmitter("particle_emitter");
				wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config").getCompoundOrEmpty("renderer").put(key, value);
				assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
			}
		}

		for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -0.01, 1.01}) {
			var wrapper = renderedEmitter("particle_emitter");
			var occlusion = wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config")
					.getCompoundOrEmpty("renderer").getCompoundOrEmpty("lobecorp$occlusion");
			occlusion.putDouble("alphaCutoff", value);
			assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		}

		var wrapper = renderedEmitter("particle_emitter");
		var occlusion = wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config")
				.getCompoundOrEmpty("renderer").getCompoundOrEmpty("lobecorp$occlusion");
		occlusion.putBoolean("enable", false);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		occlusion.putBoolean("enable", true);
		assertFalse(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		occlusion.putInt("_enable", 1);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		occlusion.putBoolean("_enable", true);
		occlusion.putBoolean("future", false);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		occlusion.remove("future");
		occlusion.remove("_enable");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		occlusion.putBoolean("_enable", true);
		occlusion.putString("alphaCutoff", "0.99");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		occlusion.remove("alphaCutoff");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		for (double value : new double[]{0, 1}) {
			occlusion.putDouble("alphaCutoff", value);
			assertFalse(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		}

		var renderer = wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config").getCompoundOrEmpty("renderer");
		renderer.remove("lobecorp$viewDepthSort");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		renderer.putBoolean("lobecorp$viewDepthSort", true);
		renderer.putString("lobecorp$occlusion", "invalid");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
	}

	@Test
	public void fixedRendererAdmissionDoesNotEscapeEmitterContextOrOtherGuards() {
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(fixedRenderer()));
		for (var type : new String[]{"empty", "future_emitter", "lobecorp_emitter_spawner"}) {
			assertTrue(PhotonEditorProjectGuardUtil.containsTag(renderedEmitter(type)));
		}

		var particle = renderedEmitter("particle_emitter");
		var config = particle.getCompoundOrEmpty("data").getCompoundOrEmpty("config");
		config.put("modelRenderer", fixedRenderer());
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(particle));
		config.remove("modelRenderer");
		config.putBoolean("lobecorp$lifetimeCycleEnabled", true);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(particle));
		var model = renderedEmitter("lobecorp_model_emitter");
		model.getCompoundOrEmpty("data").getCompoundOrEmpty("config").putString("source", "FILE");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(model));
		particle = renderedEmitter("particle_emitter");
		var data = particle.getCompoundOrEmpty("data");
		data.put("renderer", fixedRenderer());
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(particle));
		particle = renderedEmitter("particle_emitter");
		particle.getCompoundOrEmpty("data").putInt("version", 1);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(particle));
		particle.getCompoundOrEmpty("data").remove("version");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(particle));
	}

	@Test
	public void particleCycleAdmissionPreservesMagicCircleFieldsAndUnknownValues() {
		var wrapper = cycledEmitter();
		var config = wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config");
		config.getCompoundOrEmpty("sizeOverLifetime").putString("future", "preserve");
		var original = wrapper.copy();
		assertFalse(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		assertEquals(original, wrapper);
		config.getCompoundOrEmpty("rotationOverLifetime").putBoolean("lobecorp$unknownCycle", true);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
	}

	@Test
	public void particleCycleAdmissionRejectsInvalidFlagsPeriodsAndOffsets() {
		for (var moduleName : new String[]{"rotationOverLifetime", "sizeOverLifetime"}) {
			String cycle = moduleName.equals("rotationOverLifetime") ? "rotation" : "lifetime";
			for (var key : new String[]{"_enable", "lobecorp$" + cycle + "CycleEnabled"}) {
				for (Tag value : new Tag[]{ByteTag.valueOf((byte) 2), IntTag.valueOf(1), StringTag.valueOf("true")}) {
					var wrapper = cycledEmitter();
					wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config").getCompoundOrEmpty(moduleName).put(key, value);
					assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
				}
			}

			for (int ticks : new int[]{0, -1}) {
				var wrapper = cycledEmitter();
				wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config").getCompoundOrEmpty(moduleName)
						.putInt("lobecorp$" + cycle + "CycleTicks", ticks);
				assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
			}

			var wrapper = cycledEmitter();
			var module = wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config").getCompoundOrEmpty(moduleName);
			module.putFloat("lobecorp$" + cycle + "CycleTicks", 20);
			assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
			for (double offset : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE}) {
				wrapper = cycledEmitter();
				module = wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config").getCompoundOrEmpty(moduleName);
				module.putDouble(moduleName.equals("rotationOverLifetime") ? "lobecorp$cycleOffsetRoll" : "lobecorp$cycleOffsetX", offset);
				assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
			}

			wrapper = cycledEmitter();
			module = wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config").getCompoundOrEmpty(moduleName);
			module.putBoolean("enable", false);
			assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
			module.remove("enable");
			module.remove(moduleName.equals("rotationOverLifetime") ? "lobecorp$cycleOffsetYaw" : "lobecorp$cycleOffsetZ");
			assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		}
	}

	@Test
	public void particleCycleAdmissionRejectsOtherModulesTypesAndVersions() {
		var wrapper = cycledEmitter();
		var config = wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config");
		var rotation = config.getCompoundOrEmpty("rotationOverLifetime").copy();
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(rotation));
		config.put("colorOverLifetime", rotation);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		config.remove("colorOverLifetime");
		config.put("sizeOverLifetime", rotation);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		wrapper = cycledEmitter();
		wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config").getCompoundOrEmpty("sizeOverLifetime")
				.putFloat("lobecorp$cycleOffsetUV", 0);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		wrapper = cycledEmitter();
		wrapper.getCompoundOrEmpty("data").putInt("version", 1);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		wrapper = cycledEmitter();
		wrapper.putString("type", "future_emitter");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		wrapper = renderedEmitter("lobecorp_model_emitter");
		wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config").put("rotationOverLifetime", rotation);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
	}

	private static CompoundTag cycledEmitter() {
		var wrapper = renderedEmitter("particle_emitter");
		var config = wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config");
		var rotation = new CompoundTag();
		rotation.putBoolean("_enable", true);
		rotation.putBoolean("lobecorp$rotationCycleEnabled", true);
		rotation.putInt("lobecorp$rotationCycleTicks", 40);
		for (var axis : new String[]{"Roll", "Pitch", "Yaw"}) {
			rotation.putFloat("lobecorp$cycleOffset" + axis, 0);
		}

		config.put("rotationOverLifetime", rotation);
		var size = new CompoundTag();
		size.putBoolean("_enable", true);
		size.putBoolean("lobecorp$lifetimeCycleEnabled", true);
		size.putInt("lobecorp$lifetimeCycleTicks", 20);
		size.putFloat("lobecorp$cycleOffsetX", 0.2f);
		size.putFloat("lobecorp$cycleOffsetY", 0.2f);
		size.putFloat("lobecorp$cycleOffsetZ", 0.3f);
		config.put("sizeOverLifetime", size);
		return wrapper;
	}

	private static CompoundTag renderedEmitter(String type) {
		var wrapper = new CompoundTag();
		wrapper.putString("type", type);
		var data = new CompoundTag();
		data.putInt("version", type.equals("lobecorp_model_emitter") ? 0 : 2);
		var config = new CompoundTag();
		config.put("renderer", fixedRenderer());
		if (type.equals("lobecorp_model_emitter")) {
			var transform = new CompoundTag();
			transform.putString("id", "00000000-0000-0000-0000-000000000001");
			data.put("transform", transform);
			config.putString("source", "EMBEDDED");
			config.putString("assetJson", "{\"version\":1,\"vertices\":[],\"faces\":[]}");
			config.put("modelRenderer", new CompoundTag());
		}

		data.put("config", config);
		wrapper.put("data", data);
		return wrapper;
	}

	private static CompoundTag fixedRenderer() {
		var renderer = new CompoundTag();
		renderer.putBoolean("lobecorp$fixedRendering", true);
		renderer.putBoolean("lobecorp$viewDepthSort", true);
		var occlusion = new CompoundTag();
		occlusion.putBoolean("_enable", true);
		occlusion.putFloat("alphaCutoff", 0.99f);
		renderer.put("lobecorp$occlusion", occlusion);
		return renderer;
	}

	private static byte[] nbt(CompoundTag tag, boolean compressed) throws IOException {
		var output = new ByteArrayOutputStream();

		if (compressed) {
			NbtIo.writeCompressed(tag, output);
		} else {
			try (var data = new DataOutputStream(output)) {
				NbtIo.write(tag, data);
			}
		}

		return output.toByteArray();
	}

	private static void archive(Path path, Map<String, byte[]> contents) throws IOException {
		try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
			for (var entry : contents.entrySet()) {
				output.putNextEntry(new ZipEntry(entry.getKey()));
				output.write(entry.getValue());
				output.closeEntry();
			}
		}
	}
}
