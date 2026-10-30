package org.unitego.lobecorp.util.photon.performance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.unitego.lobecorp.Lobecorp;

import java.util.Map;
import java.util.function.Function;

/// 仅在显式开启时追踪当前预热任务；不持有材质、资源或 GPU 对象，不缓存渲染结果。
public class PhotonMaterialDiagnosticsUtil {
	public static final String PROPERTY = "lobecorp.photon.diag.material";
	private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
	private static final ThreadLocal<Sample> CURRENT = new ThreadLocal<>();

	public enum Stage {
		MATERIAL_LOOKUP, GRAPH_LOOKUP, GRAPH_RESOURCE_BUILD, GRAPH_MATERIAL_CACHE, KG_MATERIAL_CREATE,
		KG_PIPELINE_CACHE, KG_PIPELINE_BUILD, HDR_MATERIAL_CACHE, HDR_MATERIAL_BUILD,
		PHOTON_PIPELINE_CACHE, PHOTON_PIPELINE_BUILD,
		DEVICE_PIPELINE_CACHE, DEVICE_PIPELINE_BUILD, DEVICE_SHADER_CACHE, DEVICE_SHADER_BUILD,
		GL_COMPILE_SHADER_CALL, PROGRAM_LINK, GL_LINK_PROGRAM_CALL
	}

	public static boolean enabled() {
		return ENABLED;
	}

	public static boolean active() {
		return ENABLED && CURRENT.get() != null;
	}

	/// 禁用时直接返回原任务；开启时每个任务独立结算，异常也在 finally 清理线程上下文。
	public static Runnable wrap(String label, Runnable action) {
		if (!ENABLED) {
			return action;
		}

		return () -> {
			if (CURRENT.get() != null) {
				action.run();
				return;
			}

			var sample = new Sample(label);
			CURRENT.set(sample);
			try {
				action.run();
			} finally {
				CURRENT.remove();
				sample.log();
			}
		};
	}

	public static <T> T call(Stage stage, Operation<T> original, Object... arguments) {
		if (!active()) {
			return original.call(arguments);
		}

		var sample = CURRENT.get();
		int index = stage.ordinal();
		long started = System.nanoTime();
		sample.calls[index]++;
		boolean completed = false;
		try {
			var result = original.call(arguments);
			completed = true;
			return result;
		} finally {
			if (!completed) {
				sample.thrown[index]++;
			}
			sample.nanos[index] += System.nanoTime() - started;
		}
	}

	/// miss 仅在原缓存实际调用映射函数时增加；返回 null 或抛异常仍计 miss，不误记为 hit。
	public static Object cache(Stage cache, Stage build, Map<Object, Object> map, Object key,
	                           Function<Object, Object> mapping, Operation<Object> original) {
		if (!active()) {
			return original.call(map, key, mapping);
		}

		var sample = CURRENT.get();
		int index = cache.ordinal();
		long started = System.nanoTime();
		boolean[] missed = {false};
		boolean completed = false;
		sample.calls[index]++;
		try {
			var result = original.call(map, key, (Function<Object, Object>) value -> {
				missed[0] = true;
				sample.misses[index]++;
				int buildIndex = build.ordinal();
				long buildStarted = System.nanoTime();
				sample.calls[buildIndex]++;
				try {
					return mapping.apply(value);
				} catch (RuntimeException | Error failure) {
					sample.thrown[buildIndex]++;
					throw failure;
				} finally {
					sample.nanos[buildIndex] += System.nanoTime() - buildStarted;
				}
			});
			completed = true;
			return result;
		} catch (RuntimeException | Error failure) {
			sample.thrown[index]++;
			throw failure;
		} finally {
			if (completed && !missed[0]) {
				sample.hits[index]++;
			}
			sample.nanos[index] += System.nanoTime() - started;
		}
	}

	public static Object lookup(Stage stage, Map<Object, Object> map, Object key, Operation<Object> original) {
		var value = call(stage, original, map, key);
		if (active()) {
			var sample = CURRENT.get();
			int index = stage.ordinal();
			if (value == null) {
				sample.misses[index]++;
			} else {
				sample.hits[index]++;
			}
		}
		return value;
	}

	/// 非 null 结果且未触发 compile 才算资源缓存命中；缺失资源和失效 tag 不计命中。
	public static <T> T graphLookup(Object path, Operation<T> original) {
		if (!active() || path == null) {
			return original.call(path);
		}

		var sample = CURRENT.get();
		long before = sample.calls[Stage.GRAPH_RESOURCE_BUILD.ordinal()];
		boolean completed = false;
		T result = null;
		try {
			result = call(Stage.GRAPH_LOOKUP, original, path);
			completed = true;
			return result;
		} finally {
			int index = Stage.GRAPH_LOOKUP.ordinal();
			if (sample.calls[Stage.GRAPH_RESOURCE_BUILD.ordinal()] != before) {
				sample.misses[index]++;
			} else if (completed && result != null) {
				sample.hits[index]++;
			} else if (completed) {
				sample.misses[index]++;
			}
		}
	}

	private static class Sample {
		private final String label;
		private final long started = System.nanoTime();
		private final long[] calls = new long[Stage.values().length];
		private final long[] nanos = new long[Stage.values().length];
		private final long[] hits = new long[Stage.values().length];
		private final long[] misses = new long[Stage.values().length];
		private final long[] thrown = new long[Stage.values().length];

		private Sample(String label) {
			this.label = label;
		}

		private void log() {
			long ended = System.nanoTime();
			var details = new StringBuilder();
			for (var stage : Stage.values()) {
				int index = stage.ordinal();
				if (calls[index] == 0) {
					continue;
				}

				details.append(stage).append("[calls=").append(calls[index])
						.append(",ns=").append(nanos[index]).append(",hit=").append(hits[index])
						.append(",miss=").append(misses[index]).append(",thrown=").append(thrown[index]).append("] ");
			}

			Lobecorp.LOGGER.info("CASTMATERIAL task={} startNs={} endNs={} totalNs={} inclusiveStages={}",
					label, started, ended, ended - started, details);
		}
	}
}
