package org.unitego.lobecorp.generator.lang;

import net.minecraft.data.PackOutput;
import org.unitego.lobecorp.Lobecorp;

public class ZhCnLangGenerator extends BasicLangGenerator {
	public ZhCnLangGenerator(PackOutput output) {
		super(output, Lobecorp.NAMESPACE, LangHandler.ZH_CN);
	}

	@Override
	public void addTranslations() {
		super.addTranslations();
		addPackDescription(Lobecorp.NAMESPACE, "脑叶公司");
		add(LangHandler.translationKey("debug_entry", "skill_effect_entities"), "技能实体");
		add(LangHandler.translationKey("debug.lobecorp.skill_effect", "name"), "名称：{0}");
		add(LangHandler.translationKey("debug.lobecorp.skill_effect", "owner"), "所属者：{0}");
		add(LangHandler.translationKey("debug.lobecorp.skill_effect", "skill"), "所属技能：{0}");
		add(LangHandler.translationKey("debug.lobecorp.skill_effect", "position"), "位置：{0}");
		add(LangHandler.translationKey("debug.lobecorp.skill_effect", "lifetime"), "生命周期：{0}");
		add(LangHandler.translationKey("debug.lobecorp.skill_effect", "hitbox"), "命中框：{0}");
	}
}
