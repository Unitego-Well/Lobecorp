package org.unitego.lobecorp.registry.effect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.world.effect.BasicMobEffect;
import org.unitego.lobecorp.world.effect.StunMobEffect;
import org.unitego.lobecorp.generator.lang.LangHandler;
import org.unitego.lobecorp.registry.entity.LcAttributes;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public interface LcMobEffects {
	DeferredRegister<MobEffect> REGISTER = Lobecorp.register(BuiltInRegistries.MOB_EFFECT);
	/// 标记使伤害处理前的受伤倍率增加 50%，不随效果等级叠加。
	double MARK_DAMAGE_MULTIPLIER = 0.5;
	/// 减伤效果将既有受伤倍率乘以 50%。
	double REDUCTION_DAMAGE_MULTIPLIER = -0.5;

	DeferredHolder<MobEffect, StunMobEffect> STUN = register("stun", "Stun", "眩晕", MobEffectCategory.HARMFUL, 0xFFFFFF,
			StunMobEffect::new, s -> s
					.addAttributeModifier(Attributes.MOVEMENT_SPEED, Lobecorp.id("effect.stun_movement_speed"),
							Operation.ADD_MULTIPLIED_TOTAL, amplifier -> Math.max(-1.0, -0.5 * (amplifier + 1))));

	DeferredHolder<MobEffect, BasicMobEffect> HATRED_MARK = register("hatred_mark", "Hatred Mark", "憎恶标记", MobEffectCategory.HARMFUL, 0xFF69B4, effect -> effect
			.addAttributeModifier(LcAttributes.DAMAGE_TAKEN_MULTIPLIER, Lobecorp.id("effect.hatred_mark_damage_taken"),
					Operation.ADD_MULTIPLIED_TOTAL, amplifier -> MARK_DAMAGE_MULTIPLIER));

	DeferredHolder<MobEffect, BasicMobEffect> QUEEN_DAMAGE_REDUCTION = register("queen_damage_reduction", "Damage Reduction", "减伤",
			MobEffectCategory.BENEFICIAL, 0xFF69B4, effect -> effect
					.addAttributeModifier(LcAttributes.DAMAGE_TAKEN_MULTIPLIER, Lobecorp.id("effect.queen_damage_reduction"),
							Operation.ADD_MULTIPLIED_TOTAL, amplifier -> REDUCTION_DAMAGE_MULTIPLIER));

	private static <T extends MobEffect> DeferredHolder<MobEffect, T> register(
			String id, String en, String zh,
			MobEffectCategory category, int color,
			BiFunction<MobEffectCategory, Integer, T> function, UnaryOperator<MobEffect> unaryOperator
	) {
		DeferredHolder<MobEffect, T> holder = REGISTER.register(id, () -> (T) unaryOperator.apply(function.apply(category, color)));
		LangHandler.creates(REGISTER, en, zh, (langSet, txt) -> langSet.mobEffectText(holder, txt));
		return holder;
	}

	private static DeferredHolder<MobEffect, BasicMobEffect> register(
			String id, String en, String zh,
			MobEffectCategory category, int color,
			UnaryOperator<MobEffect> unaryOperator
	) {
		return register(id, en, zh, category, color, BasicMobEffect::new, unaryOperator);
	}

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}
}
