package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.util.photon.model.PhotonModelRuntimeCopyUtil;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Color;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;
import com.sun.management.ThreadMXBean;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteArrayTag;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.config.photon.PhotonRepeatedEffectConfig;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.util.photon.playback.PhotonRuntimeCopyUtil;

import java.lang.management.ManagementFactory;
import java.io.IOException;
import org.unitego.lobecorp.util.photon.model.PhotonModelAssetUtil;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// 主窗口结束归档后独立执行；使用当前已注册的真实 Codec，不启动或推进任何运行态。
public class PhotonRuntimeCopyNativeCheck {
	private static final int REPETITIONS = 128;
	private static volatile Object sink;
	private final JsonArray cases = new JsonArray();
	private final JsonArray costs = new JsonArray();
	private final List<Throwable> failures = new ArrayList<>();

	public static void verify(BenchClientContext context, String label) throws Exception {
		var check = new PhotonRuntimeCopyNativeCheck();
		var report = new JsonObject();
		report.addProperty("phase", "verify_after_main_window_archive");
		report.addProperty("main_window_sample", false);
		report.addProperty("native_model_copy_property", PhotonModelRuntimeCopyUtil.NATIVE_MODEL_COPY_PROPERTY);
		report.addProperty("native_model_copy_enabled", PhotonModelRuntimeCopyUtil.nativeModelCopyEnabled());
		report.addProperty("model_candidate_checks", "typed candidate checked independently even when deepCopy is forced native");
		report.addProperty("start_epoch_millis", System.currentTimeMillis());
		report.addProperty("start_nano_time", System.nanoTime());
		report.addProperty("scope", "registered IFXObject.CODEC equivalence; borrowed real FX definitions remain unmodified");
		report.addProperty("excluded", "FPS, rendering, emit, simulation, loading performance and full runtime creation are not measured");
		check.run();
		report.add("cases", check.cases);
		report.addProperty("passed", check.failures.isEmpty());
		report.addProperty("end_epoch_millis", System.currentTimeMillis());
		report.addProperty("end_nano_time", System.nanoTime());
		var costReport = new JsonObject();
		costReport.addProperty("phase", "verify_after_main_window_archive");
		costReport.addProperty("scope", "FXData.copy(true) object wrappers plus timeline.copy; not fullFX playback or FPS benefit");
		costReport.addProperty("baseline", "each object uses original encodeStart.result.flatMap(CODEC.parse.result); identical timeline.copy and list collection");
		costReport.addProperty("order", "ABBA; A=native full-wrapper round-trip, B=current deepCopy; 128 repetitions per block");
		costReport.add("blocks", check.costs);
        context.artifacts().write(label + "-runtime-copy-native.json", "application/json", report.toString());
        context.artifacts().write(label + "-runtime-copy-cost.json", "application/json", costReport.toString());
        // UUID保证接续执行不会覆盖上一次逐对象原件；旧artifact名称和verify签名保持不变。
        if (Boolean.getBoolean(PhotonRuntimeCopyLedger.ENABLED_PROPERTY)) {
            var ledger = new PhotonRuntimeCopyLedger().collect();
            context.artifacts().write(label + "-runtime-copy-ledger-" + UUID.randomUUID() + ".json", "application/json", ledger.toString());
        }

        if (!check.failures.isEmpty()) {
			var failure = new IllegalStateException("runtime_copy_native_check_failed:" + check.failures.size());
			check.failures.forEach(failure::addSuppressed);
			throw failure;
		}
	}

	private void run() {
		runCase("registered_codec", () -> Objects.requireNonNull(Platform.getFrozenRegistry(), "frozen registry unavailable"));
		runCase("typed_config_all_persisted_fields", this::allConfigFields);
		runCase("spawner_unknown_and_current_config", this::spawnerUnknown);
		runCase("definition_replace_and_mutation_visible", this::definitionChanges);
		runCase("hierarchy_ids_without_live_parent_sharing", this::hierarchy);
		runCase("fresh_runtime_slots_and_two_instance_overrides", this::runtimeState);
		runCase("native_number_functions_constant_random_curve_gradient", this::functions);
		runCase("invalid_current_values_and_exception_boundary", this::invalidValues);
		runCase("raw_codec_enum_version_resource_guards", this::rawGuards);
		runCase("subclass_callbacks_use_native_parse", this::subclasses);
		runCase("real_model_config_wrapper_transform_independence", this::modelConfigAndOwnership);
		runCase("model_typed_all_persisted_and_configurable_fields", this::modelTypedFields);
		runCase("model_fresh_runtime_renderers_hierarchy_and_callbacks", this::modelRuntimeAndHierarchy);
		runCase("model_null_normalization_extensions_and_exceptions", this::modelFallbacks);
		runCase("model_encoded_version_resource_and_legacy_guards", this::modelEncodedGuards);
		runCase("model_canonical_borrow_and_encoded_mutable_ownership", this::modelCanonicalBorrowOwnership);
		// 真实资源在主窗口之后读取；不把配置 fixture 的成本伪装成真实 FX 成本。
		for (var id : List.of(PhotonRepeatedEffectConfig.NATIVE_FX, "lobecorp:the_queen_of_hatred/laser",
				"lobecorp:the_queen_of_hatred/light_ball")) {
			runCase("real_fx:" + id, () -> realFx(Identifier.parse(id)));
		}
	}

