package org.unitego.lobecorp.mixin.conductor.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.visual.camera.CameraControl;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Unique
	private boolean lobecorp$independentCamera;

	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot, float roll);

	@Inject(method = "alignWithEntity", at = @At("HEAD"), cancellable = true)
	private void lobecorp$conductorView(float partialTicks, CallbackInfo callback) {
		var view = CameraControl.independent(partialTicks);
		lobecorp$independentCamera = view != null;

		if (view == null) {
			return;
		}

		// HEAD 取消会跳过原版角度事件和 TAIL，独立机位在此完成两者。
		var angles = NeoForge.EVENT_BUS.post(new ViewportEvent.ComputeCameraAngles(
				(Camera) (Object) this, partialTicks, view.yaw(), view.pitch(), view.roll()));
		setPosition(CameraControl.position(view.position(), partialTicks));
		setRotation(angles.getYaw(), angles.getPitch(), angles.getRoll());
		detached = true;
		callback.cancel();
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void lobecorp$independentCameraState(CameraRenderState cameraState, float cameraEntityPartialTicks, CallbackInfo callback) {
		if (!lobecorp$independentCamera) {
			return;
		}

		cameraState.entityRenderState.isPlayer = false;
		cameraState.entityRenderState.isLiving = false;
	}
}
