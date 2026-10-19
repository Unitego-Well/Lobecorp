package org.unitego.lobecorp.mixin.geckolib.client;

import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.renderer.base.RenderPassInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.registry.animation.client.LcDataTickets;
import org.unitego.lobecorp.util.animation.LcModelLodUtil;

@Mixin(CuboidGeoBone.class)
public abstract class CuboidModelLodMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lcom/geckolib/cache/model/cuboid/GeoCube;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
	private void lobecorp$drawLod(GeoCube cube, PoseStack pose, VertexConsumer vertices, int light, int overlay, int color,
	                              Operation<Void> original, @Local(argsOnly = true) RenderPassInfo<?> pass) {
		var band = pass.getOrDefaultGeckolibData(LcDataTickets.MODEL_LOD_BAND, 0);
		if (LcModelLodUtil.shouldRenderCube(pass.model(), cube, band)) {
			original.call(cube, pose, vertices, light, overlay, color);
		}
	}
}
