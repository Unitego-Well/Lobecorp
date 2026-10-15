package org.unitego.lobecorp.animation.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.attachment.ILcTransformTarget;
import org.unitego.lobecorp.attachment.ILcAttachmentAnchor;
import org.unitego.lobecorp.attachment.LcAttachments;
import org.unitego.lobecorp.attachment.LcAttachmentBinding;
import org.unitego.lobecorp.animation.behavior.LcRootMotion;
import org.unitego.lobecorp.animation.definition.LcAnimationDefinition;
import org.unitego.lobecorp.animation.player.LcAnimationTermination;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.pose.LcAnimationClip;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.animation.pose.LcSkeleton;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.util.animation.LcAnimationDebugUtil;
import org.unitego.lobecorp.util.animation.LcAnimationDebugUtil.Event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/// 一次服务端权威播放；固定资源版本，运行期参数与显式内容修改仍可改变本实例。
public class LcAnimationInstance {
	/// Minecraft 逻辑时钟的每秒刻数，资源时间使用秒。
	public static final double TICKS_PER_SECOND = 20;
	private static final String DIFFERENT_LEVEL = "Animation attachment must belong to the same level";
	private static final String INVALID_SPEED = "Animation speed must be finite and non-negative";
	private static final String INVALID_TIME = "Animation time must be finite and non-negative";
	private static final String ROOT_REQUIRED = "Root motion mapping requires a top-level skeleton bone";
	private static final String INSTANCE_STOPPED = "Animation instance has already stopped";
	private static final String READ_ONLY_PARAMETER = "Animation parameter must be a variable: ";
	private static final String TERMINATION_REQUIRED = "Stopped animation requires a termination reason";

	private final UUID id = UUID.randomUUID();
	private final ServerLevel level;
	@Nullable
	private final Entity sourceEntity;
	private final ILcTransformTarget ownerTarget;
	private final Identifier definitionId;
	private final LcAnimationDefinition definition;
	private final Map<String, Double> parameters = new LinkedHashMap<>();
	private final List<LcAttachmentBinding> attachments = new ArrayList<>();
	private LcAnimationContent content;
	private LcSkeleton skeleton;
	private LcAnimationClip clip;
	private LcSkeletalPose rawPose;
	private LcSkeletalPose pose = LcSkeletalPose.empty();
	private LcSkeletalPose poseOverrides;
	@Nullable
	private LcPlayerAnimationPolicy playerPolicy;
	private long startedTick;
	private long stoppedTick = -1;
	private long playbackRevision;
	private LcAnimationTermination termination = LcAnimationTermination.NONE;
	private double timeSeconds;
	private double speed = 1;
	private boolean paused;
	private boolean stopped;
	private boolean finishPending;
	private long lastTick = Long.MIN_VALUE;
	private long timelineRevision;
	private double lastDeltaSeconds;

	public LcAnimationInstance(ServerLevel level, @Nullable Entity sourceEntity, ILcTransformTarget ownerTarget,
	                           Identifier definitionId, LcAnimationDefinition definition, LcAnimationContent source) {
		if (ownerTarget.level() != level || sourceEntity != null && sourceEntity.level() != level) {
			throw new IllegalArgumentException(DIFFERENT_LEVEL);
		}

		this.level = level;
		this.sourceEntity = sourceEntity;
		this.ownerTarget = ownerTarget;
		this.definitionId = definitionId;
		this.definition = definition;
		this.playerPolicy = definition.playerPolicy();
		this.startedTick = level.getGameTime();
		installContent(definition.derive(source));
		var sampled = sampleResult(0, 0, parameters);
		rawPose = sampled.pose();
		poseOverrides = consumeOverrideRootChannels(sampled.overrides());
		pose = consumeRootChannels(rawPose);
	}

	public UUID id() {
		return id;
	}

	public ServerLevel level() {
		return level;
	}

	@Nullable
	public Entity sourceEntity() {
		return sourceEntity;
	}

	public ILcTransformTarget ownerTarget() {
		return ownerTarget;
	}

	public LcAnimationDefinition definition() {
		return definition;
	}

	public double timeSeconds() {
		return timeSeconds;
	}

	public LcSkeletalPose pose() {
		return pose;
	}

	public boolean isStopped() {
		return stopped;
	}

	public @Nullable LcPlayerAnimationPolicy playerPolicy() {
		return playerPolicy;
	}

	/// 现有 at/onTick 回调可切换本次视觉策略，资源时间与实际行为不因此重启。
	public void setPlayerPolicy(@Nullable LcPlayerAnimationPolicy policy) {
		if (stopped) {
			throw new IllegalStateException(INSTANCE_STOPPED);
		}

		if (!Objects.equals(playerPolicy, policy)) {
			playerPolicy = policy;
			markVisualRevision();
			logDebug(Event.SERVER_POLICY);
		}
	}

