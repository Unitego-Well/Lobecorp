package org.unitego.lobecorp.client.photon.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;

import java.io.UncheckedIOException;

import com.lowdragmc.photon.gui.editor.FXProject;
import com.lowdragmc.photon.client.gameobject.emitter.data.fixer.PhotonFXProjectDataFixer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/// 仅准备显式登记的自有组件数据；不持有世界、实体或运行租约。
@NullMarked
public class PhotonOwnedEffectResources extends ContextAwareReloadListener implements PreparableReloadListener {
	private static final Map<Identifier, Map<String, Identifier>> REGISTRATIONS = new LinkedHashMap<>();
	private static final Map<SourceKey, Prepared> AUTHORING = new ConcurrentHashMap<>();
	private static final Map<SourceKey, Map<Identifier, String>> DEPENDENCY_SOURCES = new LinkedHashMap<>();
	private static volatile Map<Identifier, Result> exported = Map.of();
	// 设备失败不丢弃已读取的独占CPU快照；对外Result仍只有READY携带成果。
	private static Map<Identifier, Prepared> exportedDefinitions = Map.of();
	private static final Map<SourceKey, DeviceRetry> DEVICE_RETRIES = new LinkedHashMap<>();
	private static final long DEVICE_RETRY_DELAY_NANOS = 1_000_000_000L;

	private record DeviceRetry(String stamp, long attemptedAt) {
	}
	private static final Map<SourceKey, Map<Identifier, FX>> RESOLVED = new LinkedHashMap<>();
	private static final Map<Identifier, Map<String, Identifier>> SNAPSHOT_COMPONENTS = new LinkedHashMap<>();
	private static @Nullable SourceKey resolvingSource;
	private static volatile boolean preparing;
	private static volatile String reloadFailure = "";
	private static volatile long generation;
	private static int nativeFXGeneration = -1;
	private static final Map<Identifier, Consumer<Prepared>> DEFINITION_PREPARERS = new LinkedHashMap<>();
	private static final Map<Identifier, Consumer<SourceKey>> DEFINITION_INVALIDATORS = new LinkedHashMap<>();

	public static synchronized void registerDefinitionPreparation(Identifier id, Consumer<Prepared> prepare, Consumer<SourceKey> invalidate) {
		DEFINITION_PREPARERS.putIfAbsent(id, prepare);
		DEFINITION_INVALIDATORS.putIfAbsent(id, invalidate);
	}

	/// 在客户端使用当前有效资源包视图恢复独立定义；不会建立运行实例。
	public static FX resolveDefinition(Identifier id, SourceKey source) {
		requireClientThread();
		boolean scoped = source.equals(resolvingSource);
		var cache = scoped ? RESOLVED.computeIfAbsent(source, _ -> new LinkedHashMap<>()) : null;
		if (cache != null && cache.containsKey(id)) {
			return cache.get(id);
		}
		try {
			var component = readComponent(Minecraft.getInstance().getResourceManager(), id);
			DEPENDENCY_SOURCES.computeIfAbsent(source, _ -> new LinkedHashMap<>()).put(id,
					component.sourcePack() + ":" + component.resource());
			var tag = component.definition();
			int version = tag.getIntOr("version", FXProject.VERSION);
			if (version > FXProject.VERSION) {
				throw new IllegalArgumentException("子特效版本高于当前Photon：" + id);
			}
			if (version < FXProject.VERSION) {
				var wrapper = new CompoundTag();
				wrapper.put("fx", tag);
				tag = PhotonFXProjectDataFixer.INSTANCE.applyFixes(version, FXProject.VERSION, wrapper).getCompoundOrEmpty("fx");
			}
			var result = new FX();
			result.deserializeNBT(Platform.getFrozenRegistry(), tag);
			result.setFxLocation(id);
			if (scoped) {
				for (var object : result.getFxData().objects()) {
					if (object instanceof PhotonNamedController controller) {
						controller.preparePlaybackDefinition();
						PhotonNamedController.compileDefinition(controller.definition(), controller.source()).install(controller);
					}
				}
			}
			if (cache != null) {
				cache.put(id, result);
			}
			return result;
		} catch (IOException failure) {
			throw new UncheckedIOException("自有子资源恢复失败：" + id, failure);
		}
	}

