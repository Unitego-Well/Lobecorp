package org.unitego.lobecorp.util.photon.compat;

import com.google.gson.GsonBuilder;
import net.minecraft.nbt.NbtIo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 只准备隔离验收副本与逐项来源清单；本测试通过不代表原生制作或独立运行已通过。
public class PhotonResourceAcceptanceInputsTest {
	private static final Path PROJECT = Path.of("").toAbsolutePath().normalize();
	private static final Path GAME = PROJECT.resolve("run/g1_resource_acceptance_20261021");
	private static final Path INPUTS = GAME.resolve("ldlib2/assets/g1_inputs");
	private static final Path ARCHIVE = PROJECT.resolve("backups/photon-full-resources-20261021/source-af77096.zip");
	private static final String COMMIT = "af77096";

	@Test
	public void prepareHashVerifiedInputsAndManifest() throws Exception {
		var baseline = JsonParser.parseString(Files.readString(PROJECT.resolve("docs/systems/photon-resource-baseline-20261019.json"))).getAsJsonObject();
		var rows = new JsonArray();
		var packByHash = new HashMap<String, String>();
		System.out.println("acceptance-inputs pid=" + ProcessHandle.current().pid() + " start=" + ProcessHandle.current().info().startInstant().orElse(null));
		try (var archive = new ZipFile(ARCHIVE.toFile())) {
			for (var entry : archive.stream().filter(value -> value.getName().endsWith(".fxpack") || value.getName().endsWith(".mfxpack")).toList()) {
				packByHash.put(hash(read(archive, entry.getName())), entry.getName());
			}
			for (var element : baseline.getAsJsonArray("roots")) {
				prepareRoot(archive, element.getAsJsonObject(), packByHash, rows);
			}
			writeSourcePack(archive, baseline.getAsJsonArray("roots").get(0).getAsJsonObject());
		}
		var manifest = new JsonObject();
		manifest.addProperty("version", 1);
		manifest.addProperty("snapshotCommit", COMMIT);
		manifest.addProperty("gameDirectory", GAME.toString());
		manifest.addProperty("recordCount", rows.size());
		manifest.addProperty("nativeRoundTripValidated", false);
		manifest.addProperty("independentRuntimeValidated", false);
		manifest.add("rows", rows);
		var report = new GsonBuilder().setPrettyPrinting().create().toJson(manifest);
		write(INPUTS.resolve("manifest.json"), report.getBytes(StandardCharsets.UTF_8));
		copyWorld();
		assertEquals(501, rows.size());
		assertTrue(rows.asList().stream().filter(value -> !value.getAsJsonObject().get("root").getAsString().equals("editor_state"))
				.allMatch(value -> value.getAsJsonObject().get("staging").getAsString().equals("hash_verified")));
		System.out.println("acceptance-inputs manifest=" + INPUTS.resolve("manifest.json"));
	}


	@Test
	public void roundTripEveryStagedResource() throws Exception {
		prepareHashVerifiedInputsAndManifest();
		var manifest = JsonParser.parseString(Files.readString(INPUTS.resolve("manifest.json"))).getAsJsonObject();
		var results = new JsonArray();
		var destination = GAME.resolve("g1_reports/raw_" + System.currentTimeMillis());
		Files.createDirectories(destination);
		int failures = 0;
		int checked = 0;
		for (var value : manifest.getAsJsonArray("rows")) {
			var row = value.getAsJsonObject().deepCopy();
			if (!row.get("staging").getAsString().equals("hash_verified")) {
				row.addProperty("rawRoundTrip", "not_run_source_unavailable");
				results.add(row);
				continue;
			}
			try {
				var source = Path.of(row.get("inputPath").getAsString());
				var document = PhotonResourceReader.read(source);
				assertEquals(row.get("sha256").getAsString(), document.sha256());
				var target = destination.resolve(Integer.toString(checked++)).resolve(source.getFileName());
				Files.createDirectories(target.getParent());
				writeDocument(document, target);
				var actual = PhotonResourceReader.read(target);
				assertEquals(document.kind(), actual.kind());
				if (document.nbt() != null) {
					assertEquals(document.nbt(), actual.nbt());
				} else if (document.json() != null) {
					assertEquals(document.json(), actual.json());
				} else if (!document.entries().isEmpty()) {
					assertEquals(entryHashes(document), entryHashes(actual));
				} else {
					assertArrayEquals(document.bytes(), actual.bytes());
				}
				assertEquals(document.sha256(), hash(Files.readAllBytes(source)));
				row.addProperty("rawRoundTrip", "passed_structure_or_dependency_bytes");
				row.addProperty("roundTripPath", target.toString());
			} catch (Exception | AssertionError invalid) {
				failures++;
				row.addProperty("rawRoundTrip", "failed");
				row.addProperty("error", invalid.toString());
			}
			results.add(row);
		}
		Files.writeString(destination.resolve("raw_round_trips.json"), new GsonBuilder().setPrettyPrinting().create().toJson(results), StandardOpenOption.CREATE_NEW);
		System.out.println("raw-round-trips checked=" + checked + " failures=" + failures + " report=" + destination.resolve("raw_round_trips.json"));
		assertEquals(0, failures);
	}

