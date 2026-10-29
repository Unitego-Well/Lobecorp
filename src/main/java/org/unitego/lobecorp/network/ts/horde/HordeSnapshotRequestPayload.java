package org.unitego.lobecorp.network.ts.horde;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.network.ts.IToServerPayload;
import org.unitego.lobecorp.world.horde.network.HordeSync;

import java.util.UUID;

public record HordeSnapshotRequestPayload(UUID eventId) implements IToServerPayload {
	public static final Type<HordeSnapshotRequestPayload> TYPE = new Type<>(Lobecorp.id("horde_snapshot_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HordeSnapshotRequestPayload> STREAM_CODEC =
			UUIDUtil.STREAM_CODEC.map(HordeSnapshotRequestPayload::new, HordeSnapshotRequestPayload::eventId).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, ServerPlayer player) {
		HordeSync.request(player, eventId);
	}
}
