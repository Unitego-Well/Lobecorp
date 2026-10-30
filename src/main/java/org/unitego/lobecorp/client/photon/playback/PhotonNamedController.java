package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import org.unitego.lobecorp.util.photon.playback.PhotonRuntimeCopyUtil;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.registry.photon.client.LcPhotonPlaybackTypes;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedPlaybackDefinitionUtil;

import javax.annotation.Nullable;

/// 制作定义独占保存；播放状态由所属FXRuntime持有，不进入工程或模板复制。
public class PhotonNamedController extends EmptyFXObject {
	@Persisted
	private CompoundTag definition = new CompoundTag();
	private CompoundTag original = new CompoundTag();
	private @Nullable PhotonNamedRuntime playback;

	/// 施放与预热共用的最终播放定义：默认动画不自动启动，子动画轨道由外层时间轴控制。
	public CompoundTag playbackDefinition() {
		return PhotonNamedPlaybackDefinitionUtil.prepare(definition);
	}

	@Override
	public @Nullable IFXObject deepCopy() {
		return PhotonRuntimeCopyUtil.deepCopy(this);
	}

	@Override
	public FXObjectType getFXObjectType() {
		return LcPhotonPlaybackTypes.CONTROLLER;
	}

	public CompoundTag definition() {
		return definition.copy();
	}

	public void definition(CompoundTag value) {
		definition = value.copy();
	}

	public CompoundTag source() {
		return original.copy();
	}

	public void rememberSource(CompoundTag source) {
		original = source.copy();
	}

	public void attach(@Nullable PhotonNamedRuntime value) {
		playback = value;
	}

	@Override
	public boolean isAlive() {
		return !removed && playback != null && !playback.closed() || super.isAlive();
	}

	@Override
	public boolean isPlaying() {
		return !removed && playback != null && !playback.closed() || super.isPlaying();
	}

	@Override
	public PhotonNamedController shallowCopy() {
		return (PhotonNamedController) deepCopy();
	}

	@Override
	public void remove(boolean force) {
		if (playback != null) {
			playback.close();
		}

		super.remove(force);
	}
}
