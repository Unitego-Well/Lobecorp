package org.unitego.lobecorp.registry.client.encounter.hud;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.encounter.EncounterHud;
import org.unitego.lobecorp.client.encounter.midnightcleanup.MidnightCleanupHud;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;
import org.unitego.lobecorp.client.encounter.midnightcleanup.MidnightCleanupDisplay;
import org.unitego.lobecorp.registry.client.encounter.EncounterDisplays;
import org.unitego.lobecorp.registry.client.horde.HordeClientHooks;
import org.unitego.lobecorp.client.encounter.EncounterClient;
import org.unitego.lobecorp.client.encounter.midnightcleanup.MidnightCleanupAmbient;
import org.unitego.lobecorp.client.encounter.midnightcleanup.MidnightCleanupSky;
import org.unitego.lobecorp.client.horde.HordeRenderMetrics;

public class EncounterHudRegistration {
	private static final Identifier EVENT_STACK_WRAPPER = Lobecorp.id("event_hud/top_left/events");

	public static void register() {
		EncounterDisplays.register(MidnightCleanupEncounter.ID, new MidnightCleanupDisplay());
		HordeClientHooks.register(MidnightCleanupSky::prepare, MidnightCleanupSky::clear, EncounterClient::clear, () -> {
			HordeRenderMetrics.set(HordeRenderMetrics.Counter.PARTICLE_AREAS, MidnightCleanupAmbient.selectedAreas());
			HordeRenderMetrics.set(HordeRenderMetrics.Counter.PARTICLES, MidnightCleanupAmbient.emittedParticles());
		});
		EncounterHud.INSTANCE.registry().registerWrapper(EVENT_STACK_WRAPPER);
		EncounterHud.INSTANCE.registry().register(MidnightCleanupEncounter.ID, "Midnight Cleanup", "深夜清扫", EVENT_STACK_WRAPPER, MidnightCleanupHud::new);
	}
}
