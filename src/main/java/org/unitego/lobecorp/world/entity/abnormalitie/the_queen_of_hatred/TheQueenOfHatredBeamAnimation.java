package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred;

import net.minecraft.server.level.ServerLevel;
import org.unitego.lobecorp.animation.runtime.LcAnimationInstance;
import org.unitego.lobecorp.animation.runtime.LcServerAnimations;
import org.unitego.lobecorp.registry.animation.TheQueenOfHatredAnimationDefinitions;
import org.unitego.lobecorp.util.TypedDataKey;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.StarBeamSkill;

/// 技能只修改本次代码动画的阶段；动作与特效从同一动画快照呈现。
@SuppressWarnings("resource") // 实体所属 Level 为借用，不由动画关闭。
public class TheQueenOfHatredBeamAnimation {
    public static final String PHASE = "v.beam_phase";
    public static final String PHASE_STARTED_AGE = "v.beam_phase_started_age";
	public static final String PHASE_STARTED_TICK = "v.beam_phase_started_tick";
    public static final String WINDUP_TICKS = "v.beam_windup_ticks";
    public static final String ENTRY_TICKS = "v.beam_entry_ticks";
    public static final String RECOVERY_TICKS = "v.beam_recovery_ticks";
    private static final TypedDataKey<LcAnimationInstance> PLAYBACK = TypedDataKey.create();

    public enum Phase {
        CHARGE, OUTPUT, RECOVERY
    }

    public static void start(TheQueenOfHatred queen, EntitySkillRuntime<?> runtime) {
        if (!(queen.level() instanceof ServerLevel)) {
            return;
        }

        stop(runtime);
        queen.cancelConductorSitting();
        queen.stopActionAnimation();
        boolean star = runtime.skill() instanceof StarBeamSkill;
        var definition = star
                ? queen.isPhaseTwo() ? TheQueenOfHatredAnimationDefinitions.STAR_BEAM_PHASE_TWO
                : TheQueenOfHatredAnimationDefinitions.STAR_BEAM
                : queen.isPhaseTwo() ? TheQueenOfHatredAnimationDefinitions.LASER_PHASE_TWO
                : TheQueenOfHatredAnimationDefinitions.LASER;
        var animation = LcServerAnimations.play(queen, definition);
        animation.setAutomaticStop(false);
        runtime.setData(PLAYBACK, animation);
        int windup = Math.max(1, runtime.skill().windupTicks());
        int entry = runtime.skill().durationTicks() < 0 ? windup
                : Math.clamp(runtime.skill().durationTicks(), 1, windup);
        animation.setParameter(WINDUP_TICKS, windup);
        animation.setParameter(ENTRY_TICKS, entry);
        animation.setParameter(RECOVERY_TICKS, Math.max(0, runtime.skill().recoveryTicks()));
        animation.setParameter(PHASE_STARTED_AGE, queen.tickCount);
        animation.setParameter(PHASE_STARTED_TICK, animation.level().getGameTime());
        animation.setParameter(PHASE, Phase.CHARGE.ordinal());
    }

    public static void activate(EntitySkillRuntime<?> runtime) {
        phase(runtime, Phase.OUTPUT);
    }

    public static void recover(EntitySkillRuntime<?> runtime) {
        phase(runtime, Phase.RECOVERY);
    }

    private static void phase(EntitySkillRuntime<?> runtime, Phase phase) {
        var animation = runtime.getData(PLAYBACK);

        if (animation != null && !animation.isStopped()) {
            var owner = animation.sourceEntity();
            animation.setParameter(PHASE_STARTED_AGE, owner == null ? 0 : owner.tickCount);
            animation.setParameter(PHASE_STARTED_TICK, animation.level().getGameTime());
            animation.setParameter(PHASE, phase.ordinal());
        }
    }

    public static void stop(EntitySkillRuntime<?> runtime) {
        var animation = runtime.removeData(PLAYBACK);

        if (animation != null) {
            animation.stop();
        }
    }
}
