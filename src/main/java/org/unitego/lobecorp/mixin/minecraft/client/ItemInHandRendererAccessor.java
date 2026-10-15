package org.unitego.lobecorp.mixin.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemInHandRenderer.class)
@SuppressWarnings("UnnecessaryModifier")
public interface ItemInHandRendererAccessor {
	@Invoker("renderMap")
	public void lobecorp$renderMap(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, ItemStack itemStack);
}
