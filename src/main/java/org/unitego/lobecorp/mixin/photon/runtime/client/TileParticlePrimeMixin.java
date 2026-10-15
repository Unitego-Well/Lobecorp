package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.runtime.PhotonEmissionPrimeUtil;

@Mixin(TileParticle.class)
public abstract class TileParticlePrimeMixin {
	@WrapOperation(method = "setup", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/particle/TileParticle;update(F)V"))
	private void lobecorp$initializeWithoutElapsedTime(TileParticle particle, float dt, Operation<Void> original) {
		// 保留 setup 的出生值和后续 updateOrigin，只在局部初始化作用域取消预推进。
		original.call(particle, PhotonEmissionPrimeUtil.active() ? 0f : dt);
	}
}
