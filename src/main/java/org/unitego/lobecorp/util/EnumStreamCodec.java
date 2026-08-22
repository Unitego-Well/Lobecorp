package org.unitego.lobecorp.util;

import io.netty.buffer.ByteBuf;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class EnumStreamCodec {
	public static <T extends Enum<T>, B extends ByteBuf> StreamCodec<B, T> create(KClass<T> enumClass) {
		return create(JvmClassMappingKt.getJavaClass(enumClass));
	}

	public static <T extends Enum<T>, B extends ByteBuf> StreamCodec<B, T> create(Class<T> enumClass) {
		return StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Enum::name,
			(buf) -> Enum.valueOf(enumClass, buf));
	}
}