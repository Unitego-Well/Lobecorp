package org.unitego.lobecorp.mixin.bench;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.bench.PhotonHybridMatrixCapture;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;

/// 仅混合场景加载；观察生产技能推进，不改变生命周期或调用次数。
@Mixin(value = EntitySkillUtil.class)
public class PhotonHybridSkillObservationMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void lobecorp$hybridBeforeSkill(LivingEntity entity, CallbackInfo callback) {
        PhotonHybridMatrixCapture.observeSkillTick(entity);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private static void lobecorp$hybridAfterSkill(LivingEntity entity, CallbackInfo callback) {
        PhotonHybridMatrixCapture.observeSkillTick(entity);
    }
}
