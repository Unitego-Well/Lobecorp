package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.math.Transform;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonTimelineProgram;
import org.unitego.lobecorp.util.photon.creation.PhotonFrozenTag;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory.Definition;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory.Lease;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory.Mode;
import org.unitego.lobecorp.config.photon.PhotonRepeatedEffectConfig;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.util.photon.creation.PhotonIndexedObjectMap;

import java.lang.reflect.Field;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/// 主窗口归档后的真实客户端契约探针；不发射、不推进生产时钟、不清理其他实例。
public class PhotonCreationNativeCheck {
	// 固定既有四模式探针范围，新增模式使用独立场景验收。
	private static final List<Mode> ORIGINAL_MODES = List.of(Mode.NATIVE, Mode.COMPILED, Mode.POOLED, Mode.INDEXED);
	private static final int EXPECTED_OBJECTS = 80;
	private static final long TIMEOUT_NS = TimeUnit.SECONDS.toNanos(30);
	private static final List<String> RESOURCES = List.of(
			"lobecorp:the_queen_of_hatred/laser", "lobecorp:the_queen_of_hatred/light_ball",
			"lobecorp:the_queen_of_hatred/magic_circle", "lobecorp:the_queen_of_hatred/magic_circle2",
			"lobecorp:the_queen_of_hatred/magic_circle3", "lobecorp:the_queen_of_hatred/accumulate_particle",
			"lobecorp:the_queen_of_hatred/laser_spell", "lobecorp:the_queen_of_hatred/star_beam_spell",
			"lobecorp:the_queen_of_hatred/star_beam_spell_ball", "lobecorp:the_queen_of_hatred/star_beam_spell_beam",
			PhotonRepeatedEffectConfig.NATIVE_FX);
	private static final Map<String, PhotonCreationNativeCheck> RUNS = new LinkedHashMap<>();
	private final JsonObject report = new JsonObject();
	private final JsonArray cases = new JsonArray();
	private final JsonArray sources = new JsonArray();
	private final List<Throwable> failures = new ArrayList<>();
	private final List<Probe> probes = new ArrayList<>();
	private final List<Lease> owned = new ArrayList<>();
	private final Map<String, Long> countersBefore = new LinkedHashMap<>(PhotonRuntimeFactory.statistics());
	private final long started = System.nanoTime();
	private final Mode activeMode = PhotonRuntimeFactory.mode();
	private long releasedTick;
	private long releasedFrame;
	private long releasedNs;
	private boolean finished;
	private boolean completed;
	private long lastStepTick;
	private long lastStepFrame;
	private long lastProgressNs;
	private int objectCount;
	private int resourceIndex;