	private void allConfigFields() throws IllegalAccessException {
		var source = new PhotonSpawnConfig();
		source.template.setTransformId(UUID.fromString("f014521a-a106-47b1-98f2-77de7fd7fd01"));
		source.fxLocation = Identifier.parse("lobecorp:runtime_copy_test");
		for (var value : List.of(source, source.shape, source.parameters)) {
			for (var field : value.getClass().getDeclaredFields()) {
				if (!field.isAnnotationPresent(Persisted.class) || Modifier.isFinal(field.getModifiers())) {
					continue;
				}

				if (field.getType() == boolean.class) {
					field.setBoolean(value, !field.getBoolean(value));
				} else if (field.getType() == int.class) {
					field.setInt(value, 37);
				} else if (field.getType() == float.class) {
					field.setFloat(value, 2.5f);
				} else if (field.getType().isEnum()) {
					field.set(value, field.getType().getEnumConstants()[1]);
				} else if (field.getType() == String.class) {
					field.set(value, "copy_target");
				}
			}
		}

		var target = new PhotonSpawnConfig();
		PhotonRuntimeCopyUtil.copyConfig(source, target);
		independentConfig(source, target);
		for (var pair : List.of(new Object[]{source, target},
				new Object[]{source.shape, target.shape}, new Object[]{source.parameters, target.parameters})) {
			for (var field : pair[0].getClass().getDeclaredFields()) {
				if (field.isAnnotationPresent(Persisted.class) && !Modifier.isFinal(field.getModifiers())) {
					equal(field.get(pair[0]), field.get(pair[1]));
				}
			}
		}

		equal(source.template.getTransformId(), target.template.getTransformId());
		source.shape.size.x = 99;
		equal(1f, target.shape.size.x);
		source.template.setTransformId(null);
		equal(true, target.template.getTransformId() != null);
	}

	private void spawnerUnknown() {
		var source = new PhotonChildSpawner();
		source.setName("runtime_copy");
		var old = new CompoundTag();
		old.putString("unknown_root", "retained");
		var config = new CompoundTag();
		config.putString("unknown_config", "retained");
		config.putString("fxLocation", "lobecorp:stale");
		var template = new CompoundTag();
		template.putString("uuid", UUID.randomUUID().toString());
		config.put("template", template);
		old.put("config", config);
		source.rememberSource(old);
		source.config.duration = 73;
		source.config.fxLocation = null;
		// 合并保留旧 template.uuid，typed 值并非 encoded payload 的实际值，必须 fallback。
		var stale = (PhotonChildSpawner) compare(source);
		equal(true, stale.config.template.getTransformId() != null);
		var encoded = encode(source).getCompoundOrEmpty("data");
		equal(false, encoded.getCompoundOrEmpty("config").getString("fxLocation").orElse("").equals("lobecorp:stale"));
		source.config.template.setTransformId(UUID.randomUUID());
		var copy = (PhotonChildSpawner) compare(source);
		equal("retained", copy.source().getStringOr("unknown_root", ""));
		equal("retained", copy.source().getCompoundOrEmpty("config").getStringOr("unknown_config", ""));
		equal(73, copy.config.duration);
		independentConfig(source.config, copy.config);
		copy.config.shape.size.x = 42;
		equal(1f, source.config.shape.size.x);
		copy.config.template.setTransformId(null);
		equal(true, source.config.template.getTransformId() != null);
	}

	private void definitionChanges() {
		var controller = new PhotonNamedController();
		var old = new CompoundTag();
		old.putString("unknown_root", "retained");
		var oldDefinition = new CompoundTag();
		oldDefinition.putInt("version", -1);
		old.put("definition", oldDefinition);
		controller.rememberSource(old);
		controller.definition(new CompoundTag());
		var first = (PhotonNamedController) compare(controller);
		equal(new CompoundTag(), first.definition());
		equal("retained", first.source().getStringOr("unknown_root", ""));
		var changed = new CompoundTag();
		changed.putInt("version", PhotonNamedLibrary.VERSION);
		changed.putString("defaultAnimation", "changed_after_first_copy");
		controller.definition(changed);
		var second = (PhotonNamedController) compare(controller);
		equal(changed, second.definition());
		equal(new CompoundTag(), first.definition());
		var returned = second.definition();
		returned.putString("defaultAnimation", "external_mutation");
		equal(changed, second.definition());
		var returnedSource = second.source();
		returnedSource.remove("definition");
		equal(changed, second.source().getCompoundOrEmpty("definition"));
	}

	private void hierarchy() {
		var parent = new PhotonNamedController();
		var source = new PhotonChildSpawner();
		var child = new PhotonNamedController();
		source.transform().parent(parent.transform(), false);
		child.transform().parent(source.transform(), false);
		source.transform().localPosition(new Vector3f(2, -3, 5));
		source.transform().localRotation(new Quaternionf(1, 2, 3, 4));
		source.transform().localScale(new Vector3f(-2, 0.5f, 4));
		var parentChildren = List.copyOf(parent.transform().children());
		var sourceChildren = List.copyOf(source.transform().children());
		var copy = compare(source);
		equal(source.transform().id(), copy.transform().id());
		equal(source.transform()._getInternalParentID(), copy.transform()._getInternalParentID());
		equal(source.transform()._getInternalChildID(), copy.transform()._getInternalChildID());
		equal(null, copy.transform().parent());
		equal(List.of(), copy.transform().children());
		equal(null, copy.getScene());
		equal(parentChildren, parent.transform().children());
		equal(sourceChildren, source.transform().children());
		different(source.transform().localPosition(), copy.transform().localPosition());
		different(source.transform().localRotation(), copy.transform().localRotation());
		different(source.transform().localScale(), copy.transform().localScale());
		copy.transform().localPosition().x = 900;
		equal(2f, source.transform().localPosition().x);
		// nullable parent ID 由当前编码的 _NULL_ 覆盖旧 source；不能重新连接旧 parent。
		source.rememberSource(encode(source).getCompoundOrEmpty("data"));
		source.transform().parent(null, false);
		var detached = compare(source);
		equal(null, detached.transform()._getInternalParentID());
	}

