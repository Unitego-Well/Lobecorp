package org.unitego.lobecorp.registry;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.unitego.lobecorp.network.tc.*;
import org.unitego.lobecorp.network.tc.animation.LcAnimationContentPayload;
import org.unitego.lobecorp.network.tc.animation.LcAnimationStatePayload;
import org.unitego.lobecorp.network.tc.animation.LcAnimationRemovePayload;
import org.unitego.lobecorp.network.tc.attachment.LcAttachmentStatePayload;
import org.unitego.lobecorp.network.tc.damage.DamageParticlePayload;
import org.unitego.lobecorp.network.tc.text.TextParticleStatePayload;
import org.unitego.lobecorp.network.ts.ConductorCommandPayload;
import org.unitego.lobecorp.network.ts.ConductorViewPayload;
import org.unitego.lobecorp.network.ts.IToServerPayload;

public class LcPayloads {
	/// 当前客户端载荷协议版本。
	private static final String NETWORK_VERSION = "20";

	public static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(NETWORK_VERSION).playToServer(
				ConductorCommandPayload.TYPE,
				ConductorCommandPayload.STREAM_CODEC,
				IToServerPayload::handle
		).playToServer(
				ConductorViewPayload.TYPE,
				ConductorViewPayload.STREAM_CODEC,
				IToServerPayload::handle
		).playToClient(
				ConductorSnapshotPayload.TYPE,
				ConductorSnapshotPayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				ConductorEntityDataSyncPayload.TYPE,
				ConductorEntityDataSyncPayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				EntitySkillDataSyncPayload.TYPE,
				EntitySkillDataSyncPayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				EntitySkillSyncPayload.TYPE,
				EntitySkillSyncPayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				LcCustomAnimationSettingsSyncPayload.TYPE,
				LcCustomAnimationSettingsSyncPayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				HitboxCreatePayload.TYPE,
				HitboxCreatePayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				HitboxUpdatePayload.TYPE,
				HitboxUpdatePayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				HitboxRemovePayload.TYPE,
				HitboxRemovePayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				LcAnimationContentPayload.TYPE,
				LcAnimationContentPayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				LcAnimationStatePayload.TYPE,
				LcAnimationStatePayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				LcAnimationRemovePayload.TYPE,
				LcAnimationRemovePayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				LcAttachmentStatePayload.TYPE,
				LcAttachmentStatePayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				TextParticleStatePayload.TYPE,
				TextParticleStatePayload.STREAM_CODEC,
				IToClientPayload::handle
		).playToClient(
				DamageParticlePayload.TYPE,
				DamageParticlePayload.STREAM_CODEC,
				IToClientPayload::handle
		);
	}
}
