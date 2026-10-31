package org.unitego.lobecorp.client.photon.runtime.creation;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import org.unitego.lobecorp.mixin.photon.creation.client.EmitterLeaseAccessor;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;

import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.mixin.photon.creation.client.FXObjectLeaseAccessor;
import org.unitego.lobecorp.mixin.photon.creation.client.ParticleEmitterLeaseAccessor;
import org.unitego.lobecorp.mixin.photon.creation.client.TimelinePlayerLeaseAccessor;
import org.unitego.lobecorp.util.photon.creation.PhotonFrozenTag;
import org.unitego.lobecorp.util.photon.creation.PhotonIndexedObjectMap;
import org.unitego.lobecorp.util.photon.creation.PhotonLeasePrivateResetUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonRuntimeCopyUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectDefinitionUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/// 定义准备、独占实例创建与受管租约的统一边界；未托管作者数据不跨调用缓存。
public class PhotonRuntimeFactory {
	public static final String MODE_PROPERTY = "lobecorp.photon.creation.mode";

	public enum Mode {
		NATIVE, COMPILED, POOLED, INDEXED, HYBRID
	}

	private static final Mode MODE = Mode.valueOf(System.getProperty(MODE_PROPERTY, "native").toUpperCase(Locale.ROOT));
	private static final PhotonRuntimePool POOL = new PhotonRuntimePool(PhotonRuntimeFactory::resetSupported, PhotonRuntimeFactory::parked);
	private static final Map<FX, String> RESOURCE_SOURCES = new WeakHashMap<>();
	private static final Map<FX, CachedDefinition> DEFINITIONS = new WeakHashMap<>();
	// 值不持有作为 key 的 runtime、scene 或其对象，避免弱键经值重新强可达。
	private static final Map<FXRuntime, Scope> SCOPES = new WeakHashMap<>();
	private static final ThreadLocal<Integer> COMPILING = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<Construction> CONSTRUCTION = new ThreadLocal<>();
	private static long generation;
	private static long definitionGeneration;
	private static final Map<FX, PreparedSourceKey> PREPARED_SOURCES = new WeakHashMap<>();

	/// 调用方保证发布后 FX 不再原地修改；来源身份不写入 Photon 通用缓存。
	public record PreparedSourceKey(String source, long revision) {
		public PreparedSourceKey {
			Objects.requireNonNull(source);
			if (source.isBlank()) {
				throw new IllegalArgumentException("准备来源身份不能为空。");
			}
		}
	}

	public static Definition registerPreparedSource(FX source, PreparedSourceKey key) {
		Objects.requireNonNull(source);
		Objects.requireNonNull(key);
		requireDetachedPreparedSource(source);
		if (RESOURCE_SOURCES.containsKey(source)) {
			throw new IllegalArgumentException("公开资源不能登记为私有准备来源。");
		}

		var cached = DEFINITIONS.get(source);
		if (key.equals(PREPARED_SOURCES.get(source)) && cached != null
				&& cached.data.get() == source.getFxData()
				&& cached.definition.generation == definitionGeneration && cached.definition.valid) {
			return cached.definition;
		}
		unregisterPreparedSource(source);
		PhotonChildSpawner.bindPreloadDefinition(source.getFxData());
		var definition = compile(source.getFxData(), key.source() + "@" + key.revision());
		preparedSourceCompilations++;
		PREPARED_SOURCES.put(source, key);
		DEFINITIONS.put(source, new CachedDefinition(new WeakReference<>(source.getFxData()), definition));
		return definition;
	}

	/// 只查询显式私有来源的当前编译缓存，不准备定义或触碰世界生命周期。
	public static boolean preparedSourceCurrent(FX source) {
		if (!PREPARED_SOURCES.containsKey(source)) {
			return false;
		}

		var cached = DEFINITIONS.get(source);
		return cached != null && cached.data.get() == source.getFxData()
				&& cached.definition.valid && cached.definition.generation == definitionGeneration;
	}

	private static void requireDetachedPreparedSource(FX source) {
		for (var object : source.getFxData().objects()) {
			if (object.getScene() != null || object.getEffectExecutor() != null) {
				throw new IllegalArgumentException("私有准备来源必须是未绑定场景或执行器的不可变快照。");
			}
		}
	}

