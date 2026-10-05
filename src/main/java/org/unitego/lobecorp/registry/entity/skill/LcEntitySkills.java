package org.unitego.lobecorp.registry.entity.skill;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.world.entity.skill.EntitySkill;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;
import org.unitego.lobecorp.generator.lang.LangHandler;
import org.unitego.lobecorp.registry.LcRegistrys;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public interface LcEntitySkills {
	DeferredRegister<IEntitySkill<?>> REGISTER = Lobecorp.register(LcRegistrys.ENTITY_SKILL_KEY);

	static void init(IEventBus iEventBus) {
		LcEntitySkillGroups.init(iEventBus);
		SweeperSkills.init();
		TheQueenOfHatredSkills.init();
		EntitySkillDefaults.init();
		REGISTER.register(iEventBus);
	}

	static <T extends IEntitySkill<?>> DeferredHolder<IEntitySkill<?>, T> register(
			String name, String enUs, String zhCn,
			Function<EntitySkill.Properties, T> factory, UnaryOperator<EntitySkill.Properties> properties
	) {
		return register(REGISTER, name, enUs, zhCn, factory, properties);
	}

	static <T extends IEntitySkill<?>> DeferredHolder<IEntitySkill<?>, T> register(
			DeferredRegister<IEntitySkill<?>> register,
			String name, String enUs, String zhCn,
			Function<EntitySkill.Properties, T> factory, UnaryOperator<EntitySkill.Properties> properties
	) {
		DeferredHolder<IEntitySkill<?>, T> holder = register.register(name,
				id -> factory.apply(properties.apply(new EntitySkill.Properties()).id(id)));
		String key = LangHandler.translationKey("entity_skill", name);
		LangHandler.creates(register.getNamespace(), key, enUs, zhCn);
		return holder;
	}
}
