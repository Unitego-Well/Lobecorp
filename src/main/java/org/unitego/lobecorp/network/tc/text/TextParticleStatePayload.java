package org.unitego.lobecorp.network.tc.text;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.client.particle.text.TextParticleClient;
import org.unitego.lobecorp.network.tc.IToClientPayload;
import org.unitego.lobecorp.particle.text.TextParticleSnapshot;

import java.util.List;
import java.util.UUID;

import static org.unitego.lobecorp.Lobecorp.id;

/// 全量包替换当前世界的实例集合；增量只更新或移除指定 ID。
public record TextParticleStatePayload(boolean full, List<TextParticleSnapshot> snapshots,
                                       List<UUID> removed) implements IToClientPayload {
	public static final Type<TextParticleStatePayload> TYPE = new Type<>(id("text_particle_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TextParticleStatePayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, TextParticleStatePayload::full,
			TextParticleSnapshot.STREAM_CODEC.apply(ByteBufCodecs.list()), TextParticleStatePayload::snapshots,
			UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), TextParticleStatePayload::removed,
			TextParticleStatePayload::new);

	public TextParticleStatePayload {
		snapshots = List.copyOf(snapshots);
		removed = List.copyOf(removed);
	}

	@Override
	public @NonNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		TextParticleClient.apply(full, snapshots, removed);
	}
}
