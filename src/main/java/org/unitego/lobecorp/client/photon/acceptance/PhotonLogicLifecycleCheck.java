package org.unitego.lobecorp.client.photon.acceptance;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectAssets;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.Readiness;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.SourceKey;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.SourceKind;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaser;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedPreparationScheduler;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/// 客户端逐帧调用；normal 完成后才能在独立 JVM 最后进入 finalShutdown。
/// Debug 必须使用 THREAD 暂停 normalize 的指定来源，禁止 ALL 暂停和固定等待替代消费证据。
public class PhotonLogicLifecycleCheck implements AutoCloseable {
	private static PhotonLogicLifecycleCheck active;
	private final BiConsumer<String, Boolean> check;
	private final Identifier id = Identifier.parse("lobecorp:logic_lifecycle/" + UUID.randomUUID());
	private final SourceKey author = new SourceKey(id, SourceKind.AUTHORING, 1);
	private final SourceKey preview = new SourceKey(id, SourceKind.PUBLISHED_PREVIEW, 1);
	private final SourceKey late = new SourceKey(id, SourceKind.AUTHORING, 2);
	private final SourceKey successor = new SourceKey(id, SourceKind.AUTHORING, 3);
	private final Map<SourceKey, Integer> publications = new HashMap<>();
	private CompletableFuture<SourceKey> authorFuture;
	private CompletableFuture<SourceKey> previewFuture;
	private CompletableFuture<SourceKey> lateFuture;
	private CompletableFuture<SourceKey> successorFuture;
	private CompletableFuture<Void> reloadFuture;
	private SourceKey exported;
	private volatile boolean workerObserved;
	private boolean actionApplied;
	private boolean callbackConsumed;
	private boolean shutdown;
	private int stage;
	private int shutdownPublicationBaseline;

	public PhotonLogicLifecycleCheck(BiConsumer<String, Boolean> check) {
		this.check = Objects.requireNonNull(check);
	}

	private static Map<String, CompoundTag> snapshot() {
		return Map.of("fixture", new FX().serializeNBT(Platform.getFrozenRegistry()));
	}

	private static CompletableFuture<SourceKey> queue(SourceKey source) {
		return PhotonOwnedEffectResources.queueSnapshot(source, snapshot());
	}

	/// 仅在 normalize(prepared) 的真实指定 worker 栈求值，参数为 prepared.source()。
	public static void atWorkerGate(SourceKey source) {
		var run = Objects.requireNonNull(active);
		if (!source.equals(run.late) || Minecraft.getInstance().isSameThread()
				|| run.stage != 3 || run.workerObserved) {
			throw new IllegalStateException("需要指定来源的真实 normalize worker，且只能观测一次");
		}
		run.workerObserved = true;
		Minecraft.getInstance().schedule(run::applyGateAction);
	}

	/// 主线 Debug 在旧 dispatch 客户端回调完成 epoch 检查并返回后调用。
	/// 必须核对该回调捕获的 task.source() 等于 gateSource；此方法本身不提供独立观测。
	public static void oldCallbackConsumed(SourceKey source) {
		client();
		var run = Objects.requireNonNull(active);
		if (!run.late.equals(source) || !run.workerObserved || !run.actionApplied) {
			throw new IllegalStateException("尚未完成指定旧 worker 的门禁动作");
		}
		run.callbackConsumed = true;
	}

	private static Map<String, Integer> pendingState() {
		try {
			var result = new HashMap<String, Integer>();
			for (var name : List.of("PENDING_SNAPSHOTS", "SNAPSHOT_COMPLETIONS")) {
				var field = PhotonOwnedEffectResources.class.getDeclaredField(name);
				field.setAccessible(true);
				result.put(name, ((Map<?, ?>) field.get(null)).size());
			}
			return Map.copyOf(result);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("无法读取关闭后的待准备状态", failure);
		}
	}

	private static boolean ready(SourceKey source) {
		return PhotonOwnedEffectResources.lookup(source).readiness() == Readiness.READY;
	}

	private static void client() {
		if (!Minecraft.getInstance().isSameThread()) {
			throw new IllegalStateException("必须在客户端线程调用");
		}
	}

	public void begin() {
		client();
		if (active != null || stage != 0) {
			throw new IllegalStateException("已有检查或重复开始");
		}
		active = this;
		var components = Map.of("fixture", Identifier.parse("lobecorp:logic_lifecycle/component"));
		PhotonOwnedEffectResources.register(id, components);
		PhotonOwnedEffectResources.registerSnapshotComponents(id, components);
		PhotonOwnedEffectResources.registerDefinitionPreparation(id, prepared -> {
			var definition = new FX();
			definition.deserializeNBT(Platform.getFrozenRegistry(), prepared.components().get("fixture").definition());
			PhotonOwnedEffectAssets.prepare(definition, prepared.source());
			publications.merge(prepared.source(), 1, Integer::sum);
		}, PhotonOwnedEffectAssets::invalidate);
		authorFuture = queue(author);
		previewFuture = queue(preview);
		stage = 1;
	}

