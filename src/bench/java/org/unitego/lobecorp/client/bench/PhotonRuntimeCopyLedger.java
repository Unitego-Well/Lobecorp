package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.sun.management.ThreadMXBean;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.config.photon.PhotonRepeatedEffectConfig;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelAssetUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelRuntimeCopyUtil;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/// 测量后逐对象账本；阶段探针互相独立，不能相加当成端到端成本。
public class PhotonRuntimeCopyLedger {
	public static final String ENABLED_PROPERTY = "lobecorp.photon.copyLedger.enabled";
	// 仅控制归档后的诊断工作，不改变40秒场景或旧128次ABBA对照。
	private static final int REPETITIONS = Math.clamp(Integer.getInteger("lobecorp.photon.copyLedger.repetitions", 8), 1, 32);
	private static final long BUDGET_NS = Math.clamp(Long.getLong("lobecorp.photon.copyLedger.budgetMillis", 60000L), 1L, 120000L) * 1000000L;
	private static volatile Object sink;
	private final JsonArray objects = new JsonArray();
	private final JsonArray resources = new JsonArray();
	private final Map<String, Totals> types = new LinkedHashMap<>();
	private final Map<String, Totals> resourcePhases = new LinkedHashMap<>();
	private String currentResource = "";
	private final ThreadMXBean allocation = allocationBean();
	private final long thread = Thread.currentThread().threadId();
	private final long started = System.nanoTime();
	private boolean exhausted;

	public JsonObject collect() {
		var report = new JsonObject();
		report.addProperty("phase", "verify_after_main_window_archive");
		report.addProperty("main_window_sample", false);
		report.addProperty("native_model_copy_property", PhotonModelRuntimeCopyUtil.NATIVE_MODEL_COPY_PROPERTY);
		report.addProperty("native_model_copy_enabled", PhotonModelRuntimeCopyUtil.nativeModelCopyEnabled());
		report.addProperty("native_baseline", "unchanged public IFXObject.CODEC encode/parse; never model.deepCopy");
		report.addProperty("start_nano_time", started);
		report.addProperty("repetitions_per_block", REPETITIONS);
		report.addProperty("soft_budget_ns", BUDGET_NS);
		report.addProperty("budget_boundary", "checked before each operation; one codec/load cannot be preempted");
		report.addProperty("control", "same source; equal warmup; ABBA full wrapper native/current; no GC or allocation-counter toggles");
		report.addProperty("memory_scope", "thread allocated bytes only; heap delta is observed, not retained size; no GPU/native/retained estimate");
		report.addProperty("phase_scope", "CODEC decode includes factory, type guards, setters and adaptation; wrapper guard is a separate probe, not part of direct CODEC copy; phases are not additive");
		report.addProperty("frequency_evidence", "20261030 remaining laser main objects.csv: reverse_muzzle FX_COPY=451; flow_model_template=132; not weights for this microbenchmark");
		try {
			// 激光根优先，其次旧主窗口的高频模型所属light_ball；其余是旧详细归档实际出现的依赖资源。
			for (var id : List.of("lobecorp:the_queen_of_hatred/laser", "lobecorp:the_queen_of_hatred/light_ball",
					"lobecorp:the_queen_of_hatred/magic_circle", "lobecorp:the_queen_of_hatred/magic_circle2",
					"lobecorp:the_queen_of_hatred/magic_circle3", "lobecorp:the_queen_of_hatred/accumulate_particle",
					"lobecorp:the_queen_of_hatred/laser_spell", "lobecorp:the_queen_of_hatred/star_beam_spell",
					"lobecorp:the_queen_of_hatred/star_beam_spell_ball", "lobecorp:the_queen_of_hatred/star_beam_spell_beam",
					PhotonRepeatedEffectConfig.NATIVE_FX)) {
				resource(id);
			}
		} finally {
			sink = null;
		}

		var aggregates = new JsonArray();
		types.forEach((key, value) -> aggregates.add(value.json(key)));
		report.add("resources", resources);
		report.add("objects", objects);
		report.add("type_phase_totals", aggregates);
		var resourceTotals = new JsonArray();
		resourcePhases.forEach((key, value) -> resourceTotals.add(value.json(key)));
		report.add("resource_phase_totals", resourceTotals);
		long failedRows = 0;
		for (var row : objects) {
			if (row.getAsJsonObject().get("status").getAsString().equals("failed")) {
				failedRows++;
			}
		}

		report.addProperty("failed_object_rows", failedRows);
		report.addProperty("budget_exhausted", exhausted);
		report.addProperty("end_nano_time", System.nanoTime());
		report.addProperty("benefit_proven", false);
		return report;
	}

