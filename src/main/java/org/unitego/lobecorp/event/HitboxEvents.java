package org.unitego.lobecorp.event;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.registry.LcAttachmentTypes;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class HitboxEvents {
	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (event.getLevel() instanceof ServerLevel serverLevel) {
			HitboxManager.tick(serverLevel);
			return;
		}
		event.getLevel().getData(LcAttachmentTypes.HITBOX_LEVEL_DATA)
				.pruneClient(event.getLevel().getGameTime());
	}
}
