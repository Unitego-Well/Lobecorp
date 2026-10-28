package org.unitego.lobecorp.client.horde;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
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
import java.util.HashSet;
import java.util.UUID;

/// 聚合声源，以及由短时 CPU 粗略流场快照驱动的边界扬尘和潮内刀光。
public class HordeAmbient {
	/// 刀光每批位置数加倍，保留原生成间隔和每处粒子数量。
	private static final int SLASH_POSITION_MULTIPLIER = 2;
	private static final List<Source> SOURCES = new ArrayList<>();
	private static long tick;
	private static long nextSlashTick;
	private static int selectedAreas;
	private static int emittedParticles;

	public static int selectedAreas() {
		return selectedAreas;
	}

	public static int emittedParticles() {
		return emittedParticles;
	}

	public static void tick() {
		selectedAreas = 0;
		emittedParticles = 0;
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		tick++;
		var nearest = HordeClient.batches().stream()
				.filter(batch -> batch.block().bounds().distanceToSqr(minecraft.player.position()) < 32 * 32).toList();
		if (nearest.isEmpty()) {
			SOURCES.clear();
		}
		if (!nearest.isEmpty() && HordeVisualConfig.SOUND.get() && tick % 6 == 0) {
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
		if (HordeVisualConfig.DUST.get()) {
			double range = HordeVisualConfig.PARTICLE_RANGE.get();
			var particleAreas = HordeClient.batches().stream()
					.filter(batch -> batch.block().bounds().distanceToSqr(minecraft.player.position()) < range * range).toList();
			emitVisibleAreas(particleAreas);
		}
	}

	// 清扫提示不是虚拟浪潮占据证明，不据此在潮外发射刀光或边界碎屑。
	@SuppressWarnings("unused")
	public static void queueCleanup(double x, double y, double z) {
	}

	private static boolean inView(Vec3 point) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var player = minecraft.player;
		return player != null && level != null
				&& point.distanceToSqr(minecraft.gameRenderer.getMainCamera().position()) <= Math.pow(HordeVisualConfig.PARTICLE_RANGE.get(), 2)
				&& level.getChunkSource().hasChunk((int) Math.floor(point.x / 16), (int) Math.floor(point.z / 16))
				&& HordeClient.ambientVisible(point);
	}

