package org.unitego.lobecorp.client.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.unitego.lobecorp.animation.behavior.LcRootMotion;
import org.unitego.lobecorp.animation.player.LcAnimationTermination;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.pose.ILcAnimationQueries;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;
import org.unitego.lobecorp.network.tc.animation.LcAnimationStatePayload;
import org.unitego.lobecorp.util.animation.LcAnimationDebugUtil;
import org.unitego.lobecorp.util.animation.LcAnimationDebugUtil.Event;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// 客户端安装权威结果并进行视觉插值，不执行服务端行为或历史时间线回调。
public class LcClientAnimations {
	private static final Map<UUID, Frame> FRAMES = new LinkedHashMap<>();
	private static final Map<UUID, LcAnimationStatePayload> PENDING = new HashMap<>();

	public static void accept(LcAnimationStatePayload payload) {
		var content = LcClientAnimationContents.get(payload.contentVersion());
		if (content == null) {
			PENDING.put(payload.instanceId(), payload);
			return;
		}

		accept(payload.snapshot(content));
	}

	public static void accept(LcAnimationSnapshot snapshot) {
		if (snapshot.stopped() && !snapshot.termination().allowsFade()) {
			if (FRAMES.containsKey(snapshot.instanceId()) || PENDING.containsKey(snapshot.instanceId())) {
				LcAnimationDebugUtil.state(Event.CLIENT_STATE, snapshot);
			}

			remove(snapshot.instanceId());
			return;
		}

		FRAMES.compute(snapshot.instanceId(), (_, previous) -> {
			if (previous == null || previous.current.playbackRevision() != snapshot.playbackRevision()
					|| previous.current.stopped() != snapshot.stopped()) {
				LcAnimationDebugUtil.state(Event.CLIENT_STATE, snapshot);
			}

			return new Frame(previous == null ? snapshot : previous.current, snapshot, gameTime());
		});
	}

	public static void contentReady(String version) {
		for (var payload : List.copyOf(PENDING.values())) {
			if (payload.contentVersion().equals(version)) {
				PENDING.remove(payload.instanceId());
				accept(payload);
			}
		}
	}

	public static @Nullable LcAnimationSnapshot snapshot(UUID id) {
		var frame = FRAMES.get(id);
		return frame == null ? null : frame.current;
	}

	public static List<LcAnimationSnapshot> snapshots() {
		pruneExpired();
		return FRAMES.values().stream().map(frame -> frame.current).filter(snapshot -> !snapshot.stopped()).toList();
	}

	/// 返回本次提取可冻结的玩家帧；权重已含策略与唯一淡入淡出，渲染器不得再次乘淡出。
	public static List<PlayerFrame> playerFrames(int entityId, float partialTick) {
		pruneExpired();
		List<PlayerFrame> result = new ArrayList<>();
		for (var frame : FRAMES.values()) {
			var snapshot = frame.current;
			var policy = snapshot.playerPolicy();
			if (snapshot.ownerEntityId() != entityId || policy == null) {
				continue;
			}

			double time = snapshot.timeSeconds();
			var overrides = snapshot.poseOverrides();
			if (!snapshot.stopped() && frame.previous.playbackRevision() == snapshot.playbackRevision()) {
				var progress = weight(frame, partialTick);
				time = frame.previous.timeSeconds() + (time - frame.previous.timeSeconds()) * progress;
				overrides = interpolate(frame.previous.poseOverrides(), overrides, progress);
			}

			double fadeIn = fadeInProgress(frame, partialTick, policy);
			double fadeOut = fadeOutProgress(frame, partialTick, policy);
			result.add(new PlayerFrame(snapshot, time, policy.weight() * fadeIn * (1 - fadeOut), overrides,
					fadeIn, fadeOut));
		}

		result.sort(Comparator.comparingInt((PlayerFrame frame) -> Objects.requireNonNull(frame.snapshot().playerPolicy()).priority())
				.thenComparing(frame -> frame.snapshot().instanceId()));
		return List.copyOf(result);
	}

	private static double fadeInProgress(Frame frame, float partialTick, LcPlayerAnimationPolicy policy) {
		double now = renderServerTick(frame, partialTick);
		int fadeIn = policy.transitions().fadeInTicks();
		double entryTime = frame.current.stopped() ? Math.min(now, frame.current.stoppedTick()) : now;
		return fadeIn <= 0 ? 1 : Math.clamp((entryTime - frame.current.startedTick()) / fadeIn, 0, 1);
	}

	private static double fadeOutProgress(Frame frame, float partialTick, LcPlayerAnimationPolicy policy) {
		if (!frame.current.stopped()) {
			return 0;
		}

		int fadeOut = policy.transitions().fadeOutTicks();
		return fadeOut <= 0 ? 1 : Math.clamp((renderServerTick(frame, partialTick) - frame.current.stoppedTick()) / fadeOut, 0, 1);
	}

