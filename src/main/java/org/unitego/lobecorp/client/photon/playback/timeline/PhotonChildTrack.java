package org.unitego.lobecorp.client.photon.playback.timeline;

import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedCodecUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonPlaybackTagUtil;

import java.util.HashSet;
import java.util.UUID;

/// 保留轨道和片段的原树；已知可空引用清除后不能从旧树恢复。
public class PhotonChildTrack extends Track {
	private CompoundTag source = new CompoundTag();

	@Override
	protected Clip createClip(double start, double duration, float speed) {
		return new PhotonChildClip(start, duration, speed);
	}

	@Override
	public void readData(HolderLookup.Provider provider, CompoundTag value) {
		PhotonPlaybackTagUtil.validateChildTrack(value);
		source = value.copy();
		super.readData(provider, value);
		var ids = new HashSet<UUID>();

		for (var valueClip : clips) {
			var child = (PhotonChildClip) valueClip;

			if (!ids.add(child.id)) {
				throw new IllegalArgumentException("子动画片段身份重复。");
			}
		}
	}

	@Override
	public CompoundTag writeData(HolderLookup.Provider provider) {
		var preserved = source.copy();
		preserved.remove("target");
		return preserved.merge(super.writeData(provider));
	}

	@Override
	protected void readClipExtra(Clip value, CompoundTag tag, HolderLookup.Provider provider) {
		var child = (PhotonChildClip) value;
		String id = tag.getStringOr("id", "");
		child.id = UUID.fromString(id);

		if (!child.id.toString().equalsIgnoreCase(id)) {
			throw new IllegalArgumentException("子动画片段身份不是完整UUID。");
		}

		child.animation = tag.getStringOr("animation", "");
		child.mode = PhotonChildClip.Mode.valueOf(tag.getStringOr("mode", "CONTROLLED"));
		child.completion = PhotonChildClip.Completion.valueOf(tag.getStringOr("completion", "INHERIT"));
		child.playbackDuration = tag.getDoubleOr("playbackDuration", -1);
		child.inputs = PhotonNamedCodecUtil.inputs(tag);
		child.validate();
		child.source(tag);
	}

	@Override
	protected void writeClipExtra(Clip value, CompoundTag tag, HolderLookup.Provider provider) {
		var child = (PhotonChildClip) value;
		var preserved = child.source();
		preserved.remove("clipTarget");
		tag.merge(preserved.merge(tag));
		tag.putString("id", child.id.toString());
		tag.putString("animation", child.animation);
		tag.putString("mode", child.mode.name());
		tag.putString("completion", child.completion.name());
		tag.putDouble("playbackDuration", child.playbackDuration);
		var inputs = new ListTag();

		for (var input : child.inputs) {
			var entry = child.source().getListOrEmpty("inputs").stream()
					.filter(raw -> raw instanceof CompoundTag tagValue && tagValue.getStringOr("target", "").equals(input.target()))
					.map(CompoundTag.class::cast).findFirst().map(CompoundTag::copy).orElseGet(CompoundTag::new);
			entry.putString("target", input.target());
			entry.putString("source", input.source());
			entry.putBoolean("follow", input.follow());
			var values = new ListTag();
			input.value().forEach(number -> values.add(FloatTag.valueOf(number)));
			entry.put("value", values);
			inputs.add(entry);
		}

		tag.put("inputs", inputs);
	}

	@Override
	protected void copyExtra(Track target) {
		((PhotonChildTrack) target).source = source.copy();
	}
}