	public static void unregisterPreparedSource(FX source) {
		if (PREPARED_SOURCES.remove(source) != null) {
			var previous = DEFINITIONS.remove(source);
			if (previous != null) {
				invalidateDefinition(previous.definition);
			}
		}
	}
	private static long tick;
	private static long frame;
	private static long sequence;
	private static long preparedDefinitions;
	private static long preparedSourceCacheHits;
	private static long preparedSourceCompilations;
	private static long compiledNodes;
	private static long createdRuntimes;
	private static long projectRequests;
	private static long projectCompilations;
	private static long indexedRuntimes;
	private static long hybridLeaseAcquires;
	private static long hybridFreshIndexedCreates;
	private static long hybridIndexedResets;
	private static long hybridReuseTypeRejections;
	private static long hybridLayoutFallbacks;
	private static long unmanagedCreates;
	private static long nativeCreates;
	private static long resetFailures;
	private static long definitionNanos;
	private static long instanceNanos;
	private static long maxInstanceNanos;
	private static long resetNanos;
	private static long maxResetNanos;
	private static boolean measurementWindow;
	private static long windowMaxResetNanos;
	private static long windowMaxInstanceNanos;
	private static long initializedNodes;
	private static long nativeNodeFallbacks;
	private static @Nullable Object observedLevel;

	private record CachedDefinition(WeakReference<FXData> data, Definition definition) {
	}

	private record Construction(FXData data, Definition definition, Mode mode) {
	}

	private record PoolKey(Definition definition, Object world, long generation, Mode mode) {
	}

	private record ProjectKey(List<UUID> ids, List<String> types, UUID target,
	                          boolean children, long controllerRevision) {
	}

	private static class Scope {
		private final Definition definition;
		private final Mode mode;
		private final Map<ProjectKey, Definition> projects = new HashMap<>();

		private Scope(Definition definition, Mode mode) {
			this.definition = definition;
			this.mode = mode;
		}
	}

	public static Mode mode() {
		return MODE;
	}

	public static long ticks() {
		return tick;
	}

	public static long frames() {
		return frame;
	}

	public static void registerResource(FX fx, String id) {
		if (MODE != Mode.NATIVE && COMPILING.get() == 0 && !PREPARED_SOURCES.containsKey(fx)) {
			RESOURCE_SOURCES.put(fx, id);
		}
	}

	/// 显式发布新制作修订；已返回定义不被原地改写。
	public static Definition prepare(FX source) {
		Objects.requireNonNull(source);
		ensureWorld();
		var preparedKey = PREPARED_SOURCES.get(source);
		if (preparedKey != null) {
			requireDetachedPreparedSource(source);
		}

		var result = compile(source.getFxData(), preparedKey == null
				? RESOURCE_SOURCES.getOrDefault(source, "authoring") : preparedKey.source() + "@" + preparedKey.revision());
		if (RESOURCE_SOURCES.containsKey(source) || PREPARED_SOURCES.containsKey(source)) {
			var previous = DEFINITIONS.put(source, new CachedDefinition(new WeakReference<>(source.getFxData()), result));
			if (previous != null) {
				invalidateDefinition(previous.definition);
			}
		}
		return result;
	}

	private static Definition preparedResource(FX source) {
		if (RESOURCE_SOURCES.containsKey(source) || PREPARED_SOURCES.containsKey(source)) {
			var cached = DEFINITIONS.get(source);
			if (cached != null && cached.data.get() == source.getFxData()
					&& cached.definition.generation == definitionGeneration && cached.definition.valid) {
				if (PREPARED_SOURCES.containsKey(source)) {
					preparedSourceCacheHits++;
				}

				return cached.definition;
			}
		}
		return prepare(source);
	}

	private static Definition compile(FXData source, String key) {
		long started = System.nanoTime();
		COMPILING.set(COMPILING.get() + 1);
		try {
			var snapshot = new PhotonFrozenTag(source.serializeNBT(Platform.getFrozenRegistry()));
			var nodes = new ArrayList<PhotonCompiledObject>();
			for (var object : source.objects()) {
				nodes.add(PhotonCompiledObject.compile(object));
			}
			var result = new Definition(key + ":" + ++sequence, definitionGeneration, nodes,
					PhotonTimelineProgram.compile(source.timeline()), snapshot);
			preparedDefinitions++;
			compiledNodes += nodes.size();
			return result;
		} finally {
			COMPILING.set(COMPILING.get() - 1);
			definitionNanos += System.nanoTime() - started;
		}
	}

