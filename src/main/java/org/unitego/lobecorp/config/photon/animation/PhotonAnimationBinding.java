package org.unitego.lobecorp.config.photon.animation;

import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;

import java.util.UUID;

public class PhotonAnimationBinding {
	public enum Blend {
		OVERRIDE, ADD, MULTIPLY;

		public float combine(float base, float value) {
			return switch (this) {
				case OVERRIDE -> value;
				case ADD -> base + value;
				case MULTIPLY -> base * value;
			};
		}
	}

	public enum Source {
		CURVE, PARAMETER, EFFECT_TIME, ANIMATION_TIME, PROGRESS, LOOP_COUNT
	}

	public UUID target = UUID.randomUUID();
	public String property = "";
	public Blend blend = Blend.OVERRIDE;
	public Source source = Source.CURVE;
	public String parameter = "";
	/// -1 表示逐分量绑定；非负值指定来源分量。
	public int component = -1;
	public float scale = 1;
	public float offset = 0;
	/// 曲线倍率与偏移的可选形参 ID；空值采用对应静态值。
	public String scaleParameter = "";
	public String offsetParameter = "";

	public boolean matches(UUID object, String path) {
		return target.equals(object) && property.equals(path);
	}

	public CompoundTag write() {
		var tag = new CompoundTag();
		tag.putString("target", target.toString());
		tag.putString("property", property);
		tag.putString("blend", blend.name());
		tag.putString("source", source.name());
		tag.putString("parameter", parameter);
		tag.putInt("component", component);
		tag.putFloat("scale", scale);
		tag.putFloat("offset", offset);
		tag.putString("scaleParameter", scaleParameter);
		tag.putString("offsetParameter", offsetParameter);
		return tag;
	}

	public static PhotonAnimationBinding read(CompoundTag tag) {
		var value = new PhotonAnimationBinding();
		value.target = PhotonAnimationDataUtil.uuid(tag, "target");
		value.property = tag.getStringOr("property", "");
		value.blend = PhotonAnimationDataUtil.enumeration(tag, "blend", Blend.OVERRIDE);
		value.source = PhotonAnimationDataUtil.enumeration(tag, "source", Source.CURVE);
		value.parameter = tag.getStringOr("parameter", "");
		value.component = tag.getIntOr("component", -1);
		value.scale = tag.getFloatOr("scale", 1);
		value.offset = tag.getFloatOr("offset", 0);
		value.scaleParameter = tag.getStringOr("scaleParameter", "");
		value.offsetParameter = tag.getStringOr("offsetParameter", "");
		return value;
	}
}
