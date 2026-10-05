package org.unitego.lobecorp.registry.entity.client;

import com.google.common.reflect.TypeToken;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.client.entity.renderer.EntityCorpseRenderer;
import org.unitego.lobecorp.event.EntityCorpseReverseEvent;

public class RegisterRenderStateModifiers {
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
