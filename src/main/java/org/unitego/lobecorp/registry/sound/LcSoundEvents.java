package org.unitego.lobecorp.registry.sound;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.translation.LangHandler;

/// 通用声音注册入口；只登记延迟持有者，不在加载期取实例或播放。
public interface LcSoundEvents {
	DeferredRegister<SoundEvent> REGISTER = Lobecorp.register(BuiltInRegistries.SOUND_EVENT);

	static DeferredHolder<SoundEvent, SoundEvent> register(String path, String enUs, String zhCn, float range) {
		var holder = REGISTER.register(path, () -> SoundEvent.createFixedRangeEvent(Lobecorp.id(path), range));
		LangHandler.creates(REGISTER, enUs, zhCn, (langSet, text) -> langSet.soundEventText(holder, text));
		return holder;
	}

	static DeferredHolder<SoundEvent, SoundEvent> register(String path, String enUs, String zhCn) {
		var holder = REGISTER.register(path, () -> SoundEvent.createVariableRangeEvent(Lobecorp.id(path)));
		LangHandler.creates(REGISTER, enUs, zhCn, (langSet, text) -> langSet.soundEventText(holder, text));
		return holder;
	}

	static void init(IEventBus bus) {
		SweeperSoundEvents.init();
		MidnightCleanupSoundEvents.init();
		EnkephalinSoundEvents.init();
		REGISTER.register(bus);
	}
}
