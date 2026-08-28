package org.unitego.lobecorp.registry.entity.client;

import com.google.common.reflect.TypeToken;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.entity.client.renderer.EntityCorpseRenderer;
import org.unitego.lobecorp.event.client.EntityCorpseReverseEvent;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class RegisterRenderStateModifiers {
    @SubscribeEvent
    public static void onRegister(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<EntityCorpseRenderer<EntityCorpse<?>>>(EntityCorpseRenderer.class) {
        }, (entity, renderState) -> {
            Entity ownerEntity = entity.getOwnerEntity();
            if (ownerEntity == null) {
                return;
            }
            if (EntityCorpseReverseEvent.contains(ownerEntity.getType())) {
                renderState.setRenderData(EntityCorpseRenderer.IS_REVERSE, false);
            }
        });
    }
}
