package org.unitego.lobecorp.client.entity.player.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.unitego.lobecorp.client.animation.LcAnimationDisplays;
import org.unitego.lobecorp.client.animation.player.LcPlayerAnimationRuntime;
import org.unitego.lobecorp.client.attachment.LcPlayerRenderAnchors;
import org.unitego.lobecorp.client.attachment.LcRenderAnchorContext;
import org.unitego.lobecorp.mixin.minecraft.client.ItemInHandRendererAccessor;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/// 第一人称使用独立玩家模型，只提交手臂子树与跟随手掌的原版持物层。
@NullMarked
public class LcPlayerFirstPersonRenderer extends AvatarRenderer<AbstractClientPlayer> {

	private static final Map<PlayerModelType, LcPlayerFirstPersonRenderer> RENDERERS = new EnumMap<>(PlayerModelType.class);
	private static final ThreadLocal<ArrayDeque<Scope>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
	private final HandLayer handLayer = new HandLayer(this);

	public LcPlayerFirstPersonRenderer(EntityRendererProvider.Context context, boolean slim) {
		super(context, slim);
		layers.clear();
	}

	public static void initialize(EntityRendererProvider.Context context) {
		RENDERERS.put(PlayerModelType.WIDE, new LcPlayerFirstPersonRenderer(context, false));
		RENDERERS.put(PlayerModelType.SLIM, new LcPlayerFirstPersonRenderer(context, true));
	}

	public static @Nullable Scope open(LocalPlayer player, float partialTick) {
		var minecraft = Minecraft.getInstance();
		var renderer = RENDERERS.get(player.getSkin().model());
		if (renderer == null || minecraft.getCameraEntity() != player || !minecraft.options.getCameraType().isFirstPerson()) {
			return null;
		}

		var state = renderer.createRenderState(player, partialTick);
		var frame = LcPlayerAnimationRuntime.frame(state);
		if (frame == null || frame.layers().stream().noneMatch(layer -> layer.policy().firstPersonArms()
				&& layer.policy().weight() > 0)) {
			return null;
		}

		renderer.model.setupAnim(state);
		var scope = new Scope(renderer, player, state, minecraft.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState);
		SCOPES.get().push(scope);
		return scope;
	}

	public static boolean active() {
		return !SCOPES.get().isEmpty();
	}

	public static boolean submitHand(InteractionHand hand, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords) {
		var scope = SCOPES.get().peek();
		if (scope == null) {
			return false;
		}

		if (!scope.player.isScoping()) {
			var arm = hand == InteractionHand.MAIN_HAND ? scope.state.mainArm : scope.state.mainArm.getOpposite();
			scope.renderer.submitHand(scope, arm, poseStack, collector, lightCoords);
		}

		return true;
	}

	private void submitHand(Scope scope, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords) {
		var state = scope.state;
		poseStack.pushPose();
		try {
			// 原入口已抵消世界视图；乘回冻结的视图旋转后，按真实身体相对相机的位置绘制。
			poseStack.mulPose(scope.camera.viewRotationMatrix);
			var offset = getRenderOffset(state);
			poseStack.translate(state.x - scope.camera.pos.x + offset.x, state.y - scope.camera.pos.y + offset.y,
					state.z - scope.camera.pos.z + offset.z);
			applyBodyPose(state, poseStack);
			submitArm(scope, arm, poseStack, collector, lightCoords);
			handLayer.submitHand(state, arm, poseStack, collector, lightCoords);
			var worldPoses = new PoseStack();
			worldPoses.translate(state.x + offset.x, state.y + offset.y, state.z + offset.z);
			applyBodyPose(state, worldPoses);
			var worldFromRender = new Matrix4f(worldPoses.last().pose()).mul(new Matrix4f(poseStack.last().pose()).invert());
			var context = LcPlayerRenderAnchors.context(model, state, scope.player.getUUID(), poseStack, worldFromRender,
					LcRenderAnchorContext.FIRST_PERSON_VIEW, Set.of(LcPlayerRenderAnchors.handNode(arm)));
			LcAnimationDisplays.submitRenderAnchors(context, collector, scope.camera, lightCoords);
		} finally {
			poseStack.popPose();
		}
	}

	private void applyBodyPose(AvatarRenderState state, PoseStack poses) {
		poses.scale(state.scale, state.scale, state.scale);
		setupRotations(state, poses, state.bodyRot, state.scale);
		poses.scale(-1, -1, 1);
		scale(state, poses);
		poses.translate(0, EntityModel.MODEL_Y_OFFSET, 0);
	}

	private void submitArm(Scope scope, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords) {
		if (scope.state.isInvisible) {
			return;
		}

		poseStack.pushPose();
		try {
			model.root().translateAndRotate(poseStack);
			if (!ClientHooks.renderSpecificFirstPersonArm(poseStack, collector, lightCoords, scope.player, arm)) {
				collector.submitModelPart(model.getArm(arm), poseStack, model.renderType(getTextureLocation(scope.state)),
						lightCoords, OverlayTexture.NO_OVERLAY, null);
			}
		} finally {
			poseStack.popPose();
		}
	}

	/// 保持独立模型姿态直到原版完成延迟提交；退出作用域时不恢复或重置模型。
	public static class Scope implements AutoCloseable {
		private final LcPlayerFirstPersonRenderer renderer;
		private final LocalPlayer player;
		private final AvatarRenderState state;
		private final CameraRenderState camera;

		private Scope(LcPlayerFirstPersonRenderer renderer, LocalPlayer player, AvatarRenderState state, CameraRenderState camera) {
			this.renderer = renderer;
			this.player = player;
			this.state = state;
			this.camera = camera;
		}

		@Override
		public void close() {
			var scopes = SCOPES.get();
			scopes.removeFirstOccurrence(this);

			if (scopes.isEmpty()) {
				SCOPES.remove();
			}
		}
	}

	private static class HandLayer extends ItemInHandLayer<AvatarRenderState, PlayerModel> {
		// 沿用原版成人手掌的握持锚点，单位为格；地图自身变换仍由原版方法处理。
		private static final Vec3 MAP_GRIP_OFFSET = new Vec3(1.0 / 16, 2.0 / 16, -10.0 / 16);

		private HandLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
			super(parent);
		}

		private void submitHand(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords) {
			submitArmWithItem(state, arm == HumanoidArm.RIGHT ? state.rightHandItemState : state.leftHandItemState,
					arm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack, arm, poseStack, collector, lightCoords);
		}

		@Override
		protected void submitArmWithItem(AvatarRenderState state, ItemStackRenderState item, ItemStack itemStack,
		                                 HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords) {
			if (!(itemStack.getItem() instanceof MapItem)) {
				super.submitArmWithItem(state, item, itemStack, arm, poseStack, collector, lightCoords);
				return;
			}

			poseStack.pushPose();
			try {
				getParentModel().translateToHand(state, arm, poseStack);
				poseStack.mulPose(Axis.XP.rotationDegrees(-90));
				poseStack.mulPose(Axis.YP.rotationDegrees(180));
				poseStack.translate((arm == HumanoidArm.LEFT ? -1 : 1) * MAP_GRIP_OFFSET.x, MAP_GRIP_OFFSET.y, MAP_GRIP_OFFSET.z);
				((ItemInHandRendererAccessor) Minecraft.getInstance().gameRenderer.itemInHandRenderer)
						.lobecorp$renderMap(poseStack, collector, lightCoords, itemStack);
			} finally {
				poseStack.popPose();
			}
		}
	}
}
