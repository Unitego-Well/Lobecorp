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
import java.util.function.Supplier;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/// 仅准备显式登记的自有组件数据；不持有世界、实体或运行租约。
@NullMarked
public class PhotonOwnedEffectResources extends ContextAwareReloadListener implements PreparableReloadListener {
	private static final Map<Identifier, Map<String, Identifier>> REGISTRATIONS = new LinkedHashMap<>();
	private static final Map<SourceKey, Prepared> AUTHORING = new ConcurrentHashMap<>();
	private static final Map<SourceKey, Map<Identifier, String>> DEPENDENCY_SOURCES = new LinkedHashMap<>();
	private static volatile Map<Identifier, Result> exported = Map.of();
	// 设备失败不丢弃已读取的独占CPU快照；对外Result仍只有READY携带成果。
	private static Map<Identifier, Prepared> exportedDefinitions = Map.of();
	private static final Map<SourceKey, Prepared> PENDING_SNAPSHOTS = new LinkedHashMap<>();
	private static final Map<SourceKey, CompletableFuture<SourceKey>> SNAPSHOT_COMPLETIONS = new LinkedHashMap<>();
	private static final long DEVICE_RETRY_DELAY_NANOS = 1_000_000_000L;

	private static final Map<SourceKey, Map<Identifier, FX>> RESOLVED = new LinkedHashMap<>();
	private static final Map<Identifier, Map<String, Identifier>> SNAPSHOT_COMPONENTS = new LinkedHashMap<>();
	private static @Nullable SourceKey resolvingSource;
	private static volatile boolean preparing;
	private static volatile String reloadFailure = "";
	private static volatile long generation;
	private static int nativeFXGeneration = -1;
	private static volatile boolean deviceDirty;
	private static long authoringRevision;
	private static volatile boolean dispatchInFlight;
	private static boolean exportedDataRetry;
	private static final AtomicLong DISPATCH_GENERATION = new AtomicLong();
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

	private static synchronized boolean registered(Identifier effectId) {
		return REGISTRATIONS.containsKey(effectId);
	}

	private static synchronized Map<Identifier, Map<String, Identifier>> registrations() {
		return Map.copyOf(REGISTRATIONS);
	}

