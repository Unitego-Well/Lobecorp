package org.unitego.lobecorp.entity.ordeal.indigo;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.unitego.lobecorp.util.EnumCodecUtil;
import org.unitego.lobecorp.util.EnumStreamCodecUtil;

/// 清道夫变种 a/b/c/d
public enum SweeperVariant {
	A,
	B,
	C,
	D;

	public static final Codec<SweeperVariant> CODEC = EnumCodecUtil.create(SweeperVariant.class);
	public static final StreamCodec<ByteBuf, SweeperVariant> STREAM_CODEC = EnumStreamCodecUtil.create(SweeperVariant.class);

	/// 资源文件命名段（小写），用于拼接 sweeper_&lt;variant&gt; 的模型/动画/纹理路径
	public String resourceName() {
		return name().toLowerCase();
	}
}
