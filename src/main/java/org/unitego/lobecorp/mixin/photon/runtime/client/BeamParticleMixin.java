package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamRuntime;
import com.lowdragmc.photon.client.gameobject.particle.BeamParticle;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.client.photon.runtime.PhotonBeamLengthAccess;

@Mixin(BeamParticle.class)
public abstract class BeamParticleMixin {
	@Shadow
	protected BeamRuntime runtime;

	@Shadow
	public abstract IParticleEmitter getEmitter();

	/// Photon 26.1.2.2 的射线检测依赖相机实体；LDLib2 场景相机可无实体，未绑定世界时保留原终点。
	/// 修复对照见 docs/systems/photon-ldlib2-fixes.md 的 PH-01。
	@Inject(method = "getRealEnd", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/beam/BeamConfig;getRaycast()Lcom/lowdragmc/photon/client/gameobject/emitter/beam/BeamConfig$RaycastMode;",
			ordinal = 0), cancellable = true)
	private void lobecorp$skipRaycastWithoutWorld(Camera camera, Vector3f from,
	                                              CallbackInfoReturnable<Vector3f> callback, @Local(name = "end") Vector3f end) {
		if (camera.entity() != null || this.getEmitter().getLevel() != null) {
			return;
		}

		callback.setReturnValue(end);
	}

	/// 场景相机没有实体时使用发射器所属世界；普通相机保留原调用，覆盖方块与实体两处检测。
	@WrapOperation(method = "getRealEnd", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;level()Lnet/minecraft/world/level/Level;"), require = 2)
	private Level lobecorp$resolveRaycastLevel(Entity entity, Operation<Level> original) {
		if (entity != null) {
			return original.call(entity);
		}

		return this.getEmitter().getLevel();
	}

	@ModifyExpressionValue(method = "getRealEnd", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/beam/BeamConfig;getEnd()Lorg/joml/Vector3f;"))
	private Vector3f lobecorp$overrideLength(Vector3f original) {
		return ((PhotonBeamLengthAccess) this.runtime).lobecorp$getBeamEnd(original);
	}
}
