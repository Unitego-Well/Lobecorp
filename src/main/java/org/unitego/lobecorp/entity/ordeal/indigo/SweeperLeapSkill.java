package org.unitego.lobecorp.entity.ordeal.indigo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.ai.skill.EntitySkill;
import org.unitego.lobecorp.entity.ai.skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.ai.skill.IEntitySkill;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;

/// 清道夫飞扑技能：起跳 leap 冲向目标，落地 leap2 时对落点周围造成范围伤害。
public class SweeperLeapSkill extends EntitySkill {
    /// 落地范围伤害半径
    private static final float DAMAGE_RADIUS = 2.5f;
    /// 起跳水平速度
    private static final double LEAP_SPEED = 0.9;
    /// 起跳垂直速度
    private static final double LEAP_UP = 0.4;

    public SweeperLeapSkill(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canUse(Mob entity, EntitySkillRuntime runtime) {
        // 有目标且目标不在近战范围（飞扑用于扑向远处目标）
        return entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                .filter(target -> !entity.isWithinMeleeAttackRange(target))
                .isPresent();
    }

    @Override
    public void onWindupStart(Mob entity, EntitySkillRuntime runtime) {
        if (!(entity instanceof Sweeper sweeper)) {
            return;
        }

        sweeper.setAnim(SweeperAnim.LEAP);
        LivingEntity target = sweeper.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target != null) {
            // 起跳前转身面向目标
            sweeper.getLookControl().setLookAt(target);
        }
    }

    @Override
    public void onActivate(Mob entity, EntitySkillRuntime runtime) {
        if (!(entity instanceof Sweeper sweeper)) {
            return;
        }

        LivingEntity target = sweeper.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null) {
            return;
        }
        // 停止寻路，飞扑期间位移完全由技能控制（避免 AI 移动覆盖冲跃方向）
        sweeper.getNavigation().stop();
        // 立即面向目标
        double dx = target.getX() - sweeper.getX();
        double dz = target.getZ() - sweeper.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        sweeper.setYRot(yaw);
        sweeper.setYHeadRot(yaw);
        sweeper.yBodyRot = yaw;
        // 朝目标方向冲跃
        double len = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
        sweeper.setDeltaMovement(dx / len * LEAP_SPEED, LEAP_UP, dz / len * LEAP_SPEED);
    }

    @Override
    public void onTick(Mob entity, EntitySkillRuntime runtime) {
        // 每 tick 朝目标保持冲跃速度（追踪），限制下落模拟滑行
        if (!(entity instanceof Sweeper sweeper)) {
            return;
        }

        LivingEntity target = sweeper.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        Vec3 v = sweeper.getDeltaMovement();
        if (target != null) {
            double dx = target.getX() - sweeper.getX();
            double dz = target.getZ() - sweeper.getZ();
            double len = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
            sweeper.setDeltaMovement(dx / len * LEAP_SPEED, v.y, dz / len * LEAP_SPEED);
            return;
        }
        sweeper.setDeltaMovement(v.x, Math.max(v.y, -0.25), v.z);
    }

    @Override
    public void onEnd(Mob entity, EntitySkillRuntime runtime) {
        if (!(entity instanceof Sweeper sweeper) || !(sweeper.level() instanceof ServerLevel level)) {
            return;
        }
        sweeper.setAnim(SweeperAnim.LEAP2);

        // 落地范围伤害
        AABB box = sweeper.getBoundingBox().inflate(DAMAGE_RADIUS);
        level.getEntitiesOfClass(LivingEntity.class, box, e -> e != sweeper && e.isAlive() && sweeper.isValidTarget(e))
                .forEach(e -> sweeper.doHurtTarget(level, e, 1f));

        Vec3 pos = sweeper.position();
        level.sendParticles(LcParticleTypes.SIMPLE_LONG_SLASH.get(), pos.x, pos.y + 0.5, pos.z, 8, DAMAGE_RADIUS, 0, DAMAGE_RADIUS, 0.05);
    }

    @Override
    public void onRecoveryEnd(Mob entity, EntitySkillRuntime runtime) {

    }
}