	private static boolean unobstructed(Vec3 point) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var player = minecraft.player;
		return level != null && player != null && inView(point)
				&& level.clip(new ClipContext(minecraft.gameRenderer.getMainCamera().position(),
				point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				player)).getType() == HitResult.Type.MISS;
	}

	private static void emitVisibleAreas(List<HordeClient.Batch> nearest) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}

		var samples = new ArrayList<LocatedSample>();
		var events = new HashSet<UUID>();
		for (var batch : nearest) {
			if (!events.add(batch.event().id)) {
				continue;
			}

			var snapshot = HordeParticleSamples.latest(batch.event(), batch.time());
			if (snapshot != null) {
				var event = batch.event();
				var circles = event.split.circles();
				for (var sample : snapshot.samples()) {
					if (activeRow(event, batch.time(), sample.row()) && sample.inWave(Math.max(0, (batch.time() - event.stop) * event.speed),
							Math.min(event.length, batch.time() * event.speed))
							&& HordeParticleFlow.clear(circles, event.origin, sample.ground(), event.entityHeight)
							&& inView(sample.ground())) {
						samples.add(new LocatedSample(batch.event(), batch.time(), sample));
					}
				}
			}
		}

		if (samples.isEmpty()) {
			return;
		}

		if (tick % HordeVisualConfig.PARTICLE_INTERVAL.get() == 0) {
			emitFragments(samples, HordeVisualConfig.FRAGMENT_GROUPS.get());
		}

		if (tick >= nextSlashTick) {
			nextSlashTick = tick + 1 + level.getRandom().nextInt(4);
			emitSlashes(samples);
		}
	}

	private static void emitFragments(List<LocatedSample> samples, int groups) {
		var level = Minecraft.getInstance().level;
		if (level == null || groups == 0) {
			return;
		}

		var candidates = samples.stream().filter(sample -> boundary(sample, samples)).toList();
		if (candidates.isEmpty()) {
			return;
		}

		var selected = new HashSet<BlockPos>();
		int limit = Math.clamp(groups, 0, 100);
		int start = level.getRandom().nextInt(candidates.size());
		for (int i = 0; i < candidates.size() && selected.size() < limit; i++) {
			var located = candidates.get((start + i) % candidates.size());
			var point = located.sample().ground().add(0, 0.05, 0);
			if (!selected.add(BlockPos.containing(point.scale(4))) || !visibleSpawn(located, point)) {
				continue;
			}

			selectedAreas++;
			var ground = level.getBlockState(BlockPos.containing(point.add(0, -0.1, 0)));
			if (!ground.isAir()) {
				particle(new BlockParticleOption(ParticleTypes.BLOCK, ground), point,
						(level.getRandom().nextDouble() - 0.5) * 0.16, 0.04,
						(level.getRandom().nextDouble() - 0.5) * 0.16, 1);
			}

			// 随机出生点独立检查一次，不能复用碎屑原点的空洞或射线结果。
			var random = level.getRandom();
			double speed = 0.04;
			var smoke = point.add((random.nextDouble() * 2 - 1) * 0.6, (random.nextDouble() * 2 - 1) * 0.1,
					(random.nextDouble() * 2 - 1) * 0.6);
			if (visibleSpawn(located, smoke)) {
				particle(LcParticleTypes.SHORT_SMOKE.get(), smoke,
						(random.nextDouble() * 2 - 1) * speed, (random.nextDouble() * 2 - 1) * speed,
						(random.nextDouble() * 2 - 1) * speed);
			}
		}
	}

	private static boolean boundary(LocatedSample located, List<LocatedSample> samples) {
		var event = located.event();
		var sample = located.sample();
		var point = sample.ground();
		double spacing = Math.max(event.rules.rowSpacing(), event.rules.columnSpacing());
		// 探针在变形后坐标上判内；稀疏样本缺邻不会制造边缘。
		if (sample.boundary()) {
			return true;
		}

		// 跨事件接合按代表点的同层距离粗判，不复用单潮身份邻接。
		for (var other : samples) {
			double contactDistance = Math.max(spacing, Math.max(other.event().rules.rowSpacing(), other.event().rules.columnSpacing()));
			if (!other.event().id.equals(event.id) && Math.abs(other.sample().ground().y - point.y) <= 1.5
					&& other.sample().ground().distanceToSqr(point) <= contactDistance * contactDistance) {
				return true;
			}
		}

		return false;
	}

	private static boolean currentInterior(LocatedSample located) {
		var event = located.event();
		var point = located.sample().ground();
		var circles = event.split.circles();
		var forward = event.tide.forward(located.sample().station());
		for (int row = -1; row <= 1; row++) {
			for (int column = -1; column <= 1; column++) {
				var probe = point.add(
						forward.x * row * event.rules.rowSpacing() - forward.z * column * event.rules.columnSpacing(),
						0, forward.z * row * event.rules.rowSpacing() + forward.x * column * event.rules.columnSpacing());
				if (!HordeParticleFlow.clear(circles, event.origin, probe, event.entityHeight)) {
					return false;
				}
			}
		}

		return true;
	}

	private static void emitSlashes(List<LocatedSample> samples) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}

		var interior = samples.stream().filter(sample -> sample.sample().interior()).toList();
		if (interior.isEmpty()) {
			return;
		}

		var random = level.getRandom();
		int count = SLASH_POSITION_MULTIPLIER * (4 + random.nextInt(7));
		int start = random.nextInt(interior.size());
		for (int i = 0; i < Math.min(count, interior.size()); i++) {
			var located = interior.get((start + i) % interior.size());
			var point = located.sample().ground().add(0, 1.7, 0);
			if (!currentInterior(located) || !visibleSpawn(located, point)) {
				continue;
			}

			selectedAreas++;
			int particles = 1 + random.nextInt(3);
			for (int j = 0; j < particles; j++) {
				var slash = switch (random.nextInt(3)) {
					case 0 -> LcParticleTypes.SIMPLE_SHORT_SLASH.get();
					case 1 -> LcParticleTypes.SIMPLE_LONG_SLASH.get();
					default -> LcParticleTypes.SIMPLE_DOUBLE_SLASH.get();
				};
				particle(slash, point, 0, 0, 0, 0.7f + random.nextFloat() * 0.5f);
			}
		}
	}

	private static boolean activeRow(HordeClient.ClientEvent event, double time, long row) {
		long first = Math.max(0, (long) Math.floor((time * event.speed - event.length) / event.rules.rowSpacing()) + 1);
		long last = (long) Math.floor(Math.min(event.stop, time) * event.speed / event.rules.rowSpacing());
		return row >= first && row <= last;
	}

	private static boolean visibleSpawn(LocatedSample located, Vec3 point) {
		var sample = located.sample();
		var envelope = sample.envelope();
		var event = located.event();
		if (envelope == null || !activeRow(event, located.time(), sample.row()) || !sample.contains(point)) {
			return false;
		}

		double station = envelope.stationAt(point);
		double tail = Math.max(0, (located.time() - event.stop) * event.speed);
		double head = Math.min(event.length, located.time() * event.speed);
		return station >= tail && station <= head
				&& HordeParticleFlow.clear(event.split.circles(), event.origin, point,
				Math.max(0, event.entityHeight - (point.y - sample.ground().y)))
				&& HordeClient.ambientGapClear(point) && unobstructed(point);
	}

	private static boolean allowed() {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		if (level == null) {
			return false;
		}

		// 坐标和射线由 visibleSpawn 每出生位置检查一次，设置仍逐粒筛选。
		var setting = minecraft.options.particles().get();
		return setting != ParticleStatus.MINIMAL
				&& (setting != ParticleStatus.DECREASED || level.getRandom().nextInt(3) != 0);
	}

	/// 不改变粒子自身缩放；仅用于复用其它系统已有的粒子外观与行为。
	private static void particle(ParticleOptions type, Vec3 point, double dx, double dy, double dz) {
		if (!allowed()) {
			return;
		}

		if (Minecraft.getInstance().particleEngine.createParticle(type, point.x, point.y, point.z, dx, dy, dz) != null) {
			emittedParticles++;
		}
	}

	private static void particle(ParticleOptions type, Vec3 point, double dx, double dy, double dz, float scale) {
		if (!allowed()) {
			return;
		}

		var particle = Minecraft.getInstance().particleEngine.createParticle(type, point.x, point.y, point.z, dx, dy, dz);
		if (particle != null) {
			particle.scale(scale);
			emittedParticles++;
		}
	}

	private record LocatedSample(HordeClient.ClientEvent event, double time, HordeParticleSamples.Sample sample) {
	}

	public static void clear() {
		SOURCES.clear();
		tick = 0;
		nextSlashTick = 0;
		selectedAreas = 0;
		emittedParticles = 0;
	}

	private static class Source {
		private long checkedAt = -20;
		private boolean blocked;
	}
}