	/// 参数变化重建当前位置的采样基线，不把轨迹幅度的变化补成瞬移。
	public void setParameter(String name, double value) {
		String parameter = name.replace("variable.", "v.").replace("temp.", "t.");

		if (parameter.startsWith("q.") || parameter.startsWith("query.")) {
			throw new IllegalArgumentException(READ_ONLY_PARAMETER + name);
		}

		if (!parameter.startsWith("v.") && !parameter.startsWith("t.")) {
			parameter = "v." + parameter;
		}

		var previousParameters = new LinkedHashMap<>(parameters);
		parameters.put(parameter, value);

		try {
			var sampled = sampleResult(sampleTime(timeSeconds), 0, new LinkedHashMap<>(parameters));
			var consumed = consumeRootChannels(sampled.pose());
			rawPose = sampled.pose();
			poseOverrides = consumeOverrideRootChannels(sampled.overrides());
			pose = consumed;
		} catch (RuntimeException | Error exception) {
			parameters.clear();
			parameters.putAll(previousParameters);
			throw exception;
		}
	}

	public Map<String, Double> parameters() {
		return Map.copyOf(parameters);
	}

	public void setSpeed(double value) {
		if (!Double.isFinite(value) || value < 0) {
			throw new IllegalArgumentException(INVALID_SPEED);
		}

		speed = value;
	}

	public void setPaused(boolean value) {
		paused = value;
	}

	/// 显式跳转不重放历史动作，也不把时间差补为实际位移。
	public void seek(double seconds) {
		if (!Double.isFinite(seconds) || seconds < 0) {
			throw new IllegalArgumentException(INVALID_TIME);
		}

		double previousTime = timeSeconds;
		timeSeconds = seconds;

		try {
			var sampled = sampleResult(sampleTime(seconds), 0, new LinkedHashMap<>(parameters));
			var consumed = consumeRootChannels(sampled.pose());
			rawPose = sampled.pose();
			poseOverrides = consumeOverrideRootChannels(sampled.overrides());
			pose = consumed;
			finishPending = false;
			lastDeltaSeconds = 0;
			timelineRevision++;
			markVisualRevision();
		} catch (RuntimeException | Error exception) {
			timeSeconds = previousTime;
			throw exception;
		}
	}

	/// 修改生成本实例的新内容版本，不触碰数据包底稿或其他播放实例。
	public void editContent(Consumer<JsonObject> modelEdit, Consumer<JsonObject> animationEdit) {
		var model = JsonParser.parseString(content.modelJson()).getAsJsonObject();
		var animation = JsonParser.parseString(content.animationJson()).getAsJsonObject();
		modelEdit.accept(model);
		animationEdit.accept(animation);
		var previousContent = content;
		var previousSkeleton = skeleton;
		var previousClip = clip;

		try {
			installContent(LcAnimationContent.of(content.modelId(), content.animationId(), model.toString(), animation.toString()));
			var sampled = sampleResult(sampleTime(timeSeconds), 0, new LinkedHashMap<>(parameters));
			var consumed = consumeRootChannels(sampled.pose());
			rawPose = sampled.pose();
			poseOverrides = consumeOverrideRootChannels(sampled.overrides());
			pose = consumed;
			finishPending = false;
			timelineRevision++;
			markVisualRevision();
		} catch (RuntimeException | Error exception) {
			content = previousContent;
			skeleton = previousSkeleton;
			clip = previousClip;
			throw exception;
		}
	}

	public LcAnimationContext context() {
		var context = new LcAnimationContext(this, sampleTime(timeSeconds), lastDeltaSeconds, new LinkedHashMap<>(parameters));
		context.usePose(pose);
		return context;
	}

	public LcTransform boneTransform(String bone) {
		return skeleton.boneTransform(pose, bone, ownerTarget.transform());
	}

	public LcTransform locatorTransform(String bone, String locator) {
		return skeleton.locatorTransform(pose, bone, locator, ownerTarget.transform());
	}

	/// 仅此入口创建的绑定随本次播放释放；独立绑定不归动画所有。
	public LcAttachmentBinding attach(ILcTransformTarget target, ILcAttachmentAnchor anchor) {
		if (stopped) {
			throw new IllegalStateException(INSTANCE_STOPPED);
		}

		if (target.level() != level) {
			throw new IllegalArgumentException(DIFFERENT_LEVEL);
		}

		var binding = LcAttachments.bind(target, anchor);
		attachments.removeIf(LcAttachmentBinding::isReleased);
		attachments.add(binding);
		return binding;
	}

	public void startActions() {
		fireActions(-Double.MIN_VALUE, 0);
		logDebug(Event.SERVER_START);
	}

