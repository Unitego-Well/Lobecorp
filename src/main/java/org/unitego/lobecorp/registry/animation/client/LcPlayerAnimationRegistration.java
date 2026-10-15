package org.unitego.lobecorp.registry.animation.client;

import com.google.common.reflect.TypeToken;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.unitego.lobecorp.client.animation.player.LcPlayerAnimationRuntime;
import org.unitego.lobecorp.client.entity.player.renderer.LcPlayerFirstPersonRenderer;
import org.unitego.lobecorp.client.attachment.LcPlayerAttachmentLayer;
import org.unitego.lobecorp.client.attachment.LcPlayerRenderAnchors;
import org.unitego.lobecorp.client.attachment.LcRenderAnchorProviders;

public class LcPlayerAnimationRegistration {
	public static void registerFirstPerson(EntityRenderersEvent.AddLayers event) {
		LcRenderAnchorProviders.register(LcPlayerRenderAnchors.PROVIDER, (reference, context) -> context.node(reference.node()));
		for (var skin : event.getSkins()) {
			var renderer = event.getPlayerRenderer(skin);
			if (renderer != null) {
				renderer.addLayer(new LcPlayerAttachmentLayer(renderer));
			}
		}

		LcPlayerFirstPersonRenderer.initialize(event.getContext());
	}

	public static void register(RegisterRenderStateModifiersEvent event) {
		event.registerEntityModifier(new TypeToken<AvatarRenderer<?>>() {
		}, (entity, state) -> {
			if (entity instanceof Player player) {
				state.setRenderData(LcPlayerRenderAnchors.SOURCE, player.getUUID());
				LcPlayerAnimationRuntime.prepare(player, state, state.ageInTicks - entity.tickCount);
			}
		});
	}
}
