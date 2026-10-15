package org.unitego.lobecorp.network.tc.damage;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.client.particle.screen.ScreenParticles;
import org.unitego.lobecorp.network.tc.IToClientPayload;

import java.util.Optional;
import java.util.UUID;

import static org.unitego.lobecorp.Lobecorp.id;

/// 一次已结算伤害的不可变快照；最大生命值取自此次服务端伤害结算，位置冻结在发生世界。
public record DamageParticlePayload(ResourceKey<Level> dimension, UUID target, Optional<UUID> attacker,
                                    Identifier damageType, Vec3 position, float amount, float maxHealth,
                                    boolean melee) implements IToClientPayload {
	public static final Type<DamageParticlePayload> TYPE = new Type<>(id("damage_particle"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DamageParticlePayload> STREAM_CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), DamageParticlePayload::dimension,
			UUIDUtil.STREAM_CODEC, DamageParticlePayload::target,
			UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional), DamageParticlePayload::attacker,
			Identifier.STREAM_CODEC, DamageParticlePayload::damageType,
			Vec3.STREAM_CODEC, DamageParticlePayload::position,
			ByteBufCodecs.FLOAT, DamageParticlePayload::amount,
			ByteBufCodecs.FLOAT, DamageParticlePayload::maxHealth,
			ByteBufCodecs.BOOL, DamageParticlePayload::melee,
			DamageParticlePayload::new);

	@Override
	public @NonNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		ScreenParticles.receive(this);
	}
}
