package org.unitego.lobecorp.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;

/// 清道夫共用资源事件；只注册，不在加载期取实例或播放。
public interface LcSoundEvents {
	DeferredRegister<SoundEvent> REGISTER = Lobecorp.register(BuiltInRegistries.SOUND_EVENT);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_MOVE = register("move", 32);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_ATTACK = register("attack", 48);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_CLEAR_START = register("clear_start", 32);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_CLEAR_LOOP = register("clear_loop", 32);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_CLEAR_END = register("clear_end", 48);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_JUMP = register("jump", 48);
	DeferredHolder<SoundEvent, SoundEvent> SWEEPER_LAND = register("land", 64);

	private static DeferredHolder<SoundEvent, SoundEvent> register(String name, float range) {
		String path = "entity.sweeper." + name;
		return REGISTER.register(path, () -> SoundEvent.createFixedRangeEvent(Lobecorp.id(path), range));
	}

	static void init(IEventBus bus) {
		REGISTER.register(bus);
	}
}