	/// 首次调用仅准备未发射的租约并归还；后续调用由 Bench 的客户端 tick 驱动。
	public static boolean step(BenchClientContext context, String label) throws Exception {
		var check = RUNS.get(label);
		if (check == null) {
			var initial = new PhotonCreationNativeCheck();
			RUNS.put(label, initial);
			initial.initializeReport();
			initial.runCase("prepare_unemitted_and_release", initial::begin);
			initial.releasedTick = PhotonRuntimeFactory.ticks();
			initial.releasedFrame = PhotonRuntimeFactory.frames();
			initial.releasedNs = System.nanoTime();
			initial.lastStepTick = initial.releasedTick;
			initial.lastStepFrame = initial.releasedFrame;
			initial.lastProgressNs = initial.releasedNs;
			return false;
		}

		if (check.finished) {
			return true;
		}

		// 排空进入隔离，再跨一轮 tick 和完成帧；不通过 sleep 或手动 pulse 伪造屏障。
		long tick = PhotonRuntimeFactory.ticks();
		long frame = PhotonRuntimeFactory.frames();
		if (tick - check.releasedTick < 2 || frame - check.releasedFrame < 2) {
			if (System.nanoTime() - check.releasedNs < TIMEOUT_NS) {
				return false;
			}

			check.runCase("real_tick_frame_barrier_timeout", () -> {
				throw new AssertionError("barrier timeout: tick=" + tick + ", frame=" + frame
						+ ", releasedTick=" + check.releasedTick + ", releasedFrame=" + check.releasedFrame);
			});
			check.finish(context, label);
			return true;
		}

		if (tick <= check.lastStepTick || frame <= check.lastStepFrame) {
			if (System.nanoTime() - check.lastProgressNs < TIMEOUT_NS) {
				return false;
			}

			check.runCase("host_callback_progress_timeout", () -> {
				throw new AssertionError("no new real tick and completed frame");
			});
			check.finish(context, label);
			return true;
		}

		check.lastStepTick = tick;
		check.lastStepFrame = frame;
		check.lastProgressNs = System.nanoTime();
		if (check.resourceIndex < RESOURCES.size()) {
			String id = RESOURCES.get(check.resourceIndex++);
			check.runCase("resource:" + id, () -> check.resource(id));
			return false;
		}

		check.runCase("compiled_object_all_entries_and_live_preparation", () -> {
			var models = check.probes.stream().flatMap(probe -> probe.source().getFxData().objects().stream())
					.filter(PhotonModelOutput.class::isInstance).toList();
			var entries = PhotonCompiledObjectNativeCheck.verify(models);
			require(!entries.isEmpty(), "compiled entry checks did not run");
			var encoded = new JsonArray();
			for (var entry : entries) {
				var item = new JsonObject();
				item.addProperty("name", entry.name());
				item.addProperty("fallback_fields", entry.fallbackFields());
				encoded.add(item);
			}
			check.report.add("compiled_object_entries", encoded);
		});
		check.runCase("state_plan_host_contracts", () -> {
			var entries = new JsonArray();
			PhotonStatePlanNativeCheck.verify().forEach(entries::add);
			check.report.add("state_plan_host_cases", entries);
		});
		check.runCase("named_compilation_host_contracts", () -> {
			var entries = new JsonArray();
			PhotonNamedCompilationNativeCheck.verify().forEach(entries::add);
			check.report.add("named_compilation_host_cases", entries);
		});
		check.runCase("unsupported_native_fallback_nonempty", check::unsupported);
		check.runCase("stale_definition_rejected_without_global_clear", check::staleGeneration);
		check.runCase("source_scope_11_resources_80_objects", () -> {
			equal(RESOURCES.size(), check.sources.size());
			equal(EXPECTED_OBJECTS, check.objectCount);
		});
		check.completed = true;
		check.finish(context, label);
		return true;
	}

	@SuppressWarnings("unused")
	public static void verify(BenchClientContext context, String label) {
		var check = RUNS.get(label);
		require(check != null && check.finished && check.completed, "creation probe incomplete");
		if (!check.failures.isEmpty()) {
			var failure = new IllegalStateException("creation_native_failed:" + check.failures.size());
			check.failures.forEach(failure::addSuppressed);
			throw failure;
		}
	}

	public static void cancel(BenchClientContext context, String label) throws Exception {
		var check = RUNS.remove(label);
		if (check == null) {
			return;
		}

		check.runCase("owned_lease_cleanup", check::closeOwned);
		if (!check.finished) {
			check.report.addProperty("cancelled", true);
			check.finish(context, label);
		}
	}

	/// 由外部已经完成的合法 reload/clear 调用；本探针自身绝不 invalidateAll。
	@SuppressWarnings("unused")
	public static void verifyInvalidatedDefinition(Definition previous) {
		for (var mode : ORIGINAL_MODES) {
			boolean rejected = false;
			try {
				var unexpected = PhotonRuntimeFactory.instantiate(previous, mode);
				unexpected.destroy(true);
			} catch (IllegalStateException expected) {
				rejected = true;
			}

			require(rejected, "old definition generation accepted:" + mode + ":" + previous.generation());
		}
	}

	private void staleGeneration() throws Exception {
		var definition = probes.getFirst().definition();
		long generation = PhotonRuntimeFactory.statistics().get("generation");
		require(definition.generation() == generation, "host generation changed during fixture");
		var constructor = Definition.class.getDeclaredConstructor(String.class, long.class, List.class,
				PhotonTimelineProgram.class, PhotonFrozenTag.class);
		constructor.setAccessible(true);
		// 只构造独立的过期定义，不修改 Factory 代次、缓存或其他活租约。
		var stale = constructor.newInstance(definition.key(), generation - 1,
				(List<?>) field(definition, "nodes"), (PhotonTimelineProgram) field(definition, "timeline"),
				(PhotonFrozenTag) field(definition, "snapshot"));
		var active = PhotonRuntimeFactory.acquire(definition, new Object(), Mode.COMPILED);
		owned.add(active);
		verifyInvalidatedDefinition(stale);
		for (var mode : ORIGINAL_MODES) {
			boolean rejected = false;
			try {
				var unexpected = PhotonRuntimeFactory.acquire(stale, new Object(), mode);
				unexpected.close(true);
			} catch (IllegalStateException expected) {
				rejected = true;
			}

			require(rejected, "stale definition acquire accepted:" + mode);
		}

		require(active.current(), "stale generation check invalidated a live lease");
		active.close(true);
		equal(generation, PhotonRuntimeFactory.statistics().get("generation"));
		report.addProperty("generation_case_scope", "isolated stale definition; actual reload not exercised");
	}

