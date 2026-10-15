package org.unitego.lobecorp.network.tc.animation;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.animation.LcClientAnimationContents;
import org.unitego.lobecorp.network.tc.IToClientPayload;

/// 完整内容分块传输，版本缓存仅在当前连接内有效。
public record LcAnimationContentPayload(Identifier modelId, Identifier animationId, String version,
                                        int index, int count, byte[] bytes) implements IToClientPayload {
	/// 分块小于单个自定义载荷上限，单位为字节。
	public static final int CHUNK_BYTES = 24 * 1024;
	public static final Type<LcAnimationContentPayload> TYPE = new Type<>(Lobecorp.id("animation_content"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LcAnimationContentPayload> STREAM_CODEC = StreamCodec.of(
			(buffer, payload) -> {
				Identifier.STREAM_CODEC.encode(buffer, payload.modelId());
				Identifier.STREAM_CODEC.encode(buffer, payload.animationId());
				buffer.writeUtf(payload.version());
				buffer.writeVarInt(payload.index());
				buffer.writeVarInt(payload.count());
				buffer.writeByteArray(payload.bytes());
			}, buffer -> new LcAnimationContentPayload(Identifier.STREAM_CODEC.decode(buffer),
					Identifier.STREAM_CODEC.decode(buffer), buffer.readUtf(), buffer.readVarInt(),
					buffer.readVarInt(), buffer.readByteArray(CHUNK_BYTES)));

	public LcAnimationContentPayload {
		bytes = bytes.clone();
	}

	@Override
	public byte[] bytes() {
		return bytes.clone();
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		LcClientAnimationContents.receive(this);
	}
}
