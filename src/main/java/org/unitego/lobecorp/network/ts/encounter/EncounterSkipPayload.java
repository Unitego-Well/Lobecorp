package org.unitego.lobecorp.network.ts.encounter;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.network.ts.IToServerPayload;
import org.unitego.lobecorp.api.encounter.Encounters;

import java.util.UUID;

@NullMarked
public record EncounterSkipPayload(UUID eventId) implements IToServerPayload {
	public static final Type<EncounterSkipPayload> TYPE = new Type<>(Lobecorp.id("encounter_skip"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EncounterSkipPayload> STREAM_CODEC =
			UUIDUtil.STREAM_CODEC.map(EncounterSkipPayload::new, EncounterSkipPayload::eventId).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, ServerPlayer player) {
		Encounters.skip(player, eventId);
	}
}