	private void initializeReport() {
		report.addProperty("phase", "after_main_window_archive");
		report.addProperty("main_window_sample", false);
		report.addProperty("active_mode", activeMode.name());
		report.addProperty("fixture_scope", "11 existing resources; no emission, simulation or visual acceptance");
		report.addProperty("pool_fixture_is_40s_performance", false);
		report.addProperty("source_head_at_implementation", "ca2b926b");
		report.addProperty("jdk_runtime", Runtime.version().toString());
		report.addProperty("photon_version", "26.1.2.3");
		report.addProperty("ldlib2_version", "26.1.2.42");
		report.addProperty("artifact_id", UUID.randomUUID().toString());
		report.addProperty("start_ns", started);
		report.add("fixture_counters_before", counters(countersBefore));
		report.addProperty("production_counter_scope", "fixture entry only; main counters are frozen in separate creation-statistics artifact");
		report.addProperty("global_invalidation_case", "not executed; isolated stale generation rejection only");
	}

	private void begin() {
		for (var id : RESOURCES) {
			runCase("lease_release:" + id, () -> {
				var source = Objects.requireNonNull(FXHelper.getFX(Identifier.parse(id)), "missing:" + id);
				var authorBefore = source.serializeNBT(Platform.getFrozenRegistry());
				var definition = PhotonRuntimeFactory.prepare(source);
				equal(authorBefore, source.serializeNBT(Platform.getFrozenRegistry()));
				var key = new Object();
				var lease = PhotonRuntimeFactory.acquire(definition, key, Mode.POOLED);
				owned.add(lease);
				var runtime = lease.runtime();
				require(!runtime.isEmitted(), "probe emitted a runtime");
				boolean reusable = PhotonRuntimeFactory.reusable(runtime);
				dirty(runtime);
				lease.close(true);
				require(!lease.current(), "closed lease is current");
				rejects(lease);
				probes.add(new Probe(id, source, definition, key, lease, runtime, reusable));
			});
		}
	}

	private void resource(String id) throws Exception {
		var source = Objects.requireNonNull(FXHelper.getFX(Identifier.parse(id)), "missing:" + id);
		var before = source.serializeNBT(Platform.getFrozenRegistry());
		var row = new JsonObject();
		sources.add(row);
		row.addProperty("resource", id);
		row.addProperty("objects", source.getFxData().objects().size());
		row.addProperty("source_sha256", hash(before));
		objectCount += source.getFxData().objects().size();
		var definition = PhotonRuntimeFactory.prepare(source);
		row.addProperty("key", String.valueOf(definition.key()));
		row.addProperty("generation", definition.generation());
		row.addProperty("fallback_fields", definition.fallbackFields());
		var reasons = new JsonArray();
		definition.fallbackReasons().forEach(reasons::add);
		row.add("fallback_reasons", reasons);
		equal(source.getFxData().serializeNBT(Platform.getFrozenRegistry()), definition.snapshot());
		Tag exported = definition.snapshot();
		if (exported instanceof CompoundTag compound) {
			compound.putString("caller_mutation", "must_not_leak");
		}
		equal(source.getFxData().serializeNBT(Platform.getFrozenRegistry()), definition.snapshot());

		var modes = new JsonArray();
		row.add("modes", modes);
		for (var mode : ORIGINAL_MODES) {
			var result = new JsonObject();
			result.addProperty("mode", mode.name());
			result.addProperty("passed", false);
			result.addProperty("fresh_instances", 0);
			modes.add(result);
			runCase(id + ":" + mode.name(), () -> compareMode(source, definition, mode, before, result));
		}

		activeAndCurrent(id, source, before, row);
	}

