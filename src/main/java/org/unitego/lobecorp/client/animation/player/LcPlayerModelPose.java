package org.unitego.lobecorp.client.animation.player;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.LcRotationTransitionMode;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy.Channel;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.util.animation.LcRotationTransitionUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/// 原版模型的不可变局部姿态，保留像素坐标、弧度及独立缩放。
public record LcPlayerModelPose(Map<String, PartPose> parts) {
	public LcPlayerModelPose {
		parts = Map.copyOf(parts);
	}

	public static Map<String, ModelPart> parts(PlayerModel model) {
		var parts = new LinkedHashMap<String, ModelPart>();
		parts.put("root", model.root());
		parts.put("head", model.head);
		parts.put("body", model.body);
		parts.put("right_arm", model.rightArm);
		parts.put("left_arm", model.leftArm);
		parts.put("right_leg", model.rightLeg);
		parts.put("left_leg", model.leftLeg);
		parts.put("hat", model.hat);
		parts.put("jacket", model.jacket);
		parts.put("right_sleeve", model.rightSleeve);
		parts.put("left_sleeve", model.leftSleeve);
		parts.put("right_pants", model.rightPants);
		parts.put("left_pants", model.leftPants);
		return parts;
	}

	public static LcPlayerModelPose capture(PlayerModel model) {
		var values = new LinkedHashMap<String, PartPose>();
		parts(model).forEach((name, part) -> values.put(name, new PartPose(part.x, part.y, part.z,
				part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale)));
		return new LcPlayerModelPose(values);
	}

	public static LcPlayerModelPose initial(PlayerModel model) {
		var values = new LinkedHashMap<String, PartPose>();
		parts(model).forEach((name, part) -> values.put(name, part.getInitialPose()));
		return new LcPlayerModelPose(values);
	}

	public void apply(PlayerModel model) {
		var targets = parts(model);
		parts.forEach((name, pose) -> {
			var part = targets.get(name);
			if (part != null) {
				part.loadPose(pose);
			}
		});
	}

	public LcPlayerModelPose blend(LcPlayerModelPose next, BiFunction<String, Channel, Double> weights,
	                               LcRotationTransitionMode rotationMode) {
		return blend(next, weights, rotationMode, null).pose();
	}

	public BlendSample blend(LcPlayerModelPose next, BiFunction<String, Channel, Double> weights,
	                         LcRotationTransitionMode rotationMode,
	                         @Nullable BlendSample previous) {
		var values = new LinkedHashMap<>(parts);
		var rotations = new LinkedHashMap<String, LcRotationTransitionUtil.State>();
		next.parts.forEach((name, target) -> {
			var source = parts.getOrDefault(name, target);
			double positionWeight = Math.clamp(weights.apply(name, Channel.TRANSLATION), 0, 1);
			double rotationWeight = Math.clamp(weights.apply(name, Channel.ROTATION), 0, 1);
			double scaleWeight = Math.clamp(weights.apply(name, Channel.SCALE), 0, 1);
			var sample = rotationMode == LcRotationTransitionMode.SHORTEST_PATH
					? LcRotationTransitionUtil.interpolate(rotation(source), rotation(target), rotationWeight,
					previous == null ? null : previous.rotations().get(name)) : null;
			if (sample != null) {
				rotations.put(name, sample.state());
			}

			var angles = rotationWeight == 0 ? new Vector3f(source.xRot(), source.yRot(), source.zRot())
					: rotationWeight == 1 ? new Vector3f(target.xRot(), target.yRot(), target.zRot())
					: sample != null ? LcRotationTransitionUtil.nearbyEuler(sample.rotation(), null)
					: new Vector3f(lerp(source.xRot(), target.xRot(), rotationWeight),
					lerp(source.yRot(), target.yRot(), rotationWeight), lerp(source.zRot(), target.zRot(), rotationWeight));
			values.put(name, new PartPose(lerp(source.x(), target.x(), positionWeight),
					lerp(source.y(), target.y(), positionWeight), lerp(source.z(), target.z(), positionWeight),
					angles.x, angles.y, angles.z, lerp(source.xScale(), target.xScale(), scaleWeight),
					lerp(source.yScale(), target.yScale(), scaleWeight), lerp(source.zScale(), target.zScale(), scaleWeight)));
		});
		var pose = new LcPlayerModelPose(values);
		if (rotationMode == LcRotationTransitionMode.SHORTEST_PATH && previous != null) {
			pose = pose.unwrap(previous.pose());
		}

		return new BlendSample(pose, rotations);
	}

