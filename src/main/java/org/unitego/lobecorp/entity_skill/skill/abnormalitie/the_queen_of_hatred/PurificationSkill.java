package org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred;

import net.minecraft.server.level.ServerLevel;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity_skill.EntitySkill;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.util.QueenSkillUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.unitego.lobecorp.registry.entity.LcAttributes;

import java.util.List;

/// 仅在需要时移除负面效果，以及按属性增益方向判定的有害修饰符。
public class PurificationSkill extends EntitySkill<TheQueenOfHatred> {
	public PurificationSkill(Properties properties) {
		super(properties);
	}

	private static boolean harmful(Attribute attribute, AttributeModifier modifier) {
		if (modifier.amount() == 0.0) return false;
		boolean increase = modifier.amount() > 0.0;
		// 自定义倍率属性目前没有负向 sentiment，按其实际业务含义判断。
		if (attribute == LcAttributes.DAMAGE_TAKEN_MULTIPLIER.get()
				|| attribute == LcAttributes.ENTITY_SKILL_COOLDOWN_MULTIPLIER.get()) return increase;
		return attribute.getStyle(increase) == ChatFormatting.RED;
	}

	public static boolean needsPurification(TheQueenOfHatred queen) {
		if (queen.getActiveEffects().stream().anyMatch(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)) return true;
		return BuiltInRegistries.ATTRIBUTE.listElements().anyMatch(holder -> {
			if (!queen.getAttributes().hasAttribute(holder)) return false;
			var instance = queen.getAttribute(holder);
			return instance != null && instance.getModifiers().stream().anyMatch(modifier -> harmful(holder.value(), modifier));
		});
	}

	@Override
	public boolean canUse(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return needsPurification(queen);
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(queen.level() instanceof ServerLevel)) return;
		boolean needed = needsPurification(queen);
		for (var effect : List.copyOf(queen.getActiveEffects())) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) queen.removeEffect(effect.getEffect());
		}
		BuiltInRegistries.ATTRIBUTE.listElements().forEach(holder -> {
			if (!queen.getAttributes().hasAttribute(holder)) return;
			var instance = queen.getAttribute(holder);
			if (instance == null) return;
			for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
				if (harmful(holder.value(), modifier)) instance.removeModifier(modifier);
			}
		});
		if (needed) runtime.markSuccessful();
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		QueenSkillUtil.startSpell(queen);
	}

	@Override
	public void onWindupTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
	}

	@Override
	public void onTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
	}

	@Override
	public void onEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
	}

	@Override
	public void onCancel(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
	}
}

