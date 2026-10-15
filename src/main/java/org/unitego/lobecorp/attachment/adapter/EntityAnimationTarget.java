package org.unitego.lobecorp.attachment.adapter;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.attachment.ILcTransformTarget;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.util.entity.EntityFacingUtil;

import java.util.Objects;
import java.util.Set;

/// 接收显式选择的实体位置和转向通道，不接管碰撞、导航、速度、重力或尺寸。
/// 世界由实体生命周期持有，此适配器仅借用创建时所在的世界。
@SuppressWarnings("resource")
public class EntityAnimationTarget implements ILcTransformTarget {
	private final Entity entity;
	private final Level level;
	private final Set<Channel> channels;

	public EntityAnimationTarget(Entity entity, Set<Channel> channels) {
		this.entity = Objects.requireNonNull(entity);
		this.level = entity.level();
		this.channels = Set.copyOf(channels);
	}

	@Override
	public Object identity() {
		return entity;
	}

	@Override
	public Level level() {
		return level;
	}

	@Override
	public LcTransform transform() {
		float yaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
		double scale = entity instanceof LivingEntity living ? living.getScale() : 1.0;
		var rotation = new Quaternionf().rotationYXZ(-yaw * Mth.DEG_TO_RAD,
				entity.getXRot() * Mth.DEG_TO_RAD, 0.0F);
		return new LcTransform(entity.position(), rotation, new Vec3(scale, scale, scale));
	}

	@Override
	public void applyTransform(LcTransform transform) {
		if (channels.contains(Channel.POSITION)) {
			var position = transform.position();
			if (entity instanceof ServerPlayer player) {
				player.teleportTo(position.x, position.y, position.z);
			} else {
				entity.setPos(position);
			}
		}

		if (channels.contains(Channel.ROTATION)) {
			applyRotation(transform.rotation());
		}
	}

	@Override
	public boolean isAvailable() {
		return !entity.isRemoved() && entity.level() == level;
	}

	private void applyRotation(Quaternionf rotation) {
		var angles = rotation.getEulerAnglesYXZ(new Vector3f());
		float yaw = -angles.y * Mth.RAD_TO_DEG;
		float pitch = angles.x * Mth.RAD_TO_DEG;
		if (entity instanceof Mob mob) {
			EntityFacingUtil.turn(mob, yaw);
			mob.setXRot(Mth.approachDegrees(mob.xRotO, pitch, mob.getMaxHeadXRot()));
			return;
		}

		entity.setYRot(yaw);
		entity.setXRot(pitch);
		if (entity instanceof LivingEntity living) {
			living.setYHeadRot(yaw);
			living.setYBodyRot(yaw);
		}
	}

	public enum Channel {
		POSITION,
		ROTATION
	}
}
