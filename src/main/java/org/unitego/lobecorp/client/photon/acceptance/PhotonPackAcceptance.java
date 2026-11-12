package org.unitego.lobecorp.client.photon.acceptance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffect;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Definition;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDependencyUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument.Kind;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;
import org.unitego.lobecorp.util.resource.DevelopmentResourceUtil;
import org.unitego.lobecorp.util.resource.PhotonAcceptancePackResourcesUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/// 显式开发验收入口：只管理本次临时实例，来源/字节与运行状态分开记录，不替代视觉验收。
@SuppressWarnings({"unused", "resource"}) // 显式Debug入口；命名运行时由所属PhotonGeoEffect统一释放。
public class PhotonPackAcceptance {
	private static final String GAME_NAME = "g1_resource_acceptance_20261021";
	private static final Map<String, PhotonGeoEffect> PLAYING = new LinkedHashMap<>();
	private static CompletableFuture<Void> reload;
	private static Path selected;
	private static JsonObject report;
	private static CompletableFuture<Void> batch;
	private static JsonArray batchResults = new JsonArray();

	public static JsonObject select(String name) throws Exception {
		requireEnvironment();
		if (reload != null && !reload.isDone()) {
			throw new IllegalStateException("前一个资源重载未完成。");
		}
		closeInstances();
		var root = FXPacks.getFxPacksDir().toPath().toAbsolutePath().normalize();
		selected = root.resolve(name).normalize();
		if (!selected.getParent().equals(root) || !Files.isRegularFile(selected)) {
			throw new IllegalArgumentException("验收包必须位于当前独立运行目录。");
		}
		System.setProperty(DevelopmentResourceUtil.ISOLATED_FX_PACK_PROPERTY, "fxpack/" + name);
		var mc = Minecraft.getInstance();
		mc.getResourcePackRepository().reload();
		reload = mc.reloadResourcePacks();
		report = new JsonObject();
		report.addProperty("package", selected.toString());
		report.addProperty("expectedPackId", "fxpack/" + name);
		report.addProperty("gameDirectory", Platform.getGamePath().toAbsolutePath().normalize().toString());
		report.addProperty("developmentSourceDisabled", true);
		report.addProperty("visualAccepted", false);
		report.add("entries", new JsonArray());
		report.add("runtime", new JsonArray());
		return status();
	}

	public static JsonObject status() {
		requireEnvironment();
		var result = new JsonObject();
		result.addProperty("ready", reload != null && reload.isDone());
		result.addProperty("failed", reload != null && reload.isCompletedExceptionally());
		return result;
	}

