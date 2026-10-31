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
	private static String attempted = "";
	private static String completed = "";
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
		var stamp = PhotonOwnedEffectAssets.deviceIdentity() + ":" + resources;
		if (stamp.equals(attempted)) {
			return;
		}
		attempted = stamp;
		failure = "";
		try {
			if (resources.isEmpty()) {
				throw new IllegalStateException("技能资源定义尚未登记");
			}
			var visited = new HashSet<Identifier>();
			for (var id : resources) {
				prepare(id, visited);
			}
			completed = PhotonOwnedEffectAssets.deviceIdentity() + ":" + resources;
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

	public static boolean ready() {
		return !ENABLED || failure.isEmpty() && completed.equals(PhotonOwnedEffectAssets.deviceIdentity() + ":" + effects());
	}

	public static String failure() {
		return failure;
	}
}
