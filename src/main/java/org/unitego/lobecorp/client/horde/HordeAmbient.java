package org.unitego.lobecorp.client.horde;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;
import org.unitego.lobecorp.config.client.HordeVisualConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/// 少量聚合声源和近处扬尘，数量不随全部虚拟个体逐个增长。
public class HordeAmbient {
	private static final List<Source> SOURCES = new ArrayList<>();
	private static long tick;
	private static final int POINT_ATTEMPTS = 12;
	private static final List<Tier> TIERS = List.of(
			new Tier(0, 8, 8, 3, 2, 4, 2),
			new Tier(8, 16, 16, 2, 1, 2, 1),
			new Tier(16, 32, 32, 1, 1, 1, 1));
	private static int selectedAreas;
	private static int emittedParticles;

	public static int selectedAreas() {
		return selectedAreas;
	}

	public static int emittedParticles() {
		return emittedParticles;
	}
	// 清扫位置提示只保留一秒，不积压已经离开视野的旧刀光。
	private static final int CLEANUP_CUE_TICKS = 20;
	private static final Map<BlockPos, CleanupCue> CLEANUP = new LinkedHashMap<>();

	public static void tick() {
		selectedAreas = 0;
		emittedParticles = 0;
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		tick++;
		long now = minecraft.level.getGameTime();
		CLEANUP.values().removeIf(cue -> now - cue.created() > CLEANUP_CUE_TICKS || !inView(cue.point()));
		var nearest = HordeClient.batches().stream()
				.filter(batch -> batch.block().bounds().distanceToSqr(minecraft.player.position()) < 32 * 32).toList();
		if (nearest.isEmpty()) {
			SOURCES.clear();
			return;
		}
		if (HordeVisualConfig.SOUND.get() && tick % 6 == 0) {
			int count = Math.min(HordeVisualConfig.SOUND_VOICES.get(), nearest.size());
			while (SOURCES.size() < count) {
				SOURCES.add(new Source());
			}
			for (int i = 0; i < count; i++) {
				var source = SOURCES.get(i);
				var point = nearest.get(i * nearest.size() / count).block().center();
				if (tick - source.checkedAt >= 20) {
					source.blocked = minecraft.level.clip(new ClipContext(minecraft.player.getEyePosition(), point.add(0, 1, 0),
							ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player)).getType() != HitResult.Type.MISS;
					source.checkedAt = tick;
				}
				float volume = (float) Math.min(1, nearest.size() / 32d) * (source.blocked ? 0.2f : 0.65f);
				minecraft.level.playLocalSound(point.x, point.y, point.z, SoundEvents.STONE_STEP,
						SoundSource.HOSTILE, volume, 0.8f + i * 0.07f, false);
			}
		}
		if (HordeVisualConfig.DUST.get() && tick % HordeVisualConfig.PARTICLE_INTERVAL.get() == 0) {
			emitVisibleAreas(nearest);
		}
	}

	public static void queueCleanup(double x, double y, double z) {
		var minecraft = Minecraft.getInstance();
		var point = new Vec3(x, y, z);
		if (minecraft.level != null && HordeVisualConfig.DUST.get() && inView(point)) {
			CLEANUP.put(BlockPos.containing(point), new CleanupCue(point, minecraft.level.getGameTime()));
		}
	}

