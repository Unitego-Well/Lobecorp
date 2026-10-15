package org.unitego.lobecorp.client.animation.player;

import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4d;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy.Channel;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationSources;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.animation.pose.LcSkeleton;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.client.animation.LcClientAnimationResources;
import org.unitego.lobecorp.client.animation.player.LcPlayerAnimationRuntime.LayerHistory;
import org.unitego.lobecorp.client.animation.player.LcPlayerAnimationRuntime.ModelHistory;
import org.unitego.lobecorp.util.animation.LcAnimationDebugUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// 原链单次执行并记录明确阶段增量；每层完成自身贡献后，才按优先级叠加和过渡。
public class LcPlayerPoseBridge {
	private static final List<String> BODY_PARTS = List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
	private static final Set<ArmPose> VANILLA_ARM_POSES = Set.of(ArmPose.EMPTY, ArmPose.ITEM, ArmPose.BLOCK,
			ArmPose.BOW_AND_ARROW, ArmPose.THROW_TRIDENT, ArmPose.CROSSBOW_CHARGE, ArmPose.CROSSBOW_HOLD,
			ArmPose.SPYGLASS, ArmPose.TOOT_HORN, ArmPose.BRUSH, ArmPose.SPEAR);
	// 公共骨架脚底原点转换为原版头顶原点，并抵消渲染器的 Y/Z 翻转。
	private static final double VANILLA_MODEL_HEIGHT = 24.0 / 16;
	// 与锁定的 26.1.2 AnimationUtils.bobModelPart 完全一致，不重复调用可能被扩展的原生方法。
	private static final float BOB_Z_FREQUENCY = 0.09F;
	private static final float BOB_X_FREQUENCY = 0.067F;
	private static final float BOB_AMPLITUDE = 0.05F;
	private static final ThreadLocal<ArrayDeque<Scope>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);

	public static Scope open(PlayerModel model, AvatarRenderState state) {
		var scope = new Scope(model, LcPlayerAnimationRuntime.frame(state));
		SCOPES.get().push(scope);
		return scope;
	}

	public static boolean isOutsideCurrentModel(Object model) {
		var scope = current();
		return scope == null || scope.model != model;
	}

	public static void resetCompleted() {
		phase(LcPlayerAnimationSources.VANILLA_LOOK);
	}

	public static void phase(Identifier source) {
		var scope = current();
		if (scope == null || !scope.active()) {
			return;
		}

		scope.endPhase();
		scope.source = source;
		scope.before = LcPlayerModelPose.capture(scope.model);
	}

	public static void endPhase() {
		var scope = current();
		if (scope != null) {
			scope.endPhase();
		}
	}

	public static void swimPhase() {
		phase(LcPlayerAnimationSources.VANILLA_MOVEMENT);
	}

	public static void operation(Identifier source, Runnable operation) {
		var scope = current();
		if (scope == null || !scope.active()) {
			operation.run();
			return;
		}

		scope.endPhase();
		var before = LcPlayerModelPose.capture(scope.model);
		operation.run();
		// 待机来源独立于 SPYGLASS 等持物条件；原生 bob 调用仍原样执行一次。
		if (!source.equals(LcPlayerAnimationSources.VANILLA_IDLE)) {
			scope.stages.add(new NativeStage(source, before, LcPlayerModelPose.capture(scope.model)));
		}
	}

	public static Identifier armSource(ArmPose pose) {
		return VANILLA_ARM_POSES.contains(pose) ? LcPlayerAnimationSources.VANILLA_ITEM : LcPlayerAnimationSources.NEO_ARM_POSE;
	}

	public static void idleSource(float ageInTicks) {
		var scope = current();
		if (scope == null || !scope.active()) {
			return;
		}

		scope.endPhase();
		var reference = LcPlayerModelPose.initial(scope.model);
		var values = new LinkedHashMap<>(reference.parts());
		addIdle(values, "right_arm", ageInTicks, 1);
		addIdle(values, "left_arm", ageInTicks, -1);
		scope.stages.add(new NativeStage(LcPlayerAnimationSources.VANILLA_IDLE, reference, new LcPlayerModelPose(values)));
	}

	private static void addIdle(LinkedHashMap<String, PartPose> values, String bone, float age, float direction) {
		values.computeIfPresent(bone, (_, part) -> new PartPose(part.x(), part.y(), part.z(),
				part.xRot() + direction * (Mth.sin(age * BOB_X_FREQUENCY) * BOB_AMPLITUDE), part.yRot(),
				part.zRot() + direction * (Mth.cos(age * BOB_Z_FREQUENCY) * BOB_AMPLITUDE + BOB_AMPLITUDE),
				part.xScale(), part.yScale(), part.zScale()));
	}

	public static void finish(Scope scope) {
		scope.endPhase();
		if (scope.frame == null || scope.history == null) {
			return;
		}

		var history = scope.history;
		if (history.frame == scope.frame && history.displayed != null) {
			history.displayed.apply(scope.model);
			return;
		}

		var reference = LcPlayerModelPose.initial(scope.model);
		var result = LcPlayerModelPose.capture(scope.model);
		var ids = scope.frame.layers().stream().map(layer -> layer.frame().snapshot().instanceId()).toList();
		history.layers.keySet().retainAll(ids);
		for (var layer : scope.frame.layers()) {
			var previous = history.layers.get(layer.frame().snapshot().instanceId());
			var mapped = mapped(reference, layer, previous);
			var target = sources(scope, layer.policy(), mapped);
			var contribution = contribution(layer, previous, target, mapped, result, reference);
			var evaluated = contribution.displayed().evaluate(result, reference);
			result = evaluated.pose();
			contribution = new LayerHistory(contribution.revision(), contribution.stopped(), evaluated.origin(),
					evaluated.displayed(), contribution.replacement(), contribution.mapped(), contribution.base());
			var snapshot = layer.frame().snapshot();
			if (LcAnimationDebugUtil.isEnabled() && (previous == null
					|| previous.revision() != snapshot.playbackRevision() || previous.stopped() != snapshot.stopped())) {
				LcAnimationDebugUtil.modelLayer(snapshot, scope.model, layer.frame().timeSeconds(),
						layer.frame().fadeInProgress(), layer.frame().fadeOutProgress(),
						result.parts().get("right_arm"), result.parts().get("head"));
			}

			history.layers.put(layer.frame().snapshot().instanceId(), contribution);
		}

		history.displayed = result;
		history.frame = scope.frame;
		result.apply(scope.model);
	}

	private static LayerHistory contribution(LcPlayerPoseFrame.Layer layer, @Nullable LayerHistory previous,
	                                         LcPlayerModelPose target, LcPlayerModelPose mapped,
	                                         LcPlayerModelPose base, LcPlayerModelPose reference) {
		var playback = layer.frame();
		var snapshot = playback.snapshot();
		var settings = layer.policy().transitions();
		boolean continuing = previous != null && previous.revision() == snapshot.playbackRevision()
				&& previous.stopped() == snapshot.stopped();
		var direct = LcPlayerPoseContribution.of(target, layer.policy(),
				continuing ? previous.displayed() : null);
		LcPlayerPoseContribution origin;
		LcPlayerPoseContribution displayed;
		boolean replacement = false;
		if (snapshot.stopped()) {
			origin = previous == null ? LcPlayerPoseContribution.blend(LcPlayerPoseContribution.identity(), direct,
					playback.fadeInProgress(), settings.fadeInTransitionMode(), settings.fadeInRotationTransitionMode())
					: previous.stopped() ? previous.origin() : previous.displayed().freeze(previous.base(), reference);
			origin = origin.freeze(base, reference);
			displayed = LcPlayerPoseContribution.blend(origin, LcPlayerPoseContribution.identity(),
					playback.fadeOutProgress(), settings.fadeOutTransitionMode(), settings.fadeOutRotationTransitionMode(),
					continuing ? previous.displayed() : null);
		} else {
			boolean changed = previous != null && (previous.revision() != snapshot.playbackRevision() || previous.stopped());
			origin = previous == null ? LcPlayerPoseContribution.identity()
					: changed ? previous.displayed().freeze(previous.base(), reference) : previous.origin();
			replacement = changed || previous != null && previous.replacement();
			displayed = LcPlayerPoseContribution.blend(origin, direct, playback.fadeInProgress(),
					replacement ? settings.animationTransitionMode() : settings.fadeInTransitionMode(),
					replacement ? settings.rotationTransitionMode() : settings.fadeInRotationTransitionMode(),
					continuing ? previous.displayed() : null);
			if (playback.fadeInProgress() >= 1) {
				origin = direct;
			}
		}

		return new LayerHistory(snapshot.playbackRevision(), snapshot.stopped(), origin, displayed, replacement, mapped, base);
	}

	private static LcPlayerModelPose sources(Scope scope, LcPlayerAnimationPolicy policy, LcPlayerModelPose mapped) {
		var result = mapped;
		for (var stage : scope.stages) {
			double weight = policy.sourceWeight(stage.source());
			if (weight > 0) {
				result = result.applyDelta(stage.before(), stage.after(),
						(bone, channel) -> selected(policy, bone, channel) ? weight : 0);
			}
		}

		for (var entry : Objects.requireNonNull(scope.frame).externalSources().entrySet()) {
			double weight = policy.sourceWeight(entry.getKey());
			if (weight <= 0) {
				continue;
			}

			var reference = LcPlayerModelPose.initial(scope.model);
			var values = new LinkedHashMap<>(reference.parts());
			entry.getValue().bones().forEach((name, delta) -> {
				var part = values.get(name);
				if (part != null) {
					var angles = entry.getValue().rotationAngles(name);
					values.put(name, new PartPose(part.x() + (float) (delta.position().x * 16),
							part.y() + (float) (delta.position().y * 16), part.z() + (float) (delta.position().z * 16),
							part.xRot() + (float) angles.x, part.yRot() + (float) angles.y, part.zRot() + (float) angles.z,
							part.xScale() * (float) delta.scale().x, part.yScale() * (float) delta.scale().y,
							part.zScale() * (float) delta.scale().z));
				}
			});
			result = result.applyDelta(reference, new LcPlayerModelPose(values),
					(bone, channel) -> selected(policy, bone, channel) ? weight : 0);
		}
		return result;
	}

	private static LcPlayerModelPose mapped(LcPlayerModelPose initial, LcPlayerPoseFrame.Layer layer,
	                                        @Nullable LayerHistory previous) {
		var skeleton = LcClientAnimationResources.skeleton(layer.frame().snapshot().content());
		var values = new LinkedHashMap<>(initial.parts());
		var rootBind = LcPlayerModelPose.transform(initial.parts().get("root"));
		var root = skeleton.bones().containsKey("root")
				? deformation(skeleton.boneTransform(layer.pose(), "root", LcTransform.identity()),
				skeleton.boneTransform(LcSkeletalPose.empty(), "root", LcTransform.identity())).compose(rootBind)
				: rootBind;
		values.put("root", LcPlayerModelPose.pose(root));
		var effectiveRoot = initial.blend(new LcPlayerModelPose(values),
				(bone, channel) -> selected(layer.policy(), bone, channel) ? 1.0 : 0.0,
				layer.policy().transitions().rotationTransitionMode()).parts().get("root");
		var inverseRoot = LcPlayerModelPose.transform(effectiveRoot).matrix().invert();
		for (var bone : BODY_PARTS) {
			if (!skeleton.bones().containsKey(bone)) {
				continue;
			}

			var bind = LcPlayerModelPose.transform(initial.parts().get(bone));
			var delta = deformation(skeleton.boneTransform(layer.pose(), bone, LcTransform.identity()),
					skeleton.boneTransform(LcSkeletalPose.empty(), bone, LcTransform.identity()));
			var world = delta.matrix().mul(rootBind.matrix()).mul(bind.matrix());
			values.put(bone, LcPlayerModelPose.pose(LcTransform.fromMatrix(new Matrix4d(inverseRoot).mul(world))));
		}

		var result = new LcPlayerModelPose(values);
		if (previous != null && previous.revision() == layer.frame().snapshot().playbackRevision()) {
			result = result.unwrap(previous.mapped());
		}

		values = new LinkedHashMap<>(result.parts());
		for (var bone : skeleton.bones().keySet()) {
			if ((bone.equals("root") || BODY_PARTS.contains(bone)) && values.containsKey(bone)
					&& directAngles(skeleton, layer.pose(), bone, layer.policy())) {
				var part = values.get(bone);
				var bind = initial.parts().get(bone);
				var angles = layer.pose().rotationAngles(bone);
				values.put(bone, new PartPose(part.x(), part.y(), part.z(),
						bind.xRot() + (float) angles.x, bind.yRot() - (float) angles.y, bind.zRot() - (float) angles.z,
						part.xScale(), part.yScale(), part.zScale()));
			}
		}
		return new LcPlayerModelPose(values);
	}

	private static boolean directAngles(LcSkeleton skeleton, LcSkeletalPose pose, String name,
	                                    LcPlayerAnimationPolicy policy) {
		var bone = skeleton.bones().get(name);
		var ownScale = pose.get(name).scale();
		if (bone.rotation().lengthSqr() != 0 || ownScale.x < 0 || ownScale.y < 0 || ownScale.z < 0) {
			return false;
		}

		for (var parent = bone.parent(); parent != null; parent = skeleton.bones().get(parent).parent()) {
			var definition = skeleton.bones().get(parent);
			var scale = pose.get(parent).scale();
			if (definition.rotation().lengthSqr() != 0 || scale.x <= 0 || scale.x != scale.y || scale.y != scale.z) {
				return false;
			}

			boolean removedRoot = parent.equals("root") && selected(policy, "root", Channel.ROTATION);
			if (!removedRoot && pose.rotationAngles(parent).lengthSqr() != 0) {
				return false;
			}
		}
		return true;
	}

	private static LcTransform deformation(LcTransform animated, LcTransform bind) {
		var basis = new Matrix4d().translation(0, VANILLA_MODEL_HEIGHT, 0).scale(1, -1, -1);
		var delta = animated.matrix().mul(bind.matrix().invert());
		return LcTransform.fromMatrix(new Matrix4d(basis).mul(delta).mul(new Matrix4d(basis).invert()));
	}

	private static boolean selected(LcPlayerAnimationPolicy policy, String bone, Channel channel) {
		return policy.includesBone(bone) && policy.includesChannel(channel);
	}

	private static @Nullable Scope current() {
		return SCOPES.get().peek();
	}

	public static void clear() {
		SCOPES.remove();
	}

	private record NativeStage(Identifier source, LcPlayerModelPose before, LcPlayerModelPose after) {
	}

	public static class Scope {
		private final PlayerModel model;
		private final @Nullable LcPlayerPoseFrame frame;
		private final @Nullable ModelHistory history;
		private final List<NativeStage> stages = new ArrayList<>();
		private @Nullable Identifier source;
		private @Nullable LcPlayerModelPose before;

		private Scope(PlayerModel model, @Nullable LcPlayerPoseFrame frame) {
			this.model = model;
			this.frame = frame;
			this.history = frame == null || !frame.active() ? null : LcPlayerAnimationRuntime.modelHistory(frame, model);
		}

		private boolean active() {
			return frame != null && frame.active();
		}

		private void endPhase() {
			if (source != null && before != null && active()) {
				stages.add(new NativeStage(source, before, LcPlayerModelPose.capture(model)));
			}

			source = null;
			before = null;
		}

		public void close() {
			var scopes = SCOPES.get();
			if (scopes.peek() == this) {
				scopes.pop();
			}

			if (scopes.isEmpty()) {
				SCOPES.remove();
			}
		}
	}
}
