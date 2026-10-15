package org.unitego.lobecorp.client.animation.player;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;

/// 兼容来源只在提取阶段采样一次；输出按原版部位命名的局部增量，位移为格、旋转为原版模型轴。
@FunctionalInterface
public interface ILcPlayerPoseSource {
	LcSkeletalPose sample(Player player, AvatarRenderState state, float partialTick);
}