	private static Map<String, String> entryHashes(PhotonResourceDocument document) {
		var result = new HashMap<String, String>();
		for (var entry : document.entries()) {
			result.put(entry.origin().substring(document.origin().length() + 2), entry.sha256());
		}
		return result;
	}

	private static void writeDocument(PhotonResourceDocument document, Path target) throws IOException {
		var nbt = document.nbt();
		var json = document.json();
		if (nbt != null) {
			var original = document.bytes();
			if (original.length >= 2 && original[0] == (byte) 0x1f && original[1] == (byte) 0x8b) {
				NbtIo.writeCompressed(nbt, target);
			} else {
				NbtIo.write(nbt, target);
			}
		} else if (json != null) {
			Files.writeString(target, json.toString(), StandardOpenOption.CREATE_NEW);
		} else if (!document.entries().isEmpty()) {
			try (var output = new ZipOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
				for (var entry : document.entries()) {
					output.putNextEntry(new ZipEntry(entry.origin().substring(document.origin().length() + 2)));
					output.write(entry.bytes());
					output.closeEntry();
				}
			}
		} else {
			Files.write(target, document.bytes(), StandardOpenOption.CREATE_NEW);
		}
	}

	private static void prepareRoot(ZipFile archive, JsonObject root, Map<String, String> packs, JsonArray rows) throws Exception {
		var rootName = root.get("root").getAsString();
		var headers = new HashMap<String, String>();
		for (var value : root.getAsJsonArray("entries")) {
			var entry = value.getAsJsonObject();
			if (entry.get("entry").getAsString().isEmpty()) {
				headers.put(entry.get("source").getAsString(), entry.get("sha256").getAsString());
			}
		}
		for (var value : root.getAsJsonArray("entries")) {
			var entry = value.getAsJsonObject();
			var source = entry.get("source").getAsString();
			var member = entry.get("entry").getAsString();
			var archived = rootName.equals("pack_cache") ? packs.get(headers.get(source))
					: root.get("directory").getAsString() + "/" + source;
			var row = entry.deepCopy();
			row.addProperty("root", rootName);
			row.addProperty("archiveEntry", archived);
			row.addProperty("nativeRoundTrip", "not_run");
			row.addProperty("independentRuntime", "not_run");
			if (archived == null || archive.getEntry(archived) == null) {
				row.addProperty("staging", "source_unavailable");
				rows.add(row);
				continue;
			}
			var raw = read(archive, archived);
			if (!member.isEmpty()) {
				raw = member(raw, member);
			}
			var expected = entry.get("sha256").getAsString();
			var exact = recordedBytes(raw, expected);
			var relative = rootName.equals("source_assets") ? "assets/" + source
					: rootName + "/" + source + (member.isEmpty() ? "" : ".entries/" + member);
			var target = INPUTS.resolve(relative).normalize();
			assertTrue(target.startsWith(INPUTS));
			write(target, exact);
			if (rootName.equals("source_packs") || rootName.equals("pack_cache")) {
				var runtimePack = GAME.resolve("photon/fxpacks/" + rootName + "_" + Path.of(source).getFileName());
				row.addProperty("runtimePackPath", runtimePack.toString());
				if (member.isEmpty()) {
					write(runtimePack, exact);
				}
			}

			row.addProperty("inputPath", target.toString());
			row.addProperty("staging", "hash_verified");
			row.addProperty("byteRepresentation", hash(raw).equals(expected) ? "archive_exact" : "recorded_line_endings");
			rows.add(row);
		}
	}

