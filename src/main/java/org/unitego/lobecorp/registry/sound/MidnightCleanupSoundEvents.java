package org.unitego.lobecorp.registry.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

public interface MidnightCleanupSoundEvents {
	DeferredHolder<SoundEvent, SoundEvent> START = LcSoundEvents.register("encounter.midnight_cleanup.start", "Encounter / Midnight Cleanup / Start", "遭遇／深夜清扫／开始");
	DeferredHolder<SoundEvent, SoundEvent> LOOP = LcSoundEvents.register("encounter.midnight_cleanup.loop", "Encounter / Midnight Cleanup / Loop", "遭遇／深夜清扫／持续");
	DeferredHolder<SoundEvent, SoundEvent> END = LcSoundEvents.register("encounter.midnight_cleanup.end", "Encounter / Midnight Cleanup / End", "遭遇／深夜清扫／结束");

	static void init() {
	}
}