	private void compareMode(FX source, Definition definition, Mode mode, CompoundTag before, JsonObject result) throws Exception {
		long begin = System.nanoTime();
		var nativeRuntime = PhotonRuntimeFactory.nativeRuntime(source);
		FXRuntime first = null;
		FXRuntime second = null;
		try {
			first = PhotonRuntimeFactory.instantiate(definition, mode);
			result.addProperty("fresh_instances", 1);
			second = PhotonRuntimeFactory.instantiate(definition, mode);
			result.addProperty("fresh_instances", 2);
			compare(nativeRuntime, first, mode);
			compare(nativeRuntime, second, mode);
			independent(first, second);
			dirty(first);
			compare(nativeRuntime, second, mode);
			equal(before, source.serializeNBT(Platform.getFrozenRegistry()));
			result.addProperty("objects_per_instance", second.objects.size());
			result.addProperty("passed", true);
		} finally {
			result.addProperty("elapsed_ns", System.nanoTime() - begin);
			nativeRuntime.destroy(true);
			if (first != null) {
				first.destroy(true);
			}
			if (second != null) {
				second.destroy(true);
			}
		}
	}

	private void activeAndCurrent(String id, FX source, CompoundTag before, JsonObject row) throws Exception {
		// 走当前 JVM 实际 legacy 入口；POOLED 下 raw 调用仍须 fresh。
		var raw = source.createRuntime(true);
		var other = source.createRuntime(true);
		var baseline = PhotonRuntimeFactory.nativeRuntime(source);
		try {
			compare(baseline, raw, activeMode);
			independent(raw, other);
		} finally {
			raw.destroy(true);
			other.destroy(true);
			baseline.destroy(true);
		}

		reuse(id, row);
		currentEdits(source);
		equal(before, source.serializeNBT(Platform.getFrozenRegistry()));
		row.addProperty("author_source_unchanged", true);
	}

	private void reuse(String id, JsonObject row) throws Exception {
		var probe = probes.stream().filter(value -> value.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("missing released probe:" + id));
		long hits = PhotonRuntimeFactory.statistics().get("pool_hit");
		var next = PhotonRuntimeFactory.acquire(probe.definition(), probe.worldKey(), Mode.POOLED);
		owned.add(next);
		var expected = PhotonRuntimeFactory.nativeRuntime(probe.source());
		try {
			if (probe.reusable()) {
				require(next.runtime() == probe.backend(), "pool did not reuse backend after real barriers:" + id);
				require(PhotonRuntimeFactory.statistics().get("pool_hit") == hits + 1, "backend identity without recorded pool hit");
			} else {
				require(next.runtime() != probe.backend(), "unsupported reset type was reused:" + id);
				require(PhotonRuntimeFactory.statistics().get("pool_hit") == hits, "unsupported reset claimed a hit");
			}
			require(next.id() != probe.oldLease().id(), "lease id reused");
			row.addProperty("pool_reset_supported", probe.reusable());
			row.addProperty("pool_backend_reused", probe.reusable());
			row.addProperty("old_lease_id", probe.oldLease().id());
			row.addProperty("new_lease_id", next.id());
			row.addProperty("reuse_tick", PhotonRuntimeFactory.ticks());
			row.addProperty("reuse_completed_frame", PhotonRuntimeFactory.frames());
			compare(expected, next.runtime(), Mode.POOLED);
			rejects(probe.oldLease());
			probe.oldLease().close(true);
			require(next.current(), "late release closed current lease");
			var active = PhotonRuntimeFactory.acquire(probe.definition(), new Object(), activeMode);
			owned.add(active);
			try {
				compare(expected, active.runtime(), activeMode);
				require(active.runtime() != next.runtime(), "world key isolation failed");
			} finally {
				active.close(true);
			}
		} finally {
			expected.destroy(true);
			next.close(true);
		}
	}

