package org.unitego.lobecorp.events;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.registry.LcAttachmentTypes;

public class HitboxEvents {
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (event.getLevel() instanceof ServerLevel serverLevel) {
			HitboxManager.tick(serverLevel);
			return;
		}
		event.getLevel().getData(LcAttachmentTypes.HITBOX_LEVEL_DATA)
				.pruneClient(event.getLevel().getGameTime());
	}
}
