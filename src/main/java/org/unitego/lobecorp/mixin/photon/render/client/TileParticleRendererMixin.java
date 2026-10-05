package org.unitego.lobecorp.mixin.photon.render.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.runtime.PhotonRotationCycleAccess;

@Mixin(TileParticleRenderer.class)
public abstract class TileParticleRendererMixin {
	@ModifyReturnValue(method = "computeModelQuaternion", at = @At("RETURN"))
	private static Quaternionf lobecorp$rotateInSimulationSpace(Quaternionf original, TileParticle particle, Vector3f rotation) {
		PhotonRotationCycleAccess cycle = (PhotonRotationCycleAccess) particle.getConfig().rotationOverLifetime;
		if (!particle.getRuntime().rotationOverLifetime.isEnable() || !cycle.lobecorp$isRotationCycleEnabled()
				|| cycle.lobecorp$getRotationCycleTicks() <= 0) {
			return original;
		}
		// 先在模拟空间内应用粒子旋转，再转换到世界；不修改 Photon 的曲线轴映射。
		return new Quaternionf(particle.getSpaceRotation()).rotateXYZ(rotation.x, rotation.y, rotation.z);
	}
}
