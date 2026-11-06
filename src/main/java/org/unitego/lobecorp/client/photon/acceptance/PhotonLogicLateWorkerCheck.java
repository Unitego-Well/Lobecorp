package org.unitego.lobecorp.client.photon.acceptance;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectAssets;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.SourceKey;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.SourceKind;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/// 在真实 normalize worker 断点处取消来源；后继任务成功证明旧提交已消费。
public class PhotonLogicLateWorkerCheck implements AutoCloseable {
	public static final Identifier ID = Identifier.parse("lobecorp:logic_late_worker_20261101");
	private static PhotonLogicLateWorkerCheck active;
	private final BiConsumer<String, Boolean> check;
	private final SourceKey old = new SourceKey(ID, SourceKind.AUTHORING, 1);
	private final SourceKey next = new SourceKey(ID, SourceKind.AUTHORING, 2);
	private CompletableFuture<SourceKey> oldFuture;
	private CompletableFuture<SourceKey> nextFuture;
	private volatile boolean gateObserved;
	private boolean cancelled;
	private int oldPublished;
	private String workerThread = "";

	public PhotonLogicLateWorkerCheck(BiConsumer<String, Boolean> check) {
		this.check = check;
	}

	public void begin() {
		if (active != null) {
			throw new IllegalStateException("已有晚到检查");
		}
		active = this;
		var components = Map.of("fixture", Identifier.parse("lobecorp:logic_late_worker/component"));
		PhotonOwnedEffectResources.register(ID, components);
		PhotonOwnedEffectResources.registerSnapshotComponents(ID, components);
		PhotonOwnedEffectResources.registerDefinitionPreparation(ID, prepared -> {
			if (prepared.source().equals(old)) {
				oldPublished++;
			}
			var definition = new FX();
			definition.deserializeNBT(Platform.getFrozenRegistry(), prepared.components().get("fixture").definition());
			PhotonOwnedEffectAssets.prepare(definition, prepared.source());
		}, PhotonOwnedEffectAssets::invalidate);
		oldFuture = PhotonOwnedEffectResources.queueSnapshot(old, snapshot());
	}

	/// 只在真实 worker 的 normalize 入口暂停后调用；不会阻塞客户端线程。
	public static void cancelAtWorkerGate(SourceKey source) {
		var run = active;
		if (run == null || !source.equals(run.old) || Minecraft.getInstance().isSameThread()) {
			throw new IllegalStateException("必须在指定旧修订的真实 worker 断点调用");
		}
		run.workerThread = Thread.currentThread().getName();
		run.gateObserved = true;
		Minecraft.getInstance().schedule(() -> {
			PhotonOwnedEffectResources.markAuthoringDirty(ID);
			run.cancelled = run.oldFuture.isCancelled();
			run.nextFuture = PhotonOwnedEffectResources.queueSnapshot(run.next, snapshot());
		});
	}

	private static Map<String, CompoundTag> snapshot() {
		return Map.of("fixture", new FX().serializeNBT(Platform.getFrozenRegistry()));
	}

	public boolean step() {
		if (!gateObserved || nextFuture == null || !nextFuture.isDone()) {
			return false;
		}
		check.accept("worker.real_background_gate", !workerThread.isBlank());
		check.accept("worker.old_future_cancelled_before_release", cancelled);
		check.accept("worker.successor_completed", nextFuture.join().equals(next));
		check.accept("worker.late_old_commit_rejected", oldPublished == 0);
		check.accept("worker.old_source_not_ready",
				PhotonOwnedEffectResources.lookup(old).readiness() != PhotonOwnedEffectResources.Readiness.READY);
		check.accept("worker.successor_ready",
				PhotonOwnedEffectResources.lookup(next).readiness() == PhotonOwnedEffectResources.Readiness.READY);
		return true;
	}

	@Override
	public void close() {
		PhotonOwnedEffectResources.invalidateAuthoring(ID);
		if (active == this) {
			active = null;
		}
	}
}