	private static void writeSourcePack(ZipFile archive, JsonObject root) throws Exception {
		var metadataPack = archive.stream().filter(value -> value.getName().endsWith(".fxpack")).findFirst().orElseThrow();
		var metadata = member(read(archive, metadataPack.getName()), "pack.mcmeta");
		var target = GAME.resolve("photon/fxpacks/g1_source_baseline.fxpack");
		Files.createDirectories(target.getParent());
		if (Files.exists(target)) {
			try (var existing = new ZipFile(target.toFile())) {
				assertEquals(root.getAsJsonArray("entries").size() + 1, existing.size(), "source wrapper entry set");
				assertArrayEquals(metadata, read(existing, "pack.mcmeta"), "source wrapper metadata");
				for (var value : root.getAsJsonArray("entries")) {
					var entry = value.getAsJsonObject();
					var name = entry.get("source").getAsString();
					assertEquals(entry.get("sha256").getAsString(), hash(read(existing, "assets/" + name)), name);
				}
			}
			return;
		}
		try (var output = new ZipOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
			output.putNextEntry(new ZipEntry("pack.mcmeta"));
			output.write(metadata);
			output.closeEntry();
			for (var value : root.getAsJsonArray("entries")) {
				var entry = value.getAsJsonObject();
				var name = entry.get("source").getAsString();
				output.putNextEntry(new ZipEntry("assets/" + name));
				output.write(Files.readAllBytes(INPUTS.resolve("assets/" + name)));
				output.closeEntry();
			}
		}
	}

	private static void copyWorld() throws IOException {
		var source = PROJECT.resolve("run/client/saves/g1_validation_20261021");
		var target = GAME.resolve("saves/g1_validation_20261021");
		if (Files.exists(target)) {
			return;
		}
		try (var paths = Files.walk(source)) {
			for (var path : paths.toList()) {
				if (Files.isSymbolicLink(path)) {
					throw new IOException("验收存档不能含符号链接：" + path);
				}
				var destination = target.resolve(source.relativize(path)).normalize();
				if (!destination.startsWith(target)) {
					throw new IOException("验收存档路径越界。");
				}
				if (Files.isDirectory(path)) {
					Files.createDirectories(destination);
				} else {
					Files.copy(path, destination);
				}
			}
		}
	}

	private static byte[] recordedBytes(byte[] raw, String expected) throws Exception {
		if (hash(raw).equals(expected)) {
			return raw;
		}
		var text = new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n");
		for (var candidate : new String[]{text, text.replace("\n", "\r\n")}) {
			var bytes = candidate.getBytes(StandardCharsets.UTF_8);
			if (hash(bytes).equals(expected)) {
				return bytes;
			}
		}
		throw new IOException("归档不匹配已冻结字节：" + expected);
	}

	private static byte[] read(ZipFile archive, String name) throws IOException {
		try (var input = archive.getInputStream(archive.getEntry(name))) {
			return input.readAllBytes();
		}
	}

	private static byte[] member(byte[] zip, String name) throws IOException {
		try (var input = new ZipInputStream(new ByteArrayInputStream(zip))) {
			for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
				if (entry.getName().equals(name)) {
					return input.readAllBytes();
				}
			}
		}
		throw new IOException("包条目缺失：" + name);
	}

	private static void write(Path target, byte[] bytes) throws IOException {
		Files.createDirectories(target.getParent());
		if (Files.exists(target)) {
			assertArrayEquals(bytes, Files.readAllBytes(target), target.toString());
		} else {
			Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
		}
	}

	private static String hash(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
