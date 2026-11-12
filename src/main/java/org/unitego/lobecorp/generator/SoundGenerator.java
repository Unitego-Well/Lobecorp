package org.unitego.lobecorp.generator;

import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.common.data.SoundDefinition;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.sound.SweeperSoundEvents;
import org.unitego.lobecorp.registry.sound.EnkephalinSoundEvents;
import org.unitego.lobecorp.registry.sound.MidnightCleanupSoundEvents;

public class SoundGenerator extends SoundDefinitionsProvider {
	public SoundGenerator(PackOutput output) {
		super(output, Lobecorp.NAMESPACE);
	}

	@Override
	public void registerSounds() {
		add(EnkephalinSoundEvents.OPEN, definition().with(sound(Lobecorp.id("enkephalin/open")).volume(0.5).pitch(0.5).attenuationDistance(8)));
		add(EnkephalinSoundEvents.DRINK, definition().with(
				sound(Lobecorp.id("enkephalin/drink")).volume(0.5).pitch(0.5).attenuationDistance(8),
				sound(Lobecorp.id("enkephalin/drink1")).volume(0.5).pitch(0.5).attenuationDistance(8)));
		add(EnkephalinSoundEvents.DRINK_UP, definition().with(sound(Lobecorp.id("enkephalin/drink_up")).volume(0.5).pitch(0.5).attenuationDistance(8)));
		music(MidnightCleanupSoundEvents.START, "start");
		music(MidnightCleanupSoundEvents.LOOP, "loop");
		music(MidnightCleanupSoundEvents.END, "end");
		sweeper(SweeperSoundEvents.SWEEPER_MOVE, "move", 12, 32, 256);
		sweeper(SweeperSoundEvents.SWEEPER_ATTACK, "attack", 4, 48, 160);
		sweeper(SweeperSoundEvents.SWEEPER_CLEAR_START, "clear_start", 2, 32, 96);
		sweeper(SweeperSoundEvents.SWEEPER_CLEAR_LOOP, "clear_loop", 3, 32, 128);
		sweeper(SweeperSoundEvents.SWEEPER_CLEAR_END, "clear_end", 1, 48, 160);
		sweeper(SweeperSoundEvents.SWEEPER_JUMP, "jump", 2, 48, 96);
		sweeper(SweeperSoundEvents.SWEEPER_LAND, "land", 2, 64, 192);
	}

	private void music(Holder<SoundEvent> event, String name) {
		add(event, definition().with(sound(Lobecorp.id("encounter/midnight_cleanup/" + name)).stream()));
	}

	private void sweeper(Holder<SoundEvent> event, String name, int variants, int attenuationDistance, int cleanupAttenuationDistance) {
		add(event, variants(name, variants, attenuationDistance));
		// 深夜清扫按客户端资源 ID 播放，不新增运行时 SoundEvent 注册。
		add(Lobecorp.id("event.midnight_cleanup." + name), variants(name, variants, cleanupAttenuationDistance));
	}

	private SoundDefinition variants(String name, int count, int attenuationDistance) {
		var definition = definition();
		for (var index = 1; index <= count; index++) {
			definition.with(sound(Lobecorp.id("sweeper/" + name + index)).attenuationDistance(attenuationDistance));
		}

		return definition;
	}
}
