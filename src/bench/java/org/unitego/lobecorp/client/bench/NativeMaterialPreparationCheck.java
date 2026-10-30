package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.FilePath;
import com.lowdragmc.lowdraglib2.editor.resource.PackResourceManager;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.photon.client.shadergraph.ShaderGraph;
import com.lowdragmc.photon.gui.editor.resource.ShaderGraphResource;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.util.photon.material.LazyBuiltinResourceProvider;
import org.unitego.lobecorp.util.photon.material.PhotonMaterialPreparationUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/// 仅由主线在主窗口归档后调用；临时 provider 不加入任何列表，不创建 ResourceInstance。
public class NativeMaterialPreparationCheck {
	public static final String RELOAD_CHECK_PROPERTY = "lobecorp.photon.bench.materialReloadCheck";
	private static final String DEFAULT_NAME = "default_particle";
	private final JsonArray cases = new JsonArray();
	private final List<Throwable> failures = new ArrayList<>();
	private final JsonObject report = new JsonObject();

	public static void captureMainWindowCounters(BenchClientContext context, String label) throws Exception {
		var snapshot = counters(PhotonMaterialPreparationUtil.counters());
		snapshot.addProperty("phase", "after_main_window_archive_before_native_checks");
		snapshot.addProperty("main_window_sample", false);
		snapshot.addProperty("nano_time", System.nanoTime());
		snapshot.addProperty("eager_builtin_control", Boolean.getBoolean(PhotonMaterialPreparationUtil.EAGER_BUILTIN_PROPERTY));
		snapshot.addProperty("scope", "cumulative production counters through archive; excludes subsequent native checks");
		context.artifacts().write(label + "-material-preparation-main-window.json", "application/json", snapshot.toString());
	}

	public static void verify(BenchClientContext context, String label) throws Exception {
		var check = new NativeMaterialPreparationCheck();
		var before = PhotonMaterialPreparationUtil.counters();
		check.report.addProperty("phase", "verify_after_main_window_archive");
		check.report.addProperty("main_window_sample", false);
		check.report.addProperty("eager_builtin_control", Boolean.getBoolean(PhotonMaterialPreparationUtil.EAGER_BUILTIN_PROPERTY));
		check.report.addProperty("start_nano_time", System.nanoTime());
		check.report.add("production_counters_before_checker", counters(before));
		// 只借用真实单例；如主窗口从未访问，它的首次初始化另计入 checker，不冒充窗口收益。
		var instance = ShaderGraphResource.INSTANCE.getResourceInstance();
		check.report.add("counters_after_borrowing_instance", counters(PhotonMaterialPreparationUtil.counters()));
		check.run(instance);
		check.report.add("cases", check.cases);
		check.report.add("counters_after_checker", counters(PhotonMaterialPreparationUtil.counters()));
		check.report.addProperty("passed", check.failures.isEmpty());
		check.report.addProperty("end_nano_time", System.nanoTime());
		check.report.addProperty("scope", "native provider contracts and NBT; no GL compile, rendering or FPS measurement");
		check.report.addProperty("reload_scope", "opt-in native PackResourceManager invalidation hook; not a full Minecraft resource reload");
		context.artifacts().write(label + "-material-preparation-native.json", "application/json", check.report.toString());
		if (!check.failures.isEmpty()) {
			var failure = new IllegalStateException("material_preparation_native_check_failed:" + check.failures.size());
			check.failures.forEach(failure::addSuppressed);
			throw failure;
		}
	}

	private void run(ResourceInstance<CompoundTag> instance) {
		runProviderChecks(instance);
		runValueChecks(instance);
		runReloadCheck(instance);
	}

