package org.unitego.lobecorp.registry.entity.client;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.client.renderer.EntityCorpseRenderer;
import org.unitego.lobecorp.entity.client.renderer.abnormalitie.TheQueenOfHatredRenderer;
import org.unitego.lobecorp.entity.client.renderer.abnormalitie.TheQueenOfHatredLaserRenderer;
import org.unitego.lobecorp.entity.client.renderer.abnormalitie.TheQueenOfHatredMagicCircleRenderer;
import org.unitego.lobecorp.entity.client.renderer.abnormalitie.TheQueenOfHatredPillarOfLightRenderer;
import org.unitego.lobecorp.entity.client.renderer.ordeal.SweeperRenderer;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.entity.LcEntityTypes;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class EntityRenderers {
	/// 普通魔法星星的渲染倍率。
	private static final float MAGIC_STAR_SCALE = 0.5F;
	/// 追踪魔法星星的渲染倍率。
	private static final float TRACKING_MAGIC_STAR_SCALE = 1.0F;
	/// 爆裂魔法星星的渲染倍率。
	private static final float BURST_MAGIC_STAR_SCALE = 2.0F;

	@SubscribeEvent
	public static void onRegister(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatredRenderer::new);
		event.registerEntityRenderer(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED_MAGIC_STAR.get(),
				context -> new ThrownItemRenderer<>(context, MAGIC_STAR_SCALE, false));
		event.registerEntityRenderer(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED_TRACKING_MAGIC_STAR.get(),
				context -> new ThrownItemRenderer<>(context, TRACKING_MAGIC_STAR_SCALE, false));
		event.registerEntityRenderer(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED_BURST_MAGIC_STAR.get(),
				context -> new ThrownItemRenderer<>(context, BURST_MAGIC_STAR_SCALE, false));
		event.registerEntityRenderer(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED_LASER.get(),
				TheQueenOfHatredLaserRenderer::new);
		event.registerEntityRenderer(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED_MAGIC_CIRCLE.get(),
				TheQueenOfHatredMagicCircleRenderer::new);
		event.registerEntityRenderer(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED_PILLAR_OF_LIGHT.get(),
				TheQueenOfHatredPillarOfLightRenderer::new);
		event.registerEntityRenderer(OrdealEntityTypes.SWEEPER.get(), SweeperRenderer::new);
		event.registerEntityRenderer(LcEntityTypes.ENTITY_CORPSE.get(), EntityCorpseRenderer::new);
	}
}
