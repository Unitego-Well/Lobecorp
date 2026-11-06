package org.unitego.lobecorp.client.photon.runtime;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import net.minecraft.resources.Identifier;

/// 自有资源准备的最小任务账本；只记录任务、失败到期和提交代次，不创建线程。
///
/// 稳态只检查最早到期时间；成功或取消只移除对应任务，不撤销其他来源的重试。
public final class PhotonOwnedPreparationScheduler {
	public enum Kind {EXPORTED, AUTHORING, PUBLISHED_PREVIEW, DEVICE, QUEEN_BEAM}

	public enum State {QUEUED, PREPARING, READY_TO_COMMIT, COMMITTED, FAILED_WAIT, CANCELLED, CLOSED}

	public record Task(long id, Kind kind, PhotonOwnedEffectResources.SourceKey source, long revision,
	                   long generation, long dueAt, State state, String failure) {
		public Task {
			if (kind != Kind.QUEEN_BEAM && kind != Kind.EXPORTED && source == null) {
				throw new IllegalArgumentException("该任务必须带来源");
			}
		}

		public Task with(State next) {
			return new Task(id, kind, source, revision, generation, dueAt, next, failure);
		}
	}

	private static final Map<Long, Task> TASKS = new LinkedHashMap<>();
	private static long nextDue = Long.MAX_VALUE;
	private static long nextId;
	private static long nextGeneration;
	private static boolean closed;

	private PhotonOwnedPreparationScheduler() {
	}

	public static synchronized Task enqueue(Kind kind, @Nullable PhotonOwnedEffectResources.SourceKey source,
	                                        long revision) {
		if (closed) {
			throw new IllegalStateException("自有资源准备已关闭");
		}
		TASKS.values().removeIf(task -> task.kind() == kind && sameSource(task.source(), source));
		var task = new Task(++nextId, kind, source, revision, ++nextGeneration, Long.MAX_VALUE, State.QUEUED, "");
		TASKS.put(task.id(), task);
		nextDue = Long.MIN_VALUE;
		return task;
	}

	public static synchronized Task begin(Task task) {
		var current = require(task);
		var next = current.with(State.PREPARING);
		TASKS.put(next.id(), next);
		updateDue();
		return next;
	}

	public static synchronized Task ready(Task task) {
		var current = require(task);
		var next = current.with(State.READY_TO_COMMIT);
		TASKS.put(next.id(), next);
		updateDue();
		return next;
	}

	public static synchronized Task commit(Task task) {
		var current = require(task);
		var next = current.with(State.COMMITTED);
		TASKS.remove(next.id());
		updateDue();
		return next;
	}

	public static synchronized Task fail(Task task, long dueAt, String stamp) {
		var current = require(task);
		var next = new Task(current.id(), current.kind(), current.source(), current.revision(),
				current.generation(), dueAt, State.FAILED_WAIT, stamp);
		TASKS.put(next.id(), next);
		updateDue();
		return next;
	}

	public static synchronized void cancel(long id) {
		TASKS.remove(id);
		updateDue();
	}

	public static synchronized void close() {
		closed = true;
		TASKS.clear();
		nextDue = Long.MAX_VALUE;
	}

	public static synchronized List<Task> ready(Kind kind, long now) {
		var result = new ArrayList<Task>();
		for (var task : TASKS.values()) {
			if (task.kind() != kind) {
				continue;
			}
			if (task.state() == State.QUEUED || task.state() == State.FAILED_WAIT && task.dueAt() <= now) {
				result.add(task);
			}
		}
		return result;
	}

	public static synchronized boolean hasWork(long now) {
		return !closed && now >= nextDue;
	}

	public static synchronized boolean current(Task task, long revision) {
		var current = TASKS.get(task.id());
		return !closed && current != null && current.generation() == task.generation()
				&& current.revision() == revision;
	}

	/// 作者编辑只撤销未发布修订，保留旧快照独立的设备恢复任务。
	public static synchronized void cancelSnapshot(Identifier effectId,
	                                               PhotonOwnedEffectResources.SourceKind kind) {
		TASKS.values().removeIf(task -> (task.kind() == Kind.AUTHORING || task.kind() == Kind.PUBLISHED_PREVIEW)
				&& task.source() != null && task.source().effectId().equals(effectId)
				&& task.source().kind() == kind);
		updateDue();
	}

	public static synchronized void cancelSource(Identifier effectId,
	                                             PhotonOwnedEffectResources.SourceKind kind) {
		TASKS.values().removeIf(task -> task.source() != null
				&& task.source().effectId().equals(effectId) && task.source().kind() == kind);
		updateDue();
	}

	public static synchronized void reset() {
		TASKS.clear();
		closed = false;
		nextDue = Long.MAX_VALUE;
	}

	private static boolean sameSource(@Nullable PhotonOwnedEffectResources.SourceKey left,
	                                  @Nullable PhotonOwnedEffectResources.SourceKey right) {
		return left == null ? right == null : right != null && left.effectId().equals(right.effectId())
				&& left.kind() == right.kind();
	}

	private static void updateDue() {
		nextDue = Long.MAX_VALUE;
		for (var task : TASKS.values()) {
			if (task.state() == State.QUEUED) {
				nextDue = Long.MIN_VALUE;
				return;
			}
			if (task.state() == State.FAILED_WAIT) {
				nextDue = Math.min(nextDue, task.dueAt());
			}
		}
	}

	public static synchronized boolean closed() {
		return closed;
	}

	private static Task require(Task task) {
		var current = TASKS.get(task.id());
		if (current == null || current.state() == State.CANCELLED || current.state() == State.CLOSED) {
			throw new IllegalStateException("准备任务已取消或关闭");
		}
		return current;
	}
}
