package org.unitego.lobecorp.registry;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.unitego.lobecorp.network.tc.*;
import org.unitego.lobecorp.network.ts.ConductorCommandPayload;
import org.unitego.lobecorp.network.ts.ConductorViewPayload;
import org.unitego.lobecorp.network.ts.ToServerPayload;

public class LcPayloads {
	/// 当前客户端载荷协议版本。
	private static final String NETWORK_VERSION = "10";

	public static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(NETWORK_VERSION).playToServer(
				ConductorCommandPayload.TYPE,
				ConductorCommandPayload.STREAM_CODEC,
				ToServerPayload::handle
			).playToServer(
				ConductorViewPayload.TYPE,
				ConductorViewPayload.STREAM_CODEC,
				ToServerPayload::handle
			).playToClient(
				ConductorSnapshotPayload.TYPE,
				ConductorSnapshotPayload.STREAM_CODEC,
				ToClientPayload::handle
			).playToClient(
				EntitySkillSyncPayload.TYPE,
				EntitySkillSyncPayload.STREAM_CODEC,
				ToClientPayload::handle
			).playToClient(
				LcCustomAnimationSettingsSyncPayload.TYPE,
				LcCustomAnimationSettingsSyncPayload.STREAM_CODEC,
				ToClientPayload::handle
			).playToClient(
				HitboxCreatePayload.TYPE,
				HitboxCreatePayload.STREAM_CODEC,
				ToClientPayload::handle
			).playToClient(
				HitboxUpdatePayload.TYPE,
				HitboxUpdatePayload.STREAM_CODEC,
				ToClientPayload::handle
			).playToClient(
				HitboxRemovePayload.TYPE,
				HitboxRemovePayload.STREAM_CODEC,
				ToClientPayload::handle
			);
	}
}