	private void currentEdits(FX borrowed) throws Exception {
		// 制作编辑只作用于独立 FX；不得改 FXHelper 缓存中的作者数据。
		var author = new FX();
		author.deserializeNBT(Platform.getFrozenRegistry(), borrowed.serializeNBT(Platform.getFrozenRegistry()));
		var before = author.serializeNBT(Platform.getFrozenRegistry());
		var old = PhotonRuntimeFactory.prepare(author);
		for (var object : author.getFxData().objects()) {
			object.setName("creation_current_" + object.getName());
			object.transform().localPosition(new Vector3f(2, -3, 5));
			object.transform().localRotation(new Quaternionf().rotationXYZ(0.2f, 0.3f, 0.4f));
			object.transform().localScale(new Vector3f(2, 0.5f, 3));
			switch (object) {
				case PhotonModelOutput model -> {
					model.config.color = 0x7f123456;
					model.config.width = 2.75f;
					model.config.playbackSpeed = 0.5f;
					var original = model.source();
					original.putString("creation_current_source", "retained");
					model.rememberSource(original);
					model.config.assetMaterialDependencies.putInt("creation_current_dependency", 1);
				}
				case PhotonChildSpawner child -> {
					child.config.duration = 73;
					child.config.parameters.color = NumberFunction.color(0x7fabcdef);
					if (child.config.source == PhotonSpawnConfig.Source.PROJECT) {
						var templateId = child.config.template.getTransformId();
						author.getFxData().objects().stream().filter(value -> value.id().equals(templateId))
								.findFirst().ifPresent(value -> value.setName("creation_current_project_template"));
					}
				}
				case PhotonNamedController controller -> {
					var original = controller.source();
					original.putString("creation_current_source", "retained");
					controller.rememberSource(original);
				}
				default -> {
				}
			}
		}

		var edited = author.serializeNBT(Platform.getFrozenRegistry());
		var current = PhotonRuntimeFactory.prepare(author);
		equal(edited, author.serializeNBT(Platform.getFrozenRegistry()));
		equal(before.getCompoundOrEmpty("fxData"), old.snapshot());
		equal(edited.getCompoundOrEmpty("fxData"), current.snapshot());
		var expected = PhotonRuntimeFactory.nativeRuntime(author);
		try {
			for (var mode : ORIGINAL_MODES) {
				var actual = PhotonRuntimeFactory.instantiate(current, mode);
				try {
					compare(expected, actual, mode);
				} finally {
					actual.destroy(true);
				}
			}
		} finally {
			expected.destroy(true);
		}
	}

	private static void compare(FXRuntime expected, FXRuntime actual, Mode mode) throws Exception {
		equal(expected.getFxData().serializeNBT(Platform.getFrozenRegistry()),
				actual.getFxData().serializeNBT(Platform.getFrozenRegistry()));
		equal(new ArrayList<>(expected.objects.keySet()), new ArrayList<>(actual.objects.keySet()));
		if (mode == Mode.INDEXED) {
			require(actual.objects instanceof PhotonIndexedObjectMap, "INDEXED did not install indexed object map");
		}
		equal(expected.isEmitted(), actual.isEmitted());
		equal(expected.isDestroyed(), actual.isDestroyed());
		equal(expected.getRate(), actual.getRate());
		require(expected.timelinePlayer != actual.timelinePlayer, "timeline player shared");
		for (var entry : expected.objects.entrySet()) {
			var a = entry.getValue();
			var b = Objects.requireNonNull(actual.objects.get(entry.getKey()), "missing runtime object");
			require(a != b && a.transform() != b.transform(), "object/transform shared");
			equal(encode(a), encode(b));
			equal(a.getName(), b.getName());
			equal(a.transform().localPosition(), b.transform().localPosition());
			equal(a.transform().localRotation(), b.transform().localRotation());
			equal(a.transform().localScale(), b.transform().localScale());
			equal(a.transform()._getInternalParentID(), b.transform()._getInternalParentID());
			equal(a.transform()._getInternalChildID(), b.transform()._getInternalChildID());
			equal(a.transform().children().stream().map(Transform::id).toList(),
					b.transform().children().stream().map(Transform::id).toList());
			var parent = b.transform().parent();
			if (parent != null) {
				require(Objects.requireNonNull(parent.sceneObject(), "missing parent scene object").getScene() == actual, "foreign live parent");
			}
			require(b.getScene() == actual, "foreign scene");
			equal(null, b.getEffectExecutor());
			equal(a.isSelfActive(), b.isSelfActive());
			equal(a.isSelfVisible(), b.isSelfVisible());
			equal(a.isSelfTimelineVisible(), b.isSelfTimelineVisible());
			equal(a.getSelfTimeScale(), b.getSelfTimeScale());
			equal(source(a), source(b));
			equal(expected.findObjects(a.getName()).stream().map(IFXObject::id).toList(),
					actual.findObjects(a.getName()).stream().map(IFXObject::id).toList());
			if (a instanceof FXObject x && b instanceof FXObject y) {
				equal(x.getDelay(), y.getDelay());
				equal(x.getRCol(), y.getRCol());
				equal(x.getGCol(), y.getGCol());
				equal(x.getBCol(), y.getBCol());
				equal(x.getAlpha(), y.getAlpha());
				equal(x.getOnUpdateTick() == null, y.getOnUpdateTick() == null);
				equal(x.getOnUpdateFrame() == null, y.getOnUpdateFrame() == null);
			}
			if (a instanceof Emitter x && b instanceof Emitter y) {
				require(x.rendererRuntime() != y.rendererRuntime(), "renderer runtime shared");
				equal(x.getMemRandom(), y.getMemRandom());
				equal(x.getAge(), y.getAge());
			}
			if (a instanceof ParticleEmitter x && b instanceof ParticleEmitter y) {
				require(x.config != y.config && x.runtime() != y.runtime(), "particle config/runtime shared");
				equal(x.getParticleAmount(), y.getParticleAmount());
				equal(x.waitToAdded.size(), y.waitToAdded.size());
				equal(x.getEmissionRateAccum(), y.getEmissionRateAccum());
				equal(x.runtime().startColor.isOverridden(), y.runtime().startColor.isOverridden());
				equal(x.getAccumulatedDistance(), y.getAccumulatedDistance());
				equal(((Collection<?>) field(x, "pendingSubEmitterSpawns")).size(),
						((Collection<?>) field(y, "pendingSubEmitterSpawns")).size());
				require(x.waitToAdded != y.waitToAdded && x.getParticles() != y.getParticles(), "particle queues shared");
			}
			if (a instanceof PhotonModelOutput x && b instanceof PhotonModelOutput y) {
				require(x.config != y.config && x.config.renderer != y.config.renderer
						&& x.config.modelRenderer != y.config.modelRenderer, "model config/renderer settings shared");
				equal(x.slot("color").isOverridden(), y.slot("color").isOverridden());
				equal(x.slot("width").isOverridden(), y.slot("width").isOverridden());
			}
			if (a instanceof PhotonChildSpawner x && b instanceof PhotonChildSpawner y) {
				require(x.config != y.config && x.slot("count") != y.slot("count"), "spawner state shared");
				equal(x.slot("count").isOverridden(), y.slot("count").isOverridden());
				equal(x.instanceIds(), y.instanceIds());
				equal(x.preparedTemplate(), y.preparedTemplate());
			}
			if (a instanceof PhotonNamedController && b instanceof PhotonNamedController) {
				equal(null, field(b, "playback"));
			}
		}
	}

