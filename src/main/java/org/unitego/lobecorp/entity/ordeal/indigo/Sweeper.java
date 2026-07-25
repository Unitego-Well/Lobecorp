package org.unitego.lobecorp.entity.ordeal.indigo;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.entity.EntityCorpse;

public class Sweeper extends PathfinderMob implements Enemy, GeoEntity, IIndigoOrdeal {
    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    public Sweeper(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        addGoals();
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, EntityCorpse.class, false));
    }

    @Override
    public void registerControllers(AnimatableManager.@NonNull ControllerRegistrar controllers) {

    }

    @Override
    public @NonNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableInstanceCache;
    }
}
