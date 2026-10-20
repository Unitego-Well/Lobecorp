package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.PhotonParticleAgeAccess;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;

@Mixin(Emitter.class)
@SuppressWarnings("unused")
public abstract class EmitterMixin implements PhotonParticleAgeAccess {
	@Shadow
	protected float ageF;
	@Unique
	private float lobecorp$previousCycleAge;
	/// 仅保留旧质量提示，适配层不引入降质策略，也不修改实体LOD。
	@Unique
	@Persisted
	private boolean lobecorp$qualityExempt;
	@Unique
	@Persisted
	private boolean lobecorp$planarLod;

	@Inject(method = "onTickBegin", at = @At("HEAD"))
	private void lobecorp$snapshotCycleAge(CallbackInfo ci) {
		lobecorp$previousCycleAge = ageF;
	}

	@Inject(method = {"reset", "setAge"}, at = @At("RETURN"))
	private void lobecorp$resetCycleAge(CallbackInfo ci) {
		lobecorp$previousCycleAge = ageF;
	}

	@Override
	public float lobecorp$getRotationAge(float partialTicks) {
		return partialTicks == 0 ? ageF : Mth.lerp(Math.clamp(partialTicks, 0, 1), lobecorp$previousCycleAge, ageF);
	}
}
