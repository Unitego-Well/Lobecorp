package org.unitego.lobecorp.entity.ordeal.indigo;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.unitego.lobecorp.util.EnumCodecUtil;
import org.unitego.lobecorp.util.EnumStreamCodecUtil;

	/// 清道夫外观变种。
public enum SweeperVariant {
	A,
	B,
	C,
	D;

	/// 清道夫变种存档编解码器。
	public static final Codec<SweeperVariant> CODEC = EnumCodecUtil.create(SweeperVariant.class);
	/// 清道夫变种网络编解码器。
	public static final StreamCodec<ByteBuf, SweeperVariant> STREAM_CODEC = EnumStreamCodecUtil.create(SweeperVariant.class);

	/// 返回用于模型、动画和纹理路径的变种名称段。
	///
	/// @return 当前枚举名称的小写形式
	public String resourceName() {
		return name().toLowerCase();
	}
}
