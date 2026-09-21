package org.unitego.lobecorp.registry.entity_skill;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillGroup;
import org.unitego.lobecorp.registry.LcRegistrys;

/// 实体技能组的注册入口与内置组定义。
/// 自定义组应通过本类的 {@code register} 方法注册，以统一使用实体技能组注册表。
public interface LcEntitySkillGroups {
	/// 未显式指定上限时，自定义组和被动组使用的默认同时运行数量。
	int DEFAULT_MAXIMUM_ACTIVE_SKILLS = 5;
	/// 实体技能组的唯一延迟注册器。
	DeferredRegister<EntitySkillGroup> REGISTER = Lobecorp.register(LcRegistrys.ENTITY_SKILL_GROUP_KEY);

	/// 默认当前执行组。该组不可从实体移除，初始同时运行上限为一。
	DeferredHolder<EntitySkillGroup, EntitySkillGroup> CURRENT = register("current", 1);
	/// 默认被动组。该组不可从实体移除，使用通用默认上限。
	DeferredHolder<EntitySkillGroup, EntitySkillGroup> PASSIVE = register("passive");

	/// 使用通用默认上限注册自定义技能组。
	/// @param name 组的注册路径，不包含命名空间
	/// @return 可在注册完成后解析技能组实例的延迟持有者
	static DeferredHolder<EntitySkillGroup, EntitySkillGroup> register(String name) {
		return register(name, DEFAULT_MAXIMUM_ACTIVE_SKILLS);
	}

	/// 使用指定默认上限注册自定义技能组。
	/// @param name 组的注册路径，不包含命名空间
	/// @param defaultMaximumActiveSkills 实体首次获得该组时使用的上限，必须大于或等于零
	/// @return 可在注册完成后解析技能组实例的延迟持有者
	static DeferredHolder<EntitySkillGroup, EntitySkillGroup> register(String name, int defaultMaximumActiveSkills) {
		return REGISTER.register(name, () -> new EntitySkillGroup(defaultMaximumActiveSkills));
	}

	/// 将技能组延迟注册器绑定到模组事件总线。
	/// 每个模组初始化流程只应调用一次。
	/// @param eventBus Lobecorp 使用的模组事件总线
	static void init(IEventBus eventBus) {
		REGISTER.register(eventBus);
	}
}
