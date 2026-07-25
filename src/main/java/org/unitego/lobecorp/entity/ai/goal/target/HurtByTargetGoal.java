package org.unitego.lobecorp.entity.ai.goal.target;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public class HurtByTargetGoal extends TargetGoal {
    private static final TargetingConditions HURT_BY_TARGETING = TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();
    private static final int ALERT_RANGE_Y = 10;
    public boolean alertSameType;
    private int timestamp;
    @Nullable
    private final Predicate<Entity> ignorePredicate;
    @Nullable
    private Predicate<Entity> toIgnoreAlertPredicate;

    public HurtByTargetGoal(Mob mob, @Nullable Predicate<Entity> ignorePredicate) {
        super(mob, true);
        this.ignorePredicate = ignorePredicate;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        int timestamp = this.mob.getLastHurtByMobTimestamp();
        LivingEntity lastHurtByMob = this.mob.getLastHurtByMob();
        if (timestamp == this.timestamp || lastHurtByMob == null) {
            return false;
        }

        if (lastHurtByMob.is(EntityType.PLAYER) && getServerLevel(this.mob).getGameRules().get(GameRules.UNIVERSAL_ANGER)) {
            return false;
        }

        if (toIgnoreAlertPredicate != null && toIgnoreAlertPredicate.test(lastHurtByMob)) {
            return false;
        }

        return this.canAttack(lastHurtByMob, HURT_BY_TARGETING);
    }

    public HurtByTargetGoal setAlertOthers(Predicate<Entity> toIgnoreAlertPredicate) {
        this.alertSameType = true;
        this.toIgnoreAlertPredicate = toIgnoreAlertPredicate;
        return this;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.mob.getLastHurtByMob());
        this.targetMob = this.mob.getTarget();
        this.timestamp = this.mob.getLastHurtByMobTimestamp();
        this.unseenMemoryTicks = 300;
        if (this.alertSameType) {
            this.alertOthers();
        }

        super.start();
    }

    protected void alertOthers() {
        double within = this.getFollowDistance();
        AABB searchAabb = AABB.unitCubeFromLowerCorner(this.mob.position()).inflate(within, 10.0, within);
        List<? extends Mob> nearby = this.mob.level().getEntitiesOfClass((Class<? extends Mob>) this.mob.getClass(), searchAabb, EntitySelector.NO_SPECTATORS);
        Iterator<? extends Mob> var5 = nearby.iterator();

        while (true) {
            Mob other;
            while (true) {
                if (!var5.hasNext()) {
                    return;
                }

                other = var5.next();

                if (this.mob == other ||
                        other.getTarget() != null ||
                        (this.mob instanceof TamableAnimal && ((TamableAnimal) this.mob).getOwner() != ((TamableAnimal) other).getOwner()) ||
                        other.isAlliedTo(this.mob.getLastHurtByMob())) {
                    continue;
                }

                if (this.ignorePredicate == null) {
                    break;
                }

                boolean ignore = false;

                if (ignorePredicate.test(other)) {
                    break;
                }

                break;
            }

            this.alertOther(other, this.mob.getLastHurtByMob());
        }
    }

    protected void alertOther(Mob other, LivingEntity hurtByMob) {
        other.setTarget(hurtByMob);
    }
}
