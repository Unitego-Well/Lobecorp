package org.unitego.lobecorp.generator.lang;

import net.minecraft.data.PackOutput;
import org.unitego.lobecorp.Lobecorp;

public class EnUsLangGenerator extends BasicLangGenerator {
	public EnUsLangGenerator(PackOutput output) {
		super(output, Lobecorp.NAMESPACE, LangHandler.EN_US);
	}

	@Override
	public void addTranslations() {
		super.addTranslations();
		addPackDescription(Lobecorp.NAMESPACE, "Lobotomy Corporation");
		add(LangHandler.translationKey("debug_entry", "skill_effect_entities"), "Skill Entities");
		add(LangHandler.translationKey("debug.lobecorp.skill_effect", "name"), "Name: {0}");
		add(LangHandler.translationKey("debug.lobecorp.skill_effect", "owner"), "Owner: {0}");
		add(LangHandler.translationKey("debug.lobecorp.skill_effect", "skill"), "Skill: {0}");
		add(LangHandler.translationKey("debug.lobecorp.skill_effect", "position"), "Position: {0}");
		add(LangHandler.translationKey("debug.lobecorp.skill_effect", "lifetime"), "Lifetime: {0}");
		add(LangHandler.translationKey("debug.lobecorp.skill_effect", "hitbox"), "Hitbox: {0}");
	}
}
