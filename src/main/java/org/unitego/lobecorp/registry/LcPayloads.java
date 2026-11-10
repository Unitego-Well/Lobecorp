package org.unitego.lobecorp.registry;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.network.IToPayload;
import org.unitego.lobecorp.network.tc.*;
import org.unitego.lobecorp.network.tc.animation.LcAnimationContentPayload;
import org.unitego.lobecorp.network.tc.animation.LcAnimationRemovePayload;
import org.unitego.lobecorp.network.tc.animation.LcAnimationStatePayload;
import org.unitego.lobecorp.network.tc.attachment.LcAttachmentStatePayload;
import org.unitego.lobecorp.network.tc.damage.DamageParticlePayload;
import org.unitego.lobecorp.network.tc.damage.PlayerDamageFilterPayload;
import org.unitego.lobecorp.network.tc.text.TextParticleStatePayload;
import org.unitego.lobecorp.network.ts.ConductorCommandPayload;
import org.unitego.lobecorp.network.ts.ConductorViewPayload;
import org.unitego.lobecorp.network.tc.horde.HordeStatePayload;
import org.unitego.lobecorp.network.tc.encounter.EncounterStatePayload;
import org.unitego.lobecorp.network.ts.horde.HordeSnapshotRequestPayload;
import org.unitego.lobecorp.network.ts.encounter.EncounterSkipPayload;

public class LcPayloads {
	/// 当前客户端载荷协议版本。
	private static final String NETWORK_VERSION = "33";

	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
		server(registrar);
		client(registrar);
		clientAndServer(registrar);
	}

	private static void server(PayloadRegistrar registrar) {
		playToServer(registrar, ConductorCommandPayload.TYPE, ConductorCommandPayload.STREAM_CODEC);
		playToServer(registrar, ConductorViewPayload.TYPE, ConductorViewPayload.STREAM_CODEC);
		playToServer(registrar, HordeSnapshotRequestPayload.TYPE, HordeSnapshotRequestPayload.STREAM_CODEC);
		playToServer(registrar, EncounterSkipPayload.TYPE, EncounterSkipPayload.STREAM_CODEC);
	}

	private static void client(PayloadRegistrar registrar) {
		playToClient(registrar, ConductorSnapshotPayload.TYPE, ConductorSnapshotPayload.STREAM_CODEC);
		playToClient(registrar, ConductorEntityDataSyncPayload.TYPE, ConductorEntityDataSyncPayload.STREAM_CODEC);
		playToClient(registrar, EntitySkillDataSyncPayload.TYPE, EntitySkillDataSyncPayload.STREAM_CODEC);
		playToClient(registrar, EntitySkillSyncPayload.TYPE, EntitySkillSyncPayload.STREAM_CODEC);
		playToClient(registrar, LcCustomAnimationSettingsSyncPayload.TYPE, LcCustomAnimationSettingsSyncPayload.STREAM_CODEC);
		playToClient(registrar, HitboxCreatePayload.TYPE, HitboxCreatePayload.STREAM_CODEC);
		playToClient(registrar, HitboxUpdatePayload.TYPE, HitboxUpdatePayload.STREAM_CODEC);
		playToClient(registrar, HitboxRemovePayload.TYPE, HitboxRemovePayload.STREAM_CODEC);
		playToClient(registrar, LcAnimationContentPayload.TYPE, LcAnimationContentPayload.STREAM_CODEC);
		playToClient(registrar, LcAnimationStatePayload.TYPE, LcAnimationStatePayload.STREAM_CODEC);
		playToClient(registrar, LcAnimationRemovePayload.TYPE, LcAnimationRemovePayload.STREAM_CODEC);
		playToClient(registrar, LcAttachmentStatePayload.TYPE, LcAttachmentStatePayload.STREAM_CODEC);
		playToClient(registrar, TextParticleStatePayload.TYPE, TextParticleStatePayload.STREAM_CODEC);
		playToClient(registrar, DamageParticlePayload.TYPE, DamageParticlePayload.STREAM_CODEC);
		playToClient(registrar, PlayerDamageFilterPayload.TYPE, PlayerDamageFilterPayload.STREAM_CODEC);
		playToClient(registrar, HordeStatePayload.TYPE, HordeStatePayload.STREAM_CODEC);
		playToClient(registrar, EncounterStatePayload.TYPE, EncounterStatePayload.STREAM_CODEC);
	}

	private static void clientAndServer(PayloadRegistrar registrar) {
	}

	private static <T extends IToPayload> @NonNull PayloadRegistrar playToServer(
			PayloadRegistrar registrar, CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		return registrar.playToServer(type, codec, IToPayload::handle);
	}

	private static <T extends IToPayload> @NonNull PayloadRegistrar playToClient(
			PayloadRegistrar registrar, CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		return registrar.playToClient(type, codec, IToPayload::handle);
	}

	private static <T extends IToPayload> @NonNull PayloadRegistrar playBidirectional(
			PayloadRegistrar registrar, CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		return registrar.playBidirectional(type, codec, IToPayload::handle);
	}
}
