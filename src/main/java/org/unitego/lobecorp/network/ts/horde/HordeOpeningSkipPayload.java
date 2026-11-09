package org.unitego.lobecorp.network.ts.horde;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.network.ts.IToServerPayload;
import org.unitego.lobecorp.world.horde.HordeRuntime;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
@SuppressWarnings("resource")
public record HordeOpeningSkipPayload(UUID eventId) implements IToServerPayload {
	public static final Type<HordeOpeningSkipPayload> TYPE = new Type<>(Lobecorp.id("horde_opening_skip"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HordeOpeningSkipPayload> STREAM_CODEC =
			UUIDUtil.STREAM_CODEC.map(HordeOpeningSkipPayload::new, HordeOpeningSkipPayload::eventId).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, ServerPlayer player) {
		HordeRuntime.get(player.level().getServer()).skipOpening(player, eventId);
	}
}
