package org.unitego.lobecorp.world.hitbox;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.attachment.ILcTransformTarget;
import org.unitego.lobecorp.attachment.LcTransform;

import java.util.Objects;
import java.util.Set;

/// 接管选定的判断框空间通道，保留同一实例的来源、寿命、激活状态和命中历史。
/// 缩放不隐式改变判断框尺寸，尺寸规则仍通过判断框自身接口声明。
public class HitboxAnimationTarget implements ILcTransformTarget {
	private final HitboxInstance hitbox;
	private final Set<Channel> channels;

	public HitboxAnimationTarget(HitboxInstance hitbox, Set<Channel> channels) {
		this.hitbox = Objects.requireNonNull(hitbox);
		this.channels = Set.copyOf(channels);
	}

	@Override
	public Object identity() {
		return hitbox;
	}

	@Override
	public Level level() {
		return hitbox.level();
	}

	@Override
	public LcTransform transform() {
		var angles = hitbox.rotation();
		var rotation = new Quaternionf().rotationZYX((float) angles.z * Mth.DEG_TO_RAD,
				(float) angles.y * Mth.DEG_TO_RAD, (float) angles.x * Mth.DEG_TO_RAD);
		return new LcTransform(hitbox.position(), rotation, LcTransform.identity().scale());
	}

	@Override
	public void applyTransform(LcTransform transform) {
		if (channels.isEmpty()) {
			return;
		}

		hitbox.stopFollowing();
		if (channels.contains(Channel.POSITION)) {
			hitbox.setPosition(transform.position());
		}

		if (channels.contains(Channel.ROTATION)) {
			var angles = transform.rotation().getEulerAnglesZYX(new Vector3f());
			hitbox.setRotation(new Vec3(angles.x * Mth.RAD_TO_DEG,
					angles.y * Mth.RAD_TO_DEG, angles.z * Mth.RAD_TO_DEG));
		}
	}

	@Override
	public boolean isAvailable() {
		return !hitbox.isRemoved();
	}

	public enum Channel {
		POSITION,
		ROTATION
	}
}
