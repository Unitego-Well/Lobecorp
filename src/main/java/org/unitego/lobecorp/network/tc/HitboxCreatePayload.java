package org.unitego.lobecorp.network.tc;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.unitego.lobecorp.hitbox.HitboxSnapshot;
import org.unitego.lobecorp.registry.LcAttachmentTypes;

import static org.unitego.lobecorp.Lobecorp.id;

public record HitboxCreatePayload(HitboxSnapshot snapshot) implements ToClientPayload {
	/// 判断框创建载荷类型。
	public static final CustomPacketPayload.Type<HitboxCreatePayload> TYPE =
			new CustomPacketPayload.Type<>(id("hitbox_create"));
	/// 判断框创建载荷编解码器。
	public static final StreamCodec<RegistryFriendlyByteBuf, HitboxCreatePayload> STREAM_CODEC =
			HitboxSnapshot.STREAM_CODEC.map(HitboxCreatePayload::new, HitboxCreatePayload::snapshot);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		player.level().getData(LcAttachmentTypes.HITBOX_LEVEL_DATA)
				.upsertClient(snapshot, player.level().getGameTime());
	}
}
