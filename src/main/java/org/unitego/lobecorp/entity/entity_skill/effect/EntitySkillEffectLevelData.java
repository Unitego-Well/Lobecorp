package org.unitego.lobecorp.entity.entity_skill.effect;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/// 单个 Level 中未持久化的实体技能效果实例。
public class EntitySkillEffectLevelData {
	private final Set<EntitySkillEffect> effects = new LinkedHashSet<>();

	boolean add(EntitySkillEffect effect) {
		return effects.add(effect);
	}

	boolean remove(EntitySkillEffect effect) {
		return effects.remove(effect);
	}

	Collection<EntitySkillEffect> effects() {
		return effects;
	}
}