	private static void prepareDefinitions(Prepared prepared) {
		var prepare = DEFINITION_PREPARERS.get(prepared.source().effectId());
		if (prepare != null) {
			PhotonOwnedEffectAssets.invalidate(prepared.source());
			RESOLVED.remove(prepared.source());
			var previousSource = resolvingSource;
			resolvingSource = prepared.source();
			try {
				prepare.accept(prepared);
				if (!PhotonOwnedEffectAssets.prepared(prepared.source())) {
					throw new IllegalStateException("组成件未完成静态资产准备");
				}
			} catch (RuntimeException failure) {
				PhotonOwnedEffectAssets.invalidate(prepared.source());
				DEPENDENCY_SOURCES.remove(prepared.source());
				RESOLVED.remove(prepared.source());
				throw failure;
			} finally {
				resolvingSource = previousSource;
			}
		}
	}


	public static FX requireResolvedDefinition(SourceKey source, Identifier id) {
		requireClientThread();
		var definitions = RESOLVED.get(source);
		var definition = definitions == null ? null : definitions.get(id);
		if (definition == null || lookup(source).readiness() != Readiness.READY) {
			throw new IllegalStateException("自有子定义未准备或已失效：" + id);
		}
		return definition;
	}

	public static void registerSnapshotComponents(Identifier effectId, Map<String, Identifier> components) {
		SNAPSHOT_COMPONENTS.putIfAbsent(effectId, Map.copyOf(components));
	}

	public static Map<Identifier, String> dependencySources(SourceKey source) {
		return Map.copyOf(DEPENDENCY_SOURCES.getOrDefault(source, Map.of()));
	}

	public enum SourceKind {AUTHORING, EXPORTED_FX, PUBLISHED_PREVIEW}

	public enum Readiness {UNREGISTERED, PREPARING, READY, FAILED}

	public record SourceKey(Identifier effectId, SourceKind kind, long revision) {
		public SourceKey {
			Objects.requireNonNull(effectId);
			Objects.requireNonNull(kind);
		}
	}

	/// 每个组件保存序列化定义的独占副本；访问时再次复制，禁止修改发布成果。
	public static class Component {
		private final Identifier resource;
		private final String sourcePack;
		private final CompoundTag definition;

		private Component(Identifier resource, String sourcePack, CompoundTag definition) {
			this.resource = resource;
			this.sourcePack = sourcePack;
			this.definition = definition.copy();
		}

		public Identifier resource() {
			return resource;
		}

		public String sourcePack() {
			return sourcePack;
		}

		public CompoundTag definition() {
			return definition.copy();
		}
	}

	public record Prepared(SourceKey source, Map<String, Component> components) {
		public Prepared {
			Objects.requireNonNull(source);
			components = Map.copyOf(components);
		}
	}

	public record Result(Readiness readiness, @Nullable Prepared prepared, String failure) {
		public Result {
			Objects.requireNonNull(readiness);
			Objects.requireNonNull(failure);
			if ((readiness == Readiness.READY) != (prepared != null)) {
				throw new IllegalArgumentException("仅数据就绪状态允许且必须提供准备成果");
			}
		}
	}

	/// 应在客户端初始化、首次资源重载之前登记，值为不含 fx/ 和后缀的组件 FX ID。
	public static synchronized void register(Identifier effectId, Map<String, Identifier> components) {
		Objects.requireNonNull(effectId);
		if (components.isEmpty()) {
			throw new IllegalArgumentException("自有特效未登记组件：" + effectId);
		}

		var copy = Map.copyOf(components);
		var previous = REGISTRATIONS.putIfAbsent(effectId, copy);
		if (previous != null && !previous.equals(copy)) {
			throw new IllegalStateException("自有特效重复登记内容冲突：" + effectId);
		}
	}

	private static synchronized Map<Identifier, Map<String, Identifier>> registrations() {
		return Map.copyOf(REGISTRATIONS);
	}

	/// READY 要求组成件定义与静态资产准备均已完成；动态场景纹理仍由实际绘制提供。
	public static Result lookup(SourceKey key) {
		if (!registrations().containsKey(key.effectId())) {
			return new Result(Readiness.UNREGISTERED, null, "");
		}

		if (key.kind() != SourceKind.EXPORTED_FX) {
			var prepared = AUTHORING.get(key);
			if (prepared != null && !PhotonOwnedEffectAssets.deviceCurrent(key)) {
				return new Result(Readiness.PREPARING, null, "设备准备已失效");
			}
			return new Result(prepared == null ? Readiness.PREPARING : Readiness.READY, prepared, "");
		}

		if (preparing) {
			return new Result(Readiness.PREPARING, null, "");
		}

		if (!reloadFailure.isEmpty()) {
			return new Result(Readiness.FAILED, null, reloadFailure);
		}

		var result = exported.get(key.effectId());
		if (result == null) {
			return new Result(Readiness.FAILED, null, "当前资源代次尚未发布该自有特效的数据");
		}

		if (result.prepared() != null && result.prepared().source().revision() != key.revision()) {
			return new Result(Readiness.FAILED, null, "请求的资源代次与已发布数据不一致");
		}

		if (result.prepared() != null && !PhotonOwnedEffectAssets.deviceCurrent(key)) {
			return new Result(Readiness.PREPARING, null, "设备准备已失效");
		}
		return result;
	}

