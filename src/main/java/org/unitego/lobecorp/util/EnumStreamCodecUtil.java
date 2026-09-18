package org.unitego.lobecorp.util;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class EnumStreamCodecUtil {
	public static <T extends Enum<T>, B extends ByteBuf> StreamCodec<B, T> create(Class<T> enumClass) {
		return StreamCodec.composite(ByteBufCodecs.VAR_INT, Enum::ordinal, buf -> enumClass.getEnumConstants()[buf]);
	}
}