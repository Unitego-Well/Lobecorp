package org.unitego.lobecorp.mixin.geckolib.client;

import com.geckolib.animation.AnimationProcessor;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.renderer.base.BoneSnapshots;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.client.animation.LcAnimationLodStateAccess;

@Mixin(AnimationProcessor.class)
public abstract class AnimationProcessorLodMixin {
	@WrapMethod(method = "createBoneSnapshots")
	private static void lobecorp$samplePose(ControllerState state, BoneSnapshots snapshots, Operation<Void> original) {
		var request = ((LcAnimationLodStateAccess) (Object) state).lobecorp$lodRequest();
		if (request == null) {
			original.call(state, snapshots);
			return;
		}
		request.cache().apply(state, snapshots, request, original::call);
	}
}
