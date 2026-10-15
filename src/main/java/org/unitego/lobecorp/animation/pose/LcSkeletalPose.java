package org.unitego.lobecorp.animation.pose;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.unitego.lobecorp.attachment.LcTransform;

import java.util.LinkedHashMap;
import java.util.Map;

/// 动画通道的不可变局部增量，不包含骨架绑定姿态。
public record LcSkeletalPose(Map<String, LcTransform> bones, Map<String, Vec3> eulerRotations) {
	public LcSkeletalPose {
		bones = Map.copyOf(bones);
		eulerRotations = Map.copyOf(eulerRotations);
	}

	public LcSkeletalPose(Map<String, LcTransform> bones) {
		this(bones, Map.of());
	}

	public Vec3 rotationAngles(String bone) {
		Vec3 angles = eulerRotations.get(bone);

		if (angles != null) {
			return angles;
		}

		var rotation = get(bone).rotation().getEulerAnglesZYX(new Vector3f());
		return new Vec3(rotation.x, rotation.y, rotation.z);
	}

	public LcSkeletalPose withEulerRotation(String bone, Vec3 angles) {
		var result = new LinkedHashMap<>(eulerRotations);
		result.put(bone, angles);
		LcTransform transform = get(bone);
		var values = new LinkedHashMap<>(bones);
		values.put(bone, new LcTransform(transform.position(),
				transform.rotation().rotationZYX((float) angles.z, (float) angles.y, (float) angles.x), transform.scale()));
		return new LcSkeletalPose(values, result);
	}

	public static LcSkeletalPose empty() {
		return new LcSkeletalPose(Map.of());
	}

	public LcTransform get(String bone) {
		return bones.getOrDefault(bone, LcTransform.identity());
	}

	public LcSkeletalPose withBone(String bone, LcTransform transform) {
		var result = new LinkedHashMap<>(bones);
		result.put(bone, transform);
		var angles = new LinkedHashMap<>(eulerRotations);
		angles.remove(bone);
		return new LcSkeletalPose(result, angles);
	}
}
