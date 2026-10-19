package org.unitego.lobecorp.client.animation;

import com.geckolib.animation.state.ControllerState;
import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.animation.object.EasingType;

import java.util.function.BiConsumer;

import java.util.LinkedHashMap;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/// 仅缓存本控制器的姿态贡献，不缓存控制器时钟、事件或其他层的最终姿态。
public class LcAnimationLodCache {
	private static final double TICKS_PER_SECOND = 20;
	private final Map<String, Contribution> values = new LinkedHashMap<>();
	private long bucket = Long.MIN_VALUE;
	private int revision;
	private @Nullable Animation animation, previous;
	private @Nullable EasingType easing;
	private boolean reusable;
	private int sampleFps;
	private int transitionTicks;
	private boolean transitioning, additive;
	private double sampledAge = Double.NEGATIVE_INFINITY, sampledTime = Double.NEGATIVE_INFINITY;

	public record Request(LcAnimationLodCache cache, int fps, int revision) {
	}

	private record Before(GeoBone bone, BoneSnapshot target, float[] values) {
	}

	private record Contribution(GeoBone bone, float[] values) {
	}

	public void apply(ControllerState state, BoneSnapshots snapshots, Request request, BiConsumer<ControllerState, BoneSnapshots> original) {
		var age = state.renderState().getAnimatableAge();
		var now = (long) Math.floor(age * request.fps() / TICKS_PER_SECOND);
		var point = state.animationPoint();
		var prev = state.prevAnimationPoint();
		var prevAnimation = prev == null ? null : prev.animation();
		boolean transition = state.transitionTime() >= 0;
		if (reusable && sampleFps == request.fps() && bucket == now && revision == request.revision() && animation == point.animation() && previous == prevAnimation
				&& easing == state.easingOverride() && additive == state.additive() && transitioning == transition
				&& transitionTicks == state.transitionTicks() && age >= sampledAge && point.animTime() >= sampledTime
				&& canReplay(snapshots)) {
			replay(snapshots, state.additive());
			return;
		}

		var touched = new LinkedHashMap<String, Before>();
		original.accept(state, name -> snapshots.get(name).map(snapshot -> {
			touched.computeIfAbsent(name, _ -> new Before(snapshot.getBone(), snapshot, read(snapshot)));
			return snapshot;
		}));
		values.clear();
		reusable = true;
		sampleFps = request.fps();
		for (var entry : touched.entrySet()) {
			var before = entry.getValue();
			var result = read(before.target());
			if (state.additive()) {
				for (int index = 0; index < result.length; index++) {
					var input = before.values()[index];
					if (index < 3) {
						if (input == 0) {
							reusable = false;
							result[index] = 1;
						} else {
							result[index] /= input;
						}
					} else {
						result[index] -= input;
					}
				}
			}
			values.put(entry.getKey(), new Contribution(before.bone(), result));
		}
		bucket = now;
		revision = request.revision();
		animation = point.animation();
		previous = prevAnimation;
		easing = state.easingOverride();
		additive = state.additive();
		transitioning = transition;
		transitionTicks = state.transitionTicks();
		sampledAge = age;
		sampledTime = point.animTime();
	}

	private boolean canReplay(BoneSnapshots snapshots) {
		for (var entry : values.entrySet()) {
			var snapshot = snapshots.get(entry.getKey()).orElse(null);
			if (snapshot == null || snapshot.getBone() != entry.getValue().bone()) {
				return false;
			}
		}
		return !values.isEmpty();
	}

	private void replay(BoneSnapshots snapshots, boolean additive) {
		for (var entry : values.entrySet()) {
			var target = snapshots.get(entry.getKey()).orElseThrow();
			var v = entry.getValue().values();
			if (additive) {
				target.setScale(target.getScaleX() * v[0], target.getScaleY() * v[1], target.getScaleZ() * v[2]);
				target.setRotation(target.getRotX() + v[3], target.getRotY() + v[4], target.getRotZ() + v[5]);
				target.setTranslation(target.getTranslateX() + v[6], target.getTranslateY() + v[7], target.getTranslateZ() + v[8]);
			} else {
				target.setScale(v[0], v[1], v[2]);
				target.setRotation(v[3], v[4], v[5]);
				target.setTranslation(v[6], v[7], v[8]);
			}
		}
	}

	private static float[] read(BoneSnapshot value) {
		return new float[]{value.getScaleX(), value.getScaleY(), value.getScaleZ(), value.getRotX(), value.getRotY(),
				value.getRotZ(), value.getTranslateX(), value.getTranslateY(), value.getTranslateZ()};
	}
}