	/// 由客户端维护入口调用，设备变化不在技能创建栈中同步补载。
	public static void refreshDeviceResources() {
		requireClientThread();
		if (preparing || Minecraft.getInstance().getOverlay() instanceof LoadingOverlay) {
			return;
		}
		if (nativeFXGeneration != FXHelper.generation()) {
			var manager = Minecraft.getInstance().getResourceManager();
			apply(prepare(manager, Profiler.get()), manager, Profiler.get());
			return;
		}
		QueenBeamResourcePreparation.prepare();
		for (var prepared : java.util.List.copyOf(AUTHORING.values())) {
			if (!PhotonOwnedEffectAssets.deviceCurrent(prepared.source())) {
				try {
					prepareDefinitions(prepared);
				} catch (RuntimeException failure) {
					invalidatePreview(prepared.source().effectId(), prepared.source().kind());
				}
			}
		}
		var current = new LinkedHashMap<>(exported);
		current.replaceAll((id, result) -> {
			var prepared = exportedDefinitions.get(id);
			if (prepared == null || PhotonOwnedEffectAssets.deviceCurrent(prepared.source())) {
				return result;
			}
			return prepareExported(prepared, result);
		});
		exported = Map.copyOf(current);
	}

	private static Result prepareExported(Prepared prepared, Result previous) {
		var source = prepared.source();
		var stamp = PhotonOwnedEffectAssets.deviceIdentity();
		long now = System.nanoTime();
		var retry = DEVICE_RETRIES.get(source);
		if (retry != null && retry.stamp().equals(stamp) && now - retry.attemptedAt() < DEVICE_RETRY_DELAY_NANOS) {
			return previous;
		}

		DEVICE_RETRIES.put(source, new DeviceRetry(stamp, now));
		try {
			prepareDefinitions(prepared);
			DEVICE_RETRIES.remove(source);
			return new Result(Readiness.READY, prepared, "");
		} catch (RuntimeException failure) {
			PhotonOwnedEffectAssets.invalidate(source);
			return new Result(Readiness.FAILED, null, "设备资源重建失败：" + failure);
		}
	}

	public static long resourceRevision() {
		return generation;
	}

	/// 编辑器从当前内存工程生成独立组件 NBT；不读取或假定外部文件已保存。
	public static void publishAuthoring(SourceKey key, Map<String, CompoundTag> definitions) {
		requireClientThread();
		if (key.kind() != SourceKind.AUTHORING) {
			throw new IllegalArgumentException("必须使用作者工程来源");
		}

		publishSnapshot(key, definitions);
	}

	public static void publishPublishedPreview(SourceKey key, Map<String, CompoundTag> definitions) {
		requireClientThread();
		if (key.kind() != SourceKind.PUBLISHED_PREVIEW) {
			throw new IllegalArgumentException("必须使用发布预览来源");
		}
		publishSnapshot(key, definitions);
	}

	private static void publishSnapshot(SourceKey key, Map<String, CompoundTag> definitions) {
		var registration = registrations().get(key.effectId());
		if (registration == null || !registration.keySet().equals(definitions.keySet())) {
			registration = SNAPSHOT_COMPONENTS.get(key.effectId());
		}
		if (registration == null || !registration.keySet().equals(definitions.keySet())) {
			throw new IllegalArgumentException("组合组件未登记或不完整：" + key.effectId());
		}
		var components = new LinkedHashMap<String, Component>();
		for (var entry : definitions.entrySet()) {
			components.put(entry.getKey(), new Component(registration.get(entry.getKey()),
					key.kind() == SourceKind.AUTHORING ? "authoring-memory" : "published-preview", entry.getValue()));
		}
		invalidatePreview(key.effectId(), key.kind());
		var prepared = new Prepared(key, components);
		prepareDefinitions(prepared);
		AUTHORING.put(key, prepared);
	}

	public static void invalidateAuthoring(Identifier effectId) {
		invalidatePreview(effectId, SourceKind.AUTHORING);
	}

	public static void invalidatePublishedPreview(Identifier effectId) {
		invalidatePreview(effectId, SourceKind.PUBLISHED_PREVIEW);
	}