	private void runtimeState() {
		var source = new PhotonChildSpawner();
		source.slot("count").setRaw(NumberFunction.constant(99));
		source.slot("duration").setRaw(777);
		source.paused(true);
		source.stopEmission(true);
		source.setSelfActive(false);
		source.setSelfTimeScale(7);
		source.setSelfVisible(false);
		var first = (PhotonChildSpawner) compare(source);
		var second = (PhotonChildSpawner) compare(source);
		equal(false, first.paused());
		equal(false, first.lobecorp$emissionStopped());
		equal(true, first.isSelfActive());
		equal(1f, first.getSelfTimeScale());
		equal(true, first.isSelfVisible());
		equal(false, first.slot("count").isOverridden());
		equal(false, first.slot("duration").isOverridden());
		equal(List.of(), first.instanceIds());
		equal(null, first.preparedTemplate());
		equal(null, first.getEffectExecutor());
		independentConfig(first.config, second.config);
		different(first.slot("count"), second.slot("count"));
		first.slot("count").setRaw(NumberFunction.constant(11));
		second.slot("count").setRaw(NumberFunction.constant(22));
		equal(11, first.function("count").get(0, () -> 0f));
		equal(22, second.function("count").get(0, () -> 0f));
		equal(99, source.function("count").get(0, () -> 0f));
	}

	private void functions() {
		var gradient = new Gradient(0x7f123456);
		gradient.getGradientColor().addAlpha(0.37f, 0.25f);
		gradient.getGradientColor().addRGB(0.63f, 0.2f, 0.4f, 0.8f);
		var curve = new Curve(0, 1, -3, 7, 2, "age", "value");
		curve.setLockControlPoint(false);
		equal(false, PhotonRuntimeCopyUtil.supportsFunction(curve));
		equal(true, curve.copy().isLockControlPoint());
		var functions = List.of(new Constant((byte) 3), new Constant((short) 4), new Constant(5),
				new Constant(6L), new Constant(7.25f), new Constant(8.5), new RandomConstant(-2f, 8f),
				new Color(0x7fabcdef), curve, gradient);
		for (var function : functions) {
			var source = new PhotonChildSpawner();
			source.config.count = function;
			source.config.parameters.size = new NumberFunction3(function, function, function);
			source.config.parameters.color = function;
			var copy = (PhotonChildSpawner) compare(source);
			independentConfig(source.config, copy.config);
			var nativeFunction = NumberFunction.CODEC.parse(NbtOps.INSTANCE,
					NumberFunction.CODEC.encodeStart(NbtOps.INSTANCE, function).getOrThrow()).getOrThrow();
			equal(NumberFunction.CODEC.encodeStart(NbtOps.INSTANCE, nativeFunction).getOrThrow(),
					NumberFunction.CODEC.encodeStart(NbtOps.INSTANCE, copy.config.count).getOrThrow());
			for (float t : new float[]{0, 0.37f, 1}) {
				equal(nativeFunction.get(t, () -> 0.41f), copy.config.count.get(t, () -> 0.41f));
			}
		}
		var source = new PhotonChildSpawner();
		source.config.parameters.color = gradient;
		var copied = (Gradient) ((PhotonChildSpawner) compare(source)).config.parameters.color;
		different(gradient.getGradientColor(), copied.getGradientColor());
		different(gradient.getGradientColor().getAP().getFirst(), copied.getGradientColor().getAP().getFirst());
		different(gradient.getGradientColor().getRgbP().getFirst(), copied.getGradientColor().getRgbP().getFirst());
		gradient.getGradientColor().getAP().clear();
		equal(false, copied.getGradientColor().getAP().isEmpty());
	}

	private void invalidValues() {
		var spawner = new PhotonChildSpawner();
		spawner.config.duration = -1;
		equivalentOutcome(spawner);
		spawner.config.duration = 10;
		spawner.config.parameters.playbackSpeed = Float.NaN;
		equivalentOutcome(spawner);
		spawner.config.parameters.playbackSpeed = PhotonSpawnConfig.MAX_PLAYBACK_SPEED + 1;
		equivalentOutcome(spawner);
		spawner.config.parameters.playbackSpeed = 1;
		spawner.config.source = null;
		equivalentOutcome(spawner);
		var controller = new PhotonNamedController();
		var invalidDefinition = new CompoundTag();
		invalidDefinition.putInt("version", -1);
		controller.definition(invalidDefinition);
		equivalentOutcome(controller);
		var color = new Color();
		color.setNumber(12.75);
		spawner = new PhotonChildSpawner();
		spawner.config.count = color;
		compare(spawner);
		spawner.config.count = new Constant(new AtomicInteger(3));
		compare(spawner);
	}

	private void rawGuards() {
		var spawner = new PhotonChildSpawner();
		for (var key : List.of("source", "mode", "endBehavior")) {
			var wrapper = encode(spawner);
			wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config").putString(key, "invalid_enum");
			equal(null, IFXObject.CODEC.parse(NbtOps.INSTANCE, wrapper).result().orElse(null));
		}
		for (var invalid : List.<Tag>of(StringTag.valueOf("future"), IntTag.valueOf(1))) {
			var wrapper = encode(spawner);
			wrapper.getCompoundOrEmpty("data").put("version", invalid);
			equal(null, IFXObject.CODEC.parse(NbtOps.INSTANCE, wrapper).result().orElse(null));
		}
		var resource = encode(spawner);
		resource.getCompoundOrEmpty("data").getCompoundOrEmpty("config").putString("fxLocation", "BAD RESOURCE ID");
		equal(null, IFXObject.CODEC.parse(NbtOps.INSTANCE, resource).result().orElse(null));
		var controller = encode(new PhotonNamedController());
		controller.getCompoundOrEmpty("data").putInt("version", 1);
		equal(null, IFXObject.CODEC.parse(NbtOps.INSTANCE, controller).result().orElse(null));
		controller.getCompoundOrEmpty("data").putInt("version", 0);
		controller.getCompoundOrEmpty("data").putString("definition", "invalid");
		equal(null, IFXObject.CODEC.parse(NbtOps.INSTANCE, controller).result().orElse(null));
	}

