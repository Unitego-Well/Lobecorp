package org.unitego.lobecorp.client.horde;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.unitego.lobecorp.Lobecorp;
import java.util.Comparator;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
	private static final List<Vec3> AUDIO_POINTS = new ArrayList<>();
	private static final float[] VOICE_GAIN = {0.55F, 0.65F, 0.55F, 0.5F, 0.6F, 0.55F, 0.75F};
	private static long nextMove, nextAttack, nextCleanup, nextJump, nextLoop, landingAt;
	private static int cleanupStage, loopsLeft;
	private static boolean pendingLanding;
	private static float previousRumble, rumble;
	private static final int SOUND_BATCH_MULTIPLIER = 3;
	private static final int SOUND_FREQUENCY_MULTIPLIER = 10;
	/// 每次在最近事件内取声源，区域半径与最大可听距离均以格为单位。
	private static final double SOUND_REGION_RADIUS = 32;
	private static final double MAX_SOUND_RANGE = 256;
	private static final String[] SOUND_TYPES = {"move", "attack", "clear_start", "clear_loop", "clear_end", "jump", "land"};
	private static HordeClient.ClientEvent soundEvent;
	private static Vec3 soundCenter;
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
		if (minecraft.level == null || minecraft.player == null) {
			stopSounds();
			AUDIO_POINTS.clear();
			previousRumble = rumble = 0;
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		tick++;
		updateAudio();
		if (HordeVisualConfig.DUST.get()) {
			double range = HordeVisualConfig.PARTICLE_RANGE.get();
			var particleAreas = HordeClient.batches().stream()
					.filter(batch -> batch.block().bounds().distanceToSqr(minecraft.player.position()) < range * range).toList();
			emitVisibleAreas(particleAreas);
		}
	}

	private static void updateAudio() {
		var minecraft = Minecraft.getInstance();
		var player = minecraft.player;
		if (player == null) {
			return;
		}

		var listener = minecraft.gameRenderer.getMainCamera().isInitialized()
				? minecraft.gameRenderer.getMainCamera().position() : player.getEyePosition();
		selectAudioPoints(listener);
		selectSoundRegion(player.position());
		previousRumble = rumble;
		float target = AUDIO_POINTS.isEmpty() || !HordeVisualConfig.EVENT_SHAKE.get() ? 0
				: envelope(AUDIO_POINTS.getFirst().distanceTo(listener), HordeVisualConfig.EVENT_SHAKE_RANGE.get());
		rumble += (target - rumble) * 0.25F;
		SOURCES.removeIf(source -> {
			if (tick - source.born > 10 && !minecraft.getSoundManager().isActive(source)) {
				source.end();
				return true;
			}
			return false;
		});
		while (SOURCES.size() > soundBudget()) {
			var source = SOURCES.stream().filter(voice -> voice.kind == 0).findFirst().orElse(SOURCES.getFirst());
			source.end();
			SOURCES.remove(source);
		}
		if (!HordeVisualConfig.SOUND.get() || HordeVisualConfig.SOUND_VOICES.get() <= 0) {
			stopSounds();
			cleanupStage = 0;
			pendingLanding = false;
			nextMove = nextAttack = nextCleanup = nextJump = tick * SOUND_FREQUENCY_MULTIPLIER;
			return;
		}
		if (soundEvent == null) {
			cleanupStage = 0;
			pendingLanding = false;
			nextMove = nextAttack = nextCleanup = nextJump = tick * SOUND_FREQUENCY_MULTIPLIER;
			return;
		}

		for (int step = 0; step < SOUND_FREQUENCY_MULTIPLIER; step++) {
			advanceAudio(tick * SOUND_FREQUENCY_MULTIPLIER + step);
		}
	}

	private static void advanceAudio(long now) {
		// 收尾与落地优先，普通移动不抢占已到期的动作声预算。
		if (pendingLanding && now >= landingAt && play(6)) {
			pendingLanding = false;
		}
		advanceCleanup(now);
		if (!pendingLanding && now >= nextJump && play(5)) {
			pendingLanding = true;
			landingAt = now + interval(29, 33);
			nextJump = now + interval(240, 400);
		}
		if (now >= nextAttack) {
			play(1);
			nextAttack = now + interval(30, 70);
		}
		if (now >= nextMove) {
			play(0);
			nextMove = now + interval(7, 13);
		}
	}

	private static void selectAudioPoints(Vec3 listener) {
		var points = new ArrayList<Vec3>();
		for (var event : HordeClient.events()) {
			if (!event.ready || event.failed || event.tide == null || event.columns <= 0) {
				continue;
			}
			double time = event.time(0);
			double head = Math.min(event.length, time * event.speed);
			double tail = Math.max(0, (time - event.stop) * event.speed);
			if (head <= tail) {
				continue;
			}
			double nearest = Math.clamp(event.tide.stationOf(listener), tail, head);
			for (double sample : new double[]{nearest, nearest - 8, nearest + 8, nearest - 32, nearest + 32, nearest - 64, nearest + 64, nearest - 128, nearest + 128, head, tail}) {
				double station = Math.clamp(sample, tail, head);
				var center = event.tide.world(station, 0);
				var forward = event.tide.forward(station);
				double lateral = (listener.x - center.x) * -forward.z + (listener.z - center.z) * forward.x;
				double half = Math.max(0, (event.columns - 1) * event.columnSpacing() / 2);
				var point = event.tide.world(station, Math.clamp(lateral, -half, half));
				if (point.isFinite() && HordeParticleFlow.clear(event.split.circles(), event.origin, point, event.entityHeight)) {
					points.add(point);
				}
			}
		}
		points.sort(Comparator.comparingDouble(point -> point.distanceToSqr(listener)));
		AUDIO_POINTS.clear();
		for (var point : points) {
			if (point.distanceToSqr(listener) <= Math.pow(Math.max(HordeVisualConfig.SOUND_RANGE.get(),
					HordeVisualConfig.EVENT_SHAKE_RANGE.get()), 2)
					&& AUDIO_POINTS.stream().noneMatch(other -> other.distanceToSqr(point) < 16)) {
				AUDIO_POINTS.add(point);
				if (AUDIO_POINTS.size() == Math.max(1, HordeVisualConfig.SOUND_VOICES.get())) {
					break;
				}
			}
		}
	}

	private static void advanceCleanup(long now) {
		if (cleanupStage == 0 && now >= nextCleanup && play(2)) {
			cleanupStage = 1;
			loopsLeft = interval(3, 6);
			nextLoop = now + 20;
			nextCleanup = now + interval(160, 240);
		} else if (cleanupStage == 1 && now >= nextLoop) {
			if (loopsLeft > 0 && play(3)) {
				loopsLeft--;
				nextLoop = now + interval(8, 16);
			} else if (loopsLeft == 0 && play(4)) {
				cleanupStage = 2;
				nextLoop = now + 28;
			}
		} else if (cleanupStage == 2 && now >= nextLoop) {
			cleanupStage = 0;
		}
	}

	private static int soundBudget() {
		return HordeVisualConfig.SOUND_MAX_ACTIVE.get() * SOUND_BATCH_MULTIPLIER * SOUND_FREQUENCY_MULTIPLIER;
	}

	private static int interval(int low, int high) {
		return low + Minecraft.getInstance().level.getRandom().nextInt(high - low + 1);
	}

	private static void selectSoundRegion(Vec3 player) {
		soundEvent = null;
		soundCenter = null;
		double closest = Math.pow(Math.min(MAX_SOUND_RANGE, HordeVisualConfig.SOUND_RANGE.get()), 2);
		for (var event : HordeClient.events()) {
			if (!event.ready || event.failed || event.tide == null || event.columns <= 0) {
				continue;
			}

			double time = event.time(0);
			double head = Math.min(event.length, time * event.speed);
			double tail = Math.max(0, (time - event.stop) * event.speed);
			if (head <= tail) {
				continue;
			}

			double nearest = Math.clamp(event.tide.stationOf(player), tail, head);
			for (double offset : new double[]{0, -8, 8, -32, 32, -64, 64, -128, 128}) {
				double station = Math.clamp(nearest + offset, tail, head);
				var center = event.tide.world(station, 0);
				var forward = event.tide.forward(station);
				double lateral = (player.x - center.x) * -forward.z + (player.z - center.z) * forward.x;
				double half = Math.max(0, (event.columns - 1) * event.columnSpacing() / 2);
				var point = event.tide.world(station, Math.clamp(lateral, -half, half));
				double distance = point.distanceToSqr(player);
				if (point.isFinite() && distance < closest
						&& HordeParticleFlow.clear(event.split.circles(), event.origin, point, event.entityHeight)) {
					closest = distance;
					soundEvent = event;
					soundCenter = point;
				}
			}
		}
	}

	private static Vec3 soundPoint() {
		var event = soundEvent;
		if (event == null || soundCenter == null) {
			return null;
		}

		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var player = minecraft.player;
		if (level == null || player == null) {
			return null;
		}

		var random = level.getRandom();
		double time = event.time(0);
		double head = Math.min(event.length, time * event.speed);
		double tail = Math.max(0, (time - event.stop) * event.speed);
		double station = Math.clamp(event.tide.stationOf(soundCenter), tail, head);
		var center = event.tide.world(station, 0);
		var forward = event.tide.forward(station);
		double lateral = (soundCenter.x - center.x) * -forward.z + (soundCenter.z - center.z) * forward.x;
		double half = Math.max(0, (event.columns - 1) * event.columnSpacing() / 2);
		double range = Math.min(MAX_SOUND_RANGE, HordeVisualConfig.SOUND_RANGE.get());
		for (int attempt = 0; attempt < 8; attempt++) {
			var point = event.tide.world(
					Math.clamp(station + (random.nextDouble() * 2 - 1) * SOUND_REGION_RADIUS, tail, head),
					Math.clamp(lateral + (random.nextDouble() * 2 - 1) * SOUND_REGION_RADIUS, -half, half));
			if (point.isFinite() && point.distanceToSqr(soundCenter) <= SOUND_REGION_RADIUS * SOUND_REGION_RADIUS
					&& point.distanceToSqr(player.position()) < range * range
					&& HordeParticleFlow.clear(event.split.circles(), event.origin, point, event.entityHeight)) {
				return point;
			}
		}

		return null;
	}

	private static boolean play(int kind) {
		var level = Minecraft.getInstance().level;
		if (level == null || SOURCES.size() + SOUND_BATCH_MULTIPLIER > soundBudget()) {
			return false;
		}

		var points = new ArrayList<Vec3>();
		for (int copy = 0; copy < SOUND_BATCH_MULTIPLIER; copy++) {
			var point = soundPoint();
			if (point == null) {
				return false;
			}

			points.add(point);
		}

		for (var point : points) {
			var source = new Source(kind, point, level.getRandom());
			SOURCES.add(source);
			Minecraft.getInstance().getSoundManager().play(source);
		}

		return true;
	}

	private static float envelope(double distance, double range) {
		float value = (float) Math.clamp(1 - distance / range, 0, 1);
		return value * value * (3 - 2 * value);
	}

	public static float shakeStrength(float partialTick) {
		return (previousRumble + (rumble - previousRumble) * Math.clamp(partialTick, 0, 1))
				* HordeVisualConfig.SHAKE_STRENGTH.get().floatValue();
	}

	public static boolean ambientSound(SoundInstance instance) {
		return instance instanceof Source;
	}

	private static void stopSounds() {
		for (var source : SOURCES) {
			source.end();
		}
		SOURCES.clear();
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
        double spacing = Math.max(event.rowSpacing(), event.columnSpacing());
		// 探针在变形后坐标上判内；稀疏样本缺邻不会制造边缘。
		if (sample.boundary()) {
			return true;
		}

		// 跨事件接合按代表点的同层距离粗判，不复用单潮身份邻接。
		for (var other : samples) {
			double contactDistance = Math.max(spacing, Math.max(other.event().rowSpacing(), other.event().columnSpacing()));
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
						forward.x * row * event.rowSpacing() - forward.z * column * event.columnSpacing(),
						0, forward.z * row * event.rowSpacing() + forward.x * column * event.columnSpacing());
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
		long first = Math.max(0, (long) Math.floor((time * event.speed - event.length) / event.rowSpacing()) + 1);
		long last = (long) Math.floor(Math.min(event.stop, time) * event.speed / event.rowSpacing());
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
		stopSounds();
		AUDIO_POINTS.clear();
		soundEvent = null;
		soundCenter = null;
		previousRumble = rumble = 0;
		nextMove = nextAttack = nextCleanup = nextJump = nextLoop = landingAt = 0;
		cleanupStage = loopsLeft = 0;
		pendingLanding = false;
		tick = 0;
		nextSlashTick = 0;
		selectedAreas = 0;
		emittedParticles = 0;
	}

	/// 固定世界坐标的普通音效，只由现有入口持有并停止自身。
	private static class Source extends SimpleSoundInstance {
		private final int kind;
		private final long born = HordeAmbient.tick;

		private Source(int kind, Vec3 point, RandomSource random) {
			super(Lobecorp.id("event.midnight_cleanup." + SOUND_TYPES[kind]), SoundSource.AMBIENT,
					VOICE_GAIN[kind], 1, random, false, 0,
					Attenuation.LINEAR, point.x, point.y, point.z, false);
			this.kind = kind;
		}

		private void end() {
			Minecraft.getInstance().getSoundManager().stop(this);
		}
	}
}
