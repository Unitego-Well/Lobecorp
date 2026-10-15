package org.unitego.lobecorp.client.photon.animation.timeline;

import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationInput;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;

/// 只绑定发射器发射器的原生片段轨道，复制与保存使用 Track 的扩展入口。
public class PhotonChildAnimationTrack extends Track {
	@Override
	protected Clip createClip(double start, double duration, float speed) {
		return new PhotonChildAnimationClip(start, duration, speed);
	}

	@Override
	protected void writeClipExtra(Clip clip, CompoundTag tag, HolderLookup.Provider provider) {
		if (!(clip instanceof PhotonChildAnimationClip child)) {
			return;
		}

		tag.putString("id", child.id.toString());
		tag.putString("animation", child.animation);
		tag.putString("mode", child.mode.name());
		tag.putString("completion", child.completion.name());
		tag.putDouble("playbackDuration", child.playbackDuration);
		var inputs = new ListTag();
		child.inputs.forEach(input -> inputs.add(input.write()));
		tag.put("inputs", inputs);
	}

	@Override
	protected void readClipExtra(Clip clip, CompoundTag tag, HolderLookup.Provider provider) {
		if (!(clip instanceof PhotonChildAnimationClip child)) {
			return;
		}

		child.id = PhotonAnimationDataUtil.uuid(tag, "id");
		child.animation = tag.getStringOr("animation", "");
		child.mode = PhotonAnimationDataUtil.strictEnumeration(tag, "mode", PhotonChildAnimationClip.Mode.CONTROLLED);
		child.completion = PhotonAnimationDataUtil.strictEnumeration(tag, "completion", PhotonChildAnimationClip.Completion.INHERIT);
		var duration = tag.getDoubleOr("playbackDuration", -1);

		if (!Double.isFinite(duration) || (duration != -1 && duration < 0)) {
			throw new IllegalArgumentException();
		}

		child.playbackDuration = duration == -1 ? -1 : PhotonAnimationDataUtil.duration(duration);
		child.inputs.clear();

		for (var entry : tag.getListOrEmpty("inputs")) {
			if (entry instanceof CompoundTag input) {
				child.inputs.add(PhotonAnimationInput.read(input));
			}
		}
	}
}