	private void runProviderChecks(ResourceInstance<CompoundTag> instance) {
		runCase("production_provider_installed", () -> {
			var providers = instance.getBuiltinProviders().getOrDefault(BuiltinResourceProvider.TYPE, List.of());
			var lazyInstalled = providers.stream().anyMatch(LazyBuiltinResourceProvider.class::isInstance);
			equal(!Boolean.getBoolean(PhotonMaterialPreparationUtil.EAGER_BUILTIN_PROPERTY), lazyInstalled);
			require(instance.getBuiltinProviders().values().stream().flatMap(List::stream)
					.anyMatch(provider -> provider.getName().equals("global")));
		});
		runCase("real_file_cold_warm_and_cache_clear_without_default", () -> fileLookup(instance));
		runCase("unsupported_file_and_loaded_peek_do_not_initialize", () -> {
			var calls = new AtomicInteger();
			var lazy = provider(instance, calls);
			var file = filePath();
			require(!lazy.addResource(lazy.createSubPath(DEFAULT_NAME), null));
			require(!lazy.hasResource(file));
			equal(null, lazy.getResource(file));
			require(!lazy.addResource(file, new CompoundTag()));
			equal(null, lazy.removeResource(file));
			equal(null, lazy.getLoadedResource(lazy.createSubPath(DEFAULT_NAME)));
			equal(0, calls.get());
			require(!lazy.initialized());
		});
		runCase("builtin_has_get_warm_iterator_and_mutable_map", () -> {
			var calls = new AtomicInteger();
			var lazy = provider(instance, calls);
			var path = lazy.createSubPath(DEFAULT_NAME);
			require(lazy.hasResource(path));
			var value = lazy.getResource(path);
			require(value != null);
			require(value == lazy.getLoadedResource(path));
			require(value == lazy.getResource(path));
			require(lazy.iterator().next().getValue() == value);
			var map = lazy.getContents();
			require(map == lazy.getContents());
			equal(1, map.size());
			map.remove(path);
			require(!lazy.hasResource(path));
			equal(1, calls.get());
		});
		runCase("cold_enumeration_and_contents_generate_once", () -> {
			var iteratorCalls = new AtomicInteger();
			var iteratorProvider = provider(instance, iteratorCalls);
			require(iteratorProvider.iterator().hasNext());
			equal(1, iteratorCalls.get());
			var mapCalls = new AtomicInteger();
			var mapProvider = provider(instance, mapCalls);
			equal(1, mapProvider.getContents().size());
			equal(1, mapCalls.get());
		});
		runCase("direct_string_and_path_mutation_preserve_native_returns", () -> {
			var calls = new AtomicInteger();
			var lazy = provider(instance, calls);
			var replacement = new CompoundTag();
			require(lazy.addResource(DEFAULT_NAME, replacement));
			require(lazy.getResource(lazy.createSubPath(DEFAULT_NAME)) == replacement);
			require(lazy.removeResource(DEFAULT_NAME) == replacement);
			equal(null, lazy.removeResource(DEFAULT_NAME));
			equal(1, calls.get());
			var removeCalls = new AtomicInteger();
			var coldRemove = provider(instance, removeCalls);
			require(coldRemove.removeResource(coldRemove.createSubPath(DEFAULT_NAME)) != null);
			equal(1, removeCalls.get());
		});
	}

	private void runValueChecks(ResourceInstance<CompoundTag> instance) {
		runCase("native_builder_exact_nbt_on_same_graph", () -> {
			// 原生 Model 的 uid 随机生成；使用同一张原生图作精确 NBT 对照，不丢弃任何字段。
			var graph = ShaderGraphResource.INSTANCE.createGraph();
			var pinned = new PinnedGraphResource(graph);
			var nativeProvider = new BuiltinResourceProvider<>("built-in", instance);
			pinned.buildBuiltin(nativeProvider);
			var lazy = new LazyBuiltinResourceProvider<>("built-in", instance, pinned::buildBuiltin);
			var expected = ShaderGraphResource.INSTANCE.serializeGraph(graph);
			equal(expected, nativeProvider.getResource(nativeProvider.createSubPath(DEFAULT_NAME)));
			equal(expected, lazy.getResource(lazy.createSubPath(DEFAULT_NAME)));
		});
		runCase("native_copy_builtin_value_remains_independent", () -> {
			var lazy = provider(instance, new AtomicInteger());
			var path = lazy.createSubPath(DEFAULT_NAME);
			var source = lazy.getResource(path);
			require(lazy.canCopy(path));
			var copy = source.copy();
			equal(source, copy);
			require(source != copy);
			copy.putString("copy_only", "changed");
			require(!source.contains("copy_only"));
		});
		runCase("failure_does_not_publish_partial_and_retries", () -> {
			var attempts = new AtomicInteger();
			var lazy = new LazyBuiltinResourceProvider<>("built-in", instance, staging -> {
				staging.addResource("partial", new CompoundTag());
				if (attempts.incrementAndGet() == 1) {
					throw new IllegalStateException("fixture_failure");
				}

				ShaderGraphResource.INSTANCE.buildBuiltin(staging);
			});
			try {
				lazy.iterator();
				throw new AssertionError("expected fixture failure");
			} catch (IllegalStateException expected) {
				equal("fixture_failure", expected.getMessage());
			}
			require(!lazy.initialized());
			equal(null, lazy.getLoadedResource(lazy.createSubPath("partial")));
			require(lazy.hasResource(lazy.createSubPath(DEFAULT_NAME)));
			equal(2, attempts.get());
		});
	}