	public void advance() {
		long tick = level.getGameTime();

		if (stopped || lastTick == tick) {
			return;
		}

		lastTick = tick;
		attachments.removeIf(LcAttachmentBinding::isReleased);

		if (!ownerTarget.isAvailable()) {
			stop(LcAnimationTermination.UNAVAILABLE);
			return;
		}

		if (finishPending) {
			stop(LcAnimationTermination.FINISHED);
			return;
		}

		double delta = paused ? 0 : speed / TICKS_PER_SECOND;
		long revision = timelineRevision;
		var beforeTick = new LinkedHashMap<>(parameters);
		var tickContext = new LcAnimationContext(this, sampleTime(timeSeconds), delta, parameters);
		tickContext.usePose(pose);

		for (var action : definition.tickActions()) {
			action.accept(tickContext);

			if (stopped || revision != timelineRevision) {
				return;
			}
		}

		delta = paused ? 0 : speed / TICKS_PER_SECOND;
		double previousTime = timeSeconds;
		var beforeSampling = new LinkedHashMap<>(parameters);
		var previousPose = beforeTick.equals(parameters) ? rawPose
				: sample(sampleTime(previousTime), new LinkedHashMap<>(beforeSampling));
		timeSeconds = clip.looping() ? timeSeconds + delta
				: Math.clamp(timeSeconds + delta, previousTime, Math.max(previousTime, clip.durationSeconds()));
		lastDeltaSeconds = timeSeconds - previousTime;
		var sampled = sampleResult(sampleTime(timeSeconds), lastDeltaSeconds, parameters);
		rawPose = sampled.pose();
		poseOverrides = consumeOverrideRootChannels(sampled.overrides());
		applyRootMotion(previousPose, previousTime, beforeSampling);

		if (stopped || revision != timelineRevision) {
			return;
		}

		pose = consumeRootChannels(rawPose);
		fireActions(previousTime, timeSeconds);

		if (!stopped && revision == timelineRevision) {
			finishPending = !clip.looping() && timeSeconds >= clip.durationSeconds();
		}
	}

	public void stop() {
		stop(LcAnimationTermination.STOPPED);
	}

	public void stop(LcAnimationTermination reason) {
		if (stopped) {
			return;
		}

		Objects.requireNonNull(reason);
		if (reason == LcAnimationTermination.NONE) {
			throw new IllegalArgumentException(TERMINATION_REQUIRED);
		}

		stopped = true;
		termination = reason;
		stoppedTick = level.getGameTime();
		timelineRevision++;
		logDebug(Event.SERVER_STOP);
		LcAttachments.releaseWriter(level, ownerTarget.identity(), this);
		attachments.forEach(LcAttachmentBinding::release);
		attachments.clear();
		Throwable failure = null;

		for (var action : definition.stopActions()) {
			try {
				action.accept(this);
			} catch (RuntimeException | Error exception) {
				if (failure == null) {
					failure = exception;
				} else {
					failure.addSuppressed(exception);
				}
			}
		}

		if (failure instanceof RuntimeException exception) {
			throw exception;
		}

		if (failure instanceof Error error) {
			throw error;
		}
	}

	private void logDebug(Event event) {
		if (LcAnimationDebugUtil.isEnabled()) {
			LcAnimationDebugUtil.state(event, id, sourceEntity == null ? -1 : sourceEntity.getId(), definitionId,
					content, playbackRevision, timeSeconds, stopped, termination, playerPolicy);
		}
	}

	public LcAnimationSnapshot snapshot() {
		LcRootMotion root = definition.rootMotion();
		return new LcAnimationSnapshot(id, sourceEntity == null ? -1 : sourceEntity.getId(), definitionId, content,
				definition.animationName(), timeSeconds, speed, paused, stopped, pose, context().snapshotParameters(),
				ownerTarget.transform(), root == null ? null : root.bone(),
				root == null ? Set.of() : root.axes(), playerPolicy, playerPolicy == null ? 0 : level.getGameTime(), playbackRevision,
				startedTick, stoppedTick, termination, poseOverrides);
	}

	private void installContent(LcAnimationContent next) {
		LcSkeleton nextSkeleton = LcSkeleton.parse(next.modelJson());
		LcAnimationClip nextClip = LcAnimationClip.parse(next.animationJson(), definition.animationName());
		LcRootMotion root = definition.rootMotion();

		if (root != null) {
			nextSkeleton.boneTransform(LcSkeletalPose.empty(), root.bone(), LcTransform.identity());

			if (nextSkeleton.bones().get(root.bone()).parent() != null) {
				throw new IllegalArgumentException(ROOT_REQUIRED);
			}
		}

		content = next;
		skeleton = nextSkeleton;
		clip = nextClip;
	}

	private LcSkeletalPose sample(double seconds, Map<String, Double> variables) {
		return sampleResult(seconds, 0, variables).pose();
	}