	private void subclasses() {
		var source = new ExtendedSpawner();
		source.extra = 73;
		ExtendedSpawner.decodes = 0;
		compare(source);
		// 注册类型 creator 仍构造基类；子类使用原生 encode/parse，扩展字段留在 source。
		equal(0, ExtendedSpawner.decodes);
		equal(73, ((PhotonChildSpawner) Objects.requireNonNull(source.deepCopy())).source().getIntOr("extra", -1));
		var controller = new ExtendedController();
		controller.extra = 37;
		var copied = (PhotonNamedController) compare(controller);
		equal(37, copied.source().getIntOr("extra", -1));
		equivalentOutcome(new PhotonChildSpawner() {
			@Override
			public void beforeSerialize() {
				throw new IllegalStateException("encoder_callback_failure");
			}
		});
		// 原生编码通过字段读取 name；对子类不可额外调用被覆盖的 getter。
		compare(new PhotonNamedController() {
			@Override
			public String getName() {
				throw new IllegalStateException("getter_must_not_be_called_by_copy");
			}
		});
	}

    private void modelConfigAndOwnership() {
        var fx = Objects.requireNonNull(FXHelper.getFX(Identifier.parse("lobecorp:the_queen_of_hatred/laser")));
        var borrowed = fx.getFxData().objects().stream().filter(PhotonModelOutput.class::isInstance).findFirst().orElseThrow();
        var before = encode(borrowed);
        var source = (PhotonModelOutput) Objects.requireNonNull(nativeCopy(borrowed));
        var preserved = source.source();
        preserved.putString("copy_unknown_root", "retained");
        preserved.getCompoundOrEmpty("config").putString("copy_unknown_config", "retained");
        source.rememberSource(preserved);
        // Configurable同样持久化，不能仅复制Persisted；只修改独占fixture。
        source.config.enabled = false;
        source.config.duration = 73;
        source.config.opacity = 0.37f;
        source.config.playbackSpeed = 2.5f;
        source.config.resource = null;
        source.transform().localPosition(new Vector3f(2, 3, 4));
        var first = (PhotonModelOutput) compare(source);
        var second = (PhotonModelOutput) compare(source);
        equal(false, first.config.enabled);
        equal(73, first.config.duration);
        equal(0.37f, first.config.opacity);
        equal(2.5f, first.config.playbackSpeed);
        equal(null, first.config.resource);
        equal("retained", first.source().getStringOr("copy_unknown_root", ""));
        equal("retained", first.source().getCompoundOrEmpty("config").getStringOr("copy_unknown_config", ""));
        different(source.config, first.config);
        different(first.config, second.config);
        different(first.config.renderer, second.config.renderer);
        different(first.config.modelRenderer, second.config.modelRenderer);
        different(first.config.assetDependencies, second.config.assetDependencies);
        different(first.config.assetMaterialDependencies, second.config.assetMaterialDependencies);
        different(first.slot("opacity"), second.slot("opacity"));
        first.slot("opacity").setRaw(0.25f);
        equal(0.37f, second.slot("opacity").get());
        first.config.assetDependencies.clear();
        equal(source.config.assetDependencies, second.config.assetDependencies);
        first.config.assetMaterialDependencies.putString("copy_mutation", "exclusive");
        equal(false, second.config.assetMaterialDependencies.contains("copy_mutation"));
        first.transform().localPosition().x = 900;
        equal(2f, second.transform().localPosition().x);
        var returnedSource = first.source();
        returnedSource.putString("copy_unknown_root", "external_mutation");
        equal("retained", first.source().getStringOr("copy_unknown_root", ""));
        var snapshot = source.config.assetSnapshot();
        source.config.setAssetSnapshot(snapshot);
        if (snapshot instanceof ByteArrayTag bytes && bytes.getAsByteArray().length > 0) {
            var expectedSnapshot = source.config.assetSnapshot();
            bytes.getAsByteArray()[0] ^= 1;
            equal(expectedSnapshot, source.config.assetSnapshot());
        }

        for (var invalid : List.<Tag>of(IntTag.valueOf(1), StringTag.valueOf("future"))) {
            var wrapper = encode(source);
            wrapper.getCompoundOrEmpty("data").put("version", invalid);
            equal(null, IFXObject.CODEC.parse(NbtOps.INSTANCE, wrapper).result().orElse(null));
        }

        var adapted = encode(source);
        var config = adapted.getCompoundOrEmpty("data").getCompoundOrEmpty("config");
        config.remove("modelRenderer");
        // RESOURCE对象可以没有内嵌快照：以真实适配器的接受/拒绝结果为准，不假定删除字段后仍合法。
        try {
            PhotonModelAssetUtil.adaptObject(adapted.getCompoundOrEmpty("data"));
            Objects.requireNonNull(IFXObject.CODEC.parse(NbtOps.INSTANCE, adapted).getOrThrow());
        } catch (IOException invalid) {
            equal(null, IFXObject.CODEC.parse(NbtOps.INSTANCE, adapted).result().orElse(null));
        }
        equal(before, encode(borrowed));
    }


	private PhotonModelOutput modelFixture() {
		var fx = Objects.requireNonNull(FXHelper.getFX(Identifier.parse("lobecorp:the_queen_of_hatred/laser")));
		var borrowed = fx.getFxData().objects().stream().filter(PhotonModelOutput.class::isInstance).findFirst().orElseThrow();
		return (PhotonModelOutput) Objects.requireNonNull(nativeCopy(borrowed));
	}

	private void modelTypedFields() throws ReflectiveOperationException {
		var source = modelFixture();
		for (var field : PhotonModelOutputConfig.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())
					|| (!field.isAnnotationPresent(Persisted.class) && !field.isAnnotationPresent(Configurable.class))) {
				continue;
			}

