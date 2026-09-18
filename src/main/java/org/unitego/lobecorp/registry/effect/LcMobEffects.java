package org.unitego.lobecorp.registry.effect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.effect.StunMobEffect;
import org.unitego.lobecorp.generator.lang.LangHandler;

public interface LcMobEffects {
	DeferredRegister<MobEffect> REGISTER = Lobecorp.register(BuiltInRegistries.MOB_EFFECT);

	/// 眩晕效果默认颜色
	int STUN_COLOR = 0xFFFFFF;
	/// 每级眩晕降低的移动速度比例
	double STUN_MOVEMENT_SPEED_PER_LEVEL = -0.5;
	/// 眩晕最多降低的移动速度比例
	double MAX_STUN_MOVEMENT_SPEED_REDUCTION = -1.0;
	/// 眩晕移动速度属性修饰的唯一标识
	Identifier STUN_MOVEMENT_SPEED_MODIFIER = Lobecorp.id("effect.stun_movement_speed");

	DeferredHolder<MobEffect, StunMobEffect> STUN = register();

	private static DeferredHolder<MobEffect, StunMobEffect> register() {
		DeferredHolder<MobEffect, StunMobEffect> holder = REGISTER.register("stun", () -> {
			StunMobEffect effect = new StunMobEffect(MobEffectCategory.HARMFUL, STUN_COLOR);
			effect.addAttributeModifier(Attributes.MOVEMENT_SPEED, STUN_MOVEMENT_SPEED_MODIFIER,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
					amplifier -> Math.max(MAX_STUN_MOVEMENT_SPEED_REDUCTION,
							STUN_MOVEMENT_SPEED_PER_LEVEL * (amplifier + 1)));
			return effect;
		});
		LangHandler.creates(REGISTER, "Stun", "眩晕", (langSet, txt) -> langSet.mobEffectText(holder, txt));
		return holder;
	}

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}
}
