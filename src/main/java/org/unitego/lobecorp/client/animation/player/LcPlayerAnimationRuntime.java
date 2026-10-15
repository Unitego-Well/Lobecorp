package org.unitego.lobecorp.client.animation.player;

import com.geckolib.cache.GeckoLibResources;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.client.animation.LcClientAnimations;
import org.unitego.lobecorp.registry.animation.client.LcPlayerPoseSources;
import org.unitego.lobecorp.util.animation.LcAnimationDebugUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/// 只在状态提取时采样；重复模型设置共享冻结帧和各实际模型的过渡起点。
public class LcPlayerAnimationRuntime {
	public static final DataTicket<LcPlayerPoseFrame> POSE_FRAME =
			DataTicket.create("lobecorp:player_pose_frame", LcPlayerPoseFrame.class);
	private static final Map<UUID, History> HISTORIES = new HashMap<>();
	private static Object modelGeneration = GeckoLibResources.getBakedModels();
	private static Object animationGeneration = GeckoLibResources.getBakedAnimations();

	public static void prepare(Player player, AvatarRenderState state, float partialTick) {
		if (!((Object) state instanceof GeoRenderState geoState)) {
			return;
		}

		refreshResources();
		var frames = LcClientAnimations.playerFrames(player.getId(), partialTick);
		if (frames.isEmpty()) {
			HISTORIES.remove(player.getUUID());
		}

		var layers = new ArrayList<LcPlayerPoseFrame.Layer>();
		for (var frame : frames) {
			var policy = frame.snapshot().playerPolicy();
			if (policy == null) {
				continue;
			}

			LcSkeletalPose pose;
			try {
				pose = LcPlayerAnimationSampler.sample(frame.snapshot(), frame.timeSeconds(), player, state,
						partialTick, frame.poseOverrides());
			} catch (RuntimeException | Error exception) {
				LcAnimationDebugUtil.sampleFailure(frame.snapshot(), frame.timeSeconds(), exception);
				throw exception;
			}

			layers.add(new LcPlayerPoseFrame.Layer(frame, pose, policy));
		}

		var external = new LinkedHashMap<Identifier, LcSkeletalPose>();
		for (var entry : LcPlayerPoseSources.sources().entrySet()) {
			boolean requested = layers.stream().anyMatch(layer -> layer.policy().sources().containsKey(entry.getKey()));
			if (requested) {
				external.put(entry.getKey(), entry.getValue().sample(player, state, partialTick));
			}
		}

		geoState.addGeckolibData(POSE_FRAME, new LcPlayerPoseFrame(player.getUUID(), layers, frames, external));
	}

	public static @Nullable LcPlayerPoseFrame frame(AvatarRenderState state) {
		return (Object) state instanceof GeoRenderState geoState ? geoState.getGeckolibData(POSE_FRAME) : null;
	}

	public static ModelHistory modelHistory(LcPlayerPoseFrame frame, PlayerModel model) {
		return HISTORIES.computeIfAbsent(frame.playerId(), _ -> new History()).models
				.computeIfAbsent(model, _ -> new ModelHistory());
	}

	public static void tick() {
		refreshResources();
		var level = Minecraft.getInstance().level;
		if (level == null) {
			clear();
			return;
		}

		boolean removed = HISTORIES.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
		if (removed) {
			LcPlayerAnimationSampler.clear();
		}
	}

	private static void refreshResources() {
		var models = GeckoLibResources.getBakedModels();
		var animations = GeckoLibResources.getBakedAnimations();
		if (modelGeneration == models && animationGeneration == animations) {
			return;
		}

		modelGeneration = models;
		animationGeneration = animations;
		HISTORIES.clear();
		LcPlayerAnimationSampler.clear();
		LcPlayerPoseBridge.clear();
	}

	public static void clear() {
		HISTORIES.clear();
		LcPlayerAnimationSampler.clear();
		LcPlayerPoseBridge.clear();
	}

	private static class History {
		private final Map<PlayerModel, ModelHistory> models = new IdentityHashMap<>();
	}

	/// 每个实际模型独立保存姿态；盔甲模型不复用主玩家模型的枢轴或可变字段。
	public static class ModelHistory {
		public @Nullable LcPlayerModelPose displayed;
		public @Nullable LcPlayerPoseFrame frame;
		public final Map<UUID, LayerHistory> layers = new HashMap<>();
	}

	public record LayerHistory(long revision, boolean stopped, LcPlayerPoseContribution origin,
	                           LcPlayerPoseContribution displayed, boolean replacement,
	                           LcPlayerModelPose mapped, LcPlayerModelPose base) {
	}
}
