package org.unitego.lobecorp.client.photon.runtime;

import net.minecraft.client.Minecraft;
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
	private static volatile Map<Identifier, Result> exported = Map.of();
	private static volatile boolean preparing;
	private static volatile String reloadFailure = "";
	private static volatile long generation;
	private static final Map<Identifier, Consumer<Prepared>> DEFINITION_PREPARERS = new LinkedHashMap<>();
	private static final Map<Identifier, Consumer<SourceKey>> DEFINITION_INVALIDATORS = new LinkedHashMap<>();

	public static synchronized void registerDefinitionPreparation(Identifier id, Consumer<Prepared> prepare, Consumer<SourceKey> invalidate) {
		DEFINITION_PREPARERS.putIfAbsent(id, prepare);
		DEFINITION_INVALIDATORS.putIfAbsent(id, invalidate);
	}

	private static void prepareDefinitions(Prepared prepared) {
		var prepare = DEFINITION_PREPARERS.get(prepared.source().effectId());
		if (prepare != null) {
			prepare.accept(prepared);
		}
	}


	public enum SourceKind {AUTHORING, EXPORTED_FX}

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

	/// READY 表示数据及已登记的客户端定义准备完成，不表示模型、材质或设备程序已准备完成。
	public static Result lookup(SourceKey key) {
		if (!registrations().containsKey(key.effectId())) {
			return new Result(Readiness.UNREGISTERED, null, "");
		}

		if (key.kind() == SourceKind.AUTHORING) {
			var prepared = AUTHORING.get(key);
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

		return result;
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

		var registration = registrations().get(key.effectId());
		if (registration == null || !registration.keySet().equals(definitions.keySet())) {
			throw new IllegalArgumentException("作者工程组件未登记或不完整：" + key.effectId());
		}

		var components = new LinkedHashMap<String, Component>();
		definitions.forEach((name, tag) -> components.put(name,
				new Component(registration.get(name), "authoring-memory", Objects.requireNonNull(tag))));
		AUTHORING.keySet().removeIf(previous -> previous.effectId().equals(key.effectId()));
		var prepared = new Prepared(key, components);
		prepareDefinitions(prepared);
		AUTHORING.put(key, prepared);
	}

	public static void invalidateAuthoring(Identifier effectId) {
		requireClientThread();
		AUTHORING.keySet().removeIf(key -> {
			if (!key.effectId().equals(effectId)) {
				return false;
			}
			var invalidate = DEFINITION_INVALIDATORS.get(effectId);
			if (invalidate != null) {
				invalidate.accept(key);
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
			return CompletableFuture.supplyAsync(() -> {
						try {
							return prepare(currentReload.resourceManager(), Profiler.get());
						} finally {
							// 全局其他监听器 prepare 失败时屏障可能不完成，不能让本地状态永久卡住。
							preparing = false;
						}
					}, taskExecutor).thenCompose(barrier::wait).thenAcceptAsync(preparations ->
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

	private Map<Identifier, Result> prepare(ResourceManager manager, ProfilerFiller profiler) {
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

	private void apply(Map<Identifier, Result> preparations, ResourceManager manager, ProfilerFiller profiler) {
		requireClientThread();
		long nextGeneration = generation + 1;
		for (var previous : exported.values()) {
			var prepared = previous.prepared();
			if (prepared != null) {
				var invalidate = DEFINITION_INVALIDATORS.get(prepared.source().effectId());
				if (invalidate != null) {
					invalidate.accept(prepared.source());
				}
			}
		}
		var published = new LinkedHashMap<Identifier, Result>();
		preparations.forEach((id, result) -> {
			var prepared = result.prepared();
			if (prepared == null) {
				published.put(id, result);
				return;
			}
			var ready = new Prepared(new SourceKey(id, SourceKind.EXPORTED_FX, nextGeneration), prepared.components());
			try {
				prepareDefinitions(ready);
				published.put(id, new Result(Readiness.READY, ready, ""));
			} catch (RuntimeException failure) {
				published.put(id, new Result(Readiness.FAILED, null, "自有组件静态定义准备失败：" + failure));
			}
		});
		exported = Map.copyOf(published);
		generation = nextGeneration;
		preparing = false;
	}
}
