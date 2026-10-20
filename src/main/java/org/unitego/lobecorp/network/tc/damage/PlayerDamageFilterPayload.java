package org.unitego.lobecorp.network.tc.damage;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.PlayerScreenFilters;
import org.unitego.lobecorp.network.tc.IToClientPayload;

import java.util.UUID;

/// 仅通知受击玩家的一次性通道事件，包含纯精神／灵魂伤害；不承担资源或护盾同步。
public record PlayerDamageFilterPayload(ResourceKey<Level> dimension, UUID target,
                                        int channels) implements IToClientPayload {
	public static final Type<PlayerDamageFilterPayload> TYPE = new Type<>(Lobecorp.id("player_damage_filter"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerDamageFilterPayload> STREAM_CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), PlayerDamageFilterPayload::dimension,
			UUIDUtil.STREAM_CODEC, PlayerDamageFilterPayload::target,
			ByteBufCodecs.VAR_INT, PlayerDamageFilterPayload::channels, PlayerDamageFilterPayload::new);

	public static void send(LivingEntity entity, int channels) {
		if (entity instanceof ServerPlayer player && channels != 0) {
			PacketDistributor.sendToPlayer(player, new PlayerDamageFilterPayload(player.level().dimension(), player.getUUID(), channels));
		}
	}

	@Override
	public @NonNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		if (player.getUUID().equals(target) && player.level().dimension().equals(dimension)) {
			PlayerScreenFilters.hit(channels);
		}
	}
}
