package org.unitego.lobecorp.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.LcAttachmentTypes;

public record HitboxRemovePayload(int id) implements ToPayload {
	/// 判断框移除载荷类型。
	public static final CustomPacketPayload.Type<HitboxRemovePayload> TYPE =
			new CustomPacketPayload.Type<>(Lobecorp.id("hitbox_remove"));
	/// 判断框移除载荷编解码器。
	public static final StreamCodec<RegistryFriendlyByteBuf, HitboxRemovePayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public @NonNull HitboxRemovePayload decode(@NonNull RegistryFriendlyByteBuf buffer) {
			return new HitboxRemovePayload(buffer.readVarInt());
		}

		@Override
		public void encode(@NonNull RegistryFriendlyByteBuf buffer, @NonNull HitboxRemovePayload payload) {
			buffer.writeVarInt(payload.id);
		}
	};

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context) {
		context.player().level().getData(LcAttachmentTypes.HITBOX_LEVEL_DATA).removeClient(id);
	}
}
