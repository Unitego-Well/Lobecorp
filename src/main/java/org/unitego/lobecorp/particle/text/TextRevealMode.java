package org.unitego.lobecorp.particle.text;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/// 逐字顺序只控制显示时间，不改变文字排版后的固定位置。
public enum TextRevealMode implements StringRepresentable {
	DIRECT("direct"),
	LEFT_TO_RIGHT("left_to_right"),
	RIGHT_TO_LEFT("right_to_left"),
	CENTER_OUT("center_out");

	public static final Codec<TextRevealMode> CODEC = StringRepresentable.fromEnum(TextRevealMode::values);
	public static final StreamCodec<RegistryFriendlyByteBuf, TextRevealMode> STREAM_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(CODEC);
	private final String serializedName;

	@SuppressWarnings("UnnecessaryModifier")
	private TextRevealMode(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public @NonNull String getSerializedName() {
		return serializedName;
	}
}
