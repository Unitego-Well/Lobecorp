package org.unitego.lobecorp.client.particle.text;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.unitego.lobecorp.particle.text.TextParticleSnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 仅由客户端主线程接收快照，同一实例的周期补发不重建粒子或重置播放进度。
public class TextParticleClient {

	private static final Map<UUID, TextParticle> PARTICLES = new HashMap<>();
	private static ClientLevel currentLevel;
	private static long layoutEpoch;

	public static void apply(boolean full, List<TextParticleSnapshot> snapshots, List<UUID> removed) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.level != currentLevel) {
			clear();
			currentLevel = minecraft.level;
		}

		if (currentLevel == null) {
			return;
		}

		var retained = new HashSet<UUID>();
		for (var id : removed) {
			remove(id);
		}

		for (var snapshot : snapshots) {
			retained.add(snapshot.id());
			var particle = PARTICLES.get(snapshot.id());
			if (particle == null || !particle.isAlive()) {
				particle = new TextParticle(currentLevel, snapshot);
				PARTICLES.put(snapshot.id(), particle);
				minecraft.particleEngine.add(particle);
			} else {
				particle.update(snapshot);
			}
		}

		if (full) {
			for (var id : List.copyOf(PARTICLES.keySet())) {
				if (!retained.contains(id)) {
					remove(id);
				}
			}
		}
	}

	private static void remove(UUID id) {
		var particle = PARTICLES.remove(id);
		if (particle != null) {
			particle.remove();
		}
	}

	public static void clear() {
		PARTICLES.values().forEach(TextParticle::remove);
		PARTICLES.clear();
		currentLevel = null;
	}

	/// 在字体和粒子资源重载完成后调用，重建引擎队列并使旧字形纹理引用失效。
	public static void invalidateLayouts() {
		layoutEpoch++;
		var minecraft = Minecraft.getInstance();
		if (currentLevel == null || currentLevel != minecraft.level) {
			return;
		}

		for (var particle : PARTICLES.values()) {
			if (particle.isAlive()) {
				minecraft.particleEngine.add(particle);
			}
		}
	}

	public static long layoutEpoch() {
		return layoutEpoch;
	}
}
