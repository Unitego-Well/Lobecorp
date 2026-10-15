package org.unitego.lobecorp.mixin.photon.animation.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.configurator.SerializableRecordAction;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationHistoryUtil;

/// 原检视与变换工具的基线及更新快照不固化命名预览姿态。
@Mixin(SerializableRecordAction.class)
public abstract class PhotonAnimationRecordMixin {
	@WrapOperation(method = "save", at = @At(value = "INVOKE",
			target = "Lnet/neoforged/neoforge/common/util/ValueIOSerializable;serialize(Lnet/minecraft/world/level/storage/ValueOutput;)V"))
	private static void lobecorp$serializeBase(ValueIOSerializable serializable, ValueOutput output, Operation<Void> original) {
		PhotonAnimationHistoryUtil.serialize(serializable, () -> original.call(serializable, output));
	}
}
