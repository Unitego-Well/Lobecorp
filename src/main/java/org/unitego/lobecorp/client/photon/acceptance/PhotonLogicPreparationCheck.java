package org.unitego.lobecorp.client.photon.acceptance;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectAssets;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.Prepared;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.Readiness;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.SourceKey;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.SourceKind;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaser;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedPreparationScheduler;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedPreparationScheduler.Kind;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedPreparationScheduler.State;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedPreparationScheduler.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/// 独立来源的真实异步准备及任务门禁检查；不重置全局账本、不注入GPU故障。
public class PhotonLogicPreparationCheck implements AutoCloseable {
	private static final Identifier FIRST = Identifier.parse("lobecorp:logic_regression_20261101/first");
	private static final Identifier SECOND = Identifier.parse("lobecorp:logic_regression_20261101/second");
	private static final Identifier LEDGER = Identifier.parse("lobecorp:logic_regression_20261101/ledger");
	private static final Identifier COMPONENT = Identifier.parse("lobecorp:logic_regression_20261101/component");
	private final BiConsumer<String, Boolean> check;
	private final Probe first = new Probe();
	private final Probe second = new Probe();
	private final List<SourceKey> sources = new ArrayList<>();
	private final List<CompletableFuture<SourceKey>> completions = new ArrayList<>();
	private final Set<SourceKey> observedFailures = new HashSet<>();
	private final Map<SourceKey, CompletableFuture<SourceKey>> initial = new HashMap<>();
	private SourceKey author;
	private SourceKey published;
	private SourceKey other;
	private CompletableFuture<SourceKey> replacement;
	private Task device;
	private int stage;
	private boolean initialized;

	public PhotonLogicPreparationCheck(BiConsumer<String, Boolean> check) {
		this.check = check;
	}

	public void begin() {
		initialized = true;
		ledgerContracts();
		register(FIRST, first);
		register(SECOND, second);
		author = source(FIRST, SourceKind.AUTHORING, 1);
		published = source(FIRST, SourceKind.PUBLISHED_PREVIEW, 1);
		other = source(SECOND, SourceKind.AUTHORING, 1);
		first.blocked.add(author);
		first.blocked.add(published);
		second.blocked.add(other);
		for (var key : List.of(author, published, other)) {
			initial.put(key, queue(key));
		}
	}

	private static void register(Identifier effect, Probe probe) {
		PhotonOwnedEffectResources.register(effect, Map.of("fixture", COMPONENT));
		PhotonOwnedEffectResources.registerSnapshotComponents(effect, Map.of("fixture", COMPONENT));
		PhotonOwnedEffectResources.registerDefinitionPreparation(effect, probe::prepare, PhotonOwnedEffectAssets::invalidate);
	}

	private SourceKey source(Identifier effect, SourceKind kind, long revision) {
		var key = new SourceKey(effect, kind, revision);
		sources.add(key);
		return key;
	}

	private CompletableFuture<SourceKey> queue(SourceKey key) {
		var tag = new FX().serializeNBT(Platform.getFrozenRegistry());
		tag.putLong("logic_revision", key.revision());
		var completion = PhotonOwnedEffectResources.queueSnapshot(key, Map.of("fixture", tag));
		completions.add(completion);
		return completion;
	}

	public boolean step() {
		switch (stage) {
			case 0 -> initialStep();
			case 1 -> replacementStep();
			case 2 -> deviceFailureStep();
			case 3 -> deviceReadyStep();
			case 4 -> {
				return exportedStep();
			}
			default -> {
				return true;
			}
		}
		return false;
	}

	private void initialStep() {
		for (var key : initial.keySet()) {
			observeFailure(key, snapshotKind(key));
		}
		if (observedFailures.size() < initial.size()) {
			return;
		}
		first.blocked.clear();
		second.blocked.clear();
		if (initial.values().stream().anyMatch(future -> !future.isDone())) {
			return;
		}
		for (var entry : initial.entrySet()) {
			expect("prepare.completed." + label(entry.getKey()), entry.getValue().join().equals(entry.getKey()));
			expectReady(entry.getKey());
		}
		expect("prepare.independent_effects_retried", first.attempts.get(author) >= 2 && second.attempts.get(other) >= 2);
		var old = queue(source(FIRST, SourceKind.AUTHORING, 2));
		var next = source(FIRST, SourceKind.AUTHORING, 3);
		replacement = queue(next);
		expect("prepare.replacement_cancels_old_completion", old.isCancelled());
		author = next;
		stage = 1;
	}