	public record BlendSample(LcPlayerModelPose pose, Map<String, LcRotationTransitionUtil.State> rotations) {
		public BlendSample {
			rotations = Map.copyOf(rotations);
		}
	}

	public static LcTransform transform(PartPose pose) {
		return new LcTransform(new Vec3(pose.x(), pose.y(), pose.z()).scale(1.0 / 16),
				rotation(pose), new Vec3(pose.xScale(), pose.yScale(), pose.zScale()));
	}

	/// 仅用于已明确归属的原链阶段；旋转按 ModelPart 的 Euler 字段增量应用。
	public LcPlayerModelPose applyDelta(LcPlayerModelPose before, LcPlayerModelPose after,
	                                    BiFunction<String, Channel, Double> weights) {
		var values = new LinkedHashMap<>(parts);
		after.parts.forEach((name, end) -> {
			var start = before.parts.get(name);
			var current = parts.get(name);
			if (start == null || current == null) {
				return;
			}

			double position = weights.apply(name, Channel.TRANSLATION);
			double rotation = weights.apply(name, Channel.ROTATION);
			double scale = weights.apply(name, Channel.SCALE);
			values.put(name, new PartPose(
					current.x() + (end.x() - start.x()) * (float) position,
					current.y() + (end.y() - start.y()) * (float) position,
					current.z() + (end.z() - start.z()) * (float) position,
					current.xRot() + (end.xRot() - start.xRot()) * (float) rotation,
					current.yRot() + (end.yRot() - start.yRot()) * (float) rotation,
					current.zRot() + (end.zRot() - start.zRot()) * (float) rotation,
					scaled(current.xScale(), start.xScale(), end.xScale(), scale),
					scaled(current.yScale(), start.yScale(), end.yScale(), scale),
					scaled(current.zScale(), start.zScale(), end.zScale(), scale)));
		});
		return new LcPlayerModelPose(values);
	}

	public LcPlayerModelPose addedTo(LcPlayerModelPose reference, LcPlayerModelPose base) {
		return base.applyDelta(reference, this, (_, _) -> 1.0);
	}

	/// 矩阵只能给出等价角度；延续上一采样的 ZYX 分支，避免跨 ±π 时跳圈。
	public LcPlayerModelPose unwrap(LcPlayerModelPose previous) {
		var values = new LinkedHashMap<>(parts);
		parts.forEach((name, pose) -> {
			var reference = previous.parts.get(name);
			if (reference == null) {
				return;
			}

			var direct = nearby(pose.xRot(), pose.yRot(), pose.zRot(), reference);
			var alternate = nearby(pose.xRot() + Math.PI, Math.PI - pose.yRot(), pose.zRot() + Math.PI, reference);
			var angles = distance(direct, reference) <= distance(alternate, reference) ? direct : alternate;
			values.put(name, new PartPose(pose.x(), pose.y(), pose.z(), angles.x, angles.y, angles.z,
					pose.xScale(), pose.yScale(), pose.zScale()));
		});
		return new LcPlayerModelPose(values);
	}

	private static float scaled(float current, float before, float after, double weight) {
		return before == 0 ? current + (after - before) * (float) weight
				: current * lerp(1, after / before, weight);
	}

	private static Vector3f nearby(double x, double y, double z, PartPose reference) {
		return new Vector3f(nearest(x, reference.xRot()), nearest(y, reference.yRot()), nearest(z, reference.zRot()));
	}

	private static float nearest(double angle, float reference) {
		return (float) (angle + Math.rint((reference - angle) / (Math.PI * 2)) * (Math.PI * 2));
	}

	private static double distance(Vector3f angles, PartPose reference) {
		return angles.distanceSquared(reference.xRot(), reference.yRot(), reference.zRot());
	}

	public static PartPose pose(LcTransform transform) {
		var angles = transform.rotation().getEulerAnglesZYX(new Vector3f());
		var position = transform.position().scale(16);
		var scale = transform.scale();
		return new PartPose((float) position.x, (float) position.y, (float) position.z,
				angles.x, angles.y, angles.z, (float) scale.x, (float) scale.y, (float) scale.z);
	}

	private static Quaternionf rotation(PartPose pose) {
		return new Quaternionf().rotationZYX(pose.zRot(), pose.yRot(), pose.xRot());
	}

	private static float lerp(float first, float second, double weight) {
		return (float) (first + (second - first) * weight);
	}
}