	/// 原生公开入口仍接受可变作者数据；没有显式租约的调用不加入复用池。
	public static FXRuntime create(FX source, boolean deep, Supplier<FXRuntime> original) {
		if (MODE == Mode.NATIVE || !deep || COMPILING.get() != 0) {
			nativeCreates++;
			return original.get();
		}
		unmanagedCreates++;
		return instantiate(prepare(source), MODE == Mode.POOLED ? Mode.COMPILED : MODE);
	}

	public static FXRuntime nativeRuntime(FX source) {
		COMPILING.set(COMPILING.get() + 1);
		try {
			return source.createRuntime(true);
		} finally {
			COMPILING.set(COMPILING.get() - 1);
		}
	}

	public static Lease acquire(FX source, Object world) {
		ensureWorld();
		if (MODE == Mode.NATIVE) {
			return new Lease(nativeRuntime(source), null, generation);
		}
		return acquire(preparedResource(source), world, MODE);
	}

	public static Lease acquire(Definition definition, Object world, Mode mode) {
		ensureCurrent(definition);
		if (mode == Mode.POOLED || mode == Mode.HYBRID) {
			return acquirePooled(definition, world, mode, null);
		}
		return new Lease(instantiate(definition, mode), null, generation);
	}

	private static Lease acquirePooled(Definition definition, Object world, Mode mode,
	                                   @Nullable List<IFXObject> live) {
		var constructionMode = mode == Mode.HYBRID ? Mode.HYBRID : Mode.COMPILED;
		var pooled = POOL.acquire(new PoolKey(definition, world, generation, mode),
				() -> construct(definition, live, constructionMode),
				value -> resetRuntime(definition, value, live, mode));

		if (mode == Mode.HYBRID) {
			hybridLeaseAcquires++;
		}

		return new Lease(null, pooled, generation);
	}

	public static FXRuntime instantiate(Definition definition, Mode mode) {
		ensureCurrent(definition);
		if (mode == Mode.NATIVE) {
			var data = new FXData();
			data.deserializeNBT(Platform.getFrozenRegistry(), definition.snapshot());
			return new FXRuntime(data);
		}
		return construct(definition, null, mode);
	}

	private static FXRuntime construct(Definition definition, @Nullable List<IFXObject> live, Mode mode) {
		long started = System.nanoTime();
		var objects = new ArrayList<IFXObject>();
		try {
			for (int i = 0; i < definition.nodes.size(); i++) {
				var plan = definition.nodes.get(i);
				var current = live == null ? null : find(live, plan.id());
				IFXObject next;
				if (current != null && !(current instanceof PhotonNamedController)) {
					next = plan.instantiateFrom(current);
				} else {
					next = plan.instantiate();
					if (current != null) {
						PhotonRuntimeCopyUtil.copyBase(current, next);
					}
				}
				if (definition.projectTarget != null) {
					if (next.id().equals(definition.projectTarget)) {
						next.transform()._setInternalParentID(FXRuntime.ROOT_UUID);
						next.transform().localPosition(new Vector3f());
						if (!definition.includeChildren) {
							next.transform()._setInternalChildID(new ArrayList<>());
						}
					} else if (definition.addedController && next instanceof PhotonNamedController) {
						next.transform()._setInternalParentID(FXRuntime.ROOT_UUID);
						next.transform()._setInternalChildID(new ArrayList<>());
					}
				}
				objects.add(next);
				initializedNodes++;
				if (plan.nativeFallback()) {
					nativeNodeFallbacks++;
				}
			}
			var data = new FXData(objects, definition.timeline.instantiate());
			var previous = CONSTRUCTION.get();
			CONSTRUCTION.set(new Construction(data, definition, mode));
			FXRuntime runtime;
			try {
				runtime = new FXRuntime(data);
			} finally {
				if (previous == null) {
					CONSTRUCTION.remove();
				} else {
					CONSTRUCTION.set(previous);
				}
			}
			SCOPES.put(runtime, new Scope(definition, mode));
			createdRuntimes++;

			if (mode == Mode.HYBRID) {
				if (runtime.objects instanceof PhotonIndexedObjectMap indexed && !indexed.isMaterialized()) {
					hybridFreshIndexedCreates++;
				} else {
					hybridLayoutFallbacks++;
				}

				if (!reusable(runtime)) {
					hybridReuseTypeRejections++;
				}
			}

			return runtime;
		} catch (RuntimeException | Error failure) {
			objects.forEach(object -> object.remove(true));
			throw failure;
		} finally {
			long elapsed = System.nanoTime() - started;
			instanceNanos += elapsed;
			maxInstanceNanos = Math.max(maxInstanceNanos, elapsed);
			if (measurementWindow) {
				windowMaxInstanceNanos = Math.max(windowMaxInstanceNanos, elapsed);
			}
		}
	}

