package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.unitego.lobecorp.util.photon.performance.PhotonPersistedMetadataUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// 只在 FULL 已结束并归档后的 verify 执行；所有对象和输入均为独立内存 fixture。
public class PhotonPersistedMetadataNativeCheck {
	private final JsonArray cases = new JsonArray();
	private final List<Throwable> failures = new ArrayList<>();

	public static void verify(BenchClientContext context, String label) throws Exception {
		var report = new JsonObject();
		report.addProperty("phase", "verify_after_full_diagnostics_and_archive");
		report.addProperty("main_window_sample", false);
		report.addProperty("jfr_tail_policy", "Exclude this verify interval from the main 40-second workload");
		report.addProperty("real_fx_scope", "Not checked here; real FX belongs to the separate main scenario acceptance");
		report.addProperty("api", "LDLib2 PersistedParser.createCodec / NbtOps.INSTANCE / Platform.getFrozenRegistry");
		report.addProperty("start_epoch_millis", System.currentTimeMillis());
		report.addProperty("start_nano_time", System.nanoTime());
		var check = new PhotonPersistedMetadataNativeCheck();
		check.run();
		report.add("cases", check.cases);
		report.addProperty("passed", check.failures.isEmpty());
		report.addProperty("end_nano_time", System.nanoTime());
		report.addProperty("end_epoch_millis", System.currentTimeMillis());
		context.artifacts().write(label + "-persisted-metadata-native.json", "application/json", report.toString());

		if (!check.failures.isEmpty()) {
			var failure = new IllegalStateException("persisted_metadata_native_check_failed:" + check.failures.size());
			check.failures.forEach(failure::addSuppressed);
			throw failure;
		}
	}

	private void run() {
		// 不设静态 codec 或 fixture，避免类初始化或 setup/预热提前执行原生解析。
		var state = new State();
		runCase("native_decode", "parent=11, child=22, configurable=33, ignored=7, nested=44, flat=55, additional=66", () -> {
			Objects.requireNonNull(Platform.getFrozenRegistry(), "frozen registry unavailable");
			state.codec = PersistedParser.createCodec(Child::new);
			state.input = input(11, 22, 33, 44, 55, 66, true);
			state.original = state.input.copy();
			state.first = state.codec.parse(NbtOps.INSTANCE, state.input).getOrThrow();
			equal(List.of(11, 22, 33, 7, 44, 55, 66), values(state.first));
			return values(state.first).toString();
		});
		runCase("mixin_cache_after_decode", "methods and fields already cached for Child, Parent, Nested, Flat; loader never called", () -> {
			requireDecoded(state);
			for (var type : List.of(Child.class, Parent.class, Nested.class, Flat.class)) {
				// 只查询原生 decode 已经触达的槽；不预填缓存、不改写借用数组。
				PhotonPersistedMetadataUtil.declaredMethods(type, _ -> {
					throw new AssertionError("native_decode_did_not_cache_methods:" + type.getName());
				});
				PhotonPersistedMetadataUtil.declaredFields(type, _ -> {
					throw new AssertionError("native_decode_did_not_cache_fields:" + type.getName());
				});
			}
			return "all eight cache slots hit";
		});
		runCase("hidden_slots_and_child_setter", "child setter called twice, parent restored before child, parent setter never called", () -> {
			requireDecoded(state);
			equal(List.of("before_decode", "child_setter:11:11:-2", "child_setter:22:11:22",
					"additional_decode:66", "after_decode"), state.first.events);
			return state.first.events.toString();
		});
		runCase("final_nested_identity", "nested and flattened final objects retain constructor identity", () -> {
			requireDecoded(state);
			equal(true, state.first.nested == state.first.initialNested);
			equal(true, state.first.flat == state.first.initialFlat);
			equal(44, state.first.nested.nestedValue);
			equal(55, state.first.flat.flatValue);
			return "both final references retained; values=44,55";
		});
		runCase("encode_callbacks_and_exact_tag", "before_encode, additional_encode, after_encode once; exact persisted tag, no ignored/flat wrapper", () -> {
			requireDecoded(state);
			state.first.events.clear();
			state.encoded = state.codec.encodeStart(NbtOps.INSTANCE, state.first).getOrThrow();
			equal(input(11, 22, 33, 44, 55, 66, false), state.encoded);
			equal(List.of("before_encode", "additional_encode", "after_encode"), state.first.events);
			return state.encoded + "; events=" + state.first.events;
		});
		runCase("round_trip", "decode encoded Tag preserves all values and root callback sequence", () -> {
			Objects.requireNonNull(state.encoded, "encode case unavailable");
			var encodedBefore = state.encoded.copy();
			var restored = state.codec.parse(NbtOps.INSTANCE, state.encoded).getOrThrow();
			equal(List.of(11, 22, 33, 7, 44, 55, 66), values(restored));
			equal(List.of("before_decode", "child_setter:11:11:-2", "child_setter:22:11:22",
					"additional_decode:66", "after_decode"), restored.events);
			equal(encodedBefore, state.encoded);
			return values(restored).toString();
		});
		runCase("two_instances_no_shared_refs", "second=101,202,303,7,404,505,606; mutations to first cannot reach second", () -> {
			requireDecoded(state);
			var secondInput = input(101, 202, 303, 404, 505, 606, true);
			var secondOriginal = secondInput.copy();
			var second = state.codec.parse(NbtOps.INSTANCE, secondInput).getOrThrow();
			equal(false, state.first == second);
			equal(false, state.first.nested == second.nested);
			equal(false, state.first.flat == second.flat);
			equal(false, state.first.events == second.events);
			equal(List.of("before_decode", "child_setter:101:101:-2", "child_setter:202:101:202",
					"additional_decode:606", "after_decode"), second.events);
			state.first.hidden = -100;
			((Parent) state.first).hidden = -101;
			state.first.configurableOnly = -102;
			state.first.nested.nestedValue = -103;
			state.first.flat.flatValue = -104;
			state.first.additional = -105;
			state.first.ignored = -106;
			state.first.events.clear();
			equal(List.of(101, 202, 303, 7, 404, 505, 606), values(second));
			equal(secondOriginal, secondInput);
			return values(second).toString();
		});
		runCase("registry_ops_decode", "FrozenRegistry serialization context decodes the same fixture", () -> {
			requireDecoded(state);
			var ops = Platform.getFrozenRegistry().createSerializationContext(NbtOps.INSTANCE);
			var restored = state.codec.parse(ops, state.input).getOrThrow();
			equal(List.of(11, 22, 33, 7, 44, 55, 66), values(restored));
			return values(restored).toString();
		});
		runCase("original_tag_unchanged", "original input including ignored key remains exactly equal after all native operations", () -> {
			Objects.requireNonNull(state.original, "input case unavailable");
			equal(state.original, state.input);
			return state.input.toString();
		});
	}

