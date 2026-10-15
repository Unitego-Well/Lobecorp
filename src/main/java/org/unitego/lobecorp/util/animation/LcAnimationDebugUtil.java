package org.unitego.lobecorp.util.animation;

import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.player.LcAnimationTermination;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;

import java.util.UUID;

/// 独立动画与玩家姿态的开发日志；调用方复用现有状态边沿，不维护额外播放状态。
public class LcAnimationDebugUtil {

	/// 仅在非生产环境生效；可通过 Debug 动态开启，重启后恢复关闭。
	public static volatile boolean ENABLED = false;
	private static final String STATE_LOG = "[LC Animation Debug] event={} instance={} entity={} definition={} model={} animation={} content={} revision={} timeSeconds={} stopped={} termination={} policy={}";
	private static final String MODEL_LOG = "[LC Animation Debug] event=MODEL_LAYER instance={} entity={} model={} modelIdentity={} revision={} timeSeconds={} fadeIn={} fadeOut={} stopped={} sources={} rightArm={} head={}";
	private static final String REMOVE_LOG = "[LC Animation Debug] event=CLIENT_REMOVED instance={} termination={}";
	private static final String CLEAR_LOG = "[LC Animation Debug] event=CLIENT_WORLD_CLEAR frames={} pending={}";
	private static final String SAMPLE_FAILURE_LOG = "[LC Animation Debug] event=SAMPLE_FAILURE instance={} entity={} definition={} model={} animation={} content={} revision={} timeSeconds={}";

	public static boolean isEnabled() {
		return ENABLED && !FMLEnvironment.isProduction();
	}

	public static void state(Event event, UUID instanceId, int ownerEntityId, Identifier definitionId,
	                         LcAnimationContent content, long revision, double timeSeconds, boolean stopped,
	                         LcAnimationTermination termination, @Nullable LcPlayerAnimationPolicy policy) {
		if (isEnabled()) {
			Lobecorp.LOGGER.info(STATE_LOG, event, instanceId, ownerEntityId, definitionId, content.modelId(),
					content.animationId(), content.version(), revision, timeSeconds, stopped, termination, policy);
		}
	}

	public static void state(Event event, LcAnimationSnapshot snapshot) {
		state(event, snapshot.instanceId(), snapshot.ownerEntityId(), snapshot.definitionId(), snapshot.content(),
				snapshot.playbackRevision(), snapshot.timeSeconds(), snapshot.stopped(), snapshot.termination(),
				snapshot.playerPolicy());
	}

	/// 姿态为截至当前层的合成结果，后续优先级更高的层仍可继续改变它。
	public static void modelLayer(LcAnimationSnapshot snapshot, Object model, double timeSeconds,
	                              double fadeIn, double fadeOut, Object rightArm, Object head) {
		if (isEnabled()) {
			Lobecorp.LOGGER.info(MODEL_LOG, snapshot.instanceId(), snapshot.ownerEntityId(),
					model.getClass().getSimpleName(), System.identityHashCode(model), snapshot.playbackRevision(),
					timeSeconds, fadeIn, fadeOut, snapshot.stopped(),
					snapshot.playerPolicy() == null ? null : snapshot.playerPolicy().sources(), rightArm, head);
		}
	}

	public static void removed(UUID instanceId, LcAnimationTermination reason) {
		if (isEnabled()) {
			Lobecorp.LOGGER.info(REMOVE_LOG, instanceId, reason);
		}
	}

	public static void clearWorld(int frames, int pending) {
		if (isEnabled() && (frames > 0 || pending > 0)) {
			Lobecorp.LOGGER.info(CLEAR_LOG, frames, pending);
		}
	}

	public static void sampleFailure(LcAnimationSnapshot snapshot, double timeSeconds, Throwable exception) {
		if (isEnabled()) {
			Lobecorp.LOGGER.error(SAMPLE_FAILURE_LOG, snapshot.instanceId(), snapshot.ownerEntityId(),
					snapshot.definitionId(), snapshot.content().modelId(), snapshot.content().animationId(),
					snapshot.content().version(), snapshot.playbackRevision(), timeSeconds, exception);
		}
	}

	public enum Event {
		SERVER_START, SERVER_POLICY, SERVER_STOP, CLIENT_STATE, CLIENT_EXPIRED
	}
}
