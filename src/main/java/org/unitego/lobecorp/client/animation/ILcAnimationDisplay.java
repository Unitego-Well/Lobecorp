package org.unitego.lobecorp.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.Level;

/// 在提取阶段冻结本帧显示数据，返回的提交回调不再读取世界业务状态。
@FunctionalInterface
public interface ILcAnimationDisplay {
	Frame extract(Level level, float partialTick);

	@FunctionalInterface
	interface Frame {
		void submit(PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera, int packedLight);
	}
}
