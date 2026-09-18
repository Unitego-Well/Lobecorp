package org.unitego.lobecorp.entity.entity_skill;

import java.util.Collection;

/// 技能持有者：拥有一个技能集的实体。
/// <p>
/// 实体实现此接口并提供技能集，同时将 {@link EntitySkillController} 注册进 brain 的
/// {@code Activity.CORE} 使其常驻驱动状态机。
public interface EntitySkillHolder {

	/// 该实体可用的所有技能
	Collection<? extends IEntitySkill<?>> skills();
}
