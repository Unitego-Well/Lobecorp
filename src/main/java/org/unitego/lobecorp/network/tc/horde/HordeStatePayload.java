package org.unitego.lobecorp.network.tc.horde;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.horde.HordeClient;
import org.unitego.lobecorp.network.tc.IToClientPayload;

public record HordeStatePayload(CompoundTag data) implements IToClientPayload {
	public static final Type<HordeStatePayload> TYPE = new Type<>(Lobecorp.id("horde_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HordeStatePayload> STREAM_CODEC =
			ByteBufCodecs.COMPOUND_TAG.map(HordeStatePayload::new, HordeStatePayload::data).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		HordeClient.apply(data);
	}
}