	private void replacementStep() {
		if (!replacement.isDone()) {
			return;
		}
		expect("prepare.latest_revision_completed", replacement.join().equals(author));
		expectReady(author);
		var old = new SourceKey(FIRST, SourceKind.AUTHORING, 1);
		expect("prepare.old_revision_not_published", PhotonOwnedEffectResources.lookup(old).readiness() != Readiness.READY);
		expectReady(published);
		cancelledCommitGuard();
		first.blocked.add(author);
		PhotonOwnedEffectAssets.invalidate(author);
		expect("device.local_source_invalidated", PhotonOwnedEffectAssets.devicePending(author));
		device = PhotonOwnedPreparationScheduler.enqueue(Kind.DEVICE, author, author.revision());
		var pending = queue(source(FIRST, SourceKind.AUTHORING, 5));
		PhotonOwnedEffectResources.markAuthoringDirty(FIRST);
		expect("device.dirty_cancels_snapshot", pending.isCancelled());
		expect("device.dirty_preserves_existing_task", PhotonOwnedPreparationScheduler.current(device, author.revision()));
		stage = 2;
	}

	private void cancelledCommitGuard() {
		var key = source(FIRST, SourceKind.AUTHORING, 4);
		var completion = queue(key);
		var task = Objects.requireNonNull(findTask(key, Kind.AUTHORING), "测试修订没有入队");
		var running = PhotonOwnedPreparationScheduler.begin(task);
		PhotonOwnedEffectResources.markAuthoringDirty(FIRST);
		expect("cancel.pending_future_cancelled", completion.isCancelled());
		expect("cancel.inflight_task_identity_rejected", !PhotonOwnedPreparationScheduler.current(running, key.revision()));
		boolean rejected = false;
		try {
			PhotonOwnedPreparationScheduler.ready(running);
		} catch (IllegalStateException expected) {
			rejected = true;
		}
		expect("cancel.stale_ready_transition_rejected", rejected);
		expect("cancel.unpublished_revision_absent", PhotonOwnedEffectResources.lookup(key).readiness() != Readiness.READY);
	}

	private void deviceFailureStep() {
		var task = findTask(author, Kind.DEVICE);
		if (task == null || task.state() != State.FAILED_WAIT) {
			return;
		}
		expect("device.real_callback_failed", first.attempts.getOrDefault(author, 0) >= 2);
		expect("device.wait_is_not_early_ready", PhotonOwnedPreparationScheduler.ready(Kind.DEVICE, task.dueAt() - 1)
				.stream().noneMatch(value -> value.id() == task.id()));
		var pending = queue(source(FIRST, SourceKind.AUTHORING, 6));
		PhotonOwnedEffectResources.markAuthoringDirty(FIRST);
		var retained = findTask(author, Kind.DEVICE);
		expect("device.cancel_snapshot_keeps_id_generation_due",
				pending.isCancelled() && retained != null && retained.id() == task.id()
						&& retained.generation() == task.generation() && retained.dueAt() == task.dueAt());
		expectReady(published);
		expectReady(other);
		first.blocked.remove(author);
		stage = 3;
	}

	private void deviceReadyStep() {
		if (!PhotonOwnedEffectAssets.deviceCurrent(author)) {
			return;
		}
		expectReady(author);
		expect("device.retry_completed_without_reset", !PhotonOwnedPreparationScheduler.current(device, author.revision()));
		stage = 4;
	}

	private boolean exportedStep() {
		var revision = PhotonOwnedEffectResources.resourceRevision();
		var key = new SourceKey(PhotonOwnedLaser.ID, SourceKind.EXPORTED_FX, revision);
		var result = PhotonOwnedEffectResources.lookup(key);
		if (result.readiness() != Readiness.READY) {
			return false;
		}
		var prepared = Objects.requireNonNull(result.prepared(), "READY缺少导出成果");
		expect("exported.real_resource_source_kind", prepared.source().kind() == SourceKind.EXPORTED_FX);
		expect("exported.real_resource_revision", prepared.source().revision() == revision);
		expect("exported.wrong_revision_rejected", PhotonOwnedEffectResources.lookup(
				new SourceKey(PhotonOwnedLaser.ID, SourceKind.EXPORTED_FX, revision + 1)).readiness() != Readiness.READY);
		stage = 5;
		return true;
	}

