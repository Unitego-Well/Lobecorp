package org.unitego.lobecorp.registry.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.unitego.lobecorp.registry.LcSoundEvents;

public interface SweeperSoundEvents {
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_MOVE = LcSoundEvents.register("entity.sweeper.move", 32);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_ATTACK = LcSoundEvents.register("entity.sweeper.attack", 48);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_CLEAR_START = LcSoundEvents.register("entity.sweeper.clear_start", 32);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_CLEAR_LOOP = LcSoundEvents.register("entity.sweeper.clear_loop", 32);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_CLEAR_END = LcSoundEvents.register("entity.sweeper.clear_end", 48);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_JUMP = LcSoundEvents.register("entity.sweeper.jump", 48);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_LAND = LcSoundEvents.register("entity.sweeper.land", 64);

	/// 加载声明以登记延迟持有者，不在注册阶段取得声音实例。
	static void init() {
	}
}
