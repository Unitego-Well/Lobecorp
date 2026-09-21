package org.unitego.lobecorp.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.unitego.lobecorp.Lobecorp;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public final class LcPayloads {
	/// 当前客户端载荷协议版本。
	private static final String NETWORK_VERSION = "1";

	private LcPayloads() {
	}

	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(NETWORK_VERSION).playToClient(
				EntitySkillSyncPayload.TYPE,
				EntitySkillSyncPayload.STREAM_CODEC,
				EntitySkillSyncPayload::work
		).playToClient(
				HitboxCreatePayload.TYPE,
				HitboxCreatePayload.STREAM_CODEC,
				HitboxCreatePayload::work
		).playToClient(
				HitboxUpdatePayload.TYPE,
				HitboxUpdatePayload.STREAM_CODEC,
				HitboxUpdatePayload::work
		).playToClient(
				HitboxRemovePayload.TYPE,
				HitboxRemovePayload.STREAM_CODEC,
				HitboxRemovePayload::work
		);
	}
}
