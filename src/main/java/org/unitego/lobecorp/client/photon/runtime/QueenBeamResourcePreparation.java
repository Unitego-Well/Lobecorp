package org.unitego.lobecorp.client.photon.runtime;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.gameobject.FXInstanceObject;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.registry.animation.LcAnimationDefinitions;
import org.unitego.lobecorp.registry.animation.TheQueenOfHatredAnimationDefinitions;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// 只预热本模组声明的旧技能资源，不在女皇出现时创建持有世界的整棵运行树。
public class QueenBeamResourcePreparation {
	private static final boolean ENABLED = !"false".equals(System.getProperty("lobecorp.photon.prewarmBeam", "true"));
    // 失败后最多每秒重试一次，避免维护入口每帧重复加载和刷日志。
    private static final long RETRY_DELAY_NANOS = 1_000_000_000L;
    private static final PreparationGate GATE = new PreparationGate();

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
    }

	private static String failure = "";

	private static List<Identifier> effects() {
		return List.of(TheQueenOfHatredAnimationDefinitions.LASER,
						TheQueenOfHatredAnimationDefinitions.LASER_PHASE_TWO,
						TheQueenOfHatredAnimationDefinitions.STAR_BEAM,
						TheQueenOfHatredAnimationDefinitions.STAR_BEAM_PHASE_TWO).stream()
				.filter(id -> !QueenOwnedLaserEffects.enabled() || TheQueenOfHatredAnimationDefinitions.isStarBeam(id))
				.map(LcAnimationDefinitions::resolve).filter(Objects::nonNull)
				.map(value -> value.effectId()).filter(Objects::nonNull).distinct().toList();
	}

	public static void register() {
		if (ENABLED) {
			FXHelper.keepPreloaded(effects());
		}
	}

	public static void prepare() {
		if (!ENABLED) {
			return;
		}
		var resources = effects();
		var stamp = preparationStamp(resources);
		if (!GATE.begin(stamp, System.nanoTime())) {
			return;
		}
		failure = "";
		try {
			if (resources.isEmpty()) {
				throw new IllegalStateException("技能资源定义尚未登记");
			}
			var visited = new HashSet<Identifier>();
			for (var id : resources) {
				prepare(id, visited);
			}
			GATE.complete(stamp);
		} catch (RuntimeException invalid) {
			failure = invalid.toString();
			Lobecorp.LOGGER.warn("技能静态资源准备失败，未建立预热运行树", invalid);
		}
	}

	private static void prepare(Identifier id, Set<Identifier> visited) {
		if (!visited.add(id)) {
			return;
		}
		FX fx = FXHelper.preloadNow(id);
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
		return PhotonOwnedEffectAssets.deviceIdentity() + ":" + PhotonOwnedEffectResources.resourceRevision()
				+ ":" + PhotonRuntimeFactory.mode() + ":" + resources;
	}

	public static boolean ready() {
		return !ENABLED || failure.isEmpty() && GATE.ready(preparationStamp(effects()));
	}

	public static String failure() {
		return failure;
	}
}
