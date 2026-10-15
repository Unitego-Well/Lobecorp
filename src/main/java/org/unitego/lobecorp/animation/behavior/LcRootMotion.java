package org.unitego.lobecorp.animation.behavior;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.animation.runtime.LcAnimationInstance;

import java.util.Objects;
import java.util.Set;

/// 显式声明根运动通道及执行策略；碰撞裁剪不暂停动画时间，也不补偿受阻位移。
public record LcRootMotion(String bone, Set<Axis> axes, boolean localSpace, MotionExecutor executor) {
	/// 原版碰撞求解必须借用来源实体的包围盒，不能推测独立对象的碰撞形状。
	private static final String ENTITY_REQUIRED_MESSAGE = "colliding root motion requires a source entity";

	public LcRootMotion {
		Objects.requireNonNull(bone);
		axes = Set.copyOf(axes);
		Objects.requireNonNull(executor);
	}

	/// 直接应用请求增量，不隐式加入碰撞、导航、重力或普通移动限制。
	public static LcRootMotion direct(String bone, Set<Axis> axes, boolean localSpace) {
		return new LcRootMotion(bone, axes, localSpace, LcRootMotion::applyDirect);
	}

	/// 使用原版方块和实体碰撞裁剪增量，不经过 Entity.move 的额外移动处理。
	public static LcRootMotion colliding(String bone, Set<Axis> axes, boolean localSpace) {
		return new LcRootMotion(bone, axes, localSpace, LcRootMotion::applyColliding);
	}

	private static void applyDirect(LcAnimationInstance instance, Vec3 requestedDelta) {
		var target = instance.ownerTarget();
		var previous = target.transform();
		target.applyTransform(new LcTransform(previous.position().add(requestedDelta),
				previous.rotation(), previous.scale()));
	}

	private static void applyColliding(LcAnimationInstance instance, Vec3 requestedDelta) {
		var source = instance.sourceEntity();
		if (source == null) {
			throw new IllegalArgumentException(ENTITY_REQUIRED_MESSAGE);
		}

		var level = instance.ownerTarget().level();
		var bounds = source.getBoundingBox();
		var entityCollisions = level.getEntityCollisions(source, bounds.expandTowards(requestedDelta));
		var allowedDelta = Entity.collideBoundingBox(source, requestedDelta, bounds, level, entityCollisions);
		applyDirect(instance, allowedDelta);
	}

	public enum Axis {
		X,
		Y,
		Z
	}

	@FunctionalInterface
	public interface MotionExecutor {
		void move(LcAnimationInstance instance, Vec3 requestedDelta);
	}
}
