package org.unitego.lobecorp.util.photon.compat;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/// 只在调用方明确给出的来源集合内解析；同名各版本保留，首个来源为本报告声明的优先项。
public class PhotonResourceDependencyUtil {
	public record Source(String key, String origin, String sha256, PhotonResourceDocument document) {
	}

	public record Edge(String from, String path, String reference, String status, List<String> candidates) {
		public Edge {
			candidates = List.copyOf(candidates);
		}
	}

	public record Report(Map<String, List<Source>> versions, List<Edge> edges, Set<String> reachable,
	                     List<List<String>> cycles, boolean closed) {
		public Report {
			var copied = new LinkedHashMap<String, List<Source>>();
			versions.forEach((key, value) -> copied.put(key, List.copyOf(value)));
			versions = Collections.unmodifiableMap(copied);
			edges = List.copyOf(edges);
			reachable = Set.copyOf(reachable);
			cycles = cycles.stream().map(List::copyOf).toList();
		}
	}

	/// 包内逻辑路径由ZIP条目决定，目录文件的逻辑路径由调用方显式声明。
	public static List<Source> sources(PhotonResourceDocument document, String logicalPath) {
		var result = new ArrayList<Source>();
		if (document.entries().isEmpty()) {
			result.add(new Source(normalize(logicalPath), document.origin(), document.sha256(), document));
		} else {
			for (var entry : document.entries()) {
				var origin = entry.origin();
				int separator = origin.lastIndexOf("!/");
				result.addAll(sources(entry, separator < 0 ? logicalPath : origin.substring(separator + 2)));
			}
		}

		return List.copyOf(result);
	}

	public static Report resolve(List<Source> sources, List<String> roots, PhotonResourceLimits limits) throws IOException {
		var versions = new LinkedHashMap<String, List<Source>>();
		for (var source : sources) {
			versions.computeIfAbsent(normalize(source.key()), _ -> new ArrayList<>()).add(source);
			if (versions.size() > limits.entries()) {
				throw new IOException("依赖图超出节点预算。");
			}
		}

		var edges = new ArrayList<Edge>();
		var reachable = new LinkedHashSet<String>();
		var pending = new ArrayDeque<String>();
		roots.forEach(root -> pending.add(normalize(root)));
		boolean closed = true;
		while (!pending.isEmpty()) {
			var key = pending.removeFirst();
			if (!reachable.add(key)) {
				continue;
			}
			var candidates = versions.get(key);
			if (candidates == null) {
				edges.add(new Edge(key, "", key, "missing_root", List.of()));
				closed = false;
				continue;
			}

			var report = PhotonResourceInspection.inspect(candidates.getFirst().document(), limits);
			for (var reference : report.references()) {
				var matches = candidates(reference, versions);
				String status = reference.value().startsWith("built-in(") ? "builtin_unverified"
						: matches.size() == 1 ? "resolved" : matches.isEmpty() ? "unresolved" : "ambiguous";
				edges.add(new Edge(key, reference.path(), reference.value(), status, matches));
				if (status.equals("resolved")) {
					pending.add(matches.getFirst());
				} else {
					closed = false;
				}
				if (edges.size() > limits.jsonNodes()) {
					throw new IOException("依赖图超出引用预算。");
				}
			}
		}

		var cycles = cycles(edges, limits.depth());
		return new Report(versions, edges, reachable, cycles, closed && cycles.isEmpty());
	}

	private static List<String> candidates(PhotonResourceInspection.Value reference, Map<String, List<Source>> versions) {
		String text = reference.value();
		if (text.startsWith("built-in(")) {
			return List.of();
		}
		if (text.startsWith("file(") && text.endsWith(")")) {
			text = text.substring(5, text.length() - 1);
		}
		var keys = new LinkedHashSet<String>();
		keys.add(normalize(text));
		int colon = text.indexOf(':');
		if (colon > 0) {
			String namespace = text.substring(0, colon);
			String path = text.substring(colon + 1);
			keys.add(normalize("assets/" + namespace + "/" + path));
			if (reference.path().endsWith("/fxLocation") || reference.path().endsWith("/effectId")) {
				keys.add(normalize("assets/" + namespace + "/fx/" + path + ".fx"));
				keys.add(normalize("assets/" + namespace + "/fx/" + path + ".mfx"));
			}
		}

		return keys.stream().filter(versions::containsKey).toList();
	}

	private static List<List<String>> cycles(List<Edge> edges, int maxDepth) throws IOException {
		var graph = new LinkedHashMap<String, List<String>>();
		for (var edge : edges) {
			if (edge.status().equals("resolved")) {
				graph.computeIfAbsent(edge.from(), _ -> new ArrayList<>()).add(edge.candidates().getFirst());
			}
		}
		var result = new ArrayList<List<String>>();
		var visited = new LinkedHashSet<String>();
		for (var key : graph.keySet()) {
			visit(key, graph, visited, new ArrayList<>(), result, maxDepth);
		}
		return List.copyOf(result);
	}

	private static void visit(String key, Map<String, List<String>> graph, Set<String> visited,
	                          List<String> path, List<List<String>> cycles, int maxDepth) throws IOException {
		int previous = path.indexOf(key);
		if (previous >= 0) {
			var cycle = new ArrayList<>(path.subList(previous, path.size()));
			cycle.add(key);
			cycles.add(cycle);
			return;
		}
		if (!visited.add(key)) {
			return;
		}
		if (path.size() >= maxDepth) {
			throw new IOException("依赖图超过深度预算。");
		}
		path.add(key);
		for (var child : graph.getOrDefault(key, List.of())) {
			visit(child, graph, visited, path, cycles, maxDepth);
		}
		path.removeLast();
	}

	public static String normalize(String key) {
		var result = key.replace('\\', '/');
		if (result.isEmpty() || result.startsWith("/") || result.contains("//") || result.indexOf(0) >= 0
				|| List.of(result.split("/")).contains("..")) {
			throw new IllegalArgumentException("资源逻辑路径无效：" + key);
		}
		return result;
	}
}
