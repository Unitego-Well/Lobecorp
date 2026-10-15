package org.unitego.lobecorp.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.world.entity.skill.EntitySkillGroup;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;

public interface LcRegistrys {
	/// 实体技能自定义注册表的稳定资源键。
	ResourceKey<Registry<IEntitySkill<?>>> ENTITY_SKILL_KEY = ResourceKey.createRegistryKey(Lobecorp.id("entity_skill"));
	/// 实体技能自定义注册表，注册标识供存档和同步解析。
	Registry<IEntitySkill<?>> ENTITY_SKILL = new RegistryBuilder<>(ENTITY_SKILL_KEY).sync(true).create();
	/// 实体技能分组自定义注册表的稳定资源键。
	ResourceKey<Registry<EntitySkillGroup>> ENTITY_SKILL_GROUP_KEY = ResourceKey.createRegistryKey(Lobecorp.id("entity_skill_group"));
	/// 实体技能分组自定义注册表，注册标识供存档和同步解析。
	Registry<EntitySkillGroup> ENTITY_SKILL_GROUP = new RegistryBuilder<>(ENTITY_SKILL_GROUP_KEY).sync(true).create();

	static void registerRegistry(NewRegistryEvent event) {
		event.register(ENTITY_SKILL_GROUP);
		event.register(ENTITY_SKILL);
	}
}
