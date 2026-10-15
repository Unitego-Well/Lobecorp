package org.unitego.lobecorp.network.tc.animation;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.player.LcAnimationTermination;
import org.unitego.lobecorp.client.animation.LcClientAnimations;
import org.unitego.lobecorp.network.tc.IToClientPayload;

import java.util.UUID;

public record LcAnimationRemovePayload(UUID instanceId, LcAnimationTermination reason) implements IToClientPayload {
	public static final Type<LcAnimationRemovePayload> TYPE = new Type<>(Lobecorp.id("animation_remove"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LcAnimationRemovePayload> STREAM_CODEC = StreamCodec.of(
			(buffer, payload) -> {
				buffer.writeUUID(payload.instanceId());
				buffer.writeEnum(payload.reason());
			},
			buffer -> new LcAnimationRemovePayload(buffer.readUUID(), buffer.readEnum(LcAnimationTermination.class)));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		LcClientAnimations.remove(instanceId, reason);
	}
}
