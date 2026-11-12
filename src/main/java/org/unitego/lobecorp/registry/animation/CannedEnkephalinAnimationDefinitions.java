package org.unitego.lobecorp.registry.animation;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.definition.LcAnimationDefinition;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.runtime.LcAnimationInstance;
import org.unitego.lobecorp.registry.sound.EnkephalinSoundEvents;
import org.unitego.lobecorp.world.item.animation.CannedEnkephalinAnimation;

import java.util.Set;

public class CannedEnkephalinAnimationDefinitions {
	public static final Identifier RIGHT = Lobecorp.id("item/canned_enkephalin");
	public static final Identifier LEFT = Lobecorp.id("item/canned_enkephalin_left");

	public static void init() {
		LcAnimationDefinitions.register(RIGHT, () -> create(RIGHT));
		LcAnimationDefinitions.register(LEFT, () -> create(LEFT));
	}

	private static LcAnimationDefinition create(Identifier resource) {
		return LcAnimationDefinition.wrap(RIGHT, resource, "canned_enkephalin")
				.playerAnimation(LcPlayerAnimationPolicy.fullOverride().withBones(Set.of("head", "right_arm", "left_arm"))
						.withFirstPersonArms(true))
				.onTick(CannedEnkephalinAnimation::tick).onStop(CannedEnkephalinAnimation::finish)
				.at(0.625, instance -> sound(instance, EnkephalinSoundEvents.OPEN.get()))
				.at(1.7083, instance -> sound(instance, EnkephalinSoundEvents.DRINK.get()))
				.at(1.9583, instance -> sound(instance, EnkephalinSoundEvents.DRINK.get()))
				.at(2.2083, instance -> sound(instance, EnkephalinSoundEvents.DRINK.get()))
				.at(2.5417, instance -> sound(instance, EnkephalinSoundEvents.DRINK_UP.get()))
				.at(3.2917, instance -> sound(instance, SoundEvents.PLAYER_BURP)).build();
	}

	private static void sound(LcAnimationInstance instance, SoundEvent sound) {
		var entity = instance.sourceEntity();
		if (entity != null) {
			instance.level().playSound(null, entity.blockPosition(), sound, SoundSource.PLAYERS, 1, 1);
		}
	}
}
