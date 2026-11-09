package org.unitego.lobecorp.client.visual.camera;

import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.visual.camera.CameraControl.Frame;

@SuppressWarnings("RedundantModifier")
public interface ICameraController {
	public default @Nullable Frame independent(float partial) {
		return null;
	}

	public default Vec3 position(Vec3 normal, float partial) {
		return normal;
	}

	public default void angles(ViewportEvent.ComputeCameraAngles event) {
	}

	public default void fov(ViewportEvent.ComputeFov event) {
	}
}