	private void runCase(String name, String expected, CaseBody body) {
		var result = new JsonObject();
		result.addProperty("case", name);
		result.addProperty("expected", expected);
		try {
			result.addProperty("actual", body.run());
			result.addProperty("passed", true);
		} catch (Exception | AssertionError | LinkageError failure) {
			failures.add(failure);
			result.addProperty("passed", false);
			result.addProperty("actual", failure.toString());
		}
		cases.add(result);
	}

	private static void requireDecoded(State state) {
		Objects.requireNonNull(state.first, "native decode case unavailable");
	}

	private static void equal(Object expected, Object actual) {
		if (!Objects.equals(expected, actual)) {
			throw new AssertionError("expected=" + expected + "; actual=" + actual);
		}
	}

	private static List<Integer> values(Child value) {
		return List.of(((Parent) value).hidden, value.hidden, value.configurableOnly, value.ignored,
				value.nested.nestedValue, value.flat.flatValue, value.additional);
	}

	private static CompoundTag input(int parent, int child, int configurable, int nested, int flat, int additional, boolean ignored) {
		var tag = new CompoundTag();
		tag.putInt("parent_slot", parent);
		tag.putInt("child_slot", child);
		tag.putInt("configurableOnly", configurable);
		var inner = new CompoundTag();
		inner.putInt("nestedValue", nested);
		tag.put("nested", inner);
		tag.putInt("flatValue", flat);
		var extra = new CompoundTag();
		extra.putInt("marker", additional);
		tag.put("_additional", extra);
		if (ignored) {
			tag.putInt("ignored", 999);
		}
		return tag;
	}

	@FunctionalInterface
	private interface CaseBody {
		String run() throws Exception;
	}

	private static class State {
		private Codec<Child> codec;
		private CompoundTag input;
		private CompoundTag original;
		private Child first;
		private Tag encoded;
	}

	private static class Parent implements IPersistedSerializable {
		@Persisted(key = "parent_slot")
		private int hidden = -1;
		protected final List<String> events = new ArrayList<>();
		protected int additional = -1;

		@ConfigSetter(field = "hidden")
		private void parentSetter(int value) {
			throw new AssertionError("parent_setter_must_not_win:" + value);
		}

		@Override
		public void beforeDeserialize() {
			events.add("before_decode");
		}

		@Override
		public void deserializeAdditionalNBT(Tag tag, HolderLookup.@NotNull Provider provider) {
			if (!(tag instanceof CompoundTag extra)) {
				throw new AssertionError("additional_not_compound:" + tag);
			}
			additional = extra.getIntOr("marker", -1);
			events.add("additional_decode:" + additional);
		}

		@Override
		public void afterDeserialize() {
			events.add("after_decode");
		}

		@Override
		public void beforeSerialize() {
			events.add("before_encode");
		}

		@Override
		public Tag serializeAdditionalNBT(HolderLookup.@NotNull Provider provider) {
			events.add("additional_encode");
			var tag = new CompoundTag();
			tag.putInt("marker", additional);
			return tag;
		}

		@Override
		public void afterSerialize() {
			events.add("after_encode");
		}
	}

	private static class Child extends Parent {
		@Persisted(key = "child_slot")
		private int hidden = -2;
		@Configurable
		private int configurableOnly = -3;
		@Persisted
		@Configurable(persisted = false)
		private int ignored = 7;
		@Persisted(subPersisted = true)
		private final Nested nested = new Nested();
		@Configurable(subConfigurable = true, subFlattenPersisted = true)
		private final Flat flat = new Flat();
		private final Nested initialNested = nested;
		private final Flat initialFlat = flat;

		@ConfigSetter(field = "hidden")
		private void childSetter(int value) {
			events.add("child_setter:" + value + ":" + ((Parent) this).hidden + ":" + hidden);
		}
	}

	private static class Nested {
		@Persisted
		private int nestedValue = -4;
	}

	private static class Flat {
		@Persisted
		private int flatValue = -5;
	}
}

