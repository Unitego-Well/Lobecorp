package org.unitego.lobecorp.registry.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

public interface SweeperSoundEvents {
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_MOVE = LcSoundEvents.register("entity.sweeper.move", "Sweeper moves", "清道夫移动", 32);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_ATTACK = LcSoundEvents.register("entity.sweeper.attack", "Sweeper attacks", "清道夫攻击", 48);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_CLEAR_START = LcSoundEvents.register("entity.sweeper.clear_start", "Sweeper begins cleaning", "清道夫开始清扫", 32);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_CLEAR_LOOP = LcSoundEvents.register("entity.sweeper.clear_loop", "Sweeper cleans", "清道夫清扫", 32);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_CLEAR_END = LcSoundEvents.register("entity.sweeper.clear_end", "Sweeper finishes cleaning", "清道夫结束清扫", 48);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_JUMP = LcSoundEvents.register("entity.sweeper.jump", "Sweeper jumps", "清道夫跃起", 48);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_LAND = LcSoundEvents.register("entity.sweeper.land", "Sweeper lands", "清道夫落地", 64);

	/// 加载声明以登记延迟持有者，不在注册阶段取得声音实例。
	static void init() {
	}
}
