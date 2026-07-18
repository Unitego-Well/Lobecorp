package org.unitego.lobecorp.ordeal.indigo;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

public class Sweeper extends PathfinderMob implements Enemy, GeoEntity, IIndigoOrdeal {
    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    public Sweeper(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.@NonNull ControllerRegistrar controllers) {

    }

    @Override
    public @NonNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableInstanceCache;
    }
}
