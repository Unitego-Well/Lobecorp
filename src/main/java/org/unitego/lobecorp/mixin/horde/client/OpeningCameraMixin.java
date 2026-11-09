package org.unitego.lobecorp.mixin.horde.client;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.hud.event.MidnightCleanupCinematic;

@Mixin(Camera.class)
public abstract class OpeningCameraMixin {

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	public abstract Vec3 position();

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void lobecorp$openingPosition(float partialTicks, CallbackInfo callback) {
		setPosition(MidnightCleanupCinematic.cameraPosition(position(), partialTicks));
	}
}
