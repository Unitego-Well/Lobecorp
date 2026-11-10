package org.unitego.lobecorp.client.horde;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.config.client.HordeVisualConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 地面覆盖单元按世界坐标固定；碎屑独立分散补发。
public class HordeGroundParticles {
	private static final double CELL_SIZE = 3;
	private static final int CELL_STRIDE = 137;
	private static final Map<UUID, Long> cursors = new HashMap<>();

	public record Emission(int groups, int particles) {
	}

	public static Emission tick(List<HordeClient.ClientHorde> events, long tick) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var player = minecraft.player;
		if (level == null || player == null) {
			clear();
			return new Emission(0, 0);
		}

		if (minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
			return new Emission(0, 0);
		}

		var present = new HashSet<UUID>();
		HordeClient.events().forEach(event -> present.add(event.id));
		var camera = minecraft.gameRenderer.getMainCamera().position();
		double range = HordeVisualConfig.PARTICLE_RANGE.get();
		cursors.keySet().removeIf(id -> !present.contains(id));
		int due = 0;
		for (int step = 0; step < HordeVisualConfig.PARTICLE_FREQUENCY_MULTIPLIER; step++) {
			if ((tick * HordeVisualConfig.PARTICLE_FREQUENCY_MULTIPLIER + step) % HordeVisualConfig.PARTICLE_INTERVAL.get() == 0) {
				due++;
			}
		}
		long budget = (long) HordeVisualConfig.FRAGMENT_GROUPS.get() * HordeVisualConfig.PARTICLE_GROUP_MULTIPLIER * due;
		int radius = (int) Math.ceil(range / CELL_SIZE), side = radius * 2 + 1;
		int firstX = (int) Math.floor(camera.x / CELL_SIZE) - radius;
		int firstZ = (int) Math.floor(camera.z / CELL_SIZE) - radius;
		int count = side * side, groups = 0, particles = 0;
		for (var event : events) {
			if (!event.ready || event.failed || event.tide == null) {
				continue;
			}

			long cursor = cursors.getOrDefault(event.id, 0L);
			for (long emitted = 0; emitted < budget; emitted++) {
				int index = (int) ((cursor++ % count) * CELL_STRIDE % count);
				int x = firstX + index % side, z = firstZ + index / side;
				var emission = emitCell(event, x, z, camera, range);
				groups += emission.groups();
				particles += emission.particles();
			}
			cursors.put(event.id, cursor);
		}
		return new Emission(groups, particles);
	}

	private static Emission emitCell(HordeClient.ClientHorde event, int x, int z, Vec3 camera, double range) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		if (level == null || minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
			return new Emission(0, 0);
		}

		var point = ground(event, new Vec3((x + 0.5) * CELL_SIZE, camera.y, (z + 0.5) * CELL_SIZE), camera, range);
		if (point == null) {
			return new Emission(0, 0);
		}

		int particles = 0;
		var random = level.getRandom();
		var debrisCandidate = point.add((random.nextDouble() - 0.5) * CELL_SIZE * 0.8, 0,
				(random.nextDouble() - 0.5) * CELL_SIZE * 0.8);
		var debris = ground(event, debrisCandidate, camera, range);
		if (debris != null && allowed()) {
			var sampled = HordeGroundCache.sample(debris.x, debris.z);
			var surface = sampled == null ? level.getBlockState(BlockPos.containing(debris.add(0, -0.05, 0))) : sampled.state();
			if (!surface.isAir() && surface.getFluidState().isEmpty()
					&& minecraft.particleEngine.createParticle(new BlockParticleOption(ParticleTypes.BLOCK, surface),
						debris.x, debris.y + 0.04, debris.z, (random.nextDouble() - 0.5) * 0.12, 0.035,
						(random.nextDouble() - 0.5) * 0.12) != null) {
				particles++;
			}
		}

		return new Emission(1, particles);
	}

	private static Vec3 ground(HordeClient.ClientHorde event, Vec3 candidate, Vec3 camera, double range) {
		double dx = candidate.x - camera.x, dz = candidate.z - camera.z;
		if (dx * dx + dz * dz > range * range) {
			return null;
		}

		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var player = minecraft.player;
		if (level == null || player == null || !level.getChunkSource().hasChunk((int) Math.floor(candidate.x / 16),
				(int) Math.floor(candidate.z / 16))) {
			return null;
		}

		double station = event.tide.particleStation(candidate);
		double time = event.time(0);
		var center = event.tide.world(station, 0);
		var direction = event.tide.forward(station);
		double lateral = -(candidate.x - center.x) * direction.z + (candidate.z - center.z) * direction.x;
		double projected = station + (candidate.x - center.x) * direction.x + (candidate.z - center.z) * direction.z;
		if (projected < Math.max(0, (time - event.stop) * event.speed)
				|| projected > Math.min(event.length, time * event.speed)
				|| Math.abs(lateral) > event.columns * event.columnSpacing() / 2) {
			return null;
		}

		var sample = HordeGroundCache.sample(candidate.x, candidate.z);
		if (sample == null) {
			return null;
		}

		var point = new Vec3(candidate.x, sample.height() + 0.02, candidate.z);
		if (point.distanceToSqr(camera) > range * range) {
			return null;
		}
		return point;
	}

	private static boolean allowed() {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var setting = minecraft.options.particles().get();
		return level != null && setting != ParticleStatus.MINIMAL
				&& (setting != ParticleStatus.DECREASED || level.getRandom().nextInt(3) != 0);
	}

	public static void clear() {
		cursors.clear();
	}
}
