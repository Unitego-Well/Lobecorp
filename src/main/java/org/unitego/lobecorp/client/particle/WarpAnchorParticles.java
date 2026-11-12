package org.unitego.lobecorp.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.DustParticleOptions;
import org.unitego.lobecorp.client.LcColors;
import org.unitego.lobecorp.registry.world.item.LcDataComponents;
import org.unitego.lobecorp.world.item.WarpAnchorItem;
import org.unitego.lobecorp.world.item.component.WarpAnchor;

import java.util.ArrayList;
import java.util.List;

public class WarpAnchorParticles {
	/// 每条十一格边分为二十二段，间距为半格；每帧只绘制一个水平截面。
	private static final int EDGE_SEGMENTS = 22;
	private static final DustParticleOptions DUST = new DustParticleOptions(LcColors.LIGHT_BLUE, 0.7F);
	private static final List<Particle> ACTIVE = new ArrayList<>();

	public static void tick() {
		ACTIVE.forEach(Particle::remove);
		ACTIVE.clear();
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}

		for (var player : level.players()) {
			if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof WarpAnchorItem)) {
				continue;
			}

			var data = player.getUseItem().getOrDefault(LcDataComponents.WARP_ANCHOR.get(), WarpAnchor.EMPTY);
			var origin = data.chargeOrigin().flatMap(WarpAnchor.Destination::location);
			if (data.clearing() || origin.isEmpty() || data.hasMoved(player)) {
				continue;
			}

			var center = player.position();
			double half = WarpAnchorItem.HALF_RANGE;
			double progress = Math.clamp(player.getTicksUsingItem() / (WarpAnchorItem.CHARGE_TICKS - 1.0), 0, 1);
			double y = center.y + 2 * half * (1 - progress);
			for (var index = 0; index < EDGE_SEGMENTS; index++) {
				double offset = -half + 2 * half * index / EDGE_SEGMENTS;
				spawn(center.x + offset, y, center.z - half);
				spawn(center.x + half, y, center.z + offset);
				spawn(center.x - offset, y, center.z + half);
				spawn(center.x - half, y, center.z - offset);
			}
		}
	}

	private static void spawn(double x, double y, double z) {
		var particle = Minecraft.getInstance().particleEngine.createParticle(DUST, x, y, z, 0, 0, 0);
		if (particle != null) {
			particle.setLifetime(2);
			ACTIVE.add(particle);
		}
	}
}
