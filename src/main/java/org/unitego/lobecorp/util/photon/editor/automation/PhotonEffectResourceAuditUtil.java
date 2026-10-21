package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDependencyUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/// 离线来源审查只读取明确指定的许可路径；不挂载包、写资源或宣告运行验收成功。
public class PhotonEffectResourceAuditUtil {
	/// 单次审查的容器上限；另受总字节、展开条目和NBT复杂度预算约束。
	private static final int MAX_SOURCES = 64;

	private record Declared(Path path, String logicalPath) {
	}

	public static JsonObject audit(JsonObject request) throws Exception {
		return prepare(request).call();
	}

	public static Callable<JsonObject> prepare(JsonObject request) throws Exception {
		var declared = PhotonEffectJsonUtil.array(request, "sources");
		if (declared.isEmpty() || declared.size() > MAX_SOURCES) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "sources");
		}

		var captured = new ArrayList<Declared>();
		for (var value : declared) {
			if (!value.isJsonObject()) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "sources");
			}
			var entry = value.getAsJsonObject();
			captured.add(new Declared(PhotonEffectFileUtil.source(entry, ""), PhotonEffectJsonUtil.string(entry, "logicalPath")));
		}
		var selectedRoots = new ArrayList<String>();
		if (request.has("roots")) {
			for (var value : PhotonEffectJsonUtil.array(request, "roots")) {
				if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
					throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "roots");
				}
				selectedRoots.add(value.getAsString());
			}
			if (selectedRoots.isEmpty()) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "roots");
			}
		}
		var sources = List.copyOf(captured);
		var roots = List.copyOf(selectedRoots);
		return () -> audit(sources, roots);
	}

	private static JsonObject audit(List<Declared> declared, List<String> selectedRoots) throws Exception {
		var limits = PhotonResourceLimits.DEFAULT;
		var sources = new ArrayList<PhotonResourceDependencyUtil.Source>();
		long sourceBytes = 0;
		long expandedBytes = 0;
		for (var entry : declared) {
			var path = entry.path();
			sourceBytes = Math.addExact(sourceBytes, Files.size(path));
			if (sourceBytes > limits.sourceBytes()) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "source byte budget");
			}
			var document = PhotonResourceReader.read(path, limits);
			var added = PhotonResourceDependencyUtil.sources(document, entry.logicalPath());
			for (var source : added) {
				expandedBytes = Math.addExact(expandedBytes, source.document().sourceBytes());
				if (expandedBytes > limits.unpackedBytes()) {
					throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "expanded byte budget");
				}
			}
			sources.addAll(added);
		}

		var roots = new ArrayList<>(selectedRoots);
		if (roots.isEmpty()) {
			sources.forEach(source -> roots.add(source.key()));
		}
		if (roots.isEmpty()) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "roots");
		}
		var report = PhotonResourceDependencyUtil.resolve(sources, roots, limits);
		var result = new JsonObject();
		result.addProperty("closed", report.closed());
		result.addProperty("runtimeAccepted", false);
		result.addProperty("sourcePolicy", "declared_only_first_source_wins");
		result.addProperty("developmentFallback", false);
		result.addProperty("sourceBytes", sourceBytes);
		result.addProperty("expandedBytes", expandedBytes);
		var versions = new JsonArray();
		report.versions().forEach((key, values) -> {
			for (int index = 0; index < values.size(); index++) {
				var entry = new JsonObject();
				entry.addProperty("logicalPath", key);
				entry.addProperty("origin", values.get(index).origin());
				entry.addProperty("sha256", values.get(index).sha256());
				entry.addProperty("selected", index == 0);
				entry.addProperty("reachable", report.reachable().contains(key));
				versions.add(entry);
			}
		});
		result.add("versions", versions);
		var edges = new JsonArray();
		for (var edge : report.edges()) {
			var entry = new JsonObject();
			entry.addProperty("from", edge.from());
			entry.addProperty("field", edge.path());
			entry.addProperty("reference", edge.reference());
			entry.addProperty("status", edge.status());
			var candidates = new JsonArray();
			edge.candidates().forEach(candidates::add);
			entry.add("candidates", candidates);
			edges.add(entry);
		}
		result.add("edges", edges);
		var cycles = new JsonArray();
		report.cycles().forEach(values -> {
			var cycle = new JsonArray();
			values.forEach(cycle::add);
			cycles.add(cycle);
		});
		result.add("cycles", cycles);
		return result;
	}
}