	private SampleResult sampleResult(double seconds, double delta, Map<String, Double> variables) {
		var context = new LcAnimationContext(this, seconds, delta, variables);
		LcSkeletalPose sampled = clip.sample(seconds, context);
		var ownedBones = new LinkedHashSet<String>();

		for (var modifier : definition.poseModifiers()) {
			context.usePose(sampled);
			sampled = modifier.operation().apply(context, sampled);
			var declaredBones = modifier.ownedBones();
			ownedBones.addAll(declaredBones == null ? sampled.bones().keySet() : declaredBones);
		}

		return new SampleResult(sampled, absoluteOverrides(ownedBones, sampled));
	}

	/// Java 修饰器只在服务端执行；声明骨骼同步最终绝对全部通道，不按数值变化猜测所有权。
	private static LcSkeletalPose absoluteOverrides(Set<String> names, LcSkeletalPose modified) {
		Map<String, LcTransform> bones = new LinkedHashMap<>();
		Map<String, Vec3> rotations = new LinkedHashMap<>();

		for (var name : names) {
			bones.put(name, modified.get(name));
			rotations.put(name, modified.rotationAngles(name));
		}

		return new LcSkeletalPose(bones, rotations);
	}

	private LcSkeletalPose consumeOverrideRootChannels(LcSkeletalPose sampled) {
		var root = definition.rootMotion();
		return root != null && sampled.bones().containsKey(root.bone()) ? consumeRootChannels(sampled) : sampled;
	}

	private void markVisualRevision() {
		playbackRevision++;
		startedTick = level.getGameTime();
	}

	private record SampleResult(LcSkeletalPose pose, LcSkeletalPose overrides) {
	}

	private double sampleTime(double seconds) {
		double duration = clip.durationSeconds();
		return clip.looping() && duration > 0 ? seconds % duration : Math.min(seconds, duration);
	}

	private void applyRootMotion(LcSkeletalPose previousPose, double previousTime, Map<String, Double> variables) {
		LcRootMotion root = definition.rootMotion();

		if (root == null) {
			return;
		}

		Vec3 delta = rawPose.get(root.bone()).position().subtract(previousPose.get(root.bone()).position());
		double duration = clip.durationSeconds();

		if (clip.looping() && duration > 0) {
			double cycles = Math.floor(timeSeconds / duration) - Math.floor(previousTime / duration);

			if (cycles != 0) {
				Vec3 start = sample(0, new LinkedHashMap<>(variables)).get(root.bone()).position();
				Vec3 end = sample(duration, new LinkedHashMap<>(variables)).get(root.bone()).position();
				delta = delta.add(end.subtract(start).scale(cycles));
			}
		}

		delta = selectAxes(delta, root.axes());

		if (root.localSpace()) {
			LcTransform world = ownerTarget.transform();
			var vector = new Vector3d(delta.x * world.scale().x, delta.y * world.scale().y, delta.z * world.scale().z);
			world.rotation().transform(vector);
			delta = new Vec3(vector.x, vector.y, vector.z);
		}

		root.executor().move(this, delta);
	}

	private LcSkeletalPose consumeRootChannels(LcSkeletalPose sampled) {
		LcRootMotion root = definition.rootMotion();

		if (root == null) {
			return sampled;
		}

		LcTransform value = sampled.get(root.bone());
		Vec3 position = value.position().subtract(selectAxes(value.position(), root.axes()));
		var bones = new LinkedHashMap<>(sampled.bones());
		bones.put(root.bone(), new LcTransform(position, value.rotation(), value.scale()));
		return new LcSkeletalPose(bones, sampled.eulerRotations());
	}

	private static Vec3 selectAxes(Vec3 value, Set<LcRootMotion.Axis> axes) {
		return new Vec3(axes.contains(LcRootMotion.Axis.X) ? value.x : 0,
				axes.contains(LcRootMotion.Axis.Y) ? value.y : 0,
				axes.contains(LcRootMotion.Axis.Z) ? value.z : 0);
	}

	private void fireActions(double previousTime, double currentTime) {
		double duration = clip.durationSeconds();
		long firstCycle = clip.looping() && duration > 0 ? Math.max(0, (long) Math.floor(previousTime / duration)) : 0;
		long lastCycle = clip.looping() && duration > 0 ? (long) Math.floor(currentTime / duration) : 0;

		long revision = timelineRevision;

		for (long cycle = firstCycle; cycle <= lastCycle; cycle++) {
			for (var action : definition.actions()) {
				double timestamp = cycle * duration + action.seconds();

				if (timestamp > previousTime && timestamp <= currentTime) {
					if (stopped || revision != timelineRevision) {
						return;
					}

					action.action().accept(this);
				}
			}

			if (cycle == lastCycle) {
				break;
			}
		}
	}
}
