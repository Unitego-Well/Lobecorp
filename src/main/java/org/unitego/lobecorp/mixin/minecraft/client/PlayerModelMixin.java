package org.unitego.lobecorp.mixin.minecraft.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.client.animation.player.LcPlayerPoseBridge;

@Mixin(PlayerModel.class)
public class PlayerModelMixin {
	@WrapMethod(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V")
	private void lobecorp$applyPlayerPose(AvatarRenderState state, Operation<Void> original) {
		var scope = LcPlayerPoseBridge.open((PlayerModel) (Object) this, state);
		try {
			original.call(state);
			LcPlayerPoseBridge.finish(scope);
		} finally {
			scope.close();
		}
	}
}
