package org.unitego.lobecorp.mixin.bench;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.bench.PhotonQuietMatrixCapture;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;

/// 仅 Quiet 激光场景装载；只观察生产技能推进，不改变生命周期或调用次数。
@Mixin(value = EntitySkillUtil.class)
public class PhotonQuietSkillObservationMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void lobecorp$quietBeforeSkill(LivingEntity entity, CallbackInfo callback) {
        PhotonQuietMatrixCapture.observeSkillTick(entity);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private static void lobecorp$quietAfterSkill(LivingEntity entity, CallbackInfo callback) {
        PhotonQuietMatrixCapture.observeSkillTick(entity);
    }
}
