package org.unitego.lobecorp.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.unitego.lobecorp.Lobecorp;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public final class LcPayloads {
	private static final String NETWORK_VERSION = "1";

	private LcPayloads() {
	}

	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(NETWORK_VERSION).playToClient(
				EntitySkillSyncPayload.TYPE,
				EntitySkillSyncPayload.STREAM_CODEC,
				EntitySkillSyncPayload::work
		);
	}
}
