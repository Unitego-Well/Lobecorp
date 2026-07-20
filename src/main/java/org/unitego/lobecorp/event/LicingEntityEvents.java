package org.unitego.lobecorp.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class LicingEntityEvents {

    @SubscribeEvent
    public static void onLivingDeath(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        Level level = entity.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (entity.getRemovalReason() != Entity.RemovalReason.KILLED) {
            return;
        }
        if (entity instanceof EntityCorpse<?>) {
            return;
        }
        if (!(entity instanceof Mob)) {
            return;
        }

        serverLevel.addFreshEntity(EntityCorpse.createCorpse(entity));
    }
}
