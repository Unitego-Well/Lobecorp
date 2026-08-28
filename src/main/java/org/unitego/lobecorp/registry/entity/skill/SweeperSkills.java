package org.unitego.lobecorp.registry.entity.skill;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.ai.skill.IEntitySkill;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperAttackSkill;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperLeapSkill;
import org.unitego.lobecorp.registry.LcRegistrys;

public interface SweeperSkills {
    DeferredRegister<IEntitySkill> REGISTER = Lobecorp.register(LcRegistrys.ENTITY_SKILL_KEY);
    DeferredHolder<IEntitySkill, SweeperAttackSkill> ATTACK = LcEntitySkills.register(REGISTER, "sweeper_attack", SweeperAttackSkill::new, p -> p
            .windupTicks(6)
            .durationTicks(0)
            .recoveryTicks(14)
            .cooldownTicks(0));
    DeferredHolder<IEntitySkill, SweeperLeapSkill> LEAP = LcEntitySkills.register(REGISTER, "sweeper_leap", SweeperLeapSkill::new, p -> p
            .windupTicks(5)
            .durationTicks(8)
            .recoveryTicks(10)
            .cooldownTicks(100));

    static void init(IEventBus iEventBus) {
        REGISTER.register(iEventBus);
    }
}
