package org.unitego.lobecorp.client.horde;

import com.lowdragmc.photon.client.light.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.unitego.lobecorp.config.client.HordeVisualConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// 世界级尘雾提供者；低矮交叠雾体共用额度，不为每个虚拟单位分配效果。
public class HordeGroundFog {
	// 为其它Photon效果留出原生32雾体额度，跨全部清扫事件共享。
	private static final int VOLUME_COUNT = 12;
	private static final double CELL_SPACING = 24;
	private static final float TRANSITION_STEP = 1F / 8;
	private static final FogProvider PROVIDER = HordeGroundFog::submit;
	private static final List<Region> REGIONS = new ArrayList<>();
	private static final List<VisibleRegion> ACTIVE = new ArrayList<>();
	private static ClientLevel boundLevel;

	public static void tick(List<HordeClient.ClientHorde> events) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		if (boundLevel != level) {
			clear();
		}

		REGIONS.clear();
		if (level == null || events.isEmpty() || !HordeVisualConfig.ENABLED.get()
				|| !HordeVisualConfig.DUST.get() || !PhotonLights.isVolumetricEnabled()) {
			clear();
			return;
		}

		var camera = minecraft.gameRenderer.getMainCamera().position();
		for (var event : events) {
			if (!event.ready || event.failed || event.tide == null) {
				continue;
			}

			collect(event, camera);
		}

		REGIONS.sort(Comparator.comparingDouble(Region::distance).thenComparingLong(Region::seed));
		if (REGIONS.size() > VOLUME_COUNT) {
			REGIONS.subList(VOLUME_COUNT, REGIONS.size()).clear();
		}
		updateSelection();

		if (ACTIVE.isEmpty()) {
			clear();
		} else if (boundLevel == null) {
			boundLevel = level;
			DynamicLightManager.addFogProvider(level, PROVIDER);
		}
	}

	private static void updateSelection() {
		for (var active : ACTIVE) {
			active.previous = active.weight;
			var desired = REGIONS.stream().filter(region -> region.seed() == active.region.seed()).findFirst();
			if (desired.isPresent()) {
				active.region = desired.get();
				active.weight = Math.min(1, active.weight + TRANSITION_STEP);
			} else {
				active.weight = Math.max(0, active.weight - TRANSITION_STEP);
			}
		}
		ACTIVE.removeIf(active -> active.weight == 0 && active.previous == 0);
		for (var region : REGIONS) {
			if (ACTIVE.size() >= VOLUME_COUNT) {
				break;
			}
			if (ACTIVE.stream().noneMatch(active -> active.region.seed() == region.seed())) {
				var active = new VisibleRegion(region);
				active.weight = TRANSITION_STEP;
				ACTIVE.add(active);
			}
		}
	}

	private static void collect(HordeClient.ClientHorde event, Vec3 camera) {
		double time = event.time(0);
		double tail = Math.max(0, (time - event.stop) * event.speed);
		double head = Math.min(event.length, time * event.speed);
		if (head <= tail) {
			return;
		}

		double station = event.tide.particleStation(camera);
		var center = event.tide.world(station, 0);
		var forward = event.tide.forward(station);
		double lateral = -(camera.x - center.x) * forward.z + (camera.z - center.z) * forward.x;
		double halfWidth = event.columns * event.columnSpacing() * 0.5;
		double range = HordeVisualConfig.PARTICLE_RANGE.get();
		int radius = (int) Math.ceil(range / CELL_SPACING) + 1;
		long first = (long) Math.floor(station / CELL_SPACING);
		int side = (int) Math.floor(lateral / CELL_SPACING);
		for (long along = first - radius; along <= first + radius; along++) {
			for (int across = side - radius; across <= side + radius; across++) {
				long seed = mix(event.id.getMostSignificantBits() ^ event.id.getLeastSignificantBits()
						^ along * 0x9E3779B97F4A7C15L ^ across * 0x632BE59BD9B4E019L);
				double s = (along + 0.5 + (random(seed) - 0.5) * 0.8) * CELL_SPACING;
				double l = (across + 0.5 + (random(seed + 1) - 0.5) * 0.8) * CELL_SPACING;
				if (s < tail || s > head || Math.abs(l) > halfWidth) {
					continue;
				}

				var point = event.tide.world(s, l);
				double distance = point.distanceToSqr(camera);
				if (distance <= (range + CELL_SPACING) * (range + CELL_SPACING)) {
					REGIONS.add(new Region(event, s, l, seed, distance));
				}
			}
		}
	}

	private static void submit(FogSink sink, float partialTick) {
		if (boundLevel != Minecraft.getInstance().level || !HordeVisualConfig.ENABLED.get()
				|| !HordeVisualConfig.DUST.get() || !PhotonLights.isVolumetricEnabled()) {
			return;
		}

		for (var active : ACTIVE) {
			var region = active.region;
			var event = region.event();
			// 事件移除后不能由上一刻快照继续提交。
			if (!HordeClient.events().contains(event)) {
				continue;
			}

			double time = event.time(partialTick);
			double tail = Math.max(0, (time - event.stop) * event.speed);
			double head = Math.min(event.length, time * event.speed);
			double width = 34 + random(region.seed() + 2) * 16;
			double length = 34 + random(region.seed() + 3) * 20;
			double phase = random(region.seed() + 4) * Math.PI * 2;
			double station = region.station() + Math.sin(time * 0.008 + phase) * 2;
			double lateral = region.lateral() + Math.sin(time * 0.006 + phase) * 2;
			double weight = active.previous + (active.weight - active.previous) * Math.clamp(partialTick, 0, 1);
			double edge = weight * Math.clamp(Math.min(head - station, station - tail) / (length * 0.5), 0, 1);
			if (edge <= 0) {
				continue;
			}

			var point = event.tide.world(station, lateral);
			var ground = HordeGroundCache.sample(point.x, point.z);
			if (ground == null || !ground.passable()) {
				continue;
			}

			var forward = event.tide.forward(station);
			float height = (float) (2.0 + random(region.seed() + 5) * 1.7);
			var fog = sink.next().shape(FogVolume.Shape.SPHERE)
					.at(point.x, ground.height() + height * 0.35, point.z)
					.size((float) width, height, (float) length)
					.rotate(new Quaternionf().rotationY((float) Math.atan2(forward.x, forward.z)))
					.density((float) (edge * (0.65 + 0.2 * Math.sin(time * 0.011 + phase))))
					.color(0.42F, 0.36F, 0.33F).absorption(0.25F).edgeFalloff(0.75F).noise(0.9F, 7F);
			fog.noiseOffset.set((float) (time * 0.012), (float) (phase * 3), (float) (time * -0.008));
		}
	}

	private static long mix(long value) {
		value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
		value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	private static double random(long seed) {
		return (mix(seed) >>> 11) * 0x1.0p-53;
	}

	public static void clear() {
		if (boundLevel != null) {
			DynamicLightManager.removeFogProvider(boundLevel, PROVIDER);
			boundLevel = null;
		}
		REGIONS.clear();
		ACTIVE.clear();
	}

	private record Region(HordeClient.ClientHorde event, double station, double lateral, long seed, double distance) {
	}

	private static class VisibleRegion {
		private Region region;
		private float previous, weight;

		private VisibleRegion(Region region) {
			this.region = region;
		}
	}
}
