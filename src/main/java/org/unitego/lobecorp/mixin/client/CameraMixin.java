package org.unitego.lobecorp.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.conductor.ConductorCamera;
import org.unitego.lobecorp.client.conductor.ConductorControls;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot, float roll);

	@Inject(method = "alignWithEntity", at = @At("HEAD"), cancellable = true)
	private void lobecorp$conductorView(float partialTicks, CallbackInfo callback) {
		ConductorCamera.View view = ConductorControls.cameraFrame(partialTicks);
		if (view == null) return;
		setPosition(view.position());
		setRotation(view.yaw(), view.pitch(), 0);
		detached = true;
		callback.cancel();
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void lobecorp$independentCameraState(CameraRenderState cameraState, float cameraEntityPartialTicks, CallbackInfo callback) {
		if (!ConductorControls.active()) return;
		cameraState.entityRenderState.isPlayer = false;
		cameraState.entityRenderState.isLiving = false;
	}
}
