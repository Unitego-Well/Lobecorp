package org.unitego.lobecorp.config.photon.animation;

import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;

/// 动画调用的一项实参；引用采用形参 ID，目标动画负责最终类型与范围校验。
public class PhotonAnimationInput {
	public String target = "";
	/// 父动画的形参 ID；空值表示传递固定 value。
	public String source = "";
	public float[] value = {1};
	/// 默认复制调用时的值；开启后跟随来源播放句柄，来源结束时冻结最后有效值。
	public boolean follow;

	public CompoundTag write() {
		var tag = new CompoundTag();
		tag.putString("target", target);
		tag.putString("source", source);
		tag.putBoolean("follow", follow);
		tag.put("value", PhotonAnimationDataUtil.floats(value));
		return tag;
	}

	public static PhotonAnimationInput read(CompoundTag tag) {
		var input = new PhotonAnimationInput();
		input.target = tag.getStringOr("target", "");
		input.source = tag.getStringOr("source", "");
		input.follow = tag.getBooleanOr("follow", false);
		input.value = PhotonAnimationDataUtil.floats(tag, "value", new float[]{1});
		return input;
	}
}
