package org.unitego.lobecorp.client.photon.runtime.creation;

import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXInstanceObject;
import com.lowdragmc.photon.client.fx.instance.InstanceBaker;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.trail.TrailEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraTrailEmitter;
import com.lowdragmc.photon.client.gameobject.forcefield.ForceFieldObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.NbtOps;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.util.photon.creation.PhotonFrozenTag;
import org.unitego.lobecorp.util.photon.creation.PhotonStatePlanUtil;
import org.unitego.lobecorp.util.photon.creation.PhotonStatePlanUtil.IStatePlan;
import org.unitego.lobecorp.util.photon.model.PhotonModelSourceUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler;
import org.unitego.lobecorp.util.photon.model.PhotonModelAssetUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/// 定义拥有构造指令与冻结数据；实例拥有配置、运行槽、变换及渲染工作区。
public class PhotonCompiledObject {
	private final PhotonFrozenTag snapshot;
	private @Nullable InstanceBaker.Placement placement;
	private final BaseState base;
	private final String type;
	private final Class<?> objectClass;
	private final Supplier<IFXObject> constructor;
	private final Consumer<IFXObject> initializer;
	private final BiConsumer<IFXObject, IFXObject> transfer;
	private final List<String> fallbackReasons;
	private final int fallbackFields;
	private final boolean nativeFallback;
	private final List<IStatePlan> statePlans;
	private final @Nullable List<PhotonChildSpawner> childPrograms;
	private final @Nullable PreparedModel preparedModel;
	private final @Nullable PhotonNamedController.CompiledDefinition preparedNamed;

	private PhotonCompiledObject(PhotonFrozenTag snapshot, BaseState base, String type, Class<?> objectClass,
	                             Supplier<IFXObject> constructor, Consumer<IFXObject> initializer,
	                             BiConsumer<IFXObject, IFXObject> transfer, List<String> fallbackReasons,
	                             int fallbackFields, boolean nativeFallback, List<IStatePlan> statePlans,
	                             @Nullable List<PhotonChildSpawner> childPrograms, @Nullable PreparedModel preparedModel,
	                             @Nullable PhotonNamedController.CompiledDefinition preparedNamed) {
		this.snapshot = snapshot;
		this.base = base;
		this.type = type;
		this.objectClass = objectClass;
		this.constructor = constructor;
		this.initializer = initializer;
		this.transfer = transfer;
		this.fallbackReasons = List.copyOf(fallbackReasons);
		this.fallbackFields = fallbackFields;
		this.nativeFallback = nativeFallback;
		this.statePlans = List.copyOf(statePlans);
		this.childPrograms = childPrograms == null ? null : List.copyOf(childPrograms);
		this.preparedModel = preparedModel;
		this.preparedNamed = preparedNamed;
	}

	public static PhotonCompiledObject compile(IFXObject authored) {
		Objects.requireNonNull(authored);
		var encoded = IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, authored).getOrThrow();

		if (!(encoded instanceof CompoundTag wrapper) || !(wrapper.get("data") instanceof CompoundTag data)) {
			throw new IllegalArgumentException("Photon 节点没有复合 wrapper/data。");
		}

		// 原生规范化只发生在编译期，不保留 authored、canonical 或其 Transform。
		var canonical = IFXObject.CODEC.parse(NbtOps.INSTANCE, wrapper.copy()).getOrThrow();
		var frozen = new PhotonFrozenTag(wrapper);
		var base = BaseState.capture(canonical);
		var type = wrapper.getStringOr("type", "");
		var factory = canonical.getFXObjectType();

		if (!known(canonical.getClass())) {
			var plan = nativePlan(frozen, base, type, canonical.getClass(), factory,
					"未知或未支持完整复位的对象类型：" + canonical.getClass().getName());
			if (authored instanceof FXInstanceObject instance) {
				// 与原生 FXData.copy 一致，placement 属于定义来源，不是运行播放器。
				plan.placement = instance.getPlacement();
			}
			return plan;
		}