	private void resource(String id) {
		currentResource = id;
		var resource = new JsonObject();
		resource.addProperty("resource", id);
		resources.add(resource);
		if (!withinBudget()) {
			resource.addProperty("status", "skipped_budget");
			return;
		}

		try {
			// 查询/首次加载在任何对象计时之外；不能混作复制阶段。
			long lookup = System.nanoTime();
			var fx = Objects.requireNonNull(FXHelper.getFX(Identifier.parse(id)), "missing FX:" + id);
			resource.addProperty("lookup_ns_outside_copy", System.nanoTime() - lookup);
			FXData data = fx.getFxData();
			resource.addProperty("objects", data.objects().size());
			var before = data.serializeNBT(Platform.getFrozenRegistry());
			int ordinal = 0;
			for (var source : data.objects()) {
				object(id, ++ordinal, source);
			}

			if (!before.equals(data.serializeNBT(Platform.getFrozenRegistry()))) {
				throw new AssertionError("borrowed FX definition changed:" + id);
			}

			resource.addProperty("source_unchanged", true);
			resource.addProperty("status", exhausted ? "partial_budget" : "complete");
		} catch (RuntimeException | AssertionError failure) {
			resource.addProperty("status", "failed");
			resource.addProperty("failure", failure.toString());
		}
	}

