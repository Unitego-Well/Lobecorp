package org.unitego.lobecorp.registry.client.horde;

import com.geckolib.cache.GeckoLibResources;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.horde.HordeResourcePrewarm;
import org.unitego.lobecorp.registry.world.horde.HordeProfiles;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

import java.util.List;

public class HordeResourceRegistration {
	private static final Identifier RELOAD_ID = Lobecorp.id("horde_resource_prewarm");

	public static void registerReload(AddClientReloadListenersEvent event) {
		event.addListener(RELOAD_ID, new HordeResourcePrewarm(HordeResourceRegistration::visuals));
		event.addDependency(GeckoLibResources.RELOAD_LISTENER_ID, RELOAD_ID);
		event.addDependency(Lobecorp.id("client_animation_resources"), RELOAD_ID);
		event.addDependency(VanillaClientListeners.TEXTURES, RELOAD_ID);
		event.addDependency(VanillaClientListeners.SHADERS, RELOAD_ID);
	}

	private static List<HordeVisualDefinition> visuals() {
		return HordeProfiles.values().stream()
				.filter(MidnightCleanupEncounter.class::isInstance)
				.map(profile -> profile.visuals()).distinct().toList();
	}
}