	private static boolean inView(Vec3 point) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var player = minecraft.player;
		return player != null && level != null
				&& point.distanceToSqr(player.position()) <= Math.pow(HordeVisualConfig.PARTICLE_RANGE.get(), 2)
				&& level.getChunkSource().hasChunk((int) Math.floor(point.x / 16), (int) Math.floor(point.z / 16))
				&& HordeClient.ambientVisible(point);
	}

	private static boolean unobstructed(Vec3 point) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var player = minecraft.player;
		return level != null && player != null && inView(point)
				&& level.clip(new ClipContext(minecraft.gameRenderer.getMainCamera().position(),
				point.add(0, 0.25, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				player)).getType() == HitResult.Type.MISS;
	}

	private static void emitVisibleAreas(List<HordeClient.Batch> nearest) {
		var selected = new HashSet<AreaKey>();
		var cues = CLEANUP.values().iterator();
		while (cues.hasNext()) {
			var cue = cues.next();
			cues.remove();
			if (unobstructed(cue.point())) {
				emit(cue.point(), TIERS.getFirst());
				break;
			}
		}
		for (var tier : TIERS) {
			for (int area = 0; area < tier.areas(); area++) {
				var point = emissionPoint(nearest, tier, selected);
				if (point != null) {
					emit(point, tier);
				}
			}
		}
	}

	private static Vec3 emissionPoint(List<HordeClient.Batch> nearest, Tier tier, Set<AreaKey> selected) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var player = minecraft.player;
		if (level == null || player == null) {
			return null;
		}
		var random = level.getRandom();
		for (int attempt = 0; attempt < POINT_ATTEMPTS; attempt++) {
			var batch = nearest.get(random.nextInt(nearest.size()));
			long ordinal = batch.offset() + random.nextInt(batch.count());
			long row = batch.firstRow() + ordinal / batch.block().columns();
			int column = batch.block().firstColumn() + (int) (ordinal % batch.block().columns());
			double station = batch.time() * batch.event().speed - row * batch.event().rules.rowSpacing();
			if (station < batch.block().first() || station >= batch.block().last() || station < 0 || station >= batch.event().length) {
				continue;
			}
			double lateral = (column - (batch.event().columns - 1) / 2d) * batch.event().rules.columnSpacing();
			var point = particlePoint(batch.event(), station, lateral);
			double distance = point.distanceToSqr(player.position());
			if (distance < tier.minimum() * tier.minimum() || distance > tier.maximum() * tier.maximum()
					|| !insideTide(batch.event(), point) || !unobstructed(point)) {
				continue;
			}
			var key = new AreaKey(batch.event().id, tier.cell(), (int) Math.floor(point.x / tier.cell()),
					(int) Math.floor(point.y / tier.cell()), (int) Math.floor(point.z / tier.cell()));
			if (selected.add(key)) {
				return point;
			}
		}
		return null;
	}

	private static Vec3 particlePoint(HordeClient.ClientEvent event, double station, double lateral) {
		int low = 0, high = event.nodes.length - 2;
		while (low < high) {
			int middle = (low + high + 1) / 2;
			if (event.nodes[middle].distance() <= station) {
				low = middle;
			} else {
				high = middle - 1;
			}
		}
		var a = event.nodes[low];
		var b = event.nodes[low + 1];
		double weight = Math.clamp((station - a.distance()) / Math.max(0.0001, b.distance() - a.distance()), 0, 1);
		var forward = b.at().subtract(a.at()).multiply(1, 0, 1).normalize();
		var center = a.at().lerp(b.at(), weight);
		double y = a.height(lateral) + (b.height(lateral) - a.height(lateral)) * weight;
		return new Vec3(center.x - forward.z * lateral, y, center.z + forward.x * lateral);
	}

	private static boolean insideTide(HordeClient.ClientEvent event, Vec3 point) {
		double radius = HordeGpuRenderer.modelRadius(event.visual);
		for (var box : event.regions.values()) {
			if (point.y < box.y() + box.sizeY() && point.y + event.entityHeight > box.y()
					&& point.x > box.x() - radius && point.x < box.x() + box.sizeX() + radius
					&& point.z > box.z() - radius && point.z < box.z() + box.sizeZ() + radius) {
				return false;
			}
		}
		return true;
	}

	private static void emit(Vec3 point, Tier tier) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		selectedAreas++;
		var random = level.getRandom();
		for (int index = 0; index < Math.max(tier.smoke(), Math.max(tier.fragments(), tier.slashes())); index++) {
			var at = point.add((random.nextDouble() - 0.5) * 1.6, 0, (random.nextDouble() - 0.5) * 1.6);
			if (index < tier.smoke()) {
				particle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.add(0, 0.15 + random.nextDouble(), 0), 0, 0.03, 0);
			}
			if (index < tier.fragments()) {
				var ground = level.getBlockState(BlockPos.containing(at).below());
				if (!ground.isAir()) {
					particle(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.add(0, 0.05, 0),
							(random.nextDouble() - 0.5) * 0.16, 0.04, (random.nextDouble() - 0.5) * 0.16);
				}
			}
			if (index < tier.slashes()) {
				var slash = switch (random.nextInt(3)) {
					case 0 -> LcParticleTypes.SIMPLE_SHORT_SLASH.get();
					case 1 -> LcParticleTypes.SIMPLE_LONG_SLASH.get();
					default -> LcParticleTypes.SIMPLE_DOUBLE_SLASH.get();
				};
				particle(slash, at.add(0, 0.5 + random.nextDouble() * 0.6, 0), 0, 0, 0);
			}
		}
	}

	private static void particle(ParticleOptions type, Vec3 point, double dx, double dy, double dz) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var player = minecraft.player;
		// 用最终出生坐标再次检查，不能只验证地面选区而让高处烟雾穿过范围或遮挡。
		if (level == null || player == null || !inView(point)
				|| level.clip(new ClipContext(minecraft.gameRenderer.getMainCamera().position(), point,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) {
			return;
		}
		level.addParticle(type, point.x, point.y, point.z, dx, dy, dz);
		emittedParticles++;
	}

	private record Tier(int minimum, int maximum, int cell, int areas, int smoke, int fragments, int slashes) {
	}

	private record AreaKey(UUID event, int cell, int x, int y, int z) {
	}

	private record CleanupCue(Vec3 point, long created) {
	}

	public static void clear() {
		SOURCES.clear();
		CLEANUP.clear();
		tick = 0;
		selectedAreas = 0;
		emittedParticles = 0;
	}

	private static class Source {
		private long checkedAt = -20;
		private boolean blocked;
	}
}