	private void object(String id, int ordinal, IFXObject source) {
		var row = new JsonObject();
		row.addProperty("resource", id);
		row.addProperty("ordinal", ordinal);
		row.addProperty("name", source.getName());
		row.addProperty("class", source.getClass().getName());
		row.addProperty("type", source.name());
		row.addProperty("transform_id", String.valueOf(source.transform().id()));
		objects.add(row);
		if (!withinBudget()) {
			row.addProperty("status", "skipped_budget");
			return;
		}

		try {
			var encoded = IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, source).getOrThrow();
			var expected = Objects.requireNonNull(IFXObject.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
			var actual = Objects.requireNonNull(source.deepCopy());
			if (source == actual || expected.getClass() != actual.getClass()
					|| !IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, expected).getOrThrow()
					.equals(IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, actual).getOrThrow())
					|| !encoded.equals(IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, source).getOrThrow())) {
				throw new AssertionError("wrapper or source equivalence failed");
			}

			row.addProperty("wrapper_equivalent_source_unchanged", true);
			var blocks = new JsonArray();
			row.add("blocks", blocks);
			// 不保留128份大对象：每次结果只写volatile sink，两个方案保留策略完全一致。
			for (int i = 0; i < 2 && withinBudget(); i++) {
				sink = nativeCopy(source);
				sink = source.deepCopy();
			}

			int order = 0;
			for (var mode : List.of("A", "B", "B", "A")) {
				var block = measure(mode.equals("A") ? () -> nativeCopy(source) : source::deepCopy);
				block.addProperty("phase", "full_copy");
				block.addProperty("mode", mode);
				block.addProperty("block", ++order);
				blocks.add(block);
				aggregate(source, "full_copy_" + mode, block);
			}

			phase(source, blocks, "encode", () -> IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, source).getOrThrow());
			phase(source, blocks, "decode_including_construct_type_guard_adapt", () -> IFXObject.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
			phase(source, blocks, "factory_only", source.getFXObjectType()::create);
			phase(source, blocks, "guard_only", () -> {
				PhotonEditorProjectGuardUtil.requireSupported(encoded);
				return Boolean.TRUE;
			});
			if (source instanceof PhotonModelOutput model && encoded instanceof CompoundTag wrapper) {
				var payload = wrapper.getCompoundOrEmpty("data");
				phase(source, blocks, "model_adapt_only", () -> adapt(payload));
				boolean eligible = PhotonModelRuntimeCopyUtil.supportsEncoded(model, encoded);
				row.addProperty("model_typed_restore_eligible", eligible);
				row.addProperty("model_actual_deep_copy_path", PhotonModelRuntimeCopyUtil.nativeModelCopyEnabled()
						? "native_forced" : eligible ? "typed_restore" : "native_fallback");
				row.addProperty("model_encode_eliminated", false);
				row.addProperty("model_restore_scope", "same encoded wrapper; typed scalar/base restore; two native renderer subtrees; full adaptObject retained");
				phase(source, blocks, "model_typed_candidate_restore_without_encode", () -> PhotonModelRuntimeCopyUtil.copyEncoded(model, encoded));
				var typed = Objects.requireNonNull(PhotonModelRuntimeCopyUtil.typedCopy(model));
				if (!IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, expected).getOrThrow()
						.equals(IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, typed).getOrThrow())) {
					throw new AssertionError("native versus typed candidate equivalence failed");
				}
				for (int i = 0; i < 2 && withinBudget(); i++) {
					sink = nativeCopy(model);
					sink = PhotonModelRuntimeCopyUtil.typedCopy(model);
				}

				for (var mode : List.of("A", "B", "B", "A")) {
					var block = measure(mode.equals("A") ? () -> nativeCopy(model) : () -> PhotonModelRuntimeCopyUtil.typedCopy(model));
					block.addProperty("phase", "model_native_vs_typed_full_copy");
					block.addProperty("mode", mode);
					block.addProperty("typed_candidate_path", eligible ? "typed_restore" : "native_fallback");
					blocks.add(block);
					aggregate(source, "model_native_vs_typed_full_copy_" + mode, block);
				}
				row.add("full_copy_comparison", comparison(blocks, "full_copy"));
				row.add("native_vs_typed_comparison", comparison(blocks, "model_native_vs_typed_full_copy"));
			}

			row.addProperty("status", exhausted ? "partial_budget" : "complete");
		} catch (RuntimeException | AssertionError failure) {
			row.addProperty("status", "failed");
			row.addProperty("failure", failure.toString());
		}
	}

	private static JsonObject comparison(JsonArray blocks, String phase) {
		long nativeNs = 0;
		long currentNs = 0;
		long nativeBytes = 0;
		long currentBytes = 0;
		long nativeCount = 0;
		long currentCount = 0;
		boolean allocationAvailable = true;
		int completeBlocks = 0;
		for (var element : blocks) {
			var block = element.getAsJsonObject();
			if (!block.get("phase").getAsString().equals(phase) || !block.get("complete").getAsBoolean()) {
				continue;
			}
			completeBlocks++;
			allocationAvailable &= block.get("allocation_available").getAsBoolean();
			if (block.get("mode").getAsString().equals("A")) {
				nativeNs += block.get("elapsed_ns").getAsLong();
				nativeBytes += block.get("allocated_bytes").getAsLong();
				nativeCount += block.get("copies").getAsLong();
			} else {
				currentNs += block.get("elapsed_ns").getAsLong();
				currentBytes += block.get("allocated_bytes").getAsLong();
				currentCount += block.get("copies").getAsLong();
			}
		}
		var result = new JsonObject();
		boolean comparable = completeBlocks == 4 && nativeCount > 0 && nativeCount == currentCount;
		result.addProperty("phase", phase);
		result.addProperty("mode_A", "unchanged_public_codec_encode_parse");
		result.addProperty("mode_B", phase.equals("full_copy") ? "actual_deepCopy" : "typed_candidate_independent_of_native_control");
		result.addProperty("equal_complete_operation_counts", comparable);
		result.addProperty("native_operations", nativeCount);
		result.addProperty("current_operations", currentCount);
		result.addProperty("native_minus_current_ns", comparable ? nativeNs - currentNs : 0);
		result.addProperty("allocation_available", comparable && allocationAvailable);
		result.addProperty("native_minus_current_allocated_bytes", comparable && allocationAvailable ? nativeBytes - currentBytes : 0);
		result.addProperty("positive_delta_means_reduction", true);
		result.addProperty("scope", "measured full copy ABBA; encode remains; no FPS or retained-memory claim");
		return result;
	}

	private void phase(IFXObject source, JsonArray blocks, String phase, Supplier<?> operation) {
		if (withinBudget()) {
			sink = operation.get();
		}

		var block = measure(operation);
		block.addProperty("phase", phase);
		blocks.add(block);
		aggregate(source, phase, block);
	}

	private JsonObject measure(Supplier<?> operation) {
		var result = new JsonObject();
		long heapBefore = heapUsed();
		long gcBefore = gcCount();
		long bytesBefore = allocated();
		long time = System.nanoTime();
		int copies = 0;
		while (copies < REPETITIONS && withinBudget()) {
			sink = operation.get();
			copies++;
		}

		long elapsed = System.nanoTime() - time;
		long bytesAfter = allocated();
		boolean available = bytesBefore >= 0 && bytesAfter >= bytesBefore;
		result.addProperty("copies", copies);
		result.addProperty("last_result_present", copies > 0 && sink != null);
		result.addProperty("complete", copies == REPETITIONS);
		result.addProperty("elapsed_ns", elapsed);
		result.addProperty("allocated_bytes", available ? bytesAfter - bytesBefore : -1);
		result.addProperty("allocation_available", available);
		result.addProperty("heap_used_delta_observed", heapUsed() - heapBefore);
		result.addProperty("gc_count_delta", gcCount() - gcBefore);
		return result;
	}

	private void aggregate(IFXObject source, String phase, JsonObject block) {
		var key = source.getClass().getName() + "|" + phase;
		types.computeIfAbsent(key, _ -> new Totals()).add(block);
		resourcePhases.computeIfAbsent(currentResource + "|" + phase, _ -> new Totals()).add(block);
	}

	private boolean withinBudget() {
		if (System.nanoTime() - started >= BUDGET_NS) {
			exhausted = true;
		}

		return !exhausted;
	}

	private long allocated() {
		return allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread);
	}

	private static ThreadMXBean allocationBean() {
		var bean = ManagementFactory.getThreadMXBean();
		return bean instanceof ThreadMXBean available && available.isThreadAllocatedMemorySupported()
				&& available.isThreadAllocatedMemoryEnabled() ? available : null;
	}

	private static long heapUsed() {
		var runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	private static long gcCount() {
		long total = 0;
		for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
			long count = collector.getCollectionCount();
			if (count >= 0) {
				total += count;
			}
		}

		return total;
	}

	private static IFXObject nativeCopy(IFXObject source) {
		return IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, source).result()
				.flatMap(tag -> IFXObject.CODEC.parse(NbtOps.INSTANCE, tag).result()).orElse(null);
	}

	private static CompoundTag adapt(CompoundTag payload) {
		try {
			return PhotonModelAssetUtil.adaptObject(payload);
		} catch (IOException failure) {
			throw new IllegalArgumentException(failure);
		}
	}

	private static class Totals {
		private long operations;
		private long elapsed;
		private long allocated;
		private int blocks;
		private boolean allocationAvailable = true;

		private void add(JsonObject block) {
			// 截断块留在逐对象原件，不混入完整块聚合。
			if (!block.get("complete").getAsBoolean()) {
				return;
			}

			blocks++;
			operations += block.get("copies").getAsLong();
			elapsed += block.get("elapsed_ns").getAsLong();
			allocationAvailable &= block.get("allocation_available").getAsBoolean();
			if (block.get("allocation_available").getAsBoolean()) {
				allocated += block.get("allocated_bytes").getAsLong();
			}
		}

		private JsonObject json(String key) {
			var result = new JsonObject();
			result.addProperty("group_phase", key);
			result.addProperty("complete_blocks", blocks);
			result.addProperty("operations", operations);
			result.addProperty("elapsed_ns", elapsed);
			result.addProperty("allocated_bytes", allocationAvailable && operations > 0 ? allocated : -1);
			result.addProperty("allocation_available", allocationAvailable && operations > 0);
			return result;
		}
	}
}
