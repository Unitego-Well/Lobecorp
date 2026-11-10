package org.unitego.lobecorp.network.tc.encounter;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.encounter.EncounterClient;
import org.unitego.lobecorp.network.tc.IToClientPayload;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record EncounterStatePayload(CompoundTag data) implements IToClientPayload {
	public static final Type<EncounterStatePayload> TYPE = new Type<>(Lobecorp.id("encounter_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EncounterStatePayload> STREAM_CODEC =
			ByteBufCodecs.COMPOUND_TAG.map(EncounterStatePayload::new, EncounterStatePayload::data).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		EncounterClient.receive(data);
	}
}