	private void observeFailure(SourceKey key, Kind kind) {
		if (observedFailures.contains(key)) {
			return;
		}
		var task = findTask(key, kind);
		if (task != null && task.state() == State.FAILED_WAIT) {
			expect("prepare.failure_wait." + label(key), !task.failure().isEmpty());
			expect("prepare.due_guard." + label(key), PhotonOwnedPreparationScheduler.ready(kind, task.dueAt() - 1)
					.stream().noneMatch(value -> value.id() == task.id()));
			observedFailures.add(key);
		}
	}

	private static Task findTask(SourceKey source, Kind kind) {
		return PhotonOwnedPreparationScheduler.ready(kind, Long.MAX_VALUE).stream()
				.filter(task -> source.equals(task.source())).findFirst().orElse(null);
	}

	private static Kind snapshotKind(SourceKey source) {
		return source.kind() == SourceKind.AUTHORING ? Kind.AUTHORING : Kind.PUBLISHED_PREVIEW;
	}

	private void expectReady(SourceKey key) {
		var result = PhotonOwnedEffectResources.lookup(key);
		expect("prepare.ready." + label(key), result.readiness() == Readiness.READY
				&& result.prepared() != null && result.prepared().source().equals(key)
				&& result.prepared().components().get("fixture").definition().getLongOr("logic_revision", -1) == key.revision()
				&& PhotonOwnedEffectAssets.prepared(key));
	}

	private static String label(SourceKey source) {
		return source.effectId().getPath() + "." + source.kind() + "." + source.revision();
	}

	private void expect(String name, boolean condition) {
		check.accept(name, condition);
	}

	private void ledgerContracts() {
		var tasks = new ArrayList<Task>();
		try {
			for (var kind : SourceKind.values()) {
				var key = new SourceKey(LEDGER, kind, 1);
				var task = PhotonOwnedPreparationScheduler.enqueue(Kind.DEVICE, key, 1);
				tasks.add(PhotonOwnedPreparationScheduler.fail(task, Long.MAX_VALUE, "logic_injected_ledger_failure"));
			}
			var snapshot = PhotonOwnedPreparationScheduler.enqueue(Kind.AUTHORING, tasks.getFirst().source(), 1);
			tasks.add(snapshot);
			PhotonOwnedPreparationScheduler.cancelSnapshot(LEDGER, tasks.getFirst().source().kind());
			expect("ledger.cancel_snapshot_removed_only_snapshot", !PhotonOwnedPreparationScheduler.current(snapshot, 1));
			for (var task : tasks.subList(0, SourceKind.values().length)) {
				expect("ledger.three_source_kinds_independent." + task.source().kind(),
						PhotonOwnedPreparationScheduler.current(task, 1)
								&& findTask(task.source(), Kind.DEVICE).dueAt() == Long.MAX_VALUE);
			}
		} finally {
			tasks.forEach(task -> PhotonOwnedPreparationScheduler.cancel(task.id()));
		}
	}

	@Override
	public void close() {
		if (!initialized) {
			return;
		}
		first.blocked.clear();
		second.blocked.clear();
		var cleanup = new ArrayList<Runnable>();
		for (var id : List.of(FIRST, SECOND)) {
			cleanup.add(() -> PhotonOwnedEffectResources.invalidateAuthoring(id));
			cleanup.add(() -> PhotonOwnedEffectResources.invalidatePublishedPreview(id));
			for (var kind : SourceKind.values()) {
				cleanup.add(() -> PhotonOwnedPreparationScheduler.cancelSource(id, kind));
			}
		}
		for (var source : sources) {
			cleanup.add(() -> PhotonOwnedEffectAssets.invalidate(source));
		}
		completions.forEach(future -> cleanup.add(() -> future.cancel(false)));
		RuntimeException failure = null;
		for (var action : cleanup) {
			try {
				action.run();
			} catch (RuntimeException problem) {
				if (failure == null) {
					failure = problem;
				} else if (failure != problem) {
					failure.addSuppressed(problem);
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

	/// 注册回调只持有独立fixture状态，不捕获测试视图、用户编辑器或报告回调。
	private static class Probe {
		private final Map<SourceKey, Integer> attempts = new HashMap<>();
		private final Set<SourceKey> blocked = new HashSet<>();

		private void prepare(Prepared prepared) {
			var source = prepared.source();
			attempts.merge(source, 1, Integer::sum);
			if (blocked.contains(source)) {
				throw new IllegalStateException("logic_injected_preparation_failure:" + label(source));
			}

			var definition = new FX();
			definition.deserializeNBT(Platform.getFrozenRegistry(), prepared.components().get("fixture").definition());
			PhotonOwnedEffectAssets.prepare(definition, source);
		}
	}
}