		var extras = new HashSet<>(data.keySet());
		extras.removeAll(schemaKeys(canonical.getClass()));
		extras.remove("version");
		extras.remove("_additional");
		boolean preservesSource = canonical instanceof PhotonModelOutput || canonical instanceof PhotonNamedController
				|| canonical instanceof PhotonChildSpawner;

		if (!extras.isEmpty() && !preservesSource) {
			return nativePlan(frozen, base, type, canonical.getClass(), factory, "不透明节点字段：" + extras);
		}

		try {
			return compileKnown(frozen, base, type, canonical, data, factory);
		} catch (IOException invalid) {
			throw new IllegalArgumentException("Photon 节点定义编译失败：" + type, invalid);
		}
	}

	private static PhotonCompiledObject compileKnown(PhotonFrozenTag snapshot, BaseState base, String type,
	                                                 IFXObject canonical, CompoundTag data, FXObjectType factory) throws IOException {
		var reasons = new ArrayList<String>();
		var plans = new ArrayList<IStatePlan>();
		int fallbacks = 0;
		Supplier<IFXObject> constructor = factory::create;
		Consumer<IFXObject> specialized = _ -> {
		};
		BiConsumer<IFXObject, IFXObject> transferSpecialized = (_, _) -> {
		};
		List<PhotonChildSpawner> childPrograms = null;
		PhotonModelOutput.PreparedSource modelSource = null;
		PhotonNamedController.CompiledDefinition namedSource = null;
		var excluded = new HashSet<>(Set.of("name", "transform"));

		if (canonical instanceof PhotonModelOutput model) {
			var source = new PhotonFrozenTag(data);
			var asset = new PhotonFrozenTag(model.config.assetSnapshot());
			Supplier<CompiledModelConfig> configConstructor = () -> new CompiledModelConfig(asset);
			var canonicalConfig = PhotonModelAssetUtil.adaptObject(data).getCompoundOrEmpty("config");
			var configPlan = PhotonStatePlanUtil.compileFields(model.config, canonicalConfig,
					configConstructor, Set.of("assetSnapshot"));
			var prepared = model.config.source == PhotonModelOutputConfig.Source.FILE
					? null : new SharedPrepared(configPlan, model.config, asset);
			modelSource = prepared;
			constructor = () -> new PhotonModelOutput(configConstructor.get(), prepared);
			specialized = target -> {
				var output = (PhotonModelOutput) target;
				configPlan.apply(output.config);
				installAsset(output.config, asset);
				output.compiledSource(source);
			};
			transferSpecialized = (live, target) -> {
				var from = (PhotonModelOutput) live;
				var to = (PhotonModelOutput) target;
				var currentAsset = from.config instanceof CompiledModelConfig config
						? config.sharedAsset() : new PhotonFrozenTag(from.config.assetSnapshot());
				configPlan.transfer(from.config, to.config);
				// 与类型 Codec 的 preserved.merge(data) 一致，来源中的依赖键仍参与当前配置。
				to.config.assetMaterialDependencies = from.source().getCompoundOrEmpty("config")
						.getCompoundOrEmpty("assetMaterialDependencies").copy().merge(to.config.assetMaterialDependencies);
				installAsset(to.config, currentAsset);
				to.compiledSource(from.compileSource());
				to.compiledPreparation(prepared != null && prepared.matches(to.config, currentAsset) ? prepared : null);
			};
			excluded.add("config");
			plans.add(configPlan);
		} else if (canonical instanceof PhotonNamedController controller) {
			var definition = controller.compileDefinition();
			namedSource = definition;
			specialized = target -> definition.install((PhotonNamedController) target);
			transferSpecialized = (live, target) -> {
				try {
					((PhotonNamedController) live).compileDefinition().install((PhotonNamedController) target);
				} catch (IOException invalid) {
					throw new UncheckedIOException(invalid);
				}
			};
			excluded.add("definition");
			reasons.addAll(definition.fallbackReasons());
			fallbacks += definition.fallbackReasons().size();
		} else if (canonical instanceof PhotonChildSpawner spawner) {
			var source = new PhotonFrozenTag(data);
			excluded.add("config");
			var config = spawner.config;
			var configPlan = config.getClass() == PhotonSpawnConfig.class
					? PhotonStatePlanUtil.compileFields(config, data.getCompoundOrEmpty("config"),
					PhotonSpawnConfig::new, Set.of()) : null;
			if (configPlan != null) {
				plans.add(configPlan);
			}
			var preparedChildren = List.of(spawner);
			specialized = target -> {
				var copy = (PhotonChildSpawner) target;
				if (configPlan != null) {
					configPlan.apply(copy.config);
				}
				copy.rememberSource(source.compoundCopy());
			};
			transferSpecialized = (live, target) -> {
				var from = (PhotonChildSpawner) live;
				var to = (PhotonChildSpawner) target;
				if (configPlan != null) {
					configPlan.transfer(from.config, to.config);
				}
				to.rememberSource(from.source());
			};
			childPrograms = preparedChildren;
		}

		var state = PhotonStatePlanUtil.compileFields(canonical, data, factory::create, excluded);
		plans.add(state);
		var initializeSpecialized = specialized;
		var copySpecialized = transferSpecialized;
		Consumer<IFXObject> initialize = target -> {
			state.apply(target);
			initializeSpecialized.accept(target);
		};
		BiConsumer<IFXObject, IFXObject> transfer = (live, target) -> {
			state.transfer(live, target);
			copySpecialized.accept(live, target);
		};
		PreparedModel model = canonical instanceof PhotonModelOutput output
				? new PreparedModel(new RendererSetting.Runtime(output.config.overrideRenderer
				? output.config.renderer : output.config.modelRenderer) {
		}, output.geometryMode(), modelSource) : null;
		return new PhotonCompiledObject(snapshot, base, type, canonical.getClass(), constructor, initialize,
				transfer, reasons, fallbacks, false, plans, childPrograms, model, namedSource);
	}

	private static void installAsset(PhotonModelOutputConfig target, PhotonFrozenTag asset) {
		if (target instanceof CompiledModelConfig config) {
			config.restoreSnapshot(asset);
		} else {
			target.setAssetSnapshot(asset.copy());
		}
	}

	private static boolean known(Class<?> type) {
		return type == EmptyFXObject.class || type == ParticleEmitter.class || type == BeamEmitter.class
				|| type == AraTrailEmitter.class || type == ForceFieldObject.class
				|| type == TrailEmitter.class || type == PhotonModelOutput.class
				|| type == PhotonNamedController.class || type == PhotonChildSpawner.class;
	}

	private static Set<String> schemaKeys(Class<?> type) {
		var result = new HashSet<String>();

		for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
			for (var field : current.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) {
					continue;
				}

				var key = PersistedParser.persistedKey(field);

				if (key != null) {
					result.add(key);
				}
			}
		}

		return result;
	}

	private static PhotonCompiledObject nativePlan(PhotonFrozenTag snapshot, BaseState base, String type,
	                                               Class<?> objectClass, FXObjectType factory, String reason) {
		Supplier<IFXObject> constructor = () -> IFXObject.CODEC.parse(NbtOps.INSTANCE, snapshot.compoundCopy()).getOrThrow();
		Consumer<IFXObject> initializer = target -> {
			var data = snapshot.compoundCopy().getCompoundOrEmpty("data");
			var fixed = factory.fixData(data, data.getIntOr("version", 0));
			PersistedParser.createCodec(() -> target).parse(NbtOps.INSTANCE, fixed).getOrThrow();
			rememberNativeSource(target, data);
		};
		return new PhotonCompiledObject(snapshot, base, type, objectClass, constructor, initializer,
				(live, target) -> {
					var current = IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, live).getOrThrow();
					var wrapper = (CompoundTag) current;
					var data = wrapper.getCompoundOrEmpty("data");
					var fixed = factory.fixData(data, data.getIntOr("version", 0));
					PersistedParser.createCodec(() -> target).parse(NbtOps.INSTANCE, fixed).getOrThrow();
					rememberNativeSource(target, data);
				}, List.of(reason + "；完整原生快照回退，PROJECT 当前 wrapper 原生恢复"), 1, true, List.of(), null, null, null);
	}

	private static void rememberNativeSource(IFXObject target, CompoundTag data) {
		if (target instanceof PhotonModelOutput model) {
			model.rememberSource(data);
		} else if (target instanceof PhotonNamedController controller) {
			controller.rememberSource(data);
		} else if (target instanceof PhotonChildSpawner spawner) {
			spawner.rememberSource(data);
		}
	}

	public IFXObject instantiate() {
		var result = Objects.requireNonNull(constructor.get());

		if (!nativeFallback) {
			initialize(result);
		}

		if (result instanceof FXInstanceObject instance) {
			instance.setPlacement(placement);
		}
		return result;
	}

	/// 恢复未接入运行场景的独占节点；场景和执行器的接线由外层工厂负责。
	public void initialize(IFXObject target) {
		Objects.requireNonNull(target);

		requireDetached(target);
		base.apply(target);
		initializer.accept(target);
	}

	public IFXObject instantiateFrom(IFXObject live) {
		Objects.requireNonNull(live);

		if (nativeFallback) {
			if (!Objects.equals(type, live.getFXObjectType().name())) {
				throw new IllegalArgumentException("当前作者节点类型与原生回退定义不匹配。");
			}

			var result = IFXObject.CODEC.parse(NbtOps.INSTANCE,
					IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, live).getOrThrow()).getOrThrow();
			if (live instanceof FXInstanceObject from && result instanceof FXInstanceObject to) {
				to.setPlacement(from.getPlacement());
			}
			return result;
		}

		var result = Objects.requireNonNull(constructor.get());
		initializeFrom(live, result);
		return result;
	}

	public void initializeFrom(IFXObject live, IFXObject target) {
		Objects.requireNonNull(live);
		Objects.requireNonNull(target);

		if (live == target || live.getFXObjectType() != target.getFXObjectType()
				|| !nativeFallback && live.getClass() != objectClass) {
			throw new IllegalArgumentException("当前作者节点不匹配此构造程序。");
		}

		requireDetached(target);
		BaseState.captureLive(live).apply(target);
		transfer.accept(live, target);
	}

	private void requireDetached(IFXObject target) {
		if (target.getClass() != objectClass || target.getScene() != null || target.getEffectExecutor() != null
				|| target.transform().parent() != null || !target.transform().children().isEmpty()) {
			throw new IllegalArgumentException("只能初始化同类型且未接入场景的独占节点。");
		}
	}

	public UUID id() {
		return base.id();
	}

	public String type() {
		return type;
	}

	public boolean nativeFallback() {
		return nativeFallback;
	}

	/// 静态子视图只复用原定义中的精确节点类型；动态配置仍由 live transfer 恢复。
	public boolean matchesType(IFXObject object) {
		return object.getClass() == objectClass && type.equals(object.getFXObjectType().name());
	}

	public boolean matchesNamedDefinition(PhotonNamedController controller) {
		return preparedNamed != null && preparedNamed.matches(controller);
	}

	public int fallbackFields() {
		return fallbackFields + statePlans.stream().mapToInt(IStatePlan::fallbackFields).sum();
	}

	public List<String> fallbackReasons() {
		var reasons = new ArrayList<>(fallbackReasons);
		statePlans.forEach(plan -> reasons.addAll(plan.fallbackReasons()));
		return List.copyOf(reasons);
	}

	/// 返回编译期捕获的未绑定子发射器，只供静态结构/配置准备读取，不参与运行实例。
	public List<PhotonChildSpawner> childPrograms() {
		return childPrograms == null ? List.of() : childPrograms;
	}

	/// 预备模型与命名播放的静态成果，不创建运行实例或持有场景。
	public void prepareStatic() throws IOException {
		if (preparedModel != null) {
			preparedModel.prepareStatic();
		}
		if (preparedNamed != null) {
			preparedNamed.prepareStatic();
		}
	}

	/// 静态模型准备只读冻结配置与资产，不创建运行实例或持有场景。
	public @Nullable PreparedModel preparedModel() {
		return preparedModel;
	}

	public CompoundTag snapshot() {
		return snapshot.compoundCopy();
	}

	private record BaseState(String name, UUID id, @Nullable UUID parent, List<UUID> children,
	                         float px, float py, float pz, float rx, float ry, float rz, float rw,
	                         float sx, float sy, float sz) {
		private static BaseState capture(IFXObject object) {
			var transform = object.transform();
			var position = transform.localPosition();
			var rotation = transform.localRotation();
			var scale = transform.localScale();
			return new BaseState(object.getName(), transform.id(), transform._getInternalParentID(),
					List.copyOf(transform._getInternalChildID()), position.x, position.y, position.z,
					rotation.x, rotation.y, rotation.z, rotation.w, scale.x, scale.y, scale.z);
		}

		private static BaseState captureLive(IFXObject object) {
			var value = capture(object);
			var rotation = new Quaternionf(value.rx, value.ry, value.rz, value.rw).normalize();
			return new BaseState(value.name, value.id, value.parent, value.children,
					value.px, value.py, value.pz, rotation.x, rotation.y, rotation.z, rotation.w,
					value.sx, value.sy, value.sz);
		}

		private void apply(IFXObject target) {
			target.setName(name);
			var transform = target.transform();
			transform.localPosition(new Vector3f(px, py, pz));
			transform.localRotation(new Quaternionf(rx, ry, rz, rw));
			transform.localScale(new Vector3f(sx, sy, sz));
			transform._setInternalID(id);
			transform._setInternalParentID(parent);
			transform._setInternalChildID(new ArrayList<>(children));
		}
	}

	private record ModelSource(PhotonModelOutputConfig.Source source, @Nullable Identifier resource,
	                           String file, boolean flipV, String snapshotSource, PhotonFrozenTag asset,
	                           PhotonFrozenTag materials, List<Identifier> dependencies) {
		private boolean matches(PhotonModelOutputConfig current, PhotonFrozenTag currentAsset) {
			return source == current.source && Objects.equals(resource, current.resource)
					&& Objects.equals(file, current.file) && flipV == current.flipObjV
					&& Objects.equals(snapshotSource, current.snapshotSource)
					&& asset.matches(currentAsset) && materials.matches(current.assetMaterialDependencies)
					&& dependencies.equals(current.assetDependencies);
		}
	}

	/// 静态准备与实例消费同一来源，不持有运行模型或场景。
	public record PreparedModel(RendererSetting.Runtime config, VertexFormat.Mode mode,
								@Nullable PhotonModelOutput.PreparedSource source) {
		public void prepareStatic() throws IOException {
			if (source == null) {
				return;
			}

			try {
				source.sampler();
			} catch (UncheckedIOException invalid) {
				throw invalid.getCause();
			}
		}
	}

	/// 定义共享延迟拓扑与纯 CPU 采样程序，实例独占求值工作区。
	private static class SharedPrepared implements PhotonModelOutput.PreparedSource {
		private final IStatePlan config;
		private final ModelSource source;
		private @Nullable PhotonModelPoseSampler sampler;

		private SharedPrepared(IStatePlan config, PhotonModelOutputConfig values, PhotonFrozenTag asset) {
			this.config = config;
			source = new ModelSource(values.source, values.resource, values.file, values.flipObjV,
					values.snapshotSource, asset, new PhotonFrozenTag(values.assetMaterialDependencies),
					List.copyOf(values.assetDependencies));
		}

		@Override
		public boolean matches(PhotonModelOutputConfig current) {
			return current instanceof CompiledModelConfig config && matches(current, config.sharedAsset());
		}

		private boolean matches(PhotonModelOutputConfig current, PhotonFrozenTag asset) {
			return source.matches(current, asset);
		}

		@Override
		public synchronized PhotonModelPoseSampler sampler() throws IOException {
			if (sampler == null) {
				var values = (PhotonModelOutputConfig) config.create();
				sampler = PhotonModelSourceUtil.prepare(values);
			}

			return sampler;
		}

		@Override
		public synchronized PhotonModelTopologyUtil.Prepared get() {
			try {
				return sampler().prepared();
			} catch (IOException invalid) {
				throw new UncheckedIOException(invalid);
			}
		}
	}
}
