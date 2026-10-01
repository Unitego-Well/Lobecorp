package org.unitego.lobecorp.network.ts;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.conductor.world.ConductorView;

/// 同步指挥家镜头的观察位置，维度不匹配的迟到载荷不生效。
public record ConductorViewPayload(boolean active, Identifier dimension, Vec3 position) implements ToServerPayload {
	public static final Type<ConductorViewPayload> TYPE = Lobecorp.type("conductor_view");
	public static final StreamCodec<RegistryFriendlyByteBuf, ConductorViewPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public @NonNull ConductorViewPayload decode(@NonNull RegistryFriendlyByteBuf buffer) {
			return new ConductorViewPayload(buffer.readBoolean(), buffer.readIdentifier(),
					new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
		}

		@Override
		public void encode(@NonNull RegistryFriendlyByteBuf buffer, @NonNull ConductorViewPayload payload) {
			buffer.writeBoolean(payload.active);
			buffer.writeIdentifier(payload.dimension);
			buffer.writeDouble(payload.position.x);
			buffer.writeDouble(payload.position.y);
			buffer.writeDouble(payload.position.z);
		}
	};

	@Override
	public void work(IPayloadContext context, ServerPlayer player) {
		if (player.level().dimension().identifier().equals(dimension)) ConductorView.update(player, active, position);
	}

	@Override
	public @NonNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
