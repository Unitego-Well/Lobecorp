package org.unitego.lobecorp.animation.runtime;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.animation.behavior.LcRootMotion;
import org.unitego.lobecorp.animation.player.LcAnimationTermination;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// 完整播放快照不包含 Java 回调；晚追踪直接安装当前结果，不重放历史动作。
public record LcAnimationSnapshot(UUID instanceId, int ownerEntityId, Identifier definitionId,
                                  LcAnimationContent content, String animationName, double timeSeconds, double speed,
                                  boolean paused, boolean stopped, LcSkeletalPose pose, Map<String, Double> parameters,
                                  LcTransform worldTransform,
                                  @Nullable String rootBone, Set<LcRootMotion.Axis> rootAxes,
                                  @Nullable LcPlayerAnimationPolicy playerPolicy, long serverTick,
                                  long playbackRevision, long startedTick, long stoppedTick,
                                  LcAnimationTermination termination, LcSkeletalPose poseOverrides) {
	public LcAnimationSnapshot {
		parameters = Map.copyOf(parameters);
		rootAxes = Set.copyOf(rootAxes);
	}

	/// 终止帧冻结姿态与参数，只刷新服务器时钟以供晚追踪定位淡出进度。
	public LcAnimationSnapshot atServerTick(long tick) {
		return new LcAnimationSnapshot(instanceId, ownerEntityId, definitionId, content, animationName,
				timeSeconds, speed, paused, stopped, pose, parameters, worldTransform, rootBone,
				rootAxes, playerPolicy, tick, playbackRevision, startedTick, stoppedTick, termination, poseOverrides);
	}
}
