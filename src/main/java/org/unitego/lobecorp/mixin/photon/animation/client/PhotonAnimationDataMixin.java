package org.unitego.lobecorp.mixin.photon.animation.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationMigrationGuardUtil;

/// 共用预览的项目保存及 FX/FXPack 导出只序列化基础属性和动画定义。
@Mixin(FXData.class)
public abstract class PhotonAnimationDataMixin {
	@WrapMethod(method = "serializeNBT")
	@SuppressWarnings("resource") // 原 FXRuntime 持有控制器，此处只临时释放预览贡献。
	private CompoundTag lobecorp$serializeBase(HolderLookup.Provider provider, Operation<CompoundTag> original) {
		var data = (FXData) (Object) this;
		PhotonAnimationMigrationGuardUtil.prepare(data);
		for (var object : data.objects()) {
			if (!(object.getScene() instanceof FXRuntime runtime) || runtime.fxData != data) {
				continue;
			}

			var animations = PhotonAnimationUtil.of(runtime);
			if (animations != null) {
				return animations.serializeBase(() -> original.call(provider));
			}
		}

		return original.call(provider);
	}
}
