package org.unitego.lobecorp.mixin.bench;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.bench.PhotonRunMatrixCapture;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;

/// 观察服务端生产技能推进前后的 active 转换，不改变技能生命周期。
@Mixin(value = EntitySkillUtil.class)
public class PhotonRunSkillObservationMixin {
	@Inject(method = "tick", at = {@At("HEAD"), @At("RETURN")})
	private static void lobecorp$runSkillTick(LivingEntity entity, CallbackInfo callback) {
		PhotonRunMatrixCapture.observeSkillTick(entity);
	}
}
