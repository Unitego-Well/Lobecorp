package org.unitego.lobecorp.client.encounter.midnightcleanup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.util.horde.HordePresenceUtil;
import org.unitego.lobecorp.client.horde.*;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// 聚合声源，以及由短时 CPU 粗略流场快照驱动的边界扬尘和潮内刀光。
public class MidnightCleanupAmbient {
	private static final List<Source> SOURCES = new ArrayList<>();
	private static final List<Vec3> AUDIO_POINTS = new ArrayList<>();
	private static long nextMove, nextAttack, nextCleanup, nextJump, nextLoop, landingAt;
	private static int cleanupStage, loopsLeft;
	private static boolean pendingLanding;
	private static float previousRumble, rumble;
	private static HordeClient.ClientHorde soundEvent;
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
			HordeGroundFog.clear();
			HordeGroundCache.clear();
			AUDIO_POINTS.clear();
			previousRumble = rumble = 0;
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		tick++;
		HordeGroundCache.tick();
		updateAudio();
		if (HordeVisualConfig.ENABLED.get() && HordeVisualConfig.DUST.get()) {
			double range = HordeVisualConfig.PARTICLE_RANGE.get();
			var camera = minecraft.gameRenderer.getMainCamera().position();
			var particleAreas = MidnightCleanupDisplay.instances().stream()
					.filter(event -> event.ready && !event.failed && event.tide != null
							&& event.blocks.stream().anyMatch(block -> block.bounds().distanceToSqr(camera) < range * range)).toList();
			HordeGroundFog.tick(particleAreas);
			var ground = HordeGroundParticles.tick(particleAreas, tick);
			selectedAreas += ground.groups();
			emittedParticles += ground.particles();
			emitVisibleAreas(particleAreas);
		} else {
			HordeGroundParticles.clear();
			HordeGroundFog.clear();
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
		float target = HordeVisualConfig.EVENT_SHAKE.get() ? eventRumble(listener) : 0;
		rumble += (target - rumble) * MidnightCleanupEncounter.RUMBLE_RESPONSE;
		SOURCES.removeIf(source -> {
			if (tick - source.born > MidnightCleanupEncounter.VOICE_IDLE_TICKS && !minecraft.getSoundManager().isActive(source)) {
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
			nextMove = nextAttack = nextCleanup = nextJump = tick * MidnightCleanupEncounter.SOUND_FREQUENCY_MULTIPLIER;
			return;
		}
		if (soundEvent == null) {
			cleanupStage = 0;
			pendingLanding = false;
			nextMove = nextAttack = nextCleanup = nextJump = tick * MidnightCleanupEncounter.SOUND_FREQUENCY_MULTIPLIER;
			return;
		}

		for (int step = 0; step < MidnightCleanupEncounter.SOUND_FREQUENCY_MULTIPLIER; step++) {
			advanceAudio(tick * MidnightCleanupEncounter.SOUND_FREQUENCY_MULTIPLIER + step);
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
			landingAt = now + interval(MidnightCleanupEncounter.LANDING_DELAY_MIN, MidnightCleanupEncounter.LANDING_DELAY_MAX);
			nextJump = now + interval(MidnightCleanupEncounter.JUMP_INTERVAL_MIN, MidnightCleanupEncounter.JUMP_INTERVAL_MAX);
		}
		if (now >= nextAttack) {
			play(1);
			nextAttack = now + interval(MidnightCleanupEncounter.ATTACK_INTERVAL_MIN, MidnightCleanupEncounter.ATTACK_INTERVAL_MAX);
		}
		if (now >= nextMove) {
			play(0);
			nextMove = now + interval(MidnightCleanupEncounter.MOVE_INTERVAL_MIN, MidnightCleanupEncounter.MOVE_INTERVAL_MAX);
		}
	}

	private static void selectAudioPoints(Vec3 listener) {
		var points = new ArrayList<Vec3>();
		for (var event : MidnightCleanupDisplay.instances()) {
			if (!event.ready || event.failed || event.tide == null || event.columns <= 0) {
				continue;
			}
			double time = event.time(0);
			double head = HordePresenceUtil.head(time, event.speed, event.length);
			double tail = HordePresenceUtil.tail(time, event.stop, event.speed, event.length);
			if (head <= tail) {
				continue;
			}
			double nearest = Math.clamp(event.tide.stationOf(listener), tail, head);
			for (double sample : new double[]{nearest, nearest - MidnightCleanupEncounter.AUDIO_SAMPLE_NEAR, nearest + MidnightCleanupEncounter.AUDIO_SAMPLE_NEAR,
					nearest - MidnightCleanupEncounter.AUDIO_SAMPLE_MIDDLE, nearest + MidnightCleanupEncounter.AUDIO_SAMPLE_MIDDLE,
					nearest - MidnightCleanupEncounter.AUDIO_SAMPLE_FAR, nearest + MidnightCleanupEncounter.AUDIO_SAMPLE_FAR,
					nearest - MidnightCleanupEncounter.AUDIO_SAMPLE_DISTANT, nearest + MidnightCleanupEncounter.AUDIO_SAMPLE_DISTANT, head, tail}) {
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
					&& AUDIO_POINTS.stream().noneMatch(other -> other.distanceToSqr(point) < MidnightCleanupEncounter.AUDIO_POINT_SEPARATION_SQUARED)) {
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
			loopsLeft = interval(MidnightCleanupEncounter.CLEANUP_LOOPS_MIN, MidnightCleanupEncounter.CLEANUP_LOOPS_MAX);
			nextLoop = now + MidnightCleanupEncounter.CLEANUP_START_DELAY;
			nextCleanup = now + interval(MidnightCleanupEncounter.CLEANUP_INTERVAL_MIN, MidnightCleanupEncounter.CLEANUP_INTERVAL_MAX);
		} else if (cleanupStage == 1 && now >= nextLoop) {
			if (loopsLeft > 0 && play(3)) {
				loopsLeft--;
				nextLoop = now + interval(MidnightCleanupEncounter.CLEANUP_LOOP_INTERVAL_MIN, MidnightCleanupEncounter.CLEANUP_LOOP_INTERVAL_MAX);
			} else if (loopsLeft == 0 && play(4)) {
				cleanupStage = 2;
				nextLoop = now + MidnightCleanupEncounter.CLEANUP_END_DELAY;
			}
		} else if (cleanupStage == 2 && now >= nextLoop) {
			cleanupStage = 0;
		}
	}

	private static int soundBudget() {
		return HordeVisualConfig.SOUND_MAX_ACTIVE.get() * MidnightCleanupEncounter.SOUND_BATCH_MULTIPLIER * MidnightCleanupEncounter.SOUND_FREQUENCY_MULTIPLIER;
	}

	private static int interval(int low, int high) {
		return low + Minecraft.getInstance().level.getRandom().nextInt(high - low + 1);
	}

	private static void selectSoundRegion(Vec3 player) {
		soundEvent = null;
		soundCenter = null;
		double closest = Math.pow(Math.min(MidnightCleanupEncounter.MAX_SOUND_RANGE, HordeVisualConfig.SOUND_RANGE.get()), 2);
		for (var event : MidnightCleanupDisplay.instances()) {
			if (!event.ready || event.failed || event.tide == null || event.columns <= 0) {
				continue;
			}

			double time = event.time(0);
			double head = HordePresenceUtil.head(time, event.speed, event.length);
			double tail = HordePresenceUtil.tail(time, event.stop, event.speed, event.length);
			if (head <= tail) {
				continue;
			}

			double nearest = Math.clamp(event.tide.stationOf(player), tail, head);
			for (double offset : new double[]{0, -MidnightCleanupEncounter.AUDIO_SAMPLE_NEAR, MidnightCleanupEncounter.AUDIO_SAMPLE_NEAR,
					-MidnightCleanupEncounter.AUDIO_SAMPLE_MIDDLE, MidnightCleanupEncounter.AUDIO_SAMPLE_MIDDLE,
					-MidnightCleanupEncounter.AUDIO_SAMPLE_FAR, MidnightCleanupEncounter.AUDIO_SAMPLE_FAR,
					-MidnightCleanupEncounter.AUDIO_SAMPLE_DISTANT, MidnightCleanupEncounter.AUDIO_SAMPLE_DISTANT}) {
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
		double head = HordePresenceUtil.head(time, event.speed, event.length);
		double tail = HordePresenceUtil.tail(time, event.stop, event.speed, event.length);
		double station = Math.clamp(event.tide.stationOf(soundCenter), tail, head);
		var center = event.tide.world(station, 0);
		var forward = event.tide.forward(station);
		double lateral = (soundCenter.x - center.x) * -forward.z + (soundCenter.z - center.z) * forward.x;
		double half = Math.max(0, (event.columns - 1) * event.columnSpacing() / 2);
		double range = Math.min(MidnightCleanupEncounter.MAX_SOUND_RANGE, HordeVisualConfig.SOUND_RANGE.get());
		for (int attempt = 0; attempt < MidnightCleanupEncounter.SOUND_POINT_ATTEMPTS; attempt++) {
			var point = event.tide.world(
					Math.clamp(station + (random.nextDouble() * 2 - 1) * MidnightCleanupEncounter.SOUND_REGION_RADIUS, tail, head),
					Math.clamp(lateral + (random.nextDouble() * 2 - 1) * MidnightCleanupEncounter.SOUND_REGION_RADIUS, -half, half));
			if (point.isFinite() && point.distanceToSqr(soundCenter) <= MidnightCleanupEncounter.SOUND_REGION_RADIUS * MidnightCleanupEncounter.SOUND_REGION_RADIUS
					&& point.distanceToSqr(player.position()) < range * range
					&& HordeParticleFlow.clear(event.split.circles(), event.origin, point, event.entityHeight)) {
				return point;
			}
		}

		return null;
	}

	private static boolean play(int kind) {
		var level = Minecraft.getInstance().level;
		if (level == null || SOURCES.size() + MidnightCleanupEncounter.SOUND_BATCH_MULTIPLIER > soundBudget()) {
			return false;
		}

		var points = new ArrayList<Vec3>();
		for (int copy = 0; copy < MidnightCleanupEncounter.SOUND_BATCH_MULTIPLIER; copy++) {
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

	/// 震源取活动浪潮的最近横向位置，不依赖声源额度或入场特效。
	private static float eventRumble(Vec3 listener) {
		float strongest = 0;
		for (var event : MidnightCleanupDisplay.instances()) {
			if (!event.ready || event.failed || event.tide == null || event.columns <= 0) {
				continue;
			}
			double time = event.time(0);
			if (time >= event.stop + event.length / event.speed) {
				continue;
			}
			double head = HordePresenceUtil.head(time, event.speed, event.length);
			double tail = HordePresenceUtil.tail(time, event.stop, event.speed, event.length);
			double station = Math.clamp(event.tide.particleStation(listener), tail, head);
			var center = event.tide.world(station, 0);
			var forward = event.tide.forward(station);
			double across = (listener.x - center.x) * -forward.z + (listener.z - center.z) * forward.x;
			double half = Math.max(0, (event.columns - 1) * event.columnSpacing() / 2);
			var source = event.tide.world(station, Math.clamp(across, -half, half));
			float faded = envelope(source.distanceTo(listener), HordeVisualConfig.EVENT_SHAKE_RANGE.get());
			strongest = Math.max(strongest, (float) Math.sqrt(faded));
		}
		return strongest;
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

	private static boolean withinParticleRange(Vec3 point) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var player = minecraft.player;
		return player != null && level != null
				&& point.distanceToSqr(minecraft.gameRenderer.getMainCamera().position()) <= Math.pow(HordeVisualConfig.PARTICLE_RANGE.get(), 2)
				&& level.getChunkSource().hasChunk((int) Math.floor(point.x / 16), (int) Math.floor(point.z / 16));
	}

	private static void emitVisibleAreas(List<HordeClient.ClientHorde> nearest) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}

		var samples = new ArrayList<LocatedSample>();
		for (var event : nearest) {
			double time = event.time(0);
			event.particleSamples.capture(event, time, tick, 0, 0, event.columns);
			var snapshot = HordeParticleSamples.latest(event, time);
			if (snapshot != null) {
				for (var sample : snapshot.samples()) {
					if (activeRow(event, time, sample.row()) && sample.inWave(HordePresenceUtil.tail(time, event.stop, event.speed, event.length),
							HordePresenceUtil.head(time, event.speed, event.length))
							&& withinParticleRange(sample.ground())) {
						samples.add(new LocatedSample(event, time, sample));
					}
				}
			}
		}

		if (samples.isEmpty()) {
			return;
		}

		for (int step = 0; step < HordeVisualConfig.PARTICLE_FREQUENCY_MULTIPLIER; step++) {
			long now = tick * HordeVisualConfig.PARTICLE_FREQUENCY_MULTIPLIER + step;
			if (now >= nextSlashTick) {
				nextSlashTick = now + MidnightCleanupEncounter.SLASH_INTERVAL_MIN + level.getRandom().nextInt(MidnightCleanupEncounter.SLASH_INTERVAL_VARIATION);
				emitSlashes(samples);
			}
		}
	}

	private static boolean currentInterior(LocatedSample located, Vec3 point) {
		var event = located.event();
		var forward = event.tide.forward(located.sample().station());
		for (int row = -MidnightCleanupEncounter.SLASH_INTERIOR_PROBE_RADIUS; row <= MidnightCleanupEncounter.SLASH_INTERIOR_PROBE_RADIUS; row++) {
			for (int column = -MidnightCleanupEncounter.SLASH_INTERIOR_PROBE_RADIUS; column <= MidnightCleanupEncounter.SLASH_INTERIOR_PROBE_RADIUS; column++) {
				var probe = point.add(
						forward.x * row * event.rowSpacing() - forward.z * column * event.columnSpacing(),
						0, forward.z * row * event.rowSpacing() + forward.x * column * event.columnSpacing());
				if (!located.sample().contains(probe)) {
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
		int count = HordeVisualConfig.PARTICLE_GROUP_MULTIPLIER * MidnightCleanupEncounter.SLASH_POSITION_MULTIPLIER * (MidnightCleanupEncounter.SLASH_GROUPS_MIN + random.nextInt(MidnightCleanupEncounter.SLASH_GROUPS_VARIATION));
		int start = random.nextInt(interior.size());
		for (int i = 0; i < count; i++) {
			var located = interior.get((start + i) % interior.size());
			var point = scatterPoint(located);
			if (point == null) {
				continue;
			}

			selectedAreas++;
			int particles = MidnightCleanupEncounter.SLASH_PARTICLES_MIN + random.nextInt(MidnightCleanupEncounter.SLASH_PARTICLES_VARIATION);
			for (int j = 0; j < particles; j++) {
				var slash = switch (random.nextInt(MidnightCleanupEncounter.SLASH_STYLE_COUNT)) {
					case 0 -> LcParticleTypes.SIMPLE_SHORT_SLASH.get();
					case 1 -> LcParticleTypes.SIMPLE_LONG_SLASH.get();
					default -> LcParticleTypes.SIMPLE_DOUBLE_SLASH.get();
				};
				particle(slash, point, MidnightCleanupEncounter.SLASH_SCALE_MIN + random.nextFloat() * MidnightCleanupEncounter.SLASH_SCALE_VARIATION);
			}
		}
	}

	private static Vec3 scatterPoint(LocatedSample located) {
		var level = Minecraft.getInstance().level;
		var sample = located.sample();
		var envelope = sample.envelope();
		if (level == null || envelope == null) {
			return null;
		}

		var event = located.event();
		var random = level.getRandom();
		var forward = envelope.forward();
		for (int attempt = 0; attempt < MidnightCleanupEncounter.PARTICLE_SCATTER_ATTEMPTS; attempt++) {
			double along = (random.nextDouble() * 2 - 1) * event.rowSpacing() * MidnightCleanupEncounter.PARTICLE_SCATTER_CELLS;
			double across = (random.nextDouble() * 2 - 1) * event.columnSpacing() * MidnightCleanupEncounter.PARTICLE_SCATTER_CELLS;
			var candidate = sample.ground().add(forward.x * along - forward.z * across, 0,
					forward.z * along + forward.x * across);
			double station = envelope.stationAt(candidate);
			if (station < envelope.tail() || station > envelope.head()) {
				continue;
			}

			var center = event.tide.world(station, 0);
			var direction = event.tide.forward(station);
			double lateral = -(candidate.x - center.x) * direction.z + (candidate.z - center.z) * direction.x;
			var ground = event.tide.world(station, lateral);
			if (!ground.isFinite() || !sample.contains(ground)
					|| !currentInterior(located, ground)) {
				continue;
			}

			var point = ground.add(0, MidnightCleanupEncounter.SLASH_HEIGHT, 0);
			if (visibleSpawn(located, point)) {
				return point;
			}
		}

		return null;
	}

	private static boolean activeRow(HordeClient.ClientHorde event, double time, long row) {
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
		return station >= tail && station <= head && withinParticleRange(point);
	}

	private static boolean allowed() {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		if (level == null) {
			return false;
		}

		// 出生位置核对活动波带、视距和已加载区块，设置仍逐粒筛选。
		var setting = minecraft.options.particles().get();
		return setting != ParticleStatus.MINIMAL
				&& (setting != ParticleStatus.DECREASED || level.getRandom().nextInt(MidnightCleanupEncounter.DECREASED_PARTICLE_DIVISOR) != 0);
	}

	/// 不改变粒子自身缩放；仅用于复用其它系统已有的粒子外观与行为。
	private static void particle(ParticleOptions type, Vec3 point, float scale) {
		if (!allowed()) {
			return;
		}

		var particle = Minecraft.getInstance().particleEngine.createParticle(type, point.x, point.y, point.z, 0, 0, 0);
		if (particle != null) {
			particle.scale(scale);
			emittedParticles++;
		}
	}

	public static void clear() {
		stopSounds();
		HordeGroundFog.clear();
		HordeGroundCache.clear();
		HordeGroundParticles.clear();
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

	private record LocatedSample(HordeClient.ClientHorde event, double time, HordeParticleSamples.Sample sample) {
	}

	/// 固定世界坐标的普通音效，只由现有入口持有并停止自身。
	private static class Source extends SimpleSoundInstance {
		private final int kind;
		private final long born = MidnightCleanupAmbient.tick;

		private Source(int kind, Vec3 point, RandomSource random) {
			super(MidnightCleanupEncounter.soundId(kind), SoundSource.AMBIENT,
					MidnightCleanupEncounter.voiceGain(kind), MidnightCleanupEncounter.SOUND_PITCH, random, false, 0,
					Attenuation.LINEAR, point.x, point.y, point.z, false);
			this.kind = kind;
		}

		private void end() {
			Minecraft.getInstance().getSoundManager().stop(this);
		}
	}
}