	public static JsonObject begin(String forbiddenId) throws Exception {
		requireEnvironment();
		if (reload == null || !reload.isDone() || reload.isCompletedExceptionally() || !PLAYING.isEmpty()) {
			throw new IllegalStateException("资源重载未成功完成或旧实例未释放。");
		}
		var mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			throw new IllegalStateException("验收需要已加载世界。");
		}
		var expected = report.get("expectedPackId").getAsString();
		var packs = mc.getResourceManager().listPacks().map(PackResources::packId).toList();
		var fxPacks = packs.stream().filter(id -> id.startsWith("fxpack/")).toList();
		if (!fxPacks.equals(List.of(expected)) || packs.contains("lobecorp_development_run")) {
			throw new IllegalStateException("实际挂载集合不满足唯一包隔离：" + packs);
		}
		report.add("mountedPacks", new Gson().toJsonTree(packs));
		var scope = PhotonAcceptancePackResourcesUtil.scope();
		if (scope == null || !scope.selectedPackId().equals(expected)) {
			throw new IllegalStateException("验收资源过滤快照缺失或已过期。");
		}
		report.add("isolationOrigins", new Gson().toJsonTree(scope.origins()));
		report.add("isolatedIds", new Gson().toJsonTree(scope.hiddenIds().stream().map(Identifier::toString).sorted().toList()));
		var canary = Identifier.parse(forbiddenId);
		boolean canaryAbsent = mc.getResourceManager().getResource(canary).isEmpty();
		report.addProperty("canary", forbiddenId);
		report.addProperty("canaryAbsent", canaryAbsent);
		if (!canaryAbsent) {
			throw new IllegalStateException("隔离负向资源仍可解析：" + canary);
		}
		var document = PhotonResourceReader.read(selected);
		report.addProperty("packageSha256", document.sha256());
		var sources = PhotonResourceDependencyUtil.sources(document, selected.getFileName().toString());
		var dependencies = PhotonResourceDependencyUtil.resolve(sources,
				sources.stream().map(PhotonResourceDependencyUtil.Source::key).toList(), PhotonResourceLimits.DEFAULT);
		var dependencyReport = new JsonObject();
		dependencyReport.addProperty("closed", dependencies.closed());
		dependencyReport.add("edges", new Gson().toJsonTree(dependencies.edges()));
		dependencyReport.add("cycles", new Gson().toJsonTree(dependencies.cycles()));
		report.add("dependencyReport", dependencyReport);
		var entries = new JsonArray();
		report.add("entries", entries);
		var definitions = new ArrayList<Identifier>();
		for (var entry : document.entries()) {
			var name = entry.origin().substring(document.origin().length() + 2);
			var item = new JsonObject();
			item.addProperty("entry", name);
			item.addProperty("sha256", entry.sha256());
			entries.add(item);
			if (name.startsWith("assets/")) {
				var split = name.indexOf('/', 7);
				if (split < 0) {
					throw new IllegalArgumentException("资源包条目缺少命名空间：" + name);
				}
				var id = Identifier.fromNamespaceAndPath(name.substring(7, split), name.substring(split + 1));
				var resource = mc.getResourceManager().getResourceOrThrow(id);
				item.addProperty("sourcePackId", resource.sourcePackId());
				try (var input = resource.open()) {
					var actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
					item.addProperty("actualSha256", actual);
					if (!expected.equals(resource.sourcePackId()) || !actual.equals(entry.sha256())) {
						throw new IllegalStateException("资源来源或字节不一致：" + id);
					}
				}
				if (entry.kind() == Kind.FX
						|| entry.kind() == Kind.MODEL_FX) {
					var path = id.getPath();
					definitions.add(Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring(3, path.lastIndexOf('.'))));
				}
			}
		}
		FXHelper.clearCache();
		report.add("runtime", startDefinitions(definitions, mc));
		report.addProperty("beganAtGameTime", mc.level.getGameTime());
		return report.deepCopy();
	}

	private static JsonArray startDefinitions(List<Identifier> definitions, Minecraft mc) {
		var player = mc.player;
		var level = mc.level;
		if (player == null || level == null) {
			throw new IllegalStateException("验收世界已退出。");
		}
		var started = new JsonArray();
		try {
			for (var id : definitions) {
				var state = new JsonObject();
				state.addProperty("fxId", id.toString());
				started.add(state);
				try {
					var definition = FXHelper.getFX(id);
					if (definition == null) {
						throw new IllegalStateException("原生定义加载失败。");
					}
					var effect = new PhotonGeoEffect(definition, level);
					PLAYING.put(id.toString(), effect);
					effect.anchor(player.position().add(3, 1, 3).toVector3f(), new Quaternionf(), new Vector3f(1));
					effect.start();
					var named = ((PhotonNamedAccess) effect.runtime()).lobecorp$namedRuntime();
					if (named != null) {
						var names = named.library().animations().stream().map(Definition::name).toList();
						for (var name : List.of("start", "charge_start", "laser_start")) {
							if (names.contains(name)) {
								named.play(name, 10, true, Map.of());
							}
						}
					}
					state.addProperty("started", true);
					state.addProperty("objectCount", effect.runtime().objects.size());
				} catch (RuntimeException invalid) {
					state.addProperty("started", false);
					state.addProperty("error", invalid.toString());
					var failed = PLAYING.get(id.toString());
					if (failed != null) {
						failed.close();
						PLAYING.remove(id.toString(), failed);
					}
				}
			}
		} catch (Exception failure) {
			closeInstances();
			throw failure;
		}
		return started;
	}

	public static JsonObject finish(String reportName) throws Exception {
		requireEnvironment();
		try {
			var mc = Minecraft.getInstance();
			report.addProperty("finishedAtGameTime", mc.level == null ? -1 : mc.level.getGameTime());
			for (var value : report.getAsJsonArray("runtime")) {
				var state = value.getAsJsonObject();
				var effect = PLAYING.get(state.get("fxId").getAsString());
				if (effect != null) {
					state.addProperty("aliveAtFinish", effect.isAlive());
					var failures = new JsonArray();
					for (var object : effect.runtime().objects.values()) {
						if (object instanceof PhotonChildSpawner source) {
							var failure = source.failure();
							if (failure != null && !failure.isBlank()) {
								failures.add(source.getName() + ":" + failure);
							}
						}
					}
					state.add("childFailures", failures);
				}
			}
		} finally {
			closeInstances();
		}
		report.addProperty("closedOwnedInstances", PLAYING.isEmpty());
		var root = Platform.getGamePath().toAbsolutePath().normalize().resolve("g1_reports");
		var target = root.resolve(reportName).normalize();
		if (!target.getParent().equals(root) || !reportName.endsWith(".json")) {
			throw new IllegalArgumentException("验收报告路径无效。");
		}
		Files.createDirectories(root);
		Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardOpenOption.CREATE_NEW);
		var result = new JsonObject();
		result.addProperty("reportPath", target.toString());
		result.addProperty("entries", report.getAsJsonArray("entries").size());
		result.addProperty("definitions", report.getAsJsonArray("runtime").size());
		result.addProperty("closedOwnedInstances", true);
		return result;
	}

	/// 有限批次由客户端执行器串行推进，避免调试器持有大型返回对象；延迟不阻塞渲染线程。
	public static String runBatch(List<String> names, String prefix) {
		requireEnvironment();
		if (batch != null && !batch.isDone()) {
			throw new IllegalStateException("上一个验收批次未完成。");
		}
		if (!prefix.matches("[a-z0-9_]+") || names.isEmpty()) {
			throw new IllegalArgumentException("验收批次名称或文件列表无效。");
		}
		batchResults = new JsonArray();
		var mc = Minecraft.getInstance();
		batch = CompletableFuture.completedFuture(null);
		for (int index = 0; index < names.size(); index++) {
			var name = names.get(index);
			var reportName = prefix + "_" + index + ".json";
			batch = batch.thenComposeAsync(_ -> runCase(name, reportName), mc);
		}
		return "scheduled=" + names.size();
	}

	public static String batchStatus() {
		requireEnvironment();
		return "done=" + (batch != null && batch.isDone()) + ",failed="
				+ (batch != null && batch.isCompletedExceptionally()) + ",recorded=" + batchResults.size();
	}

	private static CompletableFuture<Void> runCase(String name, String reportName) {
		try {
			select(name);
		} catch (Exception invalid) {
			return CompletableFuture.failedFuture(invalid);
		}
		var mc = Minecraft.getInstance();
		return reload.handleAsync((_, failure) -> {
			try {
				if (failure != null) {
					throw new IllegalStateException("资源重载失败。", failure);
				}
				var canary = name.contains("queen_star_beam")
						? "lobecorp:fx/the_queen_of_hatred/star_beam_spell.fx"
						: "lobecorp:fx/the_queen_of_hatred/queen_star_beam.fx";
				begin(canary);
			} catch (Exception invalid) {
				report.addProperty("beginFailure", invalid.toString());
				if (!report.has("runtime")) {
					report.add("runtime", new JsonArray());
				}
				closeInstances();
			}
			return null;
		}, mc).thenRunAsync(() -> {
			try {
				batchResults.add(finish(reportName));
			} catch (Exception invalid) {
				throw new IllegalStateException("验收报告或关闭失败：" + reportName, invalid);
			}
		}, CompletableFuture.delayedExecutor(2, java.util.concurrent.TimeUnit.SECONDS, mc));
	}

	private static void closeInstances() {
		RuntimeException failure = null;
		for (var entry : List.copyOf(PLAYING.entrySet())) {
			try {
				entry.getValue().close();
				PLAYING.remove(entry.getKey(), entry.getValue());
			} catch (RuntimeException invalid) {
				if (failure == null) {
					failure = invalid;
				} else {
					failure.addSuppressed(invalid);
				}
			}
		}

		if (failure != null) {
			throw failure;
		}
	}

	private static void requireEnvironment() {
		if (!Minecraft.getInstance().isSameThread() || !SharedConstants.IS_RUNNING_IN_IDE
				|| !Boolean.getBoolean(DevelopmentResourceUtil.DISABLE_SOURCE_PROPERTY)
				|| DevelopmentResourceUtil.sourceDirectory() != null
				|| !Platform.getGamePath().toAbsolutePath().normalize().getFileName().toString().equals(GAME_NAME)) {
			throw new IllegalStateException("仅允许在明确隔离的开发验收客户端调用。");
		}
	}
}
