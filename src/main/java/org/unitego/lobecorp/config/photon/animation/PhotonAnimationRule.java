package org.unitego.lobecorp.config.photon.animation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;

import java.util.ArrayList;
import java.util.List;

/// 一个事件只调用本特效中的一个动画；子动画调用由发射器发射器的时间轴片段负责。
public class PhotonAnimationRule {
	public enum Event {
		START, COMPLETE, MARKER, SIGNAL, TRIGGER
	}

	public Event event = Event.TRIGGER;
	/// 空值匹配所有来源动画；非空值使用稳定动画 ID。
	public String animation = "";
	public String channel = "";
	public String name = "";
	public String target = "";
	/// 旧单次 PLAY 的明确时长覆盖；-1 沿用目标签名和定义，单位 tick。
	public double duration = -1;
	public final List<PhotonAnimationInput> inputs = new ArrayList<>();

	public CompoundTag write() {
		var tag = new CompoundTag();
		tag.putString("event", event.name());
		tag.putString("animation", animation);
		tag.putString("channel", channel);
		tag.putString("name", name);
		tag.putString("target", target);
		tag.putDouble("duration", duration);
		var list = new ListTag();
		inputs.forEach(input -> list.add(input.write()));
		tag.put("inputs", list);
		return tag;
	}

	public static PhotonAnimationRule read(CompoundTag tag) {
		var value = new PhotonAnimationRule();
		value.event = PhotonAnimationDataUtil.strictEnumeration(tag, "event", Event.TRIGGER);
		value.animation = tag.getStringOr("animation", "");
		value.channel = tag.getStringOr("channel", "");
		value.name = tag.getStringOr("name", "");
		value.target = tag.getStringOr("target", "");
		value.duration = tag.getDoubleOr("duration", -1);

		if (!Double.isFinite(value.duration) || value.duration < 0 && value.duration != -1) {
			throw new IllegalArgumentException();
		}

		for (var entry : tag.getListOrEmpty("inputs")) {
			if (entry instanceof CompoundTag input) {
				value.inputs.add(PhotonAnimationInput.read(input));
			}
		}

		return value;
	}
}