	/// READY 要求组成件定义与静态资产准备均已完成；动态场景纹理仍由实际绘制提供。
	public static Result lookup(SourceKey key) {
		if (!registered(key.effectId())) {
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

	/// tick 只消费失效通知并派发 CPU 任务；FX 恢复与设备提交使用客户端任务队列。
	public static void refreshDeviceResources() {
		requireClientThread();
		if (PhotonOwnedPreparationScheduler.closed() || preparing
				|| Minecraft.getInstance().getOverlay() instanceof LoadingOverlay) {
			return;
		}

		if (nativeFXGeneration != FXHelper.generation()) {
			nativeFXGeneration = FXHelper.generation();
			PhotonOwnedPreparationScheduler.enqueue(PhotonOwnedPreparationScheduler.Kind.EXPORTED, null, ++authoringRevision);
		}
		if (deviceDirty) {
			deviceDirty = false;
			for (var prepared : AUTHORING.values()) {
				queueDevice(prepared.source());
			}
			for (var prepared : exportedDefinitions.values()) {
				queueDevice(prepared.source());
			}
			PhotonOwnedPreparationScheduler.enqueue(PhotonOwnedPreparationScheduler.Kind.QUEEN_BEAM, null, ++authoringRevision);
		}
		dispatchReady(System.nanoTime());
	}

	private static void queueDevice(SourceKey source) {
		PhotonOwnedPreparationScheduler.enqueue(PhotonOwnedPreparationScheduler.Kind.DEVICE, source, source.revision());
	}

	private static void dispatchReady(long now) {
		if (dispatchInFlight || !PhotonOwnedPreparationScheduler.hasWork(now)) {
			return;
		}

		for (var kind : PhotonOwnedPreparationScheduler.Kind.values()) {
			var tasks = PhotonOwnedPreparationScheduler.ready(kind, now);
			if (tasks.isEmpty()) {
				continue;
			}

			var task = PhotonOwnedPreparationScheduler.begin(tasks.getFirst());
			switch (kind) {
				case EXPORTED -> startExported(task);
				case QUEEN_BEAM -> startQueen(task);
				case AUTHORING, PUBLISHED_PREVIEW, DEVICE -> startSnapshot(task);
			}
			return;
		}
	}

	private static <T> void dispatch(PhotonOwnedPreparationScheduler.Task task,
	                                Supplier<T> worker, Consumer<T> commit) {
		dispatchInFlight = true;
		long epoch = DISPATCH_GENERATION.get();
		var device = PhotonOwnedEffectAssets.deviceIdentity();
		var client = Minecraft.getInstance();
		CompletableFuture.supplyAsync(worker).whenComplete((value, failure) ->
				client.schedule(() -> {
					if (epoch != DISPATCH_GENERATION.get()) {
						return;
					}
					dispatchInFlight = false;
					if (!PhotonOwnedPreparationScheduler.current(task, task.revision())) {
						return;
					}

					try {
						if (failure != null) {
							throw new IllegalStateException("CPU准备失败", failure);
						}
						if (!device.equals(PhotonOwnedEffectAssets.deviceIdentity())) {
							throw new IllegalStateException("准备期间设备代次已变化");
						}
						PhotonOwnedPreparationScheduler.ready(task);
						commit.accept(value);
						PhotonOwnedPreparationScheduler.commit(task);
					} catch (RuntimeException invalid) {
						PhotonOwnedPreparationScheduler.fail(task, System.nanoTime() + DEVICE_RETRY_DELAY_NANOS, invalid.toString());
					}
				}));
	}

	private static Prepared normalize(Prepared prepared) {
		var tags = PhotonOwnedLaser.prepareCpu(prepared);
		var components = new LinkedHashMap<String, Component>();
		prepared.components().forEach((name, component) -> components.put(name,
				new Component(component.resource(), component.sourcePack(), tags.get(name))));
		return new Prepared(prepared.source(), components);
	}

	private static void startExported(PhotonOwnedPreparationScheduler.Task task) {
		var manager = Minecraft.getInstance().getResourceManager();
		dispatch(task, () -> prepare(manager, Profiler.get()), preparations ->
				{
					apply(preparations, manager, Profiler.get());
					if (exportedDataRetry) {
						throw new IllegalStateException("部分导出数据读取失败");
					}
				});
	}

	private static void startQueen(PhotonOwnedPreparationScheduler.Task task) {
		// Photon 的异步预载负责文件读取；设备准备只在客户端提交。
		var resources = QueenBeamResourcePreparation.preload();
		dispatch(task, () -> {
			resources.join();
			return true;
		}, _ -> {
			QueenBeamResourcePreparation.prepare();
			if (!QueenBeamResourcePreparation.ready()) {
				throw new IllegalStateException(QueenBeamResourcePreparation.failure());
			}
		});
	}

	private static void startSnapshot(PhotonOwnedPreparationScheduler.Task task) {
		var source = task.source();
		var prepared = PENDING_SNAPSHOTS.get(source);
		if (prepared == null) {
			prepared = source.kind() == SourceKind.EXPORTED_FX
					? exportedDefinitions.get(source.effectId()) : AUTHORING.get(source);
		}
		if (prepared == null || !prepared.source().equals(source)) {
			PhotonOwnedPreparationScheduler.cancel(task.id());
			return;
		}

		var snapshot = prepared;
		dispatch(task, () -> normalize(snapshot), ready -> {
			prepareDefinitions(ready);
			if (source.kind() == SourceKind.EXPORTED_FX) {
				var next = new LinkedHashMap<>(exported);
				next.put(source.effectId(), new Result(Readiness.READY, ready, ""));
				exported = Map.copyOf(next);
			} else {
				publishPrepared(ready);
				PENDING_SNAPSHOTS.remove(source);
				var completion = SNAPSHOT_COMPLETIONS.remove(source);
				if (completion != null) {
					completion.complete(source);
				}
			}
		});
	}

	private static Result prepareExported(Prepared prepared) {
		try {
			prepareDefinitions(prepared);
			return new Result(Readiness.READY, prepared, "");
		} catch (RuntimeException failure) {
			queueDevice(prepared.source());
			var tasks = PhotonOwnedPreparationScheduler.ready(PhotonOwnedPreparationScheduler.Kind.DEVICE, System.nanoTime());
			for (var task : tasks) {
				if (prepared.source().equals(task.source())) {
					PhotonOwnedPreparationScheduler.fail(task, System.nanoTime() + DEVICE_RETRY_DELAY_NANOS, failure.toString());
				}
			}
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

	private static Prepared snapshot(SourceKey key, Map<String, CompoundTag> definitions) {
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
		return new Prepared(key, components);
	}

	private static void publishSnapshot(SourceKey key, Map<String, CompoundTag> definitions) {
		var prepared = snapshot(key, definitions);
		prepareDefinitions(prepared);
		publishPrepared(prepared);
	}

	private static void publishPrepared(Prepared prepared) {
		var source = prepared.source();
		var obsolete = AUTHORING.keySet().stream().filter(key -> !key.equals(source)
				&& key.effectId().equals(source.effectId()) && key.kind() == source.kind()).toList();
		AUTHORING.put(source, prepared);
		for (var key : obsolete) {
			AUTHORING.remove(key);
			invalidateDefinition(key);
		}
	}

	/// 捕获独占 NBT 后入队；失败保留旧快照，成功才完成通知。
	public static CompletableFuture<SourceKey> queueSnapshot(SourceKey key, Map<String, CompoundTag> definitions) {
		requireClientThread();
		if (PhotonOwnedPreparationScheduler.closed()) {
			throw new IllegalStateException("自有资源准备已关闭");
		}
		cancelPending(key.effectId(), key.kind());
		PENDING_SNAPSHOTS.put(key, snapshot(key, definitions));
		var completion = new CompletableFuture<SourceKey>();
		SNAPSHOT_COMPLETIONS.put(key, completion);
		var kind = key.kind() == SourceKind.AUTHORING
				? PhotonOwnedPreparationScheduler.Kind.AUTHORING : PhotonOwnedPreparationScheduler.Kind.PUBLISHED_PREVIEW;
		PhotonOwnedPreparationScheduler.enqueue(kind, key, key.revision());
		return completion;
	}

	private static void cancelPending(Identifier effectId, SourceKind kind) {
		PhotonOwnedPreparationScheduler.cancelSnapshot(effectId, kind);
		PENDING_SNAPSHOTS.keySet().removeIf(key -> key.effectId().equals(effectId) && key.kind() == kind);
		var cancelled = SNAPSHOT_COMPLETIONS.keySet().stream()
				.filter(key -> key.effectId().equals(effectId) && key.kind() == kind).toList();
		for (var key : cancelled) {
			SNAPSHOT_COMPLETIONS.remove(key).cancel(false);
		}
	}

	public static void invalidateAuthoring(Identifier effectId) {
		cancelPending(effectId, SourceKind.AUTHORING);
		invalidatePreview(effectId, SourceKind.AUTHORING);
	}

	public static void invalidatePublishedPreview(Identifier effectId) {
		cancelPending(effectId, SourceKind.PUBLISHED_PREVIEW);
		invalidatePreview(effectId, SourceKind.PUBLISHED_PREVIEW);
	}

	/// 编辑事件撤销尚未提交的旧修订；工作区稳定后提交新的独占快照。
	public static void markAuthoringDirty(Identifier effectId) {
		requireClientThread();
		cancelPending(effectId, SourceKind.AUTHORING);
	}

	public static void markPublishedPreviewDirty(Identifier effectId) {
		requireClientThread();
		cancelPending(effectId, SourceKind.PUBLISHED_PREVIEW);
	}

	/// 通知可能来自渲染回调；只置位，来源枚举在客户端消费通知时执行。
	public static void markDeviceDirty() {
		deviceDirty = true;
	}

	private static void invalidateDefinition(SourceKey key) {
		var invalidate = DEFINITION_INVALIDATORS.get(key.effectId());
		if (invalidate != null) {
			invalidate.accept(key);
		}
		PhotonOwnedEffectAssets.invalidate(key);
		DEPENDENCY_SOURCES.remove(key);
		RESOLVED.remove(key);
	}

	private static void invalidatePreview(Identifier effectId, SourceKind kind) {
		requireClientThread();
		PhotonOwnedPreparationScheduler.cancelSource(effectId, kind);
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

	/// 全局定义不依赖世界；仅客户端退出时撤销全部准备任务与回调。
	public static void closePreparation() {
		requireClientThread();
		DISPATCH_GENERATION.incrementAndGet();
		dispatchInFlight = false;
		PhotonOwnedPreparationScheduler.close();
		var completions = List.copyOf(SNAPSHOT_COMPLETIONS.values());
		SNAPSHOT_COMPLETIONS.clear();
		PENDING_SNAPSHOTS.clear();
		completions.forEach(completion -> completion.cancel(false));
		for (var source : List.copyOf(AUTHORING.keySet())) {
			invalidateDefinition(source);
		}
		AUTHORING.clear();
	}

	private static void requireClientThread() {
		if (!Minecraft.getInstance().isSameThread()) {
			throw new IllegalStateException("自有特效资源必须在客户端线程发布");
		}
	}

	@Override
	public CompletableFuture<Void> reload(SharedState currentReload, Executor taskExecutor,
	                                      PreparationBarrier barrier, Executor reloadExecutor) {
		long epoch = DISPATCH_GENERATION.incrementAndGet();
		dispatchInFlight = false;
		PhotonOwnedPreparationScheduler.reset();
		preparing = true;
		reloadFailure = "";
		try {
			return CompletableFuture.supplyAsync(() -> prepare(currentReload.resourceManager(), Profiler.get()), taskExecutor).thenCompose(barrier::wait).thenAcceptAsync(preparations ->
							{
								if (epoch == DISPATCH_GENERATION.get()) {
									apply(preparations, currentReload.resourceManager(), Profiler.get());
									if (exportedDataRetry) {
										var task = PhotonOwnedPreparationScheduler.enqueue(PhotonOwnedPreparationScheduler.Kind.EXPORTED, null, epoch);
										PhotonOwnedPreparationScheduler.fail(task, System.nanoTime() + DEVICE_RETRY_DELAY_NANOS, "资源读取失败");
									}
									for (var source : PENDING_SNAPSHOTS.keySet()) {
										var kind = source.kind() == SourceKind.AUTHORING
												? PhotonOwnedPreparationScheduler.Kind.AUTHORING : PhotonOwnedPreparationScheduler.Kind.PUBLISHED_PREVIEW;
										PhotonOwnedPreparationScheduler.enqueue(kind, source, source.revision());
									}
								}
							}, reloadExecutor)
					.whenComplete((_, failure) -> {
						if (epoch != DISPATCH_GENERATION.get()) {
							return;
						}
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
						normalize(new Prepared(new SourceKey(entry.getKey(), SourceKind.EXPORTED_FX, 0), components)), ""));
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
		exportedDataRetry = false;
		long nextGeneration = generation + 1;
		var previousDefinitions = exportedDefinitions;
		var definitions = new LinkedHashMap<Identifier, Prepared>();
		var published = new LinkedHashMap<Identifier, Result>();
		preparations.forEach((id, result) -> {
			var prepared = result.prepared();
			if (prepared == null) {
				exportedDataRetry = true;
				prepared = previousDefinitions.get(id);
				if (prepared == null) {
					published.put(id, result);
					return;
				}
			}
			var ready = new Prepared(new SourceKey(id, SourceKind.EXPORTED_FX, nextGeneration), prepared.components());
			var preparation = prepareExported(ready);
			var previous = previousDefinitions.get(id);
			if (preparation.readiness() != Readiness.READY && previous != null) {
				var fallback = new Prepared(ready.source(), previous.components());
				var restored = prepareExported(fallback);
				if (restored.readiness() == Readiness.READY) {
					ready = fallback;
					preparation = restored;
					exportedDataRetry = true;
				}
			}
			definitions.put(id, ready);
			published.put(id, preparation);
		});
		exportedDefinitions = Map.copyOf(definitions);
		exported = Map.copyOf(published);
		generation = nextGeneration;
		reloadFailure = "";
		nativeFXGeneration = FXHelper.generation();
		for (var prepared : previousDefinitions.values()) {
			PhotonOwnedPreparationScheduler.cancelSource(prepared.source().effectId(), SourceKind.EXPORTED_FX);
			invalidateDefinition(prepared.source());
		}
		// 导出替换不撤销作者任务；导出失败的设备任务须保留自己的到期时间。
		for (var entry : published.entrySet()) {
			if (entry.getValue().readiness() == Readiness.FAILED && definitions.containsKey(entry.getKey())) {
				var source = definitions.get(entry.getKey()).source();
				var task = PhotonOwnedPreparationScheduler.enqueue(PhotonOwnedPreparationScheduler.Kind.DEVICE, source, source.revision());
				PhotonOwnedPreparationScheduler.fail(task, System.nanoTime() + DEVICE_RETRY_DELAY_NANOS, entry.getValue().failure());
			}
		}
		preparing = false;
		PhotonOwnedPreparationScheduler.enqueue(PhotonOwnedPreparationScheduler.Kind.QUEEN_BEAM, null, generation);
	}
}