			if (field.getType() == boolean.class) {
				field.setBoolean(source.config, !field.getBoolean(source.config));
			} else if (field.getType() == int.class) {
				field.setInt(source.config, 37);
			} else if (field.getType() == float.class) {
				field.setFloat(source.config, 0.37f);
			} else if (field.getType() == String.class) {
				field.set(source.config, "typed_model_copy");
			} else if (field.getType().isEnum()) {
				field.set(source.config, field.getType().getEnumConstants()[1]);
			}
		}

		source.config.resource = Identifier.parse("lobecorp:copy_resource");
		source.config.assetDependencies.add(Identifier.parse("lobecorp:copy_dependency"));
		source.config.assetMaterialDependencies.putString("copy_material_dependency", "retained");
		source.config.renderer.setOrderInLayer(37);
		source.config.modelRenderer.setOrderInLayer(-23);
		source.config.renderer.getCustomMask().setMaskGroup("copy_override");
		source.config.modelRenderer.getCustomMask().setMaskGroup("copy_model");
		source.config.setAssetSnapshot(new ByteArrayTag(new byte[]{1, 2, 3}));
		assertTyped(source);
		var copy = (PhotonModelOutput) compare(source);
		for (var field : PhotonModelOutputConfig.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
					&& (field.isAnnotationPresent(Persisted.class) || field.isAnnotationPresent(Configurable.class))) {
				equal(field.get(source.config), field.get(copy.config));
			}
		}
		equal(source.config.assetSnapshot(), copy.config.assetSnapshot());
		equal(ByteArrayTag.class, copy.config.assetSnapshot().getClass());
		for (var value : PhotonModelOutputConfig.Source.values()) {
			source.config.source = value;
			assertTyped(source);
			compare(source);
		}
		for (var value : Anchor.values()) {
			source.config.anchor = value;
			assertTyped(source);
			compare(source);
		}
		source.config.setAssetSnapshot(StringTag.valueOf("snapshot_original_representation"));
		assertTyped(source);
		equal(StringTag.class, ((PhotonModelOutput) compare(source)).config.assetSnapshot().getClass());

		// 合并后的依赖包含旧 source 独有项；typed 恢复必须读取合并结果。
		var original = source.source();
		original.getCompoundOrEmpty("config").getCompoundOrEmpty("assetMaterialDependencies").putString("unknown_dependency", "retained");
		source.rememberSource(original);
		var merged = (PhotonModelOutput) compare(source);
		equal("retained", merged.config.assetMaterialDependencies.getStringOr("unknown_dependency", ""));
	}

	private static void assertTyped(PhotonModelOutput source) {
		equal(true, PhotonModelRuntimeCopyUtil.supportsEncoded(source, encode(source)));
	}

	private void modelRuntimeAndHierarchy() throws ReflectiveOperationException {
		var source = modelFixture();
		var parent = new PhotonNamedController();
		var child = new PhotonNamedController();
		source.transform().parent(parent.transform(), false);
		child.transform().parent(source.transform(), false);
		source.transform().localPosition(new Vector3f(2, -3, 5));
		source.transform().localRotation(new Quaternionf(1, 2, 3, 4));
		source.transform().localScale(new Vector3f(-2, 0.5f, 4));
		source.slot("duration").setRaw(999);
		source.rendererRuntime().orderInLayer.setRaw(777);
		source.setDelay(19);
		source.setSelfTimelineVisible(false);
		source.setKeepAlive(() -> {
			throw new AssertionError("keepAlive copied");
		});
		source.setSelfActive(false);
		source.setSelfVisible(false);
		source.setSelfTimeScale(7);
		source.setOnUpdateTick(() -> {
			throw new AssertionError("callback copied");
		});
		source.setOnUpdateFrame(_ -> {
			throw new AssertionError("callback copied");
		});
		var parentChildren = List.copyOf(parent.transform().children());
		var sourceChildren = List.copyOf(source.transform().children());
		assertTyped(source);
		var first = (PhotonModelOutput) compare(source);
		var second = (PhotonModelOutput) compare(source);
		equal(null, first.getScene());
		equal(null, first.transform().parent());
		equal(List.of(), first.transform().children());
		equal(source.transform().id(), first.transform().id());
		equal(source.transform()._getInternalParentID(), first.transform()._getInternalParentID());
		equal(source.transform()._getInternalChildID(), first.transform()._getInternalChildID());
		equal(parentChildren, parent.transform().children());
		equal(sourceChildren, source.transform().children());
		equal(false, first.slot("duration").isOverridden());
		equal(false, first.rendererRuntime().orderInLayer.isOverridden());
		equal(0, first.getDelay());
		equal(true, first.isSelfTimelineVisible());
		equal(null, first.getKeepAlive());
		equal(null, first.getTickHost());
		equal(0L, first.getLastTick());
		equal(true, first.isSelfActive());
		equal(true, first.isSelfVisible());
		equal(1f, first.getSelfTimeScale());
		equal(null, first.getOnUpdateTick());
		equal(null, first.getOnUpdateFrame());
		equal(null, first.getEffectExecutor());
		for (var name : List.of("clock", "modelRenderer", "overrideRenderer", "normalCache", "geometrySnapshots", "slots", "playback")) {
			var field = PhotonModelOutput.class.getDeclaredField(name);
			field.setAccessible(true);
			different(field.get(source), field.get(first));
			different(field.get(first), field.get(second));
		}
		for (var name : List.of("sampler", "materials", "dynamic", "frame", "sampledInput", "snapshotFrame")) {
			var field = PhotonModelOutput.class.getDeclaredField(name);
			field.setAccessible(true);
			equal(null, field.get(first));
		}
		for (var pair : List.of(new Object[]{first.config.renderer, second.config.renderer},
				new Object[]{first.config.modelRenderer, second.config.modelRenderer},
				new Object[]{first.config.renderer, first.config.modelRenderer})) {
			var a = (RendererSetting) pair[0];
			var b = (RendererSetting) pair[1];
			different(a, b);
			different(a.getCull(), b.getCull());
			different(a.getCustomMask(), b.getCustomMask());
			different(a.getMaterials(), b.getMaterials());
			for (int i = 0; i < Math.min(a.getMaterials().size(), b.getMaterials().size()); i++) {
				different(a.getMaterials().get(i), b.getMaterials().get(i));
				different(a.getMaterials().get(i).getBlendMode(), b.getMaterials().get(i).getBlendMode());
				different(a.getMaterials().get(i).getMaterial(), b.getMaterials().get(i).getMaterial());
			}
		}
	}

	private void modelFallbacks() {
		var source = modelFixture();
		source.config.file = LDLibExtraCodecs.NULL_STRING;
		equal(false, PhotonModelRuntimeCopyUtil.supportsEncoded(source, encode(source)));
		equivalentOutcome(source);
		source.config.file = null;
		equivalentOutcome(source);
		source.config.file = "";
		source.config.snapshotSource = null;
		equivalentOutcome(source);
		source.config.snapshotSource = "";
		source.config.clip = null;
		equivalentOutcome(source);
		source.config.clip = "";
		source.config.resource = null;
		compare(source);
		source.config.source = null;
		equivalentOutcome(source);
		source.config.source = PhotonModelOutputConfig.Source.RESOURCE;
		source.config.anchor = null;
		equivalentOutcome(source);
		source.config.anchor = Anchor.START;
		for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1}) {
			source.config.playbackSpeed = value;
			equivalentOutcome(source);
		}
		source.config.playbackSpeed = 1;
		source.config.assetDependencies.add(null);
		equivalentOutcome(source);
		source.config.assetDependencies.clear();
		var dependencies = source.config.assetDependencies;
		source.config.assetDependencies = null;
		equivalentOutcome(source);
		source.config.assetDependencies = dependencies;
		var materialDependencies = source.config.assetMaterialDependencies;
		source.config.assetMaterialDependencies = null;
		equivalentOutcome(source);
		source.config.assetMaterialDependencies = materialDependencies;
		source.transform().localRotation(new Quaternionf(0, 0, 0, 0));
		equal(false, PhotonModelRuntimeCopyUtil.supportsEncoded(source, encode(source)));
		equivalentOutcome(source);
		var extension = new ExtendedModel();
		extension.extra = 73;
		equal(false, PhotonModelRuntimeCopyUtil.supportsEncoded(extension, encode(extension)));
		equal(73, ((PhotonModelOutput) compare(extension)).source().getIntOr("extra", -1));
		equivalentOutcome(new PhotonModelOutput() {
			@Override
			public void beforeSerialize() {
				throw new IllegalStateException("model_encoder_callback_failure");
			}
		});
		compare(new PhotonModelOutput() {
			@Override
			public String getName() {
				throw new IllegalStateException("model_getter_must_not_be_called");
			}
		});
	}

	private void modelEncodedGuards() {
		var source = modelFixture();
		var unknownType = encode(source);
		unknownType.putString("type", "unregistered_copy_type");
		compareEncodedModel(source, unknownType);
		for (var invalid : List.<Tag>of(IntTag.valueOf(-1), IntTag.valueOf(1), StringTag.valueOf("future"))) {
			var wrapper = encode(source);
			wrapper.getCompoundOrEmpty("data").put("version", invalid);
			compareEncodedModel(source, wrapper);
		}
		for (var field : List.of("source", "anchor", "resource", "assetJson", "renderer", "modelRenderer")) {
			var wrapper = encode(source);
			wrapper.getCompoundOrEmpty("data").getCompoundOrEmpty("config").putString(field, "INVALID VALUE");
			compareEncodedModel(source, wrapper);
		}
		var legacy = encode(source);
		legacy.getCompoundOrEmpty("data").getCompoundOrEmpty("config").remove("modelRenderer");
		compareEncodedModel(source, legacy);
		legacy.getCompoundOrEmpty("data").getCompoundOrEmpty("config").remove("assetJson");
		compareEncodedModel(source, legacy);
	}

	private void modelCanonicalBorrowOwnership() throws IOException {
		var source = modelFixture();
		source.config.setAssetSnapshot(new ByteArrayTag(new byte[]{1, 2, 3}));
		source.config.assetDependencies.add(Identifier.parse("lobecorp:borrow_copy_dependency"));
		var wrapper = encode(source);
		var data = wrapper.getCompoundOrEmpty("data");
		var config = data.getCompoundOrEmpty("config");
		var unknown = new CompoundTag();
		unknown.put("bytes", new ByteArrayTag(new byte[]{4, 5, 6}));
		data.put("copy_unknown_mutable_root", unknown);
		config.put("copy_unknown_mutable_config", unknown.copy());
		config.getCompoundOrEmpty("assetMaterialDependencies").put("copy_mutable_dependency", unknown.copy());
		config.getCompoundOrEmpty("modelRenderer").put("copy_unknown_renderer", unknown.copy());
		assertTyped(source);
		equal(true, PhotonModelRuntimeCopyUtil.supportsEncoded(source, wrapper));
		var before = wrapper.copy();
		var borrowed = PhotonModelAssetUtil.borrowCanonicalConfig(data);
		// 返回原 config 及其原始可变子树，证明 guard 不借助整份适配副本。
		equal(true, borrowed == config);
		equal(true, borrowed.get("assetJson") == config.get("assetJson"));
		equal(true, borrowed.get("assetMaterialDependencies") == config.get("assetMaterialDependencies"));
		equal(before, wrapper);
		var adapted = PhotonModelAssetUtil.adaptObject(data);
		different(data, adapted);
		different(config, adapted.getCompoundOrEmpty("config"));
		var first = (PhotonModelOutput) Objects.requireNonNull(PhotonModelRuntimeCopyUtil.copyEncoded(source, wrapper));
		var second = (PhotonModelOutput) Objects.requireNonNull(PhotonModelRuntimeCopyUtil.copyEncoded(source, wrapper));
		var nativeCopy = Objects.requireNonNull(IFXObject.CODEC.parse(NbtOps.INSTANCE, wrapper).getOrThrow());
		equal(encode(nativeCopy), encode(first));
		equal(encode(nativeCopy), encode(second));
		var firstBefore = encode(first);
		var secondBefore = encode(second);
		var sourceBefore = encode(source);
		// 输入和公开适配结果均可变；修改后不得污染已恢复的配置或记忆。
		((ByteArrayTag) Objects.requireNonNull(config.get("assetJson"))).getAsByteArray()[0] = 99;
		config.getCompoundOrEmpty("assetMaterialDependencies").getCompoundOrEmpty("copy_mutable_dependency").putInt("changed", 1);
		config.getCompoundOrEmpty("modelRenderer").putInt("orderInLayer", 901);
		config.getCompoundOrEmpty("renderer").putInt("orderInLayer", 902);
		((ListTag) Objects.requireNonNull(config.get("assetDependencies"))).clear();
		unknown.putInt("changed", 2);
		config.getCompoundOrEmpty("copy_unknown_mutable_config").putInt("changed", 3);
		adapted.getCompoundOrEmpty("config").getCompoundOrEmpty("modelRenderer").putInt("changed", 4);
		equal(firstBefore, encode(first));
		equal(secondBefore, encode(second));
		equal(sourceBefore, encode(source));
		var inputAfter = wrapper.copy();
		first.config.setAssetSnapshot(new ByteArrayTag(new byte[]{7}));
		first.config.assetMaterialDependencies.getCompoundOrEmpty("copy_mutable_dependency").putInt("changed", 5);
		first.config.assetDependencies.clear();
		first.config.modelRenderer.setOrderInLayer(903);
		first.config.renderer.setOrderInLayer(904);
		var returned = first.source();
		returned.getCompoundOrEmpty("copy_unknown_mutable_root").putInt("changed", 6);
		equal(false, first.source().getCompoundOrEmpty("copy_unknown_mutable_root").contains("changed"));
		equal(secondBefore, encode(second));
		equal(sourceBefore, encode(source));
		equal(inputAfter, wrapper);
	}

	private static void compareEncodedModel(PhotonModelOutput source, CompoundTag wrapper) {
		var expected = outcome(() -> IFXObject.CODEC.parse(NbtOps.INSTANCE, wrapper).result().orElse(null));
		var actual = outcome(() -> PhotonModelRuntimeCopyUtil.copyEncoded(source, wrapper));
		equal(expected.failureType(), actual.failureType());
		equal(expected.message(), actual.message());
		equal(expected.object() == null, actual.object() == null);
		if (expected.object() != null) {
			var copied = Objects.requireNonNull(actual.object());
			equal(encode(expected.object()), encode(copied));
			equal(sourceTag(expected.object()), sourceTag(copied));
		}
	}

	private void realFx(Identifier id) {
		var fx = Objects.requireNonNull(FXHelper.getFX(id), "real FX missing:" + id);
		var data = fx.getFxData();
		if (data.objects().isEmpty()) {
			throw new AssertionError("real FX has no objects:" + id);
		}

		var provider = Platform.getFrozenRegistry();
		var before = data.serializeNBT(provider);
		int spawners = 0;
		int controllers = 0;
		for (var object : data.objects()) {
			if (object instanceof PhotonModelOutput model
					&& (object.getName().equals("reverse_muzzle") || object.getName().equals("flow_model_template"))) {
				assertTyped(model);
			}
			compare(object);
			if (object instanceof PhotonChildSpawner) {
				spawners++;
			} else if (object instanceof PhotonNamedController) {
				controllers++;
			}
		}

		var expected = nativeDataCopy(data);
		var actual = data.copy(true);
		equal(expected.serializeNBT(provider), actual.serializeNBT(provider));
		equal(before, data.serializeNBT(provider));
		var inventory = new JsonObject();
		inventory.addProperty("resource", id.toString());
		inventory.addProperty("objects", data.objects().size());
		inventory.addProperty("spawners", spawners);
		inventory.addProperty("controllers", controllers);
		inventory.addProperty("scope", "real FXData copy; no runtime construction, emit or simulation");
		costs.add(inventory);
		measureAbba(id.toString(), data);
		equal(before, data.serializeNBT(provider));
	}

	private void measureAbba(String id, FXData source) {
		var management = ManagementFactory.getThreadMXBean();
		ThreadMXBean bean = management instanceof ThreadMXBean available && available.isThreadAllocatedMemorySupported()
				&& available.isThreadAllocatedMemoryEnabled() ? available : null;
		// 两条路径等量预热；不改变 JVM 分配计数开关，不触发 GC。
		for (int i = 0; i < 16; i++) {
			sink = nativeDataCopy(source);
			sink = source.copy(true);
		}

		try {
			for (var mode : List.of("A", "B", "B", "A")) {
				var retained = new FXData[REPETITIONS];
				long thread = Thread.currentThread().threadId();
				long allocated = bean == null ? -1 : bean.getThreadAllocatedBytes(thread);
				long started = System.nanoTime();
				for (int i = 0; i < REPETITIONS; i++) {
					retained[i] = mode.equals("A") ? nativeDataCopy(source) : source.copy(true);
				}
				long elapsed = System.nanoTime() - started;
				long bytes = bean == null ? -1 : bean.getThreadAllocatedBytes(thread) - allocated;
				sink = retained;
				equal(REPETITIONS, ((FXData[]) sink).length);
				var block = new JsonObject();
				block.addProperty("resource", id);
				block.addProperty("mode", mode);
				block.addProperty("copies", REPETITIONS);
				block.addProperty("objects_per_copy", source.objects().size());
				block.addProperty("elapsed_ns", elapsed);
				block.addProperty("allocated_bytes", bytes);
				block.addProperty("allocation_available", bean != null && allocated >= 0 && bytes >= 0);
				costs.add(block);
			}
		} finally {
			sink = null;
		}
	}

	private static FXData nativeDataCopy(FXData source) {
		return new FXData(source.objects().stream().map(PhotonRuntimeCopyNativeCheck::nativeCopy)
				.collect(Collectors.toList()), source.timeline().copy());
	}

	private static IFXObject nativeCopy(IFXObject source) {
		return IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, source).result()
				.flatMap(tag -> IFXObject.CODEC.parse(NbtOps.INSTANCE, tag).result()).orElse(null);
	}

	private static CompoundTag encode(IFXObject object) {
		return (CompoundTag) IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, object).getOrThrow();
	}

	private static IFXObject compare(IFXObject source) {
		var before = encode(source);
		var expected = Objects.requireNonNull(nativeCopy(source), "native copy returned null");
		var actual = Objects.requireNonNull(source.deepCopy(), "current deepCopy returned null");
		if (source instanceof PhotonModelOutput model) {
			var typed = Objects.requireNonNull(PhotonModelRuntimeCopyUtil.typedCopy(model), "typed candidate returned null");
			different(source, typed);
			equal(encode(expected), encode(typed));
			equal(sourceTag(expected), sourceTag(typed));
			actual = typed;
		}
		different(source, actual);
		equal(expected.getClass(), actual.getClass());
		equal(encode(expected), encode(actual));
		equal(before, encode(source));
		equal(sourceTag(expected), sourceTag(actual));
		if (source instanceof PhotonChildSpawner spawner && actual instanceof PhotonChildSpawner copy) {
			independentConfig(spawner.config, copy.config);
		}

		return actual;
	}

	private static CompoundTag sourceTag(IFXObject object) {
		return switch (object) {
			case PhotonChildSpawner spawner -> spawner.source();
			case PhotonNamedController controller -> controller.source();
			case PhotonModelOutput model -> model.source();
			default -> new CompoundTag();
		};
	}

	private static void independentConfig(PhotonSpawnConfig from, PhotonSpawnConfig to) {
		different(from, to);
		different(from.template, to.template);
		different(from.shape, to.shape);
		different(from.shape.size, to.shape.size);
		different(from.shape.offset, to.shape.offset);
		different(from.parameters, to.parameters);
		different(from.parameters.size, to.parameters.size);
		different(to.parameters.size.x, to.parameters.size.y);
		different(to.parameters.size.y, to.parameters.size.z);
		var a = List.of(from.count, from.rate, from.probability, from.launchSpeed, from.parameters.lifetime,
				from.parameters.size.x, from.parameters.size.y, from.parameters.size.z, from.parameters.color,
				from.parameters.speed, from.parameters.beamLength, from.parameters.beamWidth);
		var b = List.of(to.count, to.rate, to.probability, to.launchSpeed, to.parameters.lifetime,
				to.parameters.size.x, to.parameters.size.y, to.parameters.size.z, to.parameters.color,
				to.parameters.speed, to.parameters.beamLength, to.parameters.beamWidth);
		for (int i = 0; i < a.size(); i++) {
			different(a.get(i), b.get(i));
		}
	}

	private static void equivalentOutcome(IFXObject source) {
		var old = outcome(() -> nativeCopy(source));
		var current = outcome(source::deepCopy);
		if (source instanceof PhotonModelOutput model) {
			var typed = outcome(() -> PhotonModelRuntimeCopyUtil.typedCopy(model));
			equal(old.failureType(), typed.failureType());
			equal(old.message(), typed.message());
			equal(old.object() == null, typed.object() == null);
			if (old.object() != null) {
				equal(encode(old.object()), encode(typed.object()));
			}
		}
		equal(old.failureType(), current.failureType());
		equal(old.message(), current.message());
		equal(old.object() == null, current.object() == null);
		if (old.object() != null) {
			equal(encode(old.object()), encode(current.object()));
		}
	}

	private static Outcome outcome(Supplier<IFXObject> operation) {
		try {
			return new Outcome(operation.get(), null, null);
		} catch (RuntimeException failure) {
			return new Outcome(null, failure.getClass().getName(), failure.getMessage());
		}
	}

	private void runCase(String name, CaseBody operation) {
		var result = new JsonObject();
		result.addProperty("case", name);
		try {
			operation.run();
			result.addProperty("passed", true);
		} catch (Exception | AssertionError | LinkageError failure) {
			failures.add(failure);
			result.addProperty("passed", false);
			result.addProperty("failure", failure.toString());
		}
		cases.add(result);
	}

	private static void different(Object source, Object copy) {
		if (source == copy) {
			throw new AssertionError("mutable reference shared:" + source.getClass().getName());
		}
	}

	private static void equal(Object expected, Object actual) {
		if (!Objects.equals(expected, actual)) {
			throw new AssertionError("expected=" + expected + "; actual=" + actual);
		}
	}

	@FunctionalInterface
	private interface CaseBody {
		void run() throws Exception;
	}

	private record Outcome(IFXObject object, String failureType, String message) {
	}

	private static class ExtendedModel extends PhotonModelOutput {
		@Persisted
		public int extra;
	}

	private static class ExtendedController extends PhotonNamedController {
		@Persisted
		public int extra;
	}

	private static class ExtendedSpawner extends PhotonChildSpawner {
		@Persisted
		public int extra;
		private static int decodes;

		@Override
		public void afterDeserialize() {
			decodes++;
		}
	}
}
