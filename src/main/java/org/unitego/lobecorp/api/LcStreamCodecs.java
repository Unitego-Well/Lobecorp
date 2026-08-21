package org.unitego.lobecorp.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

public interface LcStreamCodecs {
    StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = ByteBufCodecs.fromCodecTrusted(CompoundTag.CODEC);
    StreamCodec<RegistryFriendlyByteBuf, Optional<EntityType<?>>> OPTIONAL_ENTITY_TYPE = ByteBufCodecs.optional(EntityType.STREAM_CODEC);
}