	private void runReloadCheck(ResourceInstance<CompoundTag> instance) {
		if (Boolean.getBoolean(RELOAD_CHECK_PROPERTY)) {
			runCase("real_pack_invalidation_hook_preserves_builtin_and_refreshes_file", () -> {
				var path = filePath();
				var oldFile = instance.getResource(path);
				require(oldFile != null);
				var builtin = instance.getResource(BuiltinResourceProvider.TYPE.createFullPath(DEFAULT_NAME));
				require(builtin != null);
				var before = PhotonMaterialPreparationUtil.counters();
				PackResourceManager.INSTANCE.onResourceManagerReload(Minecraft.getInstance().getResourceManager());
				var reloaded = instance.getResource(path);
				require(reloaded != null && reloaded != oldFile);
				equal(oldFile, reloaded);
				require(builtin == instance.getResource(BuiltinResourceProvider.TYPE.createFullPath(DEFAULT_NAME)));
				equal(before.defaultsGenerated(), PhotonMaterialPreparationUtil.counters().defaultsGenerated());
			});
		} else {
			report.addProperty("reload_check", "not_run_requires_" + RELOAD_CHECK_PROPERTY);
		}
	}

	private void fileLookup(ResourceInstance<CompoundTag> instance) {
		var before = PhotonMaterialPreparationUtil.counters();
		var path = filePath();
		var first = instance.getResource(path);
		require(first != null);
		require(first == instance.getResource(path));
		instance.clearCache();
		var afterCacheClear = instance.getResource(path);
		require(afterCacheClear != null);
		equal(first, afterCacheClear);
		var after = PhotonMaterialPreparationUtil.counters();
		equal(before.defaultsGenerated(), after.defaultsGenerated());
		var coldProviders = instance.getBuiltinProviders().getOrDefault(BuiltinResourceProvider.TYPE, List.of());
		if (coldProviders.stream().anyMatch(provider -> provider instanceof LazyBuiltinResourceProvider<?> lazy && !lazy.initialized())) {
			equal(3L, after.fileRequestsWithoutDefault() - before.fileRequestsWithoutDefault());
		}
		report.addProperty("file_requests_without_default_delta_in_checker",
				after.fileRequestsWithoutDefault() - before.fileRequestsWithoutDefault());
		report.addProperty("file_lookup_cold_status", "first_checker_access_may_already_be_warm_from_main_window");
	}

	private static FilePath filePath() {
		return new FilePath(Identifier.parse("lobecorp:shaders/photon/queen_star_beam/beam/pink_body.shader_graph.nbt"));
	}

	private static LazyBuiltinResourceProvider<CompoundTag> provider(ResourceInstance<CompoundTag> instance,
	                                                                 AtomicInteger calls) {
		return new LazyBuiltinResourceProvider<>("built-in", instance, staging -> {
			calls.incrementAndGet();
			ShaderGraphResource.INSTANCE.buildBuiltin(staging);
		});
	}

	private void runCase(String name, Runnable action) {
		var result = new JsonObject();
		result.addProperty("case", name);
		try {
			action.run();
			result.addProperty("passed", true);
		} catch (RuntimeException | AssertionError | LinkageError failure) {
			failures.add(failure);
			result.addProperty("passed", false);
			result.addProperty("failure", failure.toString());
		}
		cases.add(result);
	}

	private static JsonObject counters(PhotonMaterialPreparationUtil.Counters counters) {
		var json = new JsonObject();
		json.addProperty("providers_created", counters.providersCreated());
		json.addProperty("defaults_generated", counters.defaultsGenerated());
		json.addProperty("file_requests_without_default", counters.fileRequestsWithoutDefault());
		return json;
	}

	private static void require(boolean condition) {
		if (!condition) {
			throw new AssertionError("material_preparation_contract");
		}
	}

	private static void equal(Object expected, Object actual) {
		if (!Objects.equals(expected, actual)) {
			throw new AssertionError("expected=" + expected + "; actual=" + actual);
		}
	}

	private static class PinnedGraphResource extends ShaderGraphResource {
		private final ShaderGraph graph;

		private PinnedGraphResource(ShaderGraph graph) {
			this.graph = graph;
		}

		@Override
		public ShaderGraph createGraph() {
			return graph;
		}
	}
}