	public SourceKey gateSource() {
		return late;
	}

	private void applyGateAction() {
		client();
		if (shutdown) {
			PhotonOwnedEffectResources.closePreparation();
			check.accept("lifecycle.shutdown.future_cancelled", lateFuture.isCancelled());
			check.accept("lifecycle.shutdown.closed", PhotonOwnedPreparationScheduler.closed());
			var pendingBefore = pendingState();
			boolean rejected = false;
			try {
				queue(successor);
			} catch (IllegalStateException expected) {
				rejected = true;
			}
			check.accept("lifecycle.shutdown.queue_rejected", rejected);
			check.accept("lifecycle.shutdown.rejection_preserves_pending_state", pendingBefore.equals(pendingState()));
		} else {
			reloadFuture = Minecraft.getInstance().reloadResourcePacks();
		}
		actionApplied = true;
	}

	public boolean step() {
		client();
		if (stage == 1) {
			if (!authorFuture.isDone() || !previewFuture.isDone()) {
				return false;
			}
			check.accept("lifecycle.initial.futures", authorFuture.join().equals(author)
					&& previewFuture.join().equals(preview));
			exported = new SourceKey(PhotonOwnedLaser.ID, SourceKind.EXPORTED_FX,
					PhotonOwnedEffectResources.resourceRevision());
			if (!ready(author) || !ready(preview) || !ready(exported)) {
				return false;
			}
			var stamp = PhotonOwnedEffectAssets.deviceIdentity();
			PhotonOwnedEffectAssets.invalidateDeviceResources();
			check.accept("lifecycle.global.stamp_changed", !stamp.equals(PhotonOwnedEffectAssets.deviceIdentity()));
			for (var source : List.of(author, preview, exported)) {
				check.accept("lifecycle.global.pending." + source.kind(), PhotonOwnedEffectAssets.devicePending(source));
			}
			PhotonOwnedEffectResources.markDeviceDirty();
			stage = 2;
			return false;
		}
		if (stage == 2) {
			if (!ready(author) || !ready(preview) || !ready(exported)) {
				return false;
			}
			for (var source : List.of(author, preview, exported)) {
				check.accept("lifecycle.global.recovered." + source.kind(),
						PhotonOwnedEffectAssets.prepared(source));
			}
			check.accept("lifecycle.global.notification_reprepared",
					publications.getOrDefault(author, 0) >= 2 && publications.getOrDefault(preview, 0) >= 2);
			lateFuture = queue(late);
			stage = 3;
			return false;
		}
		if (stage == 3) {
			if (!callbackConsumed) {
				return false;
			}
			if (shutdown) {
				check.accept("lifecycle.shutdown.late_not_published", publications.getOrDefault(late, 0) == shutdownPublicationBaseline);
				check.accept("lifecycle.shutdown.old_not_ready", !ready(late));
				stage = 6;
				return true;
			}
			if (reloadFuture == null || !reloadFuture.isDone()) {
				return false;
			}
			reloadFuture.join();
			if (!lateFuture.isDone()) {
				return false;
			}
			check.accept("lifecycle.reload.pending_revision_reprepared", lateFuture.join().equals(late) && ready(late));
			check.accept("lifecycle.reload.old_epoch_callback_rejected", callbackConsumed);
			successorFuture = queue(successor);
			stage = 4;
			return false;
		}
		if (stage == 4) {
			if (!successorFuture.isDone()) {
				return false;
			}
			check.accept("lifecycle.reload.successor", successorFuture.join().equals(successor) && ready(successor));
			check.accept("lifecycle.reload.obsolete_revision_not_ready", !ready(late));
			stage = 5;
		}
		return stage == 5 || stage == 6;
	}

	/// normal 完成后、全部作者测试结束后调用；关闭后不得 reload 或继续作者操作。
	public void finalShutdown() {
		client();
		if (stage != 5) {
			throw new IllegalStateException("normal 尚未完成");
		}
		workerObserved = false;
		actionApplied = false;
		callbackConsumed = false;
		shutdown = true;
		shutdownPublicationBaseline = publications.getOrDefault(late, 0);
		lateFuture = queue(late);
		stage = 3;
	}

	public String debugCondition() {
		return "prepared.source().equals(new org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.SourceKey("
				+ "net.minecraft.resources.Identifier.parse(\"" + id + "\"), "
				+ "org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.SourceKind.AUTHORING, 2))";
	}

	@Override
	public void close() {
		client();
		PhotonOwnedEffectResources.invalidateAuthoring(id);
		PhotonOwnedEffectResources.invalidatePublishedPreview(id);
		if (active == this) {
			active = null;
		}
	}
}