	private static void independent(FXRuntime first, FXRuntime second) {
		require(first != second && first.getFxData() != second.getFxData(), "runtime/data shared");
		for (var id : first.objects.keySet()) {
			var a = first.objects.get(id);
			var b = second.objects.get(id);
			require(a != b && a.transform().localPosition() != b.transform().localPosition(), "instance TRS shared");
			if (a instanceof PhotonModelOutput x && b instanceof PhotonModelOutput y) {
				require(x.config != y.config && x.slot("width") != y.slot("width"), "instance model state shared");
				require(x.config.renderer != y.config.renderer && x.config.modelRenderer != y.config.modelRenderer,
						"instance renderer settings shared");
			}
			if (a instanceof ParticleEmitter x && b instanceof ParticleEmitter y) {
				require(x.config != y.config && x.runtime() != y.runtime(), "instance particle state shared");
				require(x.config.renderer != y.config.renderer, "instance particle renderer settings shared");
			}
			if (a instanceof Emitter x && b instanceof Emitter y) {
				require(x.rendererRuntime() != y.rendererRuntime() && x.getMemRandom() != y.getMemRandom(),
						"instance renderer/random state shared");
			}
		}
	}

	private static void dirty(FXRuntime runtime) {
		((PhotonNamedAccess) runtime).lobecorp$prepareNamedRuntime();
		runtime.setRate(3);
		for (var object : runtime.objects.values()) {
			object.transform().localPosition(new Vector3f(99, 98, 97));
			object.transform().localRotation(new Quaternionf().rotationXYZ(1, 2, 3));
			object.transform().localScale(new Vector3f(7, 8, 9));
			object.setName("creation_dirty");
			object.setSelfActive(false);
			object.setSelfVisible(false);
			object.setSelfTimelineVisible(false);
			object.setSelfTimeScale(7);
			object.setDelay(17);
			if (object instanceof FXObject fx) {
				fx.rCol = 0.13f;
				fx.gCol = 0.27f;
				fx.bCol = 0.41f;
				fx.alpha = 0.55f;
				fx.setOnUpdateTick(() -> {
					throw new AssertionError("stale tick callback");
				});
				fx.setOnUpdateFrame(_ -> {
					throw new AssertionError("stale frame callback");
				});
			}
			if (object instanceof Emitter emitter) {
				emitter.getMemRandom().put("creation_dirty", 0.75f);
				emitter.setAge(31);
			}
			if (object instanceof ParticleEmitter emitter) {
				emitter.config.setDuration(37);
				emitter.runtime().startColor.setRaw(NumberFunction.color(0x7f123456));
				emitter.setEmissionRateAccum(0.7f);
				emitter.setAccumulatedDistance(12);
				// 仅污染私有待加入队列，不注册到粒子引擎，不调用 emit/setup。
				emitter.waitToAdded.add(new TileParticle(emitter, emitter.config));
				emitter.scheduleSubEmitterSpawn(() -> {
					throw new AssertionError("stale sub-emitter callback");
				});
			}
			if (object instanceof PhotonModelOutput model) {
				model.config.color = 0x7f123456;
				model.config.width = 37;
				model.slot("width").setRaw(99f);
				model.slot("color").setRaw(NumberFunction.color(0x7fabcdef));
			}
			if (object instanceof PhotonChildSpawner child) {
				child.config.duration = 37;
				child.slot("count").setRaw(NumberFunction.constant(99));
			}
		}
	}

