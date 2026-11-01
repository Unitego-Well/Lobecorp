package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;

/// 只读记录当前游戏窗口焦点变化，不据此拒绝样本。
public final class PhotonBenchFocusAudit {
	/// 只保留前 256 次焦点转换，计数和观测时长不因时间线截断而丢失。
	private static final int MAX_TRANSITIONS = 256;
	private final JsonArray timeline = new JsonArray();
	private long droppedTransitions;
	private long lastObservedNs;
	private long unfocusedObservedNs;
	private boolean focused = true;
	private long losses;
	private long restores;
	private long restoreRequests;
	private long ticks;
	private long measureTicks;
	private long measureUnfocusedTicks;

	public void audit(boolean duringMeasure, String phase) {
		long now = System.nanoTime();
		if (lastObservedNs != 0 && !focused) {
			unfocusedObservedNs += now - lastObservedNs;
		}

		ticks++;

		if (duringMeasure) {
			measureTicks++;
		}

		var window = Minecraft.getInstance().getWindow();
		boolean current = window.isFocused();
		if (ticks == 1 || current != focused) {
			if (timeline.size() < MAX_TRANSITIONS) {
				var event = new JsonObject();
				event.addProperty("observed_ns", now);
				event.addProperty("phase", phase);
				event.addProperty("focused", current);
				event.addProperty("restore_requested", false);
				timeline.add(event);
			} else {
				droppedTransitions++;
			}
		}

		if (ticks > 1 && current && !focused) {
			restores++;
		}

		if (!current) {
			if (focused) {
				losses++;
			}

			if (duringMeasure) {
				measureUnfocusedTicks++;
			}


		}

		focused = current;
		lastObservedNs = now;
	}

	public JsonObject snapshot() {
		var result = new JsonObject();
		result.addProperty("ticks", ticks);
		result.add("focus_timeline", timeline.deepCopy());
		result.addProperty("focus_timeline_dropped", droppedTransitions);
		result.addProperty("unfocused_observed_ns", unfocusedObservedNs
				+ (!focused && lastObservedNs != 0 ? System.nanoTime() - lastObservedNs : 0));
		result.addProperty("duration_scope", "audit_tick_observation_intervals_not_continuous_window_callbacks");
		result.addProperty("measure_ticks", measureTicks);
		result.addProperty("focus_losses", losses);
		result.addProperty("focus_restores", restores);
		result.addProperty("focus_restore_requests", restoreRequests);
		result.addProperty("focus_restores_scope", "observed_false_to_true_transitions_after_initial_audit");
		result.addProperty("focus_restore_requests_scope", "read_only_observation_no_focus_requests");
		result.addProperty("measure_unfocused_ticks", measureUnfocusedTicks);
		result.addProperty("focus_gate_enforced", false);
		result.addProperty("final_focused", focused);
		return result;
	}
}
