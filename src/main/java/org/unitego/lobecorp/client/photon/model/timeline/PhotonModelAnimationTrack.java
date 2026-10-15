package org.unitego.lobecorp.client.photon.model.timeline;

import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/// 模型动画控制数据沿用原生轨道和片段的复制、绑定与持久化入口。
public class PhotonModelAnimationTrack extends Track {
	@Override
	protected Clip createClip(double start, double duration, float speed) {
		return new PhotonModelAnimationClip(start, duration, speed);
	}

	@Override
	protected void writeClipExtra(Clip clip, CompoundTag tag, HolderLookup.Provider provider) {
		if (!(clip instanceof PhotonModelAnimationClip model)) {
			return;
		}

		tag.putString("animation", model.animation);
		tag.putDouble("offset", model.offset);
		tag.putBoolean("loop", model.loop);
		tag.putString("completion", model.completion.name());
	}

	@Override
	protected void readClipExtra(Clip clip, CompoundTag tag, HolderLookup.Provider provider) {
		if (!(clip instanceof PhotonModelAnimationClip model)) {
			return;
		}

		model.animation = tag.getStringOr("animation", "");
		model.offset = tag.getDoubleOr("offset", 0);
		model.loop = tag.getBooleanOr("loop", false);
		model.completion = PhotonModelAnimationClip.Completion.valueOf(
				tag.getStringOr("completion", PhotonModelAnimationClip.Completion.RESTORE.name()));

		if (!Double.isFinite(model.start()) || model.start() < 0
				|| !Double.isFinite(model.duration()) || model.duration() < 0
				|| !Float.isFinite(model.speed()) || model.speed() < 0
				|| !Double.isFinite(model.offset)) {
			throw new IllegalArgumentException();
		}
	}
}
