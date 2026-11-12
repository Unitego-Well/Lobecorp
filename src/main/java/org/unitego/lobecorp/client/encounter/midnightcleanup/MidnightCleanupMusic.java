package org.unitego.lobecorp.client.encounter.midnightcleanup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.unitego.lobecorp.registry.sound.MidnightCleanupSoundEvents;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/// 同时存在多个清扫遭遇时共用一条音乐，最后一个完成后才播放尾奏。
public class MidnightCleanupMusic {
	private static final Set<UUID> encounters = new HashSet<>();
	private static Phase phase;
	private static MusicSound sound;
	private static MusicSound fadingSound;

	public static void start(UUID instance) {
		if (!encounters.add(instance) || encounters.size() > 1) {
			return;
		}

		Minecraft.getInstance().getMusicManager().stopPlaying();
		play(Phase.START);
	}

	public static void finish(UUID instance) {
		if (encounters.remove(instance) && encounters.isEmpty()) {
			stopFading();
			fadingSound = sound;
			if (fadingSound != null) {
				fadingSound.fadeOut();
			}
			sound = null;
			play(Phase.END);
		}
	}

	public static void remove(UUID instance) {
		if (encounters.remove(instance) && encounters.isEmpty()) {
			clear();
		}
	}

	public static boolean active() {
		return phase != null;
	}

	public static void tick() {
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			clear();
			return;
		}

		if (fadingSound != null && (fadingSound.isStopped()
				|| !minecraft.getSoundManager().isActive(fadingSound))) {
			stopFading();
		}

		if (!minecraft.isPaused() && phase == Phase.LOOP && sound != null
				&& sound.age >= MidnightCleanupEncounter.MUSIC_LOOP_RESTART_TICKS
				&& minecraft.getSoundManager().isActive(sound)) {
			stopFading();
			fadingSound = sound;
			fadingSound.fadeOut();
			sound = null;
			play(Phase.LOOP);
			return;
		}

		if (minecraft.isPaused() || sound == null
				|| !sound.isStopped() && minecraft.getSoundManager().isActive(sound)) {
			return;
		}

		switch (phase) {
			case START, LOOP -> play(Phase.LOOP);
			case END -> clear();
		}
	}

	public static void clear() {
		if (sound != null) {
			Minecraft.getInstance().getSoundManager().stop(sound);
		}

		stopFading();
		sound = null;
		phase = null;
		encounters.clear();
	}

	private static void stopFading() {
		if (fadingSound != null) {
			Minecraft.getInstance().getSoundManager().stop(fadingSound);
			fadingSound = null;
		}
	}

	private static void play(Phase next) {
		if (next == Phase.START) {
			stopFading();
		}

		var manager = Minecraft.getInstance().getSoundManager();
		if (sound != null) {
			manager.stop(sound);
		}

		SoundEvent event = switch (next) {
			case START -> MidnightCleanupSoundEvents.START.get();
			case LOOP -> MidnightCleanupSoundEvents.LOOP.get();
			case END -> MidnightCleanupSoundEvents.END.get();
		};
		phase = next;
		// 每轮第47秒启动下一轮，两个独立声源交叉淡化1秒。
		sound = new MusicSound(event, next == Phase.END || next == Phase.LOOP && fadingSound != null);
		manager.play(sound);
	}

	private enum Phase {
		START, LOOP, END
	}

	private static class MusicSound extends AbstractTickableSoundInstance {
		private int fadeRemaining;
		private int age;
		private float fadeStartVolume;
		private boolean fadingOut;

		private MusicSound(SoundEvent event, boolean fadeIn) {
			super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			looping = false;
			delay = 0;
			attenuation = SoundInstance.Attenuation.NONE;
			relative = true;
			volume = fadeIn ? 0 : 1;
			fadeRemaining = fadeIn ? MidnightCleanupEncounter.MUSIC_FADE_OUT_TICKS : 0;
			pitch = 1;
		}

		private void fadeOut() {
			fadeStartVolume = volume;
			fadingOut = true;
			fadeRemaining = MidnightCleanupEncounter.MUSIC_FADE_OUT_TICKS;
		}

		@Override
		public void tick() {
			age++;
			if (fadeRemaining <= 0) {
				return;
			}

			float remaining = (float) --fadeRemaining / MidnightCleanupEncounter.MUSIC_FADE_OUT_TICKS;
			volume = fadingOut ? fadeStartVolume * remaining : 1 - remaining;
			if (fadingOut && fadeRemaining == 0) {
				stop();
			}
		}
	}
}
