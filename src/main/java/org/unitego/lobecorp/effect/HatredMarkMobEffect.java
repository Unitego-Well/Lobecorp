package org.unitego.lobecorp.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

/// 使目标在伤害处理开始前承受额外伤害的憎恶标记。
public class HatredMarkMobEffect extends MobEffect {
	/// 每级憎恶标记增加的承伤倍率。
	public static final double DAMAGE_TAKEN_INCREASE_PER_LEVEL = 0.15;

	/// @param category 状态效果分类
	/// @param color 状态粒子颜色
	public HatredMarkMobEffect(MobEffectCategory category, int color) {
		super(category, color);
	}

	/// 状态效果属性仅在效果存续期间生效，不写入实体的永久属性数据。
	@Override
	public void addAttributeModifiers(AttributeMap attributes, int amplifier) {
		createModifiers(amplifier, (attribute, modifier) -> {
			AttributeInstance instance = attributes.getInstance(attribute);
			if (instance != null) {
				instance.removeModifier(modifier.id());
				instance.addTransientModifier(modifier);
			}
		});
	}
}
