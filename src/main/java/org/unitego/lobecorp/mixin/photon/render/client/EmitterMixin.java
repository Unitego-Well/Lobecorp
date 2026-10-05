package org.unitego.lobecorp.mixin.photon.render.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionControlAccess;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.render.PhotonViewDepthSortUtil;

import java.util.List;

import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.util.Mth;
import org.unitego.lobecorp.client.photon.runtime.PhotonParticleAgeAccess;

@Mixin(Emitter.class)
public abstract class EmitterMixin implements PhotonParticleAgeAccess {
	@Shadow
	protected float ageF;
	@Unique
	private float lobecorp$previousCycleAge;

	@ModifyReturnValue(method = "isEmitting", at = @At("RETURN"))
	private boolean lobecorp$emissionAlive(boolean original) {
		if ((Object) this instanceof PhotonEmissionControlAccess control && control.lobecorp$isEmissionStopped()) {
			var emitter = (Emitter) (Object) this;
			return emitter.isActive() && emitter.getParticleAmount() != 0;
		}
		return original;
	}

	@Inject(method = "onTickBegin", at = @At("HEAD"))
	private void lobecorp$snapshotCycleAge(CallbackInfo ci) {
		this.lobecorp$previousCycleAge = this.ageF;
	}

	@Inject(method = {"reset", "setAge"}, at = @At("RETURN"))
	private void lobecorp$resetCycleAge(CallbackInfo ci) {
		this.lobecorp$previousCycleAge = this.ageF;
	}

	@Override
	public float lobecorp$getRotationAge(float partialTicks) {
		return partialTicks == 0 ? this.ageF
				: Mth.lerp(Mth.clamp(partialTicks, 0, 1), this.lobecorp$previousCycleAge, this.ageF);
	}

	@Inject(method = "bakeGroup", at = @At("HEAD"))
	private void lobecorp$rememberJobStart(CallbackInfo ci, @Local(argsOnly = true) List<DrawJob> out,
	                                       @Share("lobecorp$jobStart") LocalIntRef start) {
		start.set(out.size());
	}

	@Inject(method = "bakeInstancedGroup", at = @At("HEAD"))
	private void lobecorp$rememberInstanceJobStart(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) List<DrawJob> out,
	                                               @Share("lobecorp$jobStart") LocalIntRef start) {
		start.set(out.size());
	}

	@Inject(method = "bakeGroup", at = @At("RETURN"))
	private void lobecorp$markCpuDepth(CallbackInfo ci, @Local(argsOnly = true) List<DrawJob> out,
	                                   @Local(argsOnly = true) Camera camera, @Local(argsOnly = true) RendererSetting.Runtime renderer,
	                                   @Share("lobecorp$jobStart") LocalIntRef start) {
		PhotonViewDepthSortUtil.mark(out, start.get(), camera, ((Emitter) (Object) this).transform().position(), renderer);
	}

	@Inject(method = "bakeInstancedGroup", at = @At("RETURN"))
	private void lobecorp$markInstanceDepth(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) List<DrawJob> out,
	                                        @Local(argsOnly = true) Camera camera, @Local(argsOnly = true) RendererSetting.Runtime renderer,
	                                        @Share("lobecorp$jobStart") LocalIntRef start) {
		PhotonViewDepthSortUtil.mark(out, start.get(), camera, ((Emitter) (Object) this).transform().position(), renderer);
	}
}
