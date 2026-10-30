package org.unitego.lobecorp.mixin.photon.creation.client;

import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TimelinePlayer.class, remap = false)
public interface TimelinePlayerLeaseAccessor {
    @Accessor("effect")
    void lobecorp$setLeaseEffect(@Nullable IEffectExecutor effect);
}
