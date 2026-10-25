package org.unitego.lobecorp.util.horde;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.horde.HordeParticipant;
import org.unitego.lobecorp.world.horde.HordeRuntime;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 事件尸体来源、保留期限和清扫表现的共用规则。
public class HordeCleanupUtil {
    /// 自尸体创建起保留10秒；战斗保护内到期后继续等待。
    public static final int CORPSE_RETENTION_TICKS = 10 * TICKS_PER_SECOND;
    private static final String CORPSE_SOURCE = "lobecorp:horde_corpse_source";

    public static void markDeath(LivingEntity victim, @Nullable Entity killer) {
        boolean eventDeath = victim instanceof Sweeper && HordeParticipant.isEvent(victim)
                || killer instanceof Sweeper && HordeParticipant.isEvent(killer);
        victim.getPersistentData().putBoolean(CORPSE_SOURCE, eventDeath);
    }

    public static boolean hasTimedCorpse(Entity original) {
        return original instanceof Sweeper && HordeParticipant.isEvent(original)
                || original.getPersistentData().getBooleanOr(CORPSE_SOURCE, false);
    }

    public static boolean mayClearCorpse(long now, long deadline, boolean protectedByCombat) {
        return !protectedByCombat && deadline >= 0 && now >= deadline;
    }

    public static void discard(Entity entity) {
        if (entity.level() instanceof ServerLevel level && HordeRuntime.withinActiveWave(level, entity.position())) {
            var point = entity.position();
            level.sendParticles(LcParticleTypes.HORDE_CLEANUP.get(), point.x, point.y, point.z,
                    1, 0, 0, 0, 0);
        }
        entity.discard();
    }
}
