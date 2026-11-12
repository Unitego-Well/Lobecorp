package org.unitego.lobecorp.registry.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

public class EnkephalinSoundEvents {
	public static final DeferredHolder<SoundEvent, SoundEvent> OPEN = LcSoundEvents.register("enkephalin.open", "Can opens", "罐体：打开");
	public static final DeferredHolder<SoundEvent, SoundEvent> DRINK = LcSoundEvents.register("enkephalin.drink", "Drinking", "罐体：饮用");
	public static final DeferredHolder<SoundEvent, SoundEvent> DRINK_UP = LcSoundEvents.register("enkephalin.drink_up", "Can emptied", "罐体：饮尽");

	public static void init() {
	}
}
