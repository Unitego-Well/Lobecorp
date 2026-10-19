package org.unitego.lobecorp.util.photon.performance;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// GPU 原始环形记录之外保留有界整会话统计与峰值；共享父子 pass 的时间不能相加。
public class PhotonPerformanceGpuStatsUtil {
	public static final int MAX_GROUPS = 4096;
	private final Map<Key, Aggregate> groups = new LinkedHashMap<>();
	private long dropped;

	public void sample(int frame, String pass, int node, boolean shared, String status, long duration, JsonObject sample) {
		var aggregate = group(frame, pass, node, shared);
		if (aggregate == null) {
			return;
		}
		aggregate.requested++;
		if (aggregate.first == null) {
			aggregate.first = sample.deepCopy();
		}
		if (!"measured".equals(status) || duration < 0) {
			aggregate.unavailable++;
			return;
		}
		aggregate.measured++;
		aggregate.totalNs += duration;
		if (aggregate.maximum == null || duration > aggregate.maximumNs) {
			aggregate.maximumNs = duration;
			aggregate.maximum = sample.deepCopy();
		}
	}

	public void skipped(int frame, String pass, int node, boolean shared) {
		var aggregate = group(frame, pass, node, shared);
		if (aggregate != null) {
			aggregate.requested++;
			aggregate.skipped++;
		}
	}

	public long dropped() {
		return dropped;
	}

	public List<JsonObject> summaries() {
		var result = new ArrayList<JsonObject>(groups.size());
		for (var entry : groups.entrySet()) {
			var key = entry.getKey();
			var aggregate = entry.getValue();
			var value = new JsonObject();
			value.addProperty("record_type", "aggregate");
			value.addProperty("pass", key.pass());
			value.addProperty("node_id", key.node());
			value.addProperty("ownership", key.shared() ? "shared_or_unattributed" : "linked_scope");
			value.addProperty("aggregation_scope", "whole capture, inclusive per pass/node; do not sum nested passes");
			value.addProperty("requested", aggregate.requested);
			value.addProperty("measured", aggregate.measured);
			value.addProperty("unavailable", aggregate.unavailable);
			value.addProperty("skipped_budget_or_pending", aggregate.skipped);
			value.addProperty("first_frame_id", aggregate.firstFrame);
			value.addProperty("last_frame_id", aggregate.lastFrame);
			value.add("duration_ns_sum", aggregate.measured == 0 ? JsonNull.INSTANCE : new JsonPrimitive(aggregate.totalNs));
			value.add("duration_ns_max", aggregate.maximum == null ? JsonNull.INSTANCE : new JsonPrimitive(aggregate.maximumNs));
			value.add("first_sample", aggregate.first == null ? JsonNull.INSTANCE : aggregate.first.deepCopy());
			value.add("maximum_sample", aggregate.maximum == null ? JsonNull.INSTANCE : aggregate.maximum.deepCopy());
			result.add(value);
		}
		return List.copyOf(result);
	}

	private @Nullable Aggregate group(int frame, String pass, int node, boolean shared) {
		var key = new Key(pass, node, shared);
		var aggregate = groups.get(key);
		if (aggregate == null) {
			if (groups.size() >= MAX_GROUPS) {
				dropped++;
				return null;
			}
			aggregate = new Aggregate(frame);
			groups.put(key, aggregate);
		}
		aggregate.firstFrame = Math.min(aggregate.firstFrame, frame);
		aggregate.lastFrame = Math.max(aggregate.lastFrame, frame);
		return aggregate;
	}

	private record Key(String pass, int node, boolean shared) {
	}

	private static class Aggregate {
		private int firstFrame;
		private int lastFrame;
		private long requested, measured, unavailable, skipped, totalNs, maximumNs;
		private @Nullable JsonObject first;
		private @Nullable JsonObject maximum;

		private Aggregate(int frame) {
			firstFrame = frame;
			lastFrame = frame;
		}
	}
}
