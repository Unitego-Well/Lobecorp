package org.unitego.lobecorp.client.attachment;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.attachment.ILcTransformTarget;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.client.photon.runtime.PhotonAnchorTransform;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffect;

import java.util.Objects;

/// 复用 Photon 的同一运行实例接收视觉变换，不改变播放时间、参数或停止策略。
public class PhotonAnimationTarget implements ILcTransformTarget {
	private final PhotonGeoEffect effect;

	public PhotonAnimationTarget(PhotonGeoEffect effect) {
		this.effect = Objects.requireNonNull(effect);
	}

	@Override
	public Object identity() {
		return effect;
	}

	@Override
	public Level level() {
		return effect.getLevel();
	}

	@Override
	public LcTransform transform() {
		var transform = effect.runtime().root.transform();
		var position = transform.position();
		var scale = transform.scale();
		return new LcTransform(new Vec3(position.x, position.y, position.z), transform.rotation(),
				new Vec3(scale.x, scale.y, scale.z));
	}

	@Override
	public void applyTransform(LcTransform transform) {
		effect.updateExternalAnchor(new PhotonAnchorTransform(transform.position().toVector3f(),
				transform.rotation(), transform.scale().toVector3f()));
	}

	@Override
	public boolean isAvailable() {
		return effect.isAlive();
	}
}
