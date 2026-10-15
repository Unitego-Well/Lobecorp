package org.unitego.lobecorp.client.animation.player;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.client.animation.LcClientAnimations.PlayerFrame;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 状态提取阶段冻结的原生采样、兼容来源与各播放实例的过渡进度。
public record LcPlayerPoseFrame(UUID playerId, List<Layer> layers, List<PlayerFrame> playbacks,
                                Map<Identifier, LcSkeletalPose> externalSources) {
	public LcPlayerPoseFrame {
		layers = List.copyOf(layers);
		playbacks = List.copyOf(playbacks);
		externalSources = Map.copyOf(externalSources);
	}

	public boolean active() {
		return !layers.isEmpty();
	}

	public record Layer(PlayerFrame frame, LcSkeletalPose pose, LcPlayerAnimationPolicy policy) {
	}
}
