package org.unitego.lobecorp.entity.ordeal.indigo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.unitego.lobecorp.entity.ai.skill.EntitySkill;
import org.unitego.lobecorp.entity.ai.skill.EntitySkillBrain;
import org.unitego.lobecorp.entity.ai.skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.util.EntityUtil;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;

/// 清道夫 3 段攻击技能：attack → attack2 → attack3 → attack 循环。
/// <p>
/// 每段由前摇 + 后摇组成（共 20 tick，与 1s 动画对齐）。
/// 连段自动衔接：每段后摇结束由 {@link Sweeper#getAttackCombo()} 进位，
/// 战斗行为持续施放即可自动打出下一段。
public class SweeperAttackSkill extends EntitySkill {
    /// 一套连击（3 段）完成后进入的冷却
    private static final int COMBO_COOLDOWN = 40;

    public SweeperAttackSkill(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canUse(Mob entity, EntitySkillRuntime runtime) {
        return entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                .filter(entity::isWithinMeleeAttackRange)
                .isPresent();
    }

    @Override
    public void onWindupStart(Mob entity, EntitySkillRuntime runtime) {
        if (!(entity instanceof Sweeper sweeper)) {
            return;
        }
        int combo = sweeper.getAttackCombo() % 3;
        sweeper.setAnim(SweeperAnim.values()[SweeperAnim.ATTACK1.ordinal() + combo]);
    }

    @Override
    public void onActivate(Mob entity, EntitySkillRuntime runtime) {
        if (!(entity instanceof Sweeper sweeper)
                || !(sweeper.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = sweeper.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null) {
            return;
        }

        sweeper.swing(InteractionHand.MAIN_HAND);
        boolean hit = sweeper.doHurtTarget(level, target, 1f);
        if (hit) {
            EntityUtil.getHitPosOnAABB(sweeper, target).ifPresent(hitPos ->
                    level.sendParticles(LcParticleTypes.SIMPLE_LONG_SLASH.get(), hitPos.x, hitPos.y, hitPos.z, 1, 0, 0, 0, 0));
        }
    }

    @Override
    public void onTick(Mob entity, EntitySkillRuntime runtime) {

    }

    @Override
    public void onEnd(Mob entity, EntitySkillRuntime runtime) {

    }

    @Override
    public void onRecoveryEnd(Mob entity, EntitySkillRuntime runtime) {
        if (!(entity instanceof Sweeper sweeper)) {
            return;
        }
        int combo = (sweeper.getAttackCombo() + 1) % 3;
        sweeper.setAttackCombo(combo);
        if (combo == 0) {
            // 第 3 段完成：一套连击结束，进入冷却并恢复待机
            EntitySkillBrain.setCooldown(entity, this, COMBO_COOLDOWN);
        }
    }
}
