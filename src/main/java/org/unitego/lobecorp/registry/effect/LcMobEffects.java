package org.unitego.lobecorp.registry.effect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.effect.HatredMarkMobEffect;
import org.unitego.lobecorp.effect.StunMobEffect;
import org.unitego.lobecorp.generator.lang.LangHandler;
import org.unitego.lobecorp.registry.entity.LcAttributes;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public interface LcMobEffects {
	DeferredRegister<MobEffect> REGISTER = Lobecorp.register(BuiltInRegistries.MOB_EFFECT);

	DeferredHolder<MobEffect, StunMobEffect> STUN = register("stun", "Stun", "眩晕", MobEffectCategory.HARMFUL, 0xFFFFFF,
			StunMobEffect::new, s -> s
					.addAttributeModifier(Attributes.MOVEMENT_SPEED, Lobecorp.id("effect.stun_movement_speed"),
							Operation.ADD_MULTIPLIED_TOTAL, amplifier -> Math.max(-1.0, -0.5 * (amplifier + 1))));

	DeferredHolder<MobEffect, HatredMarkMobEffect> HATRED_MARK = register("hatred_mark", "Hatred Mark", "憎恶标记", MobEffectCategory.HARMFUL, 0xFF69B4,
			HatredMarkMobEffect::new, effect -> effect.addAttributeModifier(
					LcAttributes.DAMAGE_TAKEN_MULTIPLIER, Lobecorp.id("effect.hatred_mark_damage_taken"),
					Operation.ADD_VALUE,
					amplifier -> HatredMarkMobEffect.DAMAGE_TAKEN_INCREASE_PER_LEVEL * (amplifier + 1)));

	private static <T extends MobEffect> DeferredHolder<MobEffect, T> register(
			String id, String en, String zh,
			MobEffectCategory category, int color,
			BiFunction<MobEffectCategory, Integer, T> function, UnaryOperator<MobEffect> unaryOperator
	) {
		DeferredHolder<MobEffect, T> holder = REGISTER.register(id, () -> (T) unaryOperator.apply(function.apply(category, color)));
		LangHandler.creates(REGISTER, en, zh, (langSet, txt) -> langSet.mobEffectText(holder, txt));
		return holder;
	}

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}
}
