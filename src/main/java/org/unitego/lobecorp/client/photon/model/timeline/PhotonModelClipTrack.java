package org.unitego.lobecorp.client.photon.model.timeline;

import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.UUIDUtil;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;

/// 复用原生轨道容器；未知轨道/片段字段随实例保存，编辑已知字段不会覆盖未知内容。
public class PhotonModelClipTrack extends Track {
	private CompoundTag original = new CompoundTag();
	private final Map<Clip, CompoundTag> clipSources = new IdentityHashMap<>();

	@Override
	protected Clip createClip(double start, double duration, float speed) {
		return new PhotonModelClip(start, duration, speed);
	}

	@Override
	public void readData(HolderLookup.Provider provider, CompoundTag source) {
		validateSource(source);
		original = source.copy();
		clipSources.clear();
		super.readData(provider, source);
	}

	private static void validateSource(CompoundTag source) {
		uuid(source, "target");
		text(source, "name");
		bool(source, "mute");
		bool(source, "lock");

		if (source.contains("clips") && !(source.get("clips") instanceof ListTag)) {
			throw new IllegalArgumentException("模型轨道的片段列表类型无效。");
		}

		for (var entry : source.getListOrEmpty("clips")) {
			if (!(entry instanceof CompoundTag clip)) {
				throw new IllegalArgumentException("模型轨道包含非片段数据。");
			}

			var checked = new PhotonModelClip(number(clip, "start"), number(clip, "duration"),
					(float) number(clip, "speed"));
			checked.offset = number(clip, "offset");
			checked.validate();
			text(clip, "animation");
			text(clip, "completion");
			bool(clip, "loop");
			bool(clip, "randomSeed");
			uuid(clip, "clipTarget");
		}
	}

	private static double number(CompoundTag tag, String key) {
		if (!tag.contains(key)) {
			return 0;
		}

		if (!(tag.get(key) instanceof NumericTag number) || !Double.isFinite(number.doubleValue())) {
			throw new IllegalArgumentException("模型片段数值类型无效：" + key);
		}

		return number.doubleValue();
	}

	private static void text(CompoundTag tag, String key) {
		if (tag.contains(key) && !(tag.get(key) instanceof StringTag)) {
			throw new IllegalArgumentException("模型轨道文本类型无效：" + key);
		}
	}

	private static void bool(CompoundTag tag, String key) {
		if (tag.contains(key) && (!(tag.get(key) instanceof ByteTag value) || value.byteValue() != 0 && value.byteValue() != 1)) {
			throw new IllegalArgumentException("模型轨道布尔值无效：" + key);
		}
	}

	private static void uuid(CompoundTag tag, String key) {
		if (tag.contains(key) && tag.read(key, UUIDUtil.CODEC).isEmpty()) {
			throw new IllegalArgumentException("模型轨道对象标识无效：" + key);
		}
	}

	@Override
	public CompoundTag writeData(HolderLookup.Provider provider) {
		var preserved = original.copy();
		preserved.remove("target");
		return preserved.merge(super.writeData(provider));
	}

	@Override
	protected void readClipExtra(Clip value, CompoundTag tag, HolderLookup.Provider provider) {
		var clip = (PhotonModelClip) value;
		clip.animation = tag.getStringOr("animation", "");
		clip.offset = tag.getDoubleOr("offset", 0);
		clip.loop = tag.getBooleanOr("loop", false);
		clip.completion = PhotonModelClip.Completion.valueOf(tag.getStringOr("completion", "RESTORE"));

		clip.validate();
		clipSources.put(clip, tag.copy());
	}

	@Override
	protected void writeClipExtra(Clip value, CompoundTag tag, HolderLookup.Provider provider) {
		var clip = (PhotonModelClip) value;
		var source = clipSources.get(clip);

		if (source != null) {
			var preserved = source.copy();
			preserved.remove("clipTarget");
			tag.merge(preserved.merge(tag));
		}

		tag.putString("animation", clip.animation);
		tag.putDouble("offset", clip.offset);
		tag.putBoolean("loop", clip.loop);
		tag.putString("completion", clip.completion.name());
	}

	@Override
	protected void copyExtra(Track value) {
		var copied = (PhotonModelClipTrack) value;
		copied.original = original.copy();

		for (int index = 0; index < clips.size(); index++) {
			var source = clipSources.get(clips.get(index));

			if (source != null) {
				copied.clipSources.put(copied.clips.get(index), source.copy());
			}
		}
	}

	/// 同起点以保存顺序的最后一项为准；后开始的RESTORE片段结束后不会使更早的HOLD重新出现。
	public @Nullable PhotonModelClip selected(double time) {
		PhotonModelClip selected = null;

		for (var value : clips) {
			if (value instanceof PhotonModelClip clip && clip.start() <= time
					&& (selected == null || clip.start() >= selected.start())) {
				selected = clip;
			}
		}

		return selected == null || time >= selected.end() && selected.completion == PhotonModelClip.Completion.RESTORE
				? null : selected;
	}
}