	private void unsupported() throws Exception {
		var registry = PhotonRegistries.FX_OBJECTS;
		var key = "creation_native_opaque_" + UUID.randomUUID().toString().replace('-', '_');
		boolean frozen = registry.isFrozen();
		if (frozen) {
			registry.unfreeze();
		}
		boolean registered = false;
		try {
			registry.register(key, OpaqueNode.TYPE);
			registered = true;
			var source = new FX();
			var opaque = new OpaqueNode();
			opaque.extra.putInt("unsupported_current", 41);
			source.getFxData().objects().add(opaque);
			var definition = PhotonRuntimeFactory.prepare(source);
			require(definition.fallbackFields() > 0 && !definition.fallbackReasons().isEmpty(),
					"unsupported data missing fallback diagnostics");
			var row = new JsonObject();
			row.addProperty("fallback_fields", definition.fallbackFields());
			var reasons = new JsonArray();
			definition.fallbackReasons().forEach(reasons::add);
			row.add("fallback_reasons", reasons);
			report.add("unsupported_fixture", row);
			var expected = PhotonRuntimeFactory.nativeRuntime(source);
			try {
				for (var mode : ORIGINAL_MODES) {
					var actual = PhotonRuntimeFactory.instantiate(definition, mode);
					try {
						require(actual.objects.size() == 2, "unsupported node silently dropped");
						compare(expected, actual, mode);
					} finally {
						actual.destroy(true);
					}
				}
			} finally {
				expected.destroy(true);
			}
		} finally {
			try {
				if (registered && registry.get(key) == OpaqueNode.TYPE) {
					registry.remove(key);
				}
			} finally {
				if (frozen && !registry.isFrozen()) {
					registry.freeze();
				}
			}
		}
	}

	public static class OpaqueNode extends EmptyFXObject {
		public static final FXObjectType TYPE = new FXObjectType() {
			@Override
			public IFXObject create() {
				return new OpaqueNode();
			}

			@Override
			public IGuiTexture icon() {
				return IGuiTexture.EMPTY;
			}
		};
		@Persisted
		public CompoundTag extra = new CompoundTag();

		@Override
		public FXObjectType getFXObjectType() {
			return TYPE;
		}
	}

