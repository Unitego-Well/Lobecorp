package org.unitego.lobecorp.entity.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;

/// 清除生物负面状态和有害属性修饰符的通用工具。
public final class EntityPurificationUtil {
	private EntityPurificationUtil() {
	}

	/// 清除实体全部负面状态效果及有害属性修饰符。
	/// <p>
	/// 属性修饰符是否有害由属性自身的原版倾向决定：正面属性上的负数修饰符和负面属性上的
	/// 正数修饰符会被移除，中性属性、零值修饰符和属性基础值不会改变。该方法同时处理临时
	/// 与持久属性修饰符。
	///
	/// @param entity 要净化的生物
	public static void purify(LivingEntity entity) {
		removeHarmfulEffects(entity);
		removeDetrimentalAttributeModifiers(entity);
	}

	/// 检查实体当前是否存在可由 {@link #purify(LivingEntity)} 清除的状态。
	/// 该检查不会修改状态效果、属性基础值或属性修饰符。
	///
	/// @param entity 要检查的生物
	/// @return 存在负面状态效果或有害属性修饰符时返回 {@code true}
	public static boolean needsPurification(LivingEntity entity) {
		for (MobEffectInstance effect : entity.getActiveEffects()) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				return true;
			}
		}
		for (Holder.Reference<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.listElements().toList()) {
			AttributeInstance instance = entity.getAttribute(attribute);
			if (instance == null) {
				continue;
			}
			for (AttributeModifier modifier : instance.getModifiers()) {
				if (isDetrimental(attribute.value(), modifier)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void removeHarmfulEffects(LivingEntity entity) {
		for (MobEffectInstance effect : List.copyOf(entity.getActiveEffects())) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				entity.removeEffect(effect.getEffect());
			}
		}
	}

	private static void removeDetrimentalAttributeModifiers(LivingEntity entity) {
		for (Holder.Reference<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.listElements().toList()) {
			AttributeInstance instance = entity.getAttribute(attribute);
			if (instance == null) {
				continue;
			}
			for (AttributeModifier modifier : instance.getModifiers()) {
				if (isDetrimental(attribute.value(), modifier)) {
					instance.removeModifier(modifier);
				}
			}
		}
	}

	private static boolean isDetrimental(Attribute attribute, AttributeModifier modifier) {
		if (modifier.amount() == 0.0) {
			return false;
		}
		return attribute.getStyle(modifier.amount() > 0.0) == ChatFormatting.RED;
	}
}
