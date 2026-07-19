package org.unitego.lobecorp.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class LcStreamCodecs {
    public static final StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG_STREAM_CODEC = ByteBufCodecs.fromCodecTrusted(CompoundTag.CODEC);
}
