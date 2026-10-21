package org.unitego.lobecorp.client.photon.model.timeline;

import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.util.photon.playback.PhotonPlaybackTagUtil;

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
		PhotonPlaybackTagUtil.validateModelTrack(source);
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
