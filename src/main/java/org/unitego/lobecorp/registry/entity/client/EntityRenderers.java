package org.unitego.lobecorp.registry.entity.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.unitego.lobecorp.client.entity.renderer.EntityCorpseRenderer;
import org.unitego.lobecorp.client.entity.abnormalitie.the_queen_of_hatred.renderer.TheQueenOfHatredRenderer;
import org.unitego.lobecorp.client.entity.ordeal.indigo.renderer.SweeperRenderer;
import org.unitego.lobecorp.client.entity.projectile.renderer.MagicStarRenderer;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.entity.LcEntityTypes;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;

public class EntityRenderers {
	public static void onRegister(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredRenderer::new);
		event.registerEntityRenderer(AbnormalitieEntityTypes.MAGIC_NORMAL_STAR.get(), MagicStarRenderer::new);
		event.registerEntityRenderer(AbnormalitieEntityTypes.MAGIC_HOMING_STAR.get(), MagicStarRenderer::new);
		event.registerEntityRenderer(AbnormalitieEntityTypes.MAGIC_BURST_STAR.get(), MagicStarRenderer::new);
		event.registerEntityRenderer(OrdealEntityTypes.SWEEPER.get(), SweeperRenderer::new);
		event.registerEntityRenderer(LcEntityTypes.ENTITY_CORPSE.get(), EntityCorpseRenderer::new);
	}
}
