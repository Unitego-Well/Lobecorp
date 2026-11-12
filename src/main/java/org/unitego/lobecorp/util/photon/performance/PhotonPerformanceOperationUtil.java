package org.unitego.lobecorp.util.photon.performance;

import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

import java.util.*;

/// 仅在同 JVM 内按现有字段和 FIFO 推断请求关联；不修改业务包，不把推断当成线上 request ID。
public class PhotonPerformanceOperationUtil {
	private static final int MAX_PENDING = 1024;
	private static final Map<Key, ArrayDeque<Request>> pending = new LinkedHashMap<>();
	private static long session;
	private static long sequence;
	private static int pendingCount;
	private static long sent;
	private static long received;
	private static long unmatched;
	private static long evicted;

	public static int entity(UUID entity) {
		String identity = entity.toString();
		return PhotonPerformanceTraceUtil.namedNode(0, "entity", identity, identity, "");
	}

	private static int commandNode(@Nullable UUID player, String action, List<UUID> units, String skill) {
		int owner = units.size() == 1 ? entity(units.getFirst()) : player == null ? 0 : entity(player);
		return PhotonPerformanceTraceUtil.namedNode(owner, "conductor_command", action + ":" + skill, action, skill);
	}

	public static synchronized int send(@Nullable UUID player, String action, List<UUID> units, String skill) {
		if (!PhotonPerformanceTraceUtil.active()) {
			return 0;
		}
		ensureSession();
		while (pendingCount >= MAX_PENDING) {
			var first = pending.entrySet().iterator().next();
			first.getValue().removeFirst();
			if (first.getValue().isEmpty()) {
				pending.remove(first.getKey());
			}
			pendingCount--;
			evicted++;
		}
		var key = new Key(player, action, List.copyOf(units), skill);
		var request = new Request(++sequence, System.nanoTime());
		pending.computeIfAbsent(key, _ -> new ArrayDeque<>()).addLast(request);
		pendingCount++;
		sent++;
		int node = commandNode(player, action, units, skill);
		requestMark(Stage.COMMAND_SEND, node, "command_send", "local_observation", action,
				request.id(), units.size());
		return node;
	}

	public static synchronized int receive(@Nullable UUID player, String action, List<UUID> units, String skill) {
		if (!PhotonPerformanceTraceUtil.active()) {
			return 0;
		}
		ensureSession();
		var key = new Key(player, action, units, skill);
		var queue = pending.get(key);
		var request = queue == null ? null : queue.pollFirst();
		if (queue != null && queue.isEmpty()) {
			pending.remove(key);
		}
		if (request != null) {
			pendingCount--;
		} else {
			unmatched++;
		}
		received++;
		int node = commandNode(player, action, units, skill);
		requestMark(Stage.COMMAND_HANDLE, node, "command_receive",
				request == null ? "unmatched_or_remote" : "inferred_same_jvm_fifo", action,
				request == null ? 0 : request.id(), request == null ? Double.NaN : System.nanoTime() - request.sentNs());
		return node;
	}

	private static void requestMark(Stage stage, int node, String code, String detail, String action, double value, double secondary) {
		if (action.equals("CAST") || action.equals("END_MANUAL_CONTROL")) {
			PhotonPerformanceTraceUtil.important(stage, node, code, detail, value, secondary);
		} else {
			PhotonPerformanceTraceUtil.mark(stage, node, code, detail, value, secondary);
		}
	}

	public static synchronized Summary summary() {
		if (PhotonPerformanceTraceUtil.active()) {
			ensureSession();
		}
		return new Summary(session, sent, received, unmatched, evicted, pendingCount, "inferred_same_jvm_fifo_not_wire_id");
	}

	public static synchronized void clear() {
		pending.clear();
		pendingCount = 0;
	}

	private static void ensureSession() {
		long current = PhotonPerformanceTraceUtil.sessionId();
		if (session != current) {
			clear();
			session = current;
			sequence = 0;
			sent = 0;
			received = 0;
			unmatched = 0;
			evicted = 0;
		}
	}

	private record Key(@Nullable UUID player, String action, List<UUID> units, String skill) {
	}

	private record Request(long id, long sentNs) {
	}

	public record Summary(long sessionId, long sent, long received, long unmatched, long evicted,
	                      int pending, String correlation) {
	}
}