	private static void invalidatePreview(Identifier effectId, SourceKind kind) {
		requireClientThread();
		AUTHORING.keySet().removeIf(key -> {
			if (!key.effectId().equals(effectId) || key.kind() != kind) {
				return false;
			}
			var invalidate = DEFINITION_INVALIDATORS.get(effectId);
			if (invalidate != null) {
				invalidate.accept(key);
				PhotonOwnedEffectAssets.invalidate(key);
				DEPENDENCY_SOURCES.remove(key);
				RESOLVED.remove(key);
			}
			return true;
		});
	}

	private static void requireClientThread() {
		if (!Minecraft.getInstance().isSameThread()) {
			throw new IllegalStateException("自有特效资源必须在客户端线程发布");
		}
	}

	@Override
	public CompletableFuture<Void> reload(SharedState currentReload, Executor taskExecutor,
	                                      PreparationBarrier barrier, Executor reloadExecutor) {
		preparing = true;
		reloadFailure = "";
		try {
			return CompletableFuture.supplyAsync(() -> prepare(currentReload.resourceManager(), Profiler.get()), taskExecutor).thenCompose(barrier::wait).thenAcceptAsync(preparations ->
							apply(preparations, currentReload.resourceManager(), Profiler.get()), reloadExecutor)
					.whenComplete((_, failure) -> {
						preparing = false;
						if (failure != null) {
							reloadFailure = "自有特效资源重载失败：" + failure;
						}
					});
		} catch (RuntimeException exception) {
			preparing = false;
			reloadFailure = "自有特效资源重载提交失败：" + exception;
			throw exception;
		}
	}

	private static Map<Identifier, Result> prepare(ResourceManager manager, ProfilerFiller profiler) {
		var results = new LinkedHashMap<Identifier, Result>();
		for (var entry : registrations().entrySet()) {
			try {
				var components = new LinkedHashMap<String, Component>();
				for (var component : entry.getValue().entrySet()) {
					components.put(component.getKey(), readComponent(manager, component.getValue()));
				}

				// 代次在客户端发布时赋值，worker 不接触注册表和 FX 对象恢复。
				results.put(entry.getKey(), new Result(Readiness.READY,
						new Prepared(new SourceKey(entry.getKey(), SourceKind.EXPORTED_FX, 0), components), ""));
			} catch (IOException | RuntimeException exception) {
				results.put(entry.getKey(), new Result(Readiness.FAILED, null, "自有特效组件数据准备失败：" + exception));
			}
		}

		return Map.copyOf(results);
	}

	private static Component readComponent(ResourceManager manager, Identifier effectId) throws IOException {
		var nativePath = Identifier.fromNamespaceAndPath(effectId.getNamespace(), "fx/" + effectId.getPath() + ".fx");
		var fallbackPath = Identifier.fromNamespaceAndPath(effectId.getNamespace(), "fx/" + effectId.getPath() + ".mfx");
		var selected = manager.getResource(nativePath);
		var actualPath = nativePath;
		if (selected.isEmpty()) {
			selected = manager.getResource(fallbackPath);
			actualPath = fallbackPath;
		}

		var resource = selected.orElseThrow(() -> new IOException("缺少自有特效资源：" + effectId));
		try (var stream = new BufferedInputStream(resource.open())) {
			return new Component(actualPath, resource.sourcePackId(),
					NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap()));
		}
	}

	private static void apply(Map<Identifier, Result> preparations, ResourceManager manager, ProfilerFiller profiler) {
		requireClientThread();
		long nextGeneration = generation + 1;
		for (var prepared : exportedDefinitions.values()) {
			var invalidate = DEFINITION_INVALIDATORS.get(prepared.source().effectId());
			if (invalidate != null) {
				invalidate.accept(prepared.source());
				PhotonOwnedEffectAssets.invalidate(prepared.source());
				DEPENDENCY_SOURCES.remove(prepared.source());
				RESOLVED.remove(prepared.source());
			}
		}
		DEVICE_RETRIES.clear();
		var definitions = new LinkedHashMap<Identifier, Prepared>();
		var published = new LinkedHashMap<Identifier, Result>();
		preparations.forEach((id, result) -> {
			var prepared = result.prepared();
			if (prepared == null) {
				published.put(id, result);
				return;
			}
			var ready = new Prepared(new SourceKey(id, SourceKind.EXPORTED_FX, nextGeneration), prepared.components());
			definitions.put(id, ready);
			published.put(id, prepareExported(ready, new Result(Readiness.PREPARING, null, "")));
		});
        exportedDefinitions = Map.copyOf(definitions);
		exported = Map.copyOf(published);
		generation = nextGeneration;
		nativeFXGeneration = FXHelper.generation();
		preparing = false;
		QueenBeamResourcePreparation.prepare();
	}
}
