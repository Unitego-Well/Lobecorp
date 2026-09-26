package org.unitego.lobecorp.registry.entity_skill;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredRepelSkill;
import org.unitego.lobecorp.registry.LcRegistrys;

public interface TheQueenOfHatredSkills {
	DeferredRegister<IEntitySkill<?>> REGISTER = Lobecorp.register(LcRegistrys.ENTITY_SKILL_KEY);

	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredRepelSkill> REPEL = LcEntitySkills.register(REGISTER,
			"the_queen_of_hatred_repel", "Repel", "退散",
			TheQueenOfHatredRepelSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(44)
					.durationTicks(0)
					.recoveryTicks(10)
					.cooldownTicks(0));

	static void init(IEventBus eventBus) {
		REGISTER.register(eventBus);
	}
}
