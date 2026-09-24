package org.unitego.lobecorp.registry.entity.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.client.renderer.EntityCorpseRenderer;
import org.unitego.lobecorp.entity.client.renderer.abnormalitie.TheQueenOfHatredRenderer;
import org.unitego.lobecorp.entity.client.renderer.ordeal.SweeperRenderer;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.entity.LcEntityTypes;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class EntityRenderers {
	@SubscribeEvent
	public static void onRegister(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredRenderer::new);
		event.registerEntityRenderer(OrdealEntityTypes.SWEEPER.get(), SweeperRenderer::new);
		event.registerEntityRenderer(LcEntityTypes.ENTITY_CORPSE.get(), EntityCorpseRenderer::new);
	}
}
