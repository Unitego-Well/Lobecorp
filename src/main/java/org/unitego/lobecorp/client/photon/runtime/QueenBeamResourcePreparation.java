package org.unitego.lobecorp.client.photon.runtime;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.gameobject.FXInstanceObject;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.registry.animation.LcAnimationDefinitions;
import org.unitego.lobecorp.registry.animation.TheQueenOfHatredAnimationDefinitions;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/// 只预热本模组声明的旧技能资源，不在女皇出现时创建持有世界的整棵运行树。
public class QueenBeamResourcePreparation {
	private static final boolean ENABLED = !"false".equals(System.getProperty("lobecorp.photon.prewarmBeam", "true"));
	// 失败后最多每秒重试一次，避免维护入口每帧重复加载和刷日志。
	private static final long RETRY_DELAY_NANOS = 1_000_000_000L;
	private static final PreparationGate GATE = new PreparationGate();
	private static String failure = "";
	private static List<Identifier> cachedEffects = List.of();
	private static long cachedEffectsRevision = Long.MIN_VALUE;
	private static PhotonRuntimeFactory.Mode cachedEffectsMode;
	private static boolean cachedEffectsOwned;
	private static PreparationStamp cachedPreparationStamp;

	private static List<Identifier> effects() {
		long revision = PhotonOwnedEffectResources.resourceRevision();
		var mode = PhotonRuntimeFactory.mode();
		boolean owned = QueenOwnedLaserEffects.enabled();
		if (revision == cachedEffectsRevision && mode == cachedEffectsMode && owned == cachedEffectsOwned) {
			return cachedEffects;
		}
		var candidates = List.of(TheQueenOfHatredAnimationDefinitions.LASER,
						TheQueenOfHatredAnimationDefinitions.LASER_PHASE_TWO,
						TheQueenOfHatredAnimationDefinitions.STAR_BEAM,
						TheQueenOfHatredAnimationDefinitions.STAR_BEAM_PHASE_TWO).stream()
				.filter(id -> !owned || TheQueenOfHatredAnimationDefinitions.isStarBeam(id))
				.toList();
		var effectIds = new LinkedHashSet<Identifier>();
		boolean complete = true;
		for (var id : candidates) {
			var definition = LcAnimationDefinitions.resolve(id);
			var effectId = definition == null ? null : definition.effectId();
			if (effectId == null) {
				complete = false;
				continue;
			}
			effectIds.add(effectId);
		}
		var next = List.copyOf(effectIds);
		if (complete) {
			cachedEffects = next;
			cachedEffectsRevision = revision;
			cachedEffectsMode = mode;
			cachedEffectsOwned = owned;
		}
		return next;
	}

	public static void register() {
		if (ENABLED) {
			FXHelper.keepPreloaded(effects());
		}
	}

	public static CompletableFuture<Void> preload() {
		return ENABLED ? FXHelper.preload(effects()) : CompletableFuture.completedFuture(null);
	}

	public static void prepare() {
		if (!ENABLED) {
			return;
		}
		var resources = effects();
		var stamp = preparationStamp(resources);
		long now = System.nanoTime();
		if (!GATE.begin(stamp, now)) {
			return;
		}
		failure = "";
		try {
			if (resources.isEmpty()) {
				throw new IllegalStateException("技能资源定义尚未登记");
			}
			var visited = new HashSet<Identifier>();
			for (var id : resources) {
				if (FXHelper.preloadNow(id) == null) {
					throw new IllegalArgumentException("技能资源缺失：" + id);
				}
				prepare(id, visited);
			}
			GATE.complete(stamp);
		} catch (RuntimeException invalid) {
			failure = invalid.toString();
			GATE.fail(stamp, now + RETRY_DELAY_NANOS);
			Lobecorp.LOGGER.warn("技能静态资源准备失败，未建立预热运行树", invalid);
		}
	}

	/// 只读判断是否存在已到期或待重试的技能资源；无失败时返回 false。
	public static boolean retryDue(long now) {
		return ENABLED && GATE.retryDue(now);
	}

	private static void prepare(Identifier id, Set<Identifier> visited) {
		if (!visited.add(id)) {
			return;
		}
		// 原生预加载已递归准备子效果；这里只核对定义并准备本模组的可选编译模式。
		FX fx = FXHelper.getFX(id);
		if (fx == null) {
			throw new IllegalArgumentException("技能资源缺失：" + id);
		}
		for (var object : fx.getFxData().objects()) {
			if (object instanceof PhotonChildSpawner child && child.config.source == PhotonSpawnConfig.Source.FX
					&& child.config.fxLocation != null) {
				prepare(child.config.fxLocation, visited);
			} else if (object instanceof FXInstanceObject instance && instance.getFx() != null) {
				prepare(instance.getFx(), visited);
			}
		}
		if (PhotonRuntimeFactory.mode() != PhotonRuntimeFactory.Mode.NATIVE) {
			PhotonRuntimeFactory.prepare(fx);
		}
	}

	private static String preparationStamp(List<Identifier> resources) {
		long revision = PhotonOwnedEffectResources.resourceRevision();
		var mode = PhotonRuntimeFactory.mode();
		var device = PhotonOwnedEffectAssets.deviceIdentity();
		var cached = cachedPreparationStamp;
		if (cached != null && cached.revision() == revision && cached.mode() == mode
				&& cached.device().equals(device) && cached.resources().equals(resources)) {
			return cached.value();
		}
		var value = device + ":" + revision + ":" + mode + ":" + resources;
		cachedPreparationStamp = new PreparationStamp(revision, mode, device, List.copyOf(resources), value);
		return value;
	}

	public static boolean ready() {
		return !ENABLED || failure.isEmpty() && GATE.ready(preparationStamp(effects()));
	}

	public static String failure() {
		return failure;
	}

	public static class PreparationGate {
		private String attempted = "";
		private String completed = "";
		private long lastAttempt;

		public boolean begin(String stamp, long now) {
			if (completed.equals(stamp) || attempted.equals(stamp) && now - lastAttempt < RETRY_DELAY_NANOS) {
				return false;
			}
			attempted = stamp;
			lastAttempt = now;
			completed = "";
			return true;
		}

		public void complete(String stamp) {
			completed = stamp;
		}

		public boolean ready(String stamp) {
			return completed.equals(stamp);
		}

		public void fail(String stamp, long dueAt) {
			attempted = stamp;
			lastAttempt = dueAt - RETRY_DELAY_NANOS;
			completed = "";
		}

		public boolean retryDue(long now) {
			return completed.isEmpty() && !attempted.isEmpty() && now >= lastAttempt + RETRY_DELAY_NANOS;
		}
	}

	private record PreparationStamp(long revision, PhotonRuntimeFactory.Mode mode, String device,
	                                List<Identifier> resources, String value) {
	}
}
