package org.unitego.lobecorp.mixin.minecraft.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationSources;
import org.unitego.lobecorp.client.animation.player.LcPlayerPoseBridge;

@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {
	@Unique
	private static final String lobecorp$SETUP = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V";

	@Inject(method = lobecorp$SETUP, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	private void lobecorp$afterReset(HumanoidRenderState state, CallbackInfo ci) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			return;
		}

		LcPlayerPoseBridge.resetCompleted();
	}

	@Inject(method = lobecorp$SETUP, at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/model/geom/ModelPart;yRot:F", opcode = Opcodes.PUTFIELD,
			ordinal = 0, shift = At.Shift.AFTER))
	private void lobecorp$headPosture(HumanoidRenderState state, CallbackInfo ci) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			return;
		}

		LcPlayerPoseBridge.phase(LcPlayerAnimationSources.VANILLA_POSTURE);
	}

	@Inject(method = lobecorp$SETUP, at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;walkAnimationPos:F", opcode = Opcodes.GETFIELD))
	private void lobecorp$walk(HumanoidRenderState state, CallbackInfo ci) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			return;
		}

		LcPlayerPoseBridge.phase(LcPlayerAnimationSources.VANILLA_MOVEMENT);
	}

	@Inject(method = lobecorp$SETUP, at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;isPassenger:Z", opcode = Opcodes.GETFIELD))
	private void lobecorp$riding(HumanoidRenderState state, CallbackInfo ci) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			return;
		}

		LcPlayerPoseBridge.phase(LcPlayerAnimationSources.VANILLA_POSTURE);
	}

	@Inject(method = lobecorp$SETUP, at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;mainArm:Lnet/minecraft/world/entity/HumanoidArm;", opcode = Opcodes.GETFIELD))
	private void lobecorp$beforeArmPoses(HumanoidRenderState state, CallbackInfo ci) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			return;
		}

		LcPlayerPoseBridge.endPhase();
	}

	@WrapOperation(method = lobecorp$SETUP, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/HumanoidModel;poseRightArm(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V"))
	private void lobecorp$rightArm(HumanoidModel<?> model, HumanoidRenderState state, Operation<Void> original) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			original.call(model, state);
			return;
		}

		LcPlayerPoseBridge.operation(LcPlayerPoseBridge.armSource(state.rightArmPose), () -> original.call(model, state));
	}

	@WrapOperation(method = lobecorp$SETUP, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/HumanoidModel;poseLeftArm(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V"))
	private void lobecorp$leftArm(HumanoidModel<?> model, HumanoidRenderState state, Operation<Void> original) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			original.call(model, state);
			return;
		}

		LcPlayerPoseBridge.operation(LcPlayerPoseBridge.armSource(state.leftArmPose), () -> original.call(model, state));
	}

	@WrapOperation(method = lobecorp$SETUP, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/HumanoidModel;setupAttackAnimation(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V"))
	private void lobecorp$attack(HumanoidModel<?> model, HumanoidRenderState state, Operation<Void> original) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			original.call(model, state);
			return;
		}

		LcPlayerPoseBridge.operation(LcPlayerAnimationSources.VANILLA_ATTACK, () -> original.call(model, state));
	}

	@Inject(method = lobecorp$SETUP, at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;isCrouching:Z", opcode = Opcodes.GETFIELD))
	private void lobecorp$crouch(HumanoidRenderState state, CallbackInfo ci) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			return;
		}

		LcPlayerPoseBridge.phase(LcPlayerAnimationSources.VANILLA_POSTURE);
	}

	@Inject(method = lobecorp$SETUP, at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/model/HumanoidModel$ArmPose;SPYGLASS:Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
			opcode = Opcodes.GETSTATIC, ordinal = 0))
	private void lobecorp$idleSource(HumanoidRenderState state, CallbackInfo ci) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			return;
		}

		LcPlayerPoseBridge.idleSource(state.ageInTicks);
	}

	@WrapOperation(method = lobecorp$SETUP, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/AnimationUtils;bobModelPart(Lnet/minecraft/client/model/geom/ModelPart;FF)V"))
	private void lobecorp$idle(ModelPart modelPart, float ageInTicks, float scale, Operation<Void> original) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			original.call(modelPart, ageInTicks, scale);
			return;
		}

		LcPlayerPoseBridge.operation(LcPlayerAnimationSources.VANILLA_IDLE, () -> original.call(modelPart, ageInTicks, scale));
	}

	@Inject(method = lobecorp$SETUP, at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;attackArm:Lnet/minecraft/world/entity/HumanoidArm;", opcode = Opcodes.GETFIELD))
	private void lobecorp$swim(HumanoidRenderState state, CallbackInfo ci) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			return;
		}

		LcPlayerPoseBridge.swimPhase();
	}

	@Inject(method = lobecorp$SETUP, at = @At("RETURN"))
	private void lobecorp$finishStages(HumanoidRenderState state, CallbackInfo ci) {
		if (LcPlayerPoseBridge.isOutsideCurrentModel(this)) {
			return;
		}

		LcPlayerPoseBridge.endPhase();
	}
}
