package org.unitego.lobecorp.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.sound.SweeperSoundEvents;

/// 通用声音注册入口；只登记延迟持有者，不在加载期取实例或播放。
public interface LcSoundEvents {
	DeferredRegister<SoundEvent> REGISTER = Lobecorp.register(BuiltInRegistries.SOUND_EVENT);

	public static DeferredHolder<SoundEvent, SoundEvent> register(String path, float range) {
		return REGISTER.register(path, () -> SoundEvent.createFixedRangeEvent(Lobecorp.id(path), range));
	}

	static void init(IEventBus bus) {
		SweeperSoundEvents.init();
		REGISTER.register(bus);
	}
}
