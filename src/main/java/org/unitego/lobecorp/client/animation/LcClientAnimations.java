package org.unitego.lobecorp.client.animation;

import com.geckolib.cache.GeckoLibResources;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.behavior.LcRootMotion;
import org.unitego.lobecorp.animation.player.LcAnimationTermination;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.pose.ILcAnimationQueries;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.animation.runtime.LcAnimationInstance;
import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.client.photon.performance.QueenBeamPerformanceTrial;
import org.unitego.lobecorp.config.client.VisualQualityConfig;
import org.unitego.lobecorp.network.tc.animation.LcAnimationStatePayload;
import org.unitego.lobecorp.registry.animation.TheQueenOfHatredAnimationDefinitions;
import org.unitego.lobecorp.util.animation.LcAnimationDebugUtil;
import org.unitego.lobecorp.util.animation.LcAnimationDebugUtil.Event;
import org.unitego.lobecorp.util.animation.LcVisualQualityUtil;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredBeamAnimation;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredBeamAnimation.Phase;

import java.util.*;
import java.util.function.Predicate;

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

			long now = gameTime();
			boolean resetClock = previous == null || previous.current.playbackRevision() != snapshot.playbackRevision()
					|| previous.current.paused() != snapshot.paused()
					|| Double.compare(previous.current.speed(), snapshot.speed()) != 0;
			return new Frame(previous == null ? snapshot : previous.current, snapshot, now,
					resetClock ? snapshot.timeSeconds() : previous.clockSeconds,
					resetClock ? now : previous.clockTick,
					previous == null ? snapshot.parameters().getOrDefault("q.life_time", 0.0)
							* LcAnimationInstance.TICKS_PER_SECOND - now : previous.ageOffset,
					previous == null ? TheQueenOfHatredAnimationDefinitions.isBeam(snapshot.definitionId())
							? new CodePoseCache() : null : previous.codePose);
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

	public static @Nullable LcAnimationSnapshot snapshotForEntity(int entityId, Predicate<LcAnimationSnapshot> filter) {
		for (var frame : FRAMES.values()) {
			if (frame.current.ownerEntityId() == entityId && !frame.current.stopped() && filter.test(frame.current)) {
				return frame.current;
			}
		}
		return null;
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

	/// 使用既有收包时钟外推显示阶段，不依赖动作片段是否已到末帧。
	public static double renderServerTick(UUID id, float partialTick) {
		var frame = FRAMES.get(id);
		return frame == null ? 0 : renderServerTick(frame, partialTick);
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

	public static @Nullable LcSkeletalPose pose(UUID id) {
		var snapshot = snapshot(id);
		return snapshot == null ? null : snapshot.pose();
	}

	public static @Nullable LcSkeletalPose entityPose(int entityId) {
		return visualPoseForEntity(entityId, 1);
	}

	/// 只查询策略资格，不采样骨骼；已停止但仍淡出的玩家帧继续参与判断。
	public static boolean hasPlayerAnimation(int entityId, boolean requireFirstPersonArms) {
		pruneExpired();
		for (var frame : FRAMES.values()) {
			var snapshot = frame.current;
			var policy = snapshot.playerPolicy();
			if (snapshot.ownerEntityId() == entityId && policy != null
					&& (!requireFirstPersonArms || policy.firstPersonArms() && policy.weight() > 0)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasEntityAnimation(int entityId) {
		return FRAMES.values().stream().anyMatch(frame -> frame.current.ownerEntityId() == entityId);
	}

	public static boolean hasEntityAnimation(int entityId, Predicate<LcAnimationSnapshot> predicate) {
		for (var frame : FRAMES.values()) {
			if (frame.current.ownerEntityId() == entityId && predicate.test(frame.current)) {
				return true;
			}
		}

		return false;
	}

	/// 女皇代码动作与仍在推进的基础控制器混合；过渡时钟不依赖网络包间隔或已保持的片段时间。
	public static double beamPoseWeight(int entityId, float partialTick) {
		for (var frame : FRAMES.values()) {
			var snapshot = frame.current;
			if (snapshot.ownerEntityId() != entityId || !TheQueenOfHatredAnimationDefinitions.isBeam(snapshot.definitionId())) {
				continue;
			}
			var values = snapshot.parameters();
			var phase = values.getOrDefault(TheQueenOfHatredBeamAnimation.PHASE, 0.0).intValue();
			if (phase == Phase.OUTPUT.ordinal()) {
				return 1;
			}
			double tick = snapshot.paused() ? snapshot.serverTick() : renderServerTick(frame, partialTick);
			var fps = codeSampleFps(frame);
			if (fps > 0) {
				tick = Math.floor(tick * fps / LcAnimationInstance.TICKS_PER_SECOND) * LcAnimationInstance.TICKS_PER_SECOND / fps;
			}
			var elapsed = Math.max(0, tick - values.getOrDefault(TheQueenOfHatredBeamAnimation.PHASE_STARTED_TICK, tick));
			var ticks = values.getOrDefault(phase == Phase.RECOVERY.ordinal()
					? TheQueenOfHatredBeamAnimation.RECOVERY_TICKS : TheQueenOfHatredBeamAnimation.WINDUP_TICKS, 0.0);
			var progress = ticks <= 0 ? 1 : Math.clamp(elapsed / ticks, 0, 1);
			var eased = progress * progress * (3 - 2 * progress);
			return phase == Phase.RECOVERY.ordinal() ? 1 - eased : eased;
		}
		return 1;
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

		var fps = codeSampleFps(frame);
		var cache = frame.codePose;
		double tick = gameTime() + (double) partialTick;
		long bucket = fps > 0 ? (long) Math.floor(tick * fps / LcAnimationInstance.TICKS_PER_SECOND) : 0;
		var generation = GeckoLibResources.getBakedModels();
		var phase = frame.current.parameters().getOrDefault(TheQueenOfHatredBeamAnimation.PHASE, 0.0);
		if (fps > 0 && cache != null && cache.pose != null && cache.fps == fps && cache.bucket == bucket
				&& cache.revision == frame.current.playbackRevision() && Double.compare(cache.phase, phase) == 0
				&& cache.generation == generation) {
			return cache.pose;
		}
		var pose = sampleVisualPose(frame, partialTick);
		if (cache != null) {
			cache.pose = fps > 0 ? pose : null;
			cache.fps = fps;
			cache.bucket = bucket;
			cache.revision = frame.current.playbackRevision();
			cache.phase = phase;
			cache.generation = generation;
		}
		return pose;
	}

	/// 只为已替代 action 控制器的本模组女皇代码动作沿用其距离设置与豁免。
	private static int codeSampleFps(Frame frame) {
		var cache = frame.codePose;
		var level = Minecraft.getInstance().level;
		if (cache == null || level == null || !VisualQualityConfig.ANIMATIONS.get()
				|| !(level.getEntity(frame.current.ownerEntityId()) instanceof TheQueenOfHatred queen)) {
			return 0;
		}
		var id = BuiltInRegistries.ENTITY_TYPE.getKey(queen.getType());
		var excluded = VisualQualityConfig.ANIMATION_EXEMPT.get();
		if (!id.getNamespace().equals(Lobecorp.NAMESPACE) || !excluded.isEmpty() && excluded.contains(id.toString())
				|| VisualQualityConfig.CONTROLLER_EXEMPT.get().contains("action")) {
			cache.band = 0;
			return 0;
		}
		var distance = Minecraft.getInstance().gameRenderer.getMainCamera().position().distanceTo(queen.position());
		cache.band = LcVisualQualityUtil.band(distance, cache.band, VisualQualityConfig.ANIMATION_NEAR.get(),
				VisualQualityConfig.ANIMATION_FAR.get(), VisualQualityConfig.HYSTERESIS.get());
		return cache.band == 0 ? 0 : cache.band == 1 ? VisualQualityConfig.MEDIUM_FPS.get() : VisualQualityConfig.FAR_FPS.get();
	}

	private static LcSkeletalPose sampleVisualPose(Frame frame, float partialTick) {
		boolean revised = frame.previous.playbackRevision() != frame.current.playbackRevision();
		var weight = revised ? 1 : weight(frame, partialTick);
		var clip = LcClientAnimationResources.visualClip(frame.current.content(), frame.current.animationName());
		if (clip == null) {
			return revised ? frame.current.pose() : interpolateLoopPose(frame, weight);
		}

		var time = revised ? frame.current.timeSeconds()
				: frame.previous.timeSeconds() + (frame.current.timeSeconds() - frame.previous.timeSeconds()) * weight;
		if (!frame.current.paused() && !frame.current.stopped()) {
			time = frame.clockSeconds + Math.max(0, gameTime() - frame.clockTick + partialTick)
					* frame.current.speed() / LcAnimationInstance.TICKS_PER_SECOND;
		}
		var duration = clip.durationSeconds();
		var sampleTime = duration <= 0 ? 0 : clip.looping()
				? time - Math.floor(time / duration) * duration : Math.clamp(time, 0, duration);
		var queries = new VisualQueries(frame.current.parameters());
		queries.useVisualTime(sampleTime, (frame.ageOffset + gameTime() + partialTick) / LcAnimationInstance.TICKS_PER_SECOND);
		var pose = clip.sample(sampleTime, queries);
		if (frame.current.poseOverrides().bones().isEmpty() && frame.previous.poseOverrides().bones().isEmpty()) {
			return removeRootChannels(pose, frame.current);
		}

		var overrides = revised ? frame.current.poseOverrides()
				: interpolate(frame.previous.poseOverrides(), frame.current.poseOverrides(), weight);
		if (TheQueenOfHatredAnimationDefinitions.isBeam(frame.current.definitionId())) {
			var previousPitch = frame.previous.parameters().getOrDefault("q.head_x_rotation", 0.0);
			var currentPitch = frame.current.parameters().getOrDefault("q.head_x_rotation", previousPitch);
			var pitch = (float) (previousPitch + (currentPitch - previousPitch) * weight);
			var visualAim = TheQueenOfHatredAnimationDefinitions.aimPose(pose, pitch);
			QueenBeamPerformanceTrial.renderedPose(frame.current, sampleTime,
					beamPoseWeight(frame.current.ownerEntityId(), partialTick), gameTime() + (double) partialTick);
			overrides = overrides.withBone(TheQueenOfHatredAnimationDefinitions.UPPER_BODY,
					visualAim.get(TheQueenOfHatredAnimationDefinitions.UPPER_BODY));
		}
		return removeRootChannels(merge(pose, overrides), frame.current);
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
		LcClientAnimationResources.clearConnectionContent();
	}

	public record PlayerFrame(LcAnimationSnapshot snapshot, double timeSeconds, double weight,
	                          LcSkeletalPose poseOverrides, double fadeInProgress, double fadeOutProgress) {
	}

	private static class CodePoseCache {
		private @Nullable LcSkeletalPose pose;
		private @Nullable Object generation;
		private long bucket, revision;
		private int band, fps;
		private double phase;
	}

	private record Frame(LcAnimationSnapshot previous, LcAnimationSnapshot current, long receivedTick,
	                     double clockSeconds, long clockTick, double ageOffset, @Nullable CodePoseCache codePose) {
	}

	/// 每次视觉采样从权威参数副本开始，变量赋值不随帧率或附着数量累积。
	private static class VisualQueries implements ILcAnimationQueries {
		private final Map<String, Double> variables = new HashMap<>();

		private VisualQueries(Map<String, Double> parameters) {
			parameters.forEach((name, value) -> variables.put(normalize(name), value));
		}

		private static String normalize(String name) {
			return name.replace("query.", "q.").replace("variable.", "v.").replace("temp.", "t.");
		}

		private void useVisualTime(double time, double age) {
			variables.put("q.anim_time", time);
			variables.put("q.life_time", age);
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
	}
}
