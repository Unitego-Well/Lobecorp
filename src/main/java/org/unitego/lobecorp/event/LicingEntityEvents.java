package org.unitego.lobecorp.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class LicingEntityEvents {
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        var entity = event.getEntity();
        Level level = entity.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(entity instanceof Mob) ||
                entity instanceof EntityCorpse) {
            return;
        }
        EntityCorpse entityCorpse = new EntityCorpse(level, entity);
        entityCorpse.absSnapTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
        serverLevel.addFreshEntity(entityCorpse);
    }
}