	private static double renderServerTick(Frame frame, float partialTick) {
		return frame.current.serverTick() + Math.max(0, gameTime() - frame.receivedTick + partialTick);
	}

	private static void pruneExpired() {
		FRAMES.values().removeIf(frame -> {
			boolean expired = frame.current.stopped()
					&& (frame.current.playerPolicy() == null || !frame.current.termination().allowsFade()
					|| renderServerTick(frame, 0) >= frame.current.stoppedTick()
					+ frame.current.playerPolicy().transitions().fadeOutTicks());
			if (expired) {
				LcAnimationDebugUtil.state(Event.CLIENT_EXPIRED, frame.current);
			}

			return expired;
		});
	}

	public record PlayerFrame(LcAnimationSnapshot snapshot, double timeSeconds, double weight,
	                          LcSkeletalPose poseOverrides, double fadeInProgress, double fadeOutProgress) {
	}

	public static @Nullable LcSkeletalPose pose(UUID id) {
		var snapshot = snapshot(id);
		return snapshot == null ? null : snapshot.pose();
	}

	public static @Nullable LcSkeletalPose entityPose(int entityId) {
		return visualPoseForEntity(entityId, 1);
	}

	public static boolean hasEntityAnimation(int entityId) {
		return FRAMES.values().stream().anyMatch(frame -> frame.current.ownerEntityId() == entityId);
	}

	public static @Nullable LcSkeletalPose visualPoseForEntity(int entityId, float partialTick) {
		LcSkeletalPose result = null;
		for (var entry : FRAMES.entrySet()) {
			if (entry.getValue().current.ownerEntityId() != entityId) {
				continue;
			}

			var pose = visualPose(entry.getKey(), partialTick);
			if (pose != null) {
				result = result == null ? pose : merge(result, pose);
			}
		}

		return result;
	}

	public static @Nullable LcSkeletalPose visualPose(UUID id, float partialTick) {
		var frame = FRAMES.get(id);
		if (frame == null) {
			return null;
		}

		boolean revised = frame.previous.playbackRevision() != frame.current.playbackRevision();
		var weight = revised ? 1 : weight(frame, partialTick);
		var clip = LcClientAnimationResources.visualClip(frame.current.content(), frame.current.animationName());
		if (clip == null) {
			return revised ? frame.current.pose() : interpolateLoopPose(frame, weight);
		}

		var time = revised ? frame.current.timeSeconds()
				: frame.previous.timeSeconds() + (frame.current.timeSeconds() - frame.previous.timeSeconds()) * weight;
		var duration = clip.durationSeconds();
		var sampleTime = duration <= 0 ? 0 : clip.looping()
				? time - Math.floor(time / duration) * duration : Math.clamp(time, 0, duration);
		var pose = clip.sample(sampleTime, new VisualQueries(frame.current.parameters()));
		return removeRootChannels(pose, frame.current);
	}

	private static LcSkeletalPose interpolateLoopPose(Frame frame, double weight) {
		var pose = interpolate(frame.previous.pose(), frame.current.pose(), weight);
		var clip = LcClientAnimationResources.contentClip(frame.current.content(), frame.current.animationName());
		var duration = clip.durationSeconds();
		if (!clip.looping() || duration <= 0
				|| Math.floor(frame.previous.timeSeconds() / duration) == Math.floor(frame.current.timeSeconds() / duration)) {
			return pose;
		}

		// 循环边界按实际资源轨迹采样旋转，避免整圈 Euler 回零被插成反向半圈。
		var time = frame.previous.timeSeconds() + (frame.current.timeSeconds() - frame.previous.timeSeconds()) * weight;
		var sampled = clip.sample(time - Math.floor(time / duration) * duration, new VisualQueries(frame.current.parameters()));
		var bones = new LinkedHashMap<>(pose.bones());
		var angles = new LinkedHashMap<>(pose.eulerRotations());
		for (var name : pose.bones().keySet()) {
			if (frame.previous.poseOverrides().bones().containsKey(name) || frame.current.poseOverrides().bones().containsKey(name)
					|| !sampled.bones().containsKey(name)) {
				continue;
			}

			// Java 声明拥有的骨骼保持同步绝对结果；位移和缩放继续使用原快照插值。
			var transform = pose.get(name);
			bones.put(name, new LcTransform(transform.position(), sampled.get(name).rotation(), transform.scale()));
			angles.put(name, sampled.rotationAngles(name));
		}

		return new LcSkeletalPose(bones, angles);
	}

