package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.conductor.ConductorControls;
import org.unitego.lobecorp.client.photon.runtime.QueenBeamEffects;

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// 显式启用后只在计时归档末尾运行；堆差属于整 JVM，不冒充模型独占净存活字节。
public class PhotonRuntimeLifetimeNativeCheck {
	public static final String ENABLED_PROPERTY = "lobecorp.photon.bench.retentionProbe";
	private static final int RUNTIME_COUNT = 8;
	private static final int GC_ATTEMPTS = 3;
	private final JsonObject report = new JsonObject();
	private final JsonArray cases = new JsonArray();
	private final List<Throwable> failures = new ArrayList<>();

	public static void verify(BenchClientContext context, String label) throws Exception {
		if (!Boolean.getBoolean(ENABLED_PROPERTY)) {
			return;
		}

		var check = new PhotonRuntimeLifetimeNativeCheck();
		check.report.addProperty("phase", "verify_after_main_and_copy_measurement_archive");
		check.report.addProperty("main_window_sample", false);
		check.report.addProperty("forced_gc_only_in_this_opt_in_probe", true);
		check.report.addProperty("heap_scope", "whole JVM after requested GC; background and cache activity prevent exact subsystem retained-byte attribution");
		check.report.addProperty("start_nano_time", System.nanoTime());
		check.runCase("disconnect_clear_preserves_mouse_state", check::clearControls);
		check.runCase("queen_runtime_holders_cleared", check::clearQueen);
		check.runCase("owned_runtime_destroy_and_reference_release", check::runtimes);
		check.report.add("cases", check.cases);
		check.report.addProperty("passed", check.failures.isEmpty());
		check.report.addProperty("end_nano_time", System.nanoTime());
		context.artifacts().write(label + "-runtime-lifetime-native.json", "application/json", check.report.toString());

		if (!check.failures.isEmpty()) {
			var failure = new IllegalStateException("runtime_lifetime_check_failed:" + check.failures.size());
			check.failures.forEach(failure::addSuppressed);
			throw failure;
		}
	}

	private void clearControls() {
		var minecraft = Minecraft.getInstance();
		boolean grabbed = minecraft.mouseHandler.isMouseGrabbed();
		ConductorControls.clearSession();
		equal(grabbed, minecraft.mouseHandler.isMouseGrabbed());
		equal(false, ConductorControls.active());
		equal(true, ConductorControls.selected().isEmpty());
		ConductorControls.clearSession();
		equal(grabbed, minecraft.mouseHandler.isMouseGrabbed());
	}

	private void clearQueen() {
		QueenBeamEffects.clear();
		var state = QueenBeamEffects.retentionStatistics();
		equal(0, state.playing());
		equal(0, state.retired());
		equal(0, state.prepared());
		equal(0, state.idle());
		equal(false, state.queueRetained());
		equal(false, state.levelRetained());
		QueenBeamEffects.clear();
		equal(state, QueenBeamEffects.retentionStatistics());
	}



	private void runtimes() {
		var baseline = collect();
		var state = create(effect());
		var held = collect();
		equal(RUNTIME_COUNT, state.strong.size());
		equal(0, collected(state.references));
		report.addProperty("owned_reference_count", state.references.size());
		release(state);
		var released = collect();
		for (int attempt = 1; attempt < GC_ATTEMPTS && collected(state.references) < state.references.size(); attempt++) {
			released = collect();
		}

		int collected = collected(state.references);
		boolean gcObserved = released.gcCount() > held.gcCount();
		var memory = new JsonObject();
		memory.add("baseline", snapshot(baseline));
		memory.add("held", snapshot(held));
		memory.add("released", snapshot(released));
		memory.addProperty("held_minus_baseline_heap_bytes", held.heapBytes() - baseline.heapBytes());
		memory.addProperty("released_minus_baseline_heap_bytes", released.heapBytes() - baseline.heapBytes());
		memory.addProperty("collected_owned_references", collected);
		memory.addProperty("remaining_owned_references", state.references.size() - collected);
		memory.addProperty("gc_observed", gcObserved);
		memory.addProperty("release_status", collected == state.references.size() ? "collected"
				: gcObserved ? "still_reachable_under_probe" : "unknown_gc_not_observed");
		memory.addProperty("net_model_retained_bytes", "unknown: heap deltas are not exclusive retained sizes");
		report.add("memory", memory);
		if (gcObserved && collected != state.references.size()) {
			throw new AssertionError("owned runtime objects still reachable after destroy:" + (state.references.size() - collected));
		}
	}



	private static FX effect() {
		return Objects.requireNonNull(FXHelper.getFX(Identifier.parse("lobecorp:the_queen_of_hatred/laser")),
				"real laser FX unavailable");
	}

	private static Owned create(FX source) {
		var state = new Owned();
		for (int i = 0; i < RUNTIME_COUNT; i++) {
			var runtime = source.createRuntime(true);
			state.strong.add(runtime);
			state.references.add(new WeakReference<>(runtime));
			runtime.objects.values().forEach(object -> state.references.add(new WeakReference<>(object)));
		}

		return state;
	}

	private static void release(Owned state) {
		state.strong.forEach(runtime -> runtime.destroy(true));
		state.strong.clear();
	}

	private static int collected(List<WeakReference<Object>> references) {
		int collected = 0;
		for (var reference : references) {
			if (reference.get() == null) {
				collected++;
			}
		}

		return collected;
	}

	private static GcSnapshot collect() {
		System.gc();
		return new GcSnapshot(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(), gcCount());
	}

	private static long gcCount() {
		long count = 0;
		for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
			count += Math.max(0, collector.getCollectionCount());
		}

		return count;
	}

	private static JsonObject snapshot(GcSnapshot value) {
		var result = new JsonObject();
		result.addProperty("heap_bytes", value.heapBytes());
		result.addProperty("gc_count", value.gcCount());
		return result;
	}




	private void runCase(String name, CaseBody body) {
		var result = new JsonObject();
		result.addProperty("case", name);
		try {
			body.run();
			result.addProperty("passed", true);
		} catch (Exception | AssertionError failure) {
			failures.add(failure);
			result.addProperty("passed", false);
			result.addProperty("failure", failure.toString());
		}
		cases.add(result);
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

	private record GcSnapshot(long heapBytes, long gcCount) {
	}

	private static class Owned {
		private final List<FXRuntime> strong = new ArrayList<>();
		private final List<WeakReference<Object>> references = new ArrayList<>();
	}
}
