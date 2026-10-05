package org.unitego.lobecorp.world.entity.skill;

import com.mojang.serialization.Codec;
import org.unitego.lobecorp.registry.LcRegistrys;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/// 实体技能的持久化修正；未修正的技能实时继承实体类型的默认定义。
public record EntitySkillState(Map<IEntitySkill<?>, Boolean> overrides) {
	/// true 表示显式添加，false 表示显式移除；默认技能本身不写入附件。
	public static final Codec<EntitySkillState> CODEC = Codec.dispatchedMap(
					LcRegistrys.ENTITY_SKILL.byNameCodec(), ignored -> Codec.BOOL)
			.xmap(EntitySkillState::new, EntitySkillState::overrides);

	public EntitySkillState {
		overrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
	}

	public static EntitySkillState empty() {
		return new EntitySkillState(Map.of());
	}

	/// 合并默认定义与本实体修正，保留默认技能顺序。
	public Set<IEntitySkill<?>> apply(Set<IEntitySkill<?>> defaults) {
		Set<IEntitySkill<?>> skills = new LinkedHashSet<>(defaults);
		overrides.forEach((skill, owned) -> {
			if (owned)
				skills.add(skill);
			else
				skills.remove(skill);
		});
		return Collections.unmodifiableSet(skills);
	}

	/// 与默认值一致时清除修正，恢复继承默认定义。
	public EntitySkillState with(IEntitySkill<?> skill, boolean owned, boolean defaultOwned) {
		Map<IEntitySkill<?>, Boolean> next = new LinkedHashMap<>(overrides);
		if (owned == defaultOwned)
			next.remove(skill);
		else
			next.put(skill, owned);
		return new EntitySkillState(next);
	}
}