	public static @Nullable LcTransform worldTransform(UUID id, float partialTick) {
		var frame = FRAMES.get(id);
		if (frame == null) {
			return null;
		}

		return frame.previous.playbackRevision() != frame.current.playbackRevision() ? frame.current.worldTransform()
				: frame.previous.worldTransform().interpolate(frame.current.worldTransform(), weight(frame, partialTick));
	}

	private static LcSkeletalPose removeRootChannels(LcSkeletalPose pose, LcAnimationSnapshot snapshot) {
		if (snapshot.rootBone() == null) {
			return pose;
		}

		var root = pose.get(snapshot.rootBone());
		var position = root.position();
		var axes = snapshot.rootAxes();
		var stripped = new Vec3(axes.contains(LcRootMotion.Axis.X) ? 0 : position.x,
				axes.contains(LcRootMotion.Axis.Y) ? 0 : position.y, axes.contains(LcRootMotion.Axis.Z) ? 0 : position.z);
		var angles = pose.rotationAngles(snapshot.rootBone());
		return pose.withBone(snapshot.rootBone(), new LcTransform(stripped, root.rotation(), root.scale()))
				.withEulerRotation(snapshot.rootBone(), angles);
	}

	private static LcSkeletalPose interpolate(LcSkeletalPose previous, LcSkeletalPose current, double weight) {
		var names = new LinkedHashSet<>(previous.bones().keySet());
		names.addAll(current.bones().keySet());
		Map<String, LcTransform> bones = new LinkedHashMap<>();
		Map<String, Vec3> angles = new LinkedHashMap<>();
		for (var name : names) {
			var transform = previous.get(name).interpolate(current.get(name), weight);
			var rotation = previous.rotationAngles(name).lerp(current.rotationAngles(name), weight);
			bones.put(name, new LcTransform(transform.position(), new Quaternionf().rotationZYX(
					(float) rotation.z, (float) rotation.y, (float) rotation.x), transform.scale()));
			angles.put(name, rotation);
		}

		return new LcSkeletalPose(bones, angles);
	}

	private static LcSkeletalPose merge(LcSkeletalPose first, LcSkeletalPose second) {
		var bones = new LinkedHashMap<>(first.bones());
		var angles = new LinkedHashMap<>(first.eulerRotations());
		bones.putAll(second.bones());
		for (var name : second.bones().keySet()) {
			angles.remove(name);
		}

		angles.putAll(second.eulerRotations());
		return new LcSkeletalPose(bones, angles);
	}

	private static double weight(Frame frame, float partialTick) {
		return Math.clamp(gameTime() - frame.receivedTick + partialTick, 0, 1);
	}

	private static long gameTime() {
		var level = Minecraft.getInstance().level;
		return level == null ? 0 : level.getGameTime();
	}

	public static void remove(UUID id) {
		FRAMES.remove(id);
		PENDING.remove(id);
	}

	public static void remove(UUID id, LcAnimationTermination reason) {
		boolean removed = PENDING.remove(id) != null;
		var frame = FRAMES.get(id);
		if (!reason.allowsFade() || frame == null || !frame.current.stopped()) {
			FRAMES.remove(id);
			removed |= frame != null;
		}

		if (removed) {
			LcAnimationDebugUtil.removed(id, reason);
		}

		pruneExpired();
	}

	public static void clearWorld() {
		LcAnimationDebugUtil.clearWorld(FRAMES.size(), PENDING.size());
		FRAMES.clear();
		PENDING.clear();
		LcAnimationDisplays.clear();
	}

	public static void clear() {
		clearWorld();
		LcClientAnimationContents.clear();
		LcClientAnimationResources.clear();
	}

	private record Frame(LcAnimationSnapshot previous, LcAnimationSnapshot current, long receivedTick) {
	}

	/// 每次视觉采样从权威参数副本开始，变量赋值不随帧率或附着数量累积。
	private static class VisualQueries implements ILcAnimationQueries {
		private final Map<String, Double> variables = new HashMap<>();

		private VisualQueries(Map<String, Double> parameters) {
			parameters.forEach((name, value) -> variables.put(normalize(name), value));
		}

		@Override
		public double get(String name) {
			var key = normalize(name);
			var value = variables.get(key);
			if (value != null) {
				return value;
			}

			if (key.startsWith("v.") || key.startsWith("t.")) {
				return variables.getOrDefault(key.substring(2), 0.0);
			}

			throw new IllegalArgumentException(name);
		}

		@Override
		public void set(String name, double value) {
			var key = normalize(name);
			if (!key.startsWith("v.") && !key.startsWith("t.")) {
				throw new IllegalArgumentException(name);
			}

			variables.put(key, value);
		}

		private static String normalize(String name) {
			return name.replace("query.", "q.").replace("variable.", "v.").replace("temp.", "t.");
		}
	}
}