	private static Tag encode(IFXObject object) {
		return IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, object).getOrThrow();
	}

	private static CompoundTag source(IFXObject object) {
		return switch (object) {
			case PhotonModelOutput model -> model.source();
			case PhotonChildSpawner child -> child.source();
			case PhotonNamedController named -> named.source();
			default -> new CompoundTag();
		};
	}

	private static Object field(Object owner, String name) throws Exception {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	private static void rejects(Lease lease) {
		boolean rejected = false;
		try {
			lease.runtime();
		} catch (IllegalStateException expected) {
			rejected = true;
		}
		require(rejected, "stale Lease.runtime accepted");
	}

	private void closeOwned() {
		for (var lease : owned) {
			try {
				lease.close(true);
			} catch (RuntimeException failure) {
				failures.add(failure);
			}
		}
		owned.clear();
	}

	private void finish(BenchClientContext context, String label) throws Exception {
		runCase("owned_lease_cleanup_at_finish", this::closeOwned);
		report.add("sources", sources);
		report.add("cases", cases);
		report.addProperty("source_count", sources.size());
		report.addProperty("object_count", objectCount);
		report.addProperty("case_count", cases.size());
		report.addProperty("failed_count", failures.size());
		var failureStacks = new JsonArray();
		for (var failure : failures) {
			var stack = new StringWriter();
			try (var writer = new PrintWriter(stack)) {
				failure.printStackTrace(writer);
			}
			failureStacks.add(stack.toString());
		}
		report.add("failure_stack_traces", failureStacks);
		report.addProperty("passed", completed && failures.isEmpty() && resourceIndex == RESOURCES.size()
				&& sources.size() == RESOURCES.size() && objectCount == EXPECTED_OBJECTS);
		report.addProperty("status", report.get("passed").getAsBoolean() ? "PASSED" : "FAILED");
		report.addProperty("completed", completed);
		report.addProperty("released_tick", releasedTick);
		report.addProperty("released_frame", releasedFrame);
		report.addProperty("end_tick", PhotonRuntimeFactory.ticks());
		report.addProperty("end_frame", PhotonRuntimeFactory.frames());
		report.addProperty("elapsed_ns", System.nanoTime() - started);
		var after = PhotonRuntimeFactory.statistics();
		report.add("post_archive_fixture_counters_after", counters(after));
		var delta = new LinkedHashMap<String, Long>();
		after.forEach((key, value) -> delta.put(key, value - countersBefore.getOrDefault(key, 0L)));
		report.add("post_archive_fixture_counter_delta", counters(delta));
		context.artifacts().write(label + "-" + report.get("artifact_id").getAsString()
				+ "-creation-native.json", "application/json", report.toString());
		finished = true;
	}

	private void runCase(String name, CaseBody body) {
		var result = new JsonObject();
		result.addProperty("case", name);
		long begin = System.nanoTime();
		try {
			body.run();
			result.addProperty("passed", true);
		} catch (Exception | AssertionError | LinkageError failure) {
			failures.add(failure);
			result.addProperty("passed", false);
			result.addProperty("failure", failure.toString());
			var stack = new StringWriter();
			try (var writer = new PrintWriter(stack)) {
				failure.printStackTrace(writer);
			}
			result.addProperty("failure_stack_trace", stack.toString());
		}
		result.addProperty("elapsed_ns", System.nanoTime() - begin);
		cases.add(result);
	}

	private static JsonObject counters(Map<String, Long> values) {
		var result = new JsonObject();
		values.forEach(result::addProperty);
		return result;
	}

	private static String hash(Tag tag) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(tag.toString().getBytes(StandardCharsets.UTF_8)));
	}

	private static void equal(Object expected, Object actual) {
		if (Objects.equals(expected, actual)) {
			return;
		}

		if (expected instanceof Tag left && actual instanceof Tag right) {
			var path = new StringBuilder("$");
			while (left.getId() == right.getId()) {
				if (left instanceof CompoundTag a && right instanceof CompoundTag b) {
					var keys = new TreeSet<>(a.keySet());
					keys.addAll(b.keySet());
					String different = null;
					for (var key : keys) {
						if (!Objects.equals(a.get(key), b.get(key))) {
							different = key;
							break;
						}
					}
					if (different == null) {
						break;
					}
					var keyPreview = different.length() > 96 ? different.substring(0, 96) + "..." : different;
					path.append("[\"").append(keyPreview.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"]");
					left = a.get(different);
					right = b.get(different);
					if (left == null || right == null) {
						break;
					}
				} else if (left instanceof CollectionTag a && right instanceof CollectionTag b) {
					int index = 0;
					while (index < Math.min(a.size(), b.size()) && Objects.equals(a.get(index), b.get(index))) {
						index++;
					}
					path.append('[').append(index).append(']');
					left = index < a.size() ? a.get(index) : null;
					right = index < b.size() ? b.get(index) : null;
					if (left == null || right == null) {
						break;
					}
				} else {
					break;
				}
			}

			var previews = new ArrayList<String>();
			for (var value : new Tag[]{left, right}) {
				var summary = switch (value) {
					case null -> "<missing>";
					case CompoundTag compound -> "CompoundTag(keys=" + compound.size() + ")";
					case CollectionTag collection ->
							value.getClass().getSimpleName() + "(size=" + collection.size() + ")";
					case StringTag string -> {
						var text = string.value();
						yield "StringTag(length=" + text.length() + ", value=" + text.substring(0, Math.min(160, text.length())) + ")";
					}
					default -> value.getClass().getSimpleName() + "(" + value + ")";
				};
				previews.add(summary);
			}
			throw new AssertionError("Tag first-difference path=" + path + "; expected=" + previews.get(0)
					+ "; actual=" + previews.get(1));
		}

		throw new AssertionError("expected=" + expected + "; actual=" + actual);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	@FunctionalInterface
	private interface CaseBody {
		void run() throws Exception;
	}

	private record Probe(String id, FX source, Definition definition, Object worldKey,
	                     Lease oldLease, FXRuntime backend, boolean reusable) {
	}
}
