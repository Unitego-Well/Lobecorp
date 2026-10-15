package org.unitego.lobecorp.client.attachment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.unitego.lobecorp.Lobecorp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// 玩家适配只导出已完成混合的原版模型节点，通用附着核心不依赖玩家模型。
public class LcPlayerRenderAnchors {
	public static final Identifier PROVIDER = Lobecorp.id("player");
	public static final String BACK = "back";
	public static final String LEFT_HAND = "left_hand";
	public static final String RIGHT_HAND = "right_hand";
	public static final ContextKey<UUID> SOURCE = new ContextKey<>(Lobecorp.id("attachment_player_source"));
	// 与原版成人持物层使用相同的握持偏移，单位为格。
	private static final Vec3 HAND_GRIP = new Vec3(1.0 / 16, 2.0 / 16, -10.0 / 16);
	// 原版躯干深度四像素，背面距身体原点两像素。
	private static final double BACK_DEPTH = 2.0 / 16;

	public static LcRenderAnchorContext context(PlayerModel model, AvatarRenderState state, UUID source,
	                                            PoseStack poses, Matrix4fc worldFromRender, Identifier view,
	                                            Set<String> visibleNodes) {
		var nodes = new LinkedHashMap<String, Matrix4fc>();
		poses.pushPose();
		try {
			model.root().translateAndRotate(poses);
			model.body.translateAndRotate(poses);
			poses.translate(0, 0, BACK_DEPTH);
			nodes.put(BACK, new Matrix4f(poses.last().pose()));
		} finally {
			poses.popPose();
		}

		for (var arm : HumanoidArm.values()) {
			poses.pushPose();
			try {
				model.translateToHand(state, arm, poses);
				poses.mulPose(Axis.XP.rotationDegrees(-90));
				poses.mulPose(Axis.YP.rotationDegrees(180));
				poses.translate((arm == HumanoidArm.LEFT ? -1 : 1) * HAND_GRIP.x, HAND_GRIP.y, HAND_GRIP.z);
				nodes.put(handNode(arm), new Matrix4f(poses.last().pose()));
			} finally {
				poses.popPose();
			}
		}

		return new LcRenderAnchorContext(PROVIDER, source, state.id, view, worldFromRender, Map.copyOf(nodes), visibleNodes);
	}

	public static String handNode(HumanoidArm arm) {
		return arm == HumanoidArm.RIGHT ? RIGHT_HAND : LEFT_HAND;
	}
}