	public static @Nullable Map<UUID, IFXObject> indexedMap(FXData data, Map<UUID, IFXObject> current) {
		var construction = CONSTRUCTION.get();
		if (construction == null || construction.data != data || construction.mode != Mode.INDEXED && construction.mode != Mode.HYBRID) {
			return null;
		}
		var result = new PhotonIndexedObjectMap(construction.definition.layout);
		if (construction.mode == Mode.HYBRID) {
			// putAll 会物化，逐项装载才能保留数组布局。
			//noinspection UseBulkOperation
			current.forEach(result::put);
		} else {
			result.putAll(current);
		}

		indexedRuntimes++;
		return result;
	}

	public static boolean managed(IScene scene) {
		return scene instanceof FXRuntime runtime && SCOPES.containsKey(runtime);
	}

	public static @Nullable Lease project(IScene scene, List<IFXObject> selected,
	                                      UUID target, boolean includeChildren, Object world) {
		if (!(scene instanceof FXRuntime scopeRuntime)) {
			return null;
		}
		var scope = SCOPES.get(scopeRuntime);
		if (scope == null || scope.definition.generation != definitionGeneration || !scope.definition.isValid()) {
			return null;
		}
		var mode = scope.mode == Mode.HYBRID ? Mode.HYBRID : MODE;

		if (mode == Mode.NATIVE) {
			return null;
		}

		projectRequests++;
		var controllers = scene.getAllSceneObjects().stream()
				.filter(PhotonNamedController.class::isInstance).map(PhotonNamedController.class::cast).toList();
		if (controllers.size() > 1) {
			throw new IllegalArgumentException("项目模板含多个命名控制器。");
		}
		var controller = controllers.isEmpty() ? null : controllers.getFirst();
		if (controller != null && selected.size() >= PhotonSpawnConfig.MAX_TEMPLATE_OBJECTS) {
			throw new IllegalArgumentException("项目模板命名控制器超出对象预算。");
		}
		var key = new ProjectKey(selected.stream().map(IFXObject::id).toList(),
				selected.stream().map(object -> object.getClass().getName()).toList(),
				target, includeChildren, controller == null ? -1 : controller.definitionRevision());
		var definition = scope.projects.get(key);
		if (definition == null) {
			definition = compileProject(scope.definition, selected, controller, target, includeChildren);
			scope.projects.put(key, definition);
			projectCompilations++;
		}
		var inputs = new ArrayList<>(selected);
		if (controller != null && inputs.stream().noneMatch(PhotonNamedController.class::isInstance)) {
			inputs.add(controller);
		}
		if (mode == Mode.POOLED || mode == Mode.HYBRID) {
			return acquirePooled(definition, world, mode, inputs);
		}
		return new Lease(construct(definition, inputs, mode), null, generation);
	}

	private static Definition compileProject(Definition parent, List<IFXObject> selected,
	                                         @Nullable PhotonNamedController controller, UUID target,
	                                         boolean includeChildren) {
		var plans = new ArrayList<PhotonCompiledObject>();
		for (var object : selected) {
			if (object instanceof PhotonNamedController) {
				continue;
			}
			var plan = parent.byId.get(object.id());
			if (plan == null || !plan.type().equals(object.getFXObjectType().name())) {
				plan = PhotonCompiledObject.compile(object);
			}
			plans.add(plan);
		}
		if (controller != null) {
			var targets = plans.stream().map(PhotonCompiledObject::id).collect(Collectors.toSet());
			var wrapper = (CompoundTag) IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, controller).getOrThrow();
			var data = wrapper.getCompoundOrEmpty("data");
			data.put("definition", PhotonProjectDefinitionUtil.filter(controller.definition(), targets));
			var filtered = IFXObject.CODEC.parse(NbtOps.INSTANCE, wrapper).getOrThrow();
			plans.add(PhotonCompiledObject.compile(filtered));
		}
		var definition = new Definition(parent.key + "/project:" + target, definitionGeneration, plans,
				PhotonTimelineProgram.compile(new Timeline()), null);
		definition.parent = parent;
		definition.addedController = controller != null && selected.stream().noneMatch(PhotonNamedController.class::isInstance);
		definition.projectTarget = target;
		definition.includeChildren = includeChildren;
		return definition;
	}

	private static @Nullable IFXObject find(List<IFXObject> live, UUID id) {
		for (var value : live) {
			if (value.id().equals(id)) {
				return value;
			}
		}
		return null;
	}

	private static void resetRuntime(Definition definition, FXRuntime runtime,
	                                 @Nullable List<IFXObject> live, Mode mode) {
		ensureCurrent(definition);
		long started = System.nanoTime();
		var originalMap = runtime.objects;
		try {
			if (mode == Mode.HYBRID) {
				if (!(originalMap instanceof PhotonIndexedObjectMap)) {
					hybridLayoutFallbacks++;
					throw new IllegalStateException("HYBRID 租约缺少索引布局。");
				}

				if (!reusable(runtime)) {
					hybridReuseTypeRejections++;
					throw new IllegalStateException("HYBRID 租约包含不支持完整复位的精确类型。");
				}
			}

			runtime.destroy(true);
			var actors = new ArrayList<>(runtime.fxData.objects());
			if (actors.size() != definition.nodes.size() || runtime.objects.size() != actors.size() + 1) {
				throw new IllegalStateException("动态修改后的对象树不可按原布局复位。");
			}
			for (var actor : actors) {
				if (actor instanceof FXObject object) {
					((FXObjectLeaseAccessor) object).lobecorp$setLeaseExecutor(null);
					object.setSceneInternal(null);
				}
				actor.transform().parent(null, false);
				actor.transform().destroy();
			}
			runtime.root.transform()._setInternalChildID(new ArrayList<>());
			for (int i = 0; i < actors.size(); i++) {
				var actor = actors.get(i);
				resetActor(actor);
				var current = live == null ? null : find(live, definition.nodes.get(i).id());
				if (current != null && !(current instanceof PhotonNamedController)) {
					definition.nodes.get(i).initializeFrom(current, actor);
				} else {
					definition.nodes.get(i).initialize(actor);
					if (current != null) {
						PhotonRuntimeCopyUtil.copyBase(current, actor);
					}
				}
				if (definition.projectTarget != null) {
					if (actor.id().equals(definition.projectTarget)) {
						actor.transform()._setInternalParentID(FXRuntime.ROOT_UUID);
						actor.transform().localPosition(new Vector3f());
						if (!definition.includeChildren) {
							actor.transform()._setInternalChildID(new ArrayList<>());
						}
					} else if (definition.addedController && actor instanceof PhotonNamedController) {
						actor.transform()._setInternalParentID(FXRuntime.ROOT_UUID);
						actor.transform()._setInternalChildID(new ArrayList<>());
					}
				}
				actor.reset();
			}
			resetActor(runtime.root);
			runtime.root.reset();
			runtime.root.setName("root");
			runtime.root.transform()._setInternalID(FXRuntime.ROOT_UUID);
			runtime.root.transform()._setInternalParentID(null);
			runtime.root.transform().localPosition(new Vector3f());
			runtime.root.transform().localRotation(new Quaternionf());
			runtime.root.transform().localScale(new Vector3f(1));
			if (!(runtime instanceof IPhotonRuntimeCreationAccess access)) {
				throw new IllegalStateException("运行时缺少完整复位桥接。");
			}
			access.lobecorp$resetCreationState(new FXData(actors, definition.timeline.instantiate()));
			runtime.setRate(1);
			access.lobecorp$restoreCreationCallbacks();
			if (mode == Mode.HYBRID) {
				// 普通 clear 会永久物化；独占租约复位恢复同一张 Map 的数组布局。
				((PhotonIndexedObjectMap) originalMap).resetForLease();
			} else {
				runtime.objects.clear();
			}

			runtime.addSceneObjectInternal(runtime.root);
			for (var actor : actors) {
				runtime.addSceneObjectInternal(actor);
			}
			for (var actor : actors) {
				actor.setScene(runtime);
			}
			for (var actor : actors) {
				if (actor.transform().parent() == null) {
					actor.transform().parent(runtime.root.transform(), false);
				}
				actor.transform().rebuildChildOrder();
			}
			if (mode == Mode.HYBRID) {
				if (((PhotonIndexedObjectMap) originalMap).isMaterialized()) {
					hybridLayoutFallbacks++;
					throw new IllegalStateException("HYBRID 复位没有保留同实例数组索引布局。");
				}

				hybridIndexedResets++;
			}

			SCOPES.put(runtime, new Scope(definition, mode));
		} catch (RuntimeException | Error failure) {
			resetFailures++;
			Lobecorp.LOGGER.warn("特效租约复位失败，退役后重新创建：{}", definition.key, failure);
			throw failure;
		} finally {
			long elapsed = System.nanoTime() - started;
			resetNanos += elapsed;
			maxResetNanos = Math.max(maxResetNanos, elapsed);
			if (measurementWindow) {
				windowMaxResetNanos = Math.max(windowMaxResetNanos, elapsed);
			}
		}
	}

	public static boolean reusable(FXRuntime runtime) {
		return runtime.objects.values().stream().allMatch(PhotonRuntimeFactory::resetSupported);
	}

	private static boolean resetSupported(IFXObject actor) {
		var type = actor.getClass();
		return type == EmptyFXObject.class
				|| type == ParticleEmitter.class || type == PhotonModelOutput.class
				|| type == PhotonNamedController.class || type == PhotonChildSpawner.class;
	}

	private static void resetActor(IFXObject actor) {
		if (!(actor instanceof FXObject object)) {
			throw new IllegalArgumentException("租约仅支持有完整复位桥接的FXObject。");
		}
		object.setOnUpdateTick(null);
		object.setOnUpdateFrame(null);
		object.setSelfVisible(true);
		object.setSelfTimelineVisible(true);
		object.setHasPhysics(false);
		object.setDelay(0);
		object.setSelfActive(true);
		object.setSelfTimeScale(1);
		object.rCol = object.gCol = object.bCol = object.alpha = 1;
		object.roll = object.oRoll = 0;
		object.setTickHost(null);
		object.setLastHostTick(0);
		PhotonLeasePrivateResetUtil.resetTiming(object);
		if (object instanceof ParticleEmitter particle) {
			particle.waitToAdded.clear();
			clearEmitterReferences(particle);
			PhotonLeasePrivateResetUtil.resetParticleRuntime(particle);
		}
		if (object instanceof PhotonNamedController controller) {
			controller.attach(null);
		}
	}

	private static void clearEmitterReferences(FXObject object) {
		if (object instanceof Emitter emitter) {
			var light = (EmitterLeaseAccessor) emitter;
			light.lobecorp$setLeaseBlockScene(null);
			light.lobecorp$getLeaseLightCache().clear();
			light.lobecorp$getLeaseLightQueryQueue().clear();
			light.lobecorp$setLeaseParallelLightPhase(false);
		}
		if (object instanceof ParticleEmitter particle) {
			var access = (ParticleEmitterLeaseAccessor) particle;
			access.lobecorp$registerLeaseLights(null);
			access.lobecorp$setLeaseCollisionScene(null);
			access.lobecorp$setLeaseCollisionWorld(null);
			access.lobecorp$getLeaseCollisionListeners().clear();
			access.lobecorp$getLeasePendingSubEmitterSpawns().clear();
			access.lobecorp$getLeasePendingCollisionReports().clear();
			access.lobecorp$getLeaseDedupeVerdicts().clear();
			access.lobecorp$setLeaseDeferredFailed(false);
			access.lobecorp$setLeaseCollisionNanos(0);
			access.lobecorp$setLeaseCollisionParallel(false);
			access.lobecorp$setLeaseLastCollisionMillis(0);
			access.lobecorp$setLeaseLastCollisionParallel(false);
			Arrays.fill(access.lobecorp$getLeaseLastCollisionStats(), 0);
			access.lobecorp$setLeaseContactLog(null);
			access.lobecorp$setLeaseSimMotionX(0);
			access.lobecorp$setLeaseSimMotionY(0);
			access.lobecorp$setLeaseSimMotionZ(0);
			access.lobecorp$setLeaseTargetMotionScale(1);
			access.lobecorp$setLeaseCachedSpawnFrame(null);
			access.lobecorp$setLeaseCachedFrameEmitterToWorld(null);
			access.lobecorp$setLeaseCachedFrameSimToWorld(null);
			access.lobecorp$setLeaseActiveForceFields(List.of());
			access.lobecorp$setLeaseExtractRenderer(null);
			access.lobecorp$setLeaseTrailExtractRenderer(null);
			access.lobecorp$setLeaseAraTrailExtractRenderer(null);
		}
	}

	private static void parked(FXRuntime runtime) {
		if (runtime instanceof IPhotonRuntimeCreationAccess access) {
			access.lobecorp$restoreCreationCallbacks();
		}
		if (runtime.timelinePlayer instanceof TimelinePlayerLeaseAccessor timeline) {
			timeline.lobecorp$setLeaseEffect(null);
		}
		for (var actor : runtime.objects.values()) {
			if (actor instanceof ParticleEmitter particle) {
				// 场景尚在时清除同组碰撞记忆，再保持停放对象不可播放。
				particle.reset();
				particle.remove(true);
				particle.waitToAdded.clear();
			}
			if (actor instanceof FXObject object) {
				((FXObjectLeaseAccessor) object).lobecorp$setLeaseExecutor(null);
				clearEmitterReferences(object);
				object.setLevel(null);
				object.setTickHost(null);
				if (object != runtime.root) {
					object.setOnUpdateTick(null);
					object.setOnUpdateFrame(null);
				}
			}
			if (actor instanceof PhotonNamedController controller) {
				controller.attach(null);
			}
			if (actor instanceof PhotonChildSpawner child) {
				child.source(runtime, Set.of());
			}
		}
	}

	public static void onTick() {
		ensureWorld();
		tick++;
		POOL.pulse(tick, frame);
	}

	public static void onFrameEnd() {
		frame++;
		POOL.pulse(tick, frame);
	}

	private static void ensureWorld() {
		var minecraft = Minecraft.getInstance();
		var level = minecraft == null ? null : minecraft.level;
		if (observedLevel != level) {
			invalidateWorld();
			observedLevel = level;
		}
	}

	/// 世界边界只撤销租约，不丢弃加载阶段准备的静态定义。
	public static void invalidateWorld() {
		generation++;
		observedLevel = null;
		SCOPES.clear();
		POOL.clear();
	}

	public static void invalidateAll() {
		definitionGeneration++;
		PREPARED_SOURCES.clear();
		DEFINITIONS.clear();
		RESOURCE_SOURCES.clear();
		invalidateWorld();
	}

	/// 在 Photon 完成 ID 失效后调用；只读取缓存，包含原生递归移除的公开依赖。
	public static void invalidateResources(Collection<Identifier> locations) {
		var ids = locations.stream().map(Identifier::toString).collect(Collectors.toSet());
		var invalidated = new ArrayList<Definition>();
		var sources = RESOURCE_SOURCES.entrySet().iterator();
		while (sources.hasNext()) {
			var entry = sources.next();
			var source = entry.getKey();
			if (source == null || !ids.contains(entry.getValue())
					&& FXHelper.getCachedFX(Identifier.parse(entry.getValue())) == source) {
				continue;
			}

			sources.remove();
			var cached = DEFINITIONS.remove(source);
			if (cached != null) {
				invalidated.add(cached.definition);
			}
		}

		invalidated.forEach(PhotonRuntimeFactory::invalidateDefinition);
	}

	private static void invalidateDefinition(Definition definition) {
		definition.valid = false;
		POOL.invalidate(key -> key instanceof PoolKey pooled && pooled.definition.dependsOn(definition));
	}

	private static void ensureCurrent(Definition definition) {
		if (definition.generation != definitionGeneration || !definition.isValid()) {
			throw new IllegalStateException("特效定义准备已失效，必须按新代次准备。");
		}
	}

	public static Map<String, Long> beginMeasurementWindow() {
		windowMaxResetNanos = 0;
		windowMaxInstanceNanos = 0;
		measurementWindow = true;
		return statistics();
	}

	public static Map<String, Long> endMeasurementWindow() {
		measurementWindow = false;
		return statistics();
	}

	public static Map<String, Long> statistics() {
		var result = new LinkedHashMap<String, Long>();
		result.put("generation", generation);
		result.put("prepared_definitions", preparedDefinitions);
		result.put("prepared_source_cache_hits", preparedSourceCacheHits);
		result.put("prepared_source_compilations", preparedSourceCompilations);
		result.put("compiled_nodes", compiledNodes);
		result.put("created_runtimes", createdRuntimes);
		result.put("initialized_nodes", initializedNodes);
		result.put("native_node_fallbacks", nativeNodeFallbacks);
		result.put("project_requests", projectRequests);
		result.put("project_compilations", projectCompilations);
		result.put("indexed_runtimes", indexedRuntimes);
		result.put("hybrid_lease_acquires", hybridLeaseAcquires);
		result.put("hybrid_fresh_indexed_creates", hybridFreshIndexedCreates);
		result.put("hybrid_indexed_resets", hybridIndexedResets);
		result.put("hybrid_reuse_type_rejections", hybridReuseTypeRejections);
		result.put("hybrid_layout_fallbacks", hybridLayoutFallbacks);
		result.put("unmanaged_creates", unmanagedCreates);
		result.put("native_creates", nativeCreates);
		result.put("reset_failures", resetFailures);
		result.put("definition_ns", definitionNanos);
		result.put("instance_ns", instanceNanos);
		result.put("max_instance_ns", maxInstanceNanos);
		result.put("reset_ns", resetNanos);
		result.put("max_reset_ns", maxResetNanos);
		result.put("window_max_reset_ns", windowMaxResetNanos);
		result.put("window_max_instance_ns", windowMaxInstanceNanos);
		result.put("cached_definitions", (long) DEFINITIONS.size());
		result.put("managed_scopes", (long) SCOPES.size());
		var pool = POOL.statistics();
		result.put("pool_hit", pool.hit());
		result.put("pool_miss", pool.miss());
		result.put("pool_reset", pool.reset());
		result.put("pool_created", pool.created());
		result.put("pool_idle", (long) pool.idle());
		result.put("pool_active", (long) pool.active());
		result.put("pool_quarantine", (long) pool.quarantine());
		result.put("pool_retired", pool.retired());
		return Map.copyOf(result);
	}

	public static class Definition {
		private final String key;
		private boolean valid = true;
		private @Nullable Definition parent;
		private final long generation;
		private final List<PhotonCompiledObject> nodes;
		private final Map<UUID, PhotonCompiledObject> byId;
		private final PhotonTimelineProgram timeline;
		private final @Nullable PhotonFrozenTag snapshot;
		private final PhotonIndexedObjectMap.Layout layout;
		private @Nullable UUID projectTarget;
		private boolean includeChildren;
		private boolean addedController;

		private Definition(String key, long generation, List<PhotonCompiledObject> nodes,
		                   PhotonTimelineProgram timeline, @Nullable PhotonFrozenTag snapshot) {
			this.key = key;
			this.generation = generation;
			this.nodes = List.copyOf(nodes);
			this.timeline = timeline;
			this.snapshot = snapshot;
			var index = new LinkedHashMap<UUID, PhotonCompiledObject>();
			var ids = new ArrayList<UUID>();
			ids.add(FXRuntime.ROOT_UUID);
			for (var node : nodes) {
				if (index.put(node.id(), node) != null) {
					throw new IllegalArgumentException("定义节点ID重复：" + node.id());
				}
				ids.add(node.id());
			}
			byId = Map.copyOf(index);
			layout = PhotonIndexedObjectMap.compile(ids);
		}

		private boolean isValid() {
			return valid && (parent == null || parent.isValid());
		}

		private boolean dependsOn(Definition definition) {
			return this == definition || parent != null && parent.dependsOn(definition);
		}

		public String key() {
			return key;
		}

		public long generation() {
			return generation;
		}

		public int fallbackFields() {
			return nodes.stream().mapToInt(PhotonCompiledObject::fallbackFields).sum();
		}

		public List<String> fallbackReasons() {
			return nodes.stream().flatMap(node -> node.fallbackReasons().stream()).toList();
		}

		public CompoundTag snapshot() {
			if (snapshot != null) {
				return snapshot.compoundCopy();
			}
			var values = new ArrayList<IFXObject>();
			nodes.forEach(node -> values.add(node.instantiate()));
			return new FXData(values, timeline.instantiate()).serializeNBT(Platform.getFrozenRegistry());
		}
	}

	public static class Lease {
		private final @Nullable FXRuntime fresh;
		private final @Nullable PhotonRuntimePool.Lease pooled;
		private final long generation;
		private boolean closed;

		private Lease(@Nullable FXRuntime fresh, @Nullable PhotonRuntimePool.Lease pooled, long generation) {
			this.fresh = fresh;
			this.pooled = pooled;
			this.generation = generation;
		}

		public boolean current() {
			return !closed && generation == PhotonRuntimeFactory.generation && (pooled == null || pooled.current());
		}

		public long id() {
			return pooled == null ? System.identityHashCode(this) : pooled.id();
		}

		public FXRuntime runtime() {
			if (!current()) {
				throw new IllegalStateException("特效租约已关闭或失效。");
			}
			return pooled == null ? Objects.requireNonNull(fresh) : pooled.runtime();
		}

		public void close(boolean force) {
			if (closed) {
				return;
			}
			closed = true;
			if (pooled != null) {
				POOL.release(pooled, force);
			} else if (fresh != null) {
				fresh.destroy(force);
			}
		}
	}
}
