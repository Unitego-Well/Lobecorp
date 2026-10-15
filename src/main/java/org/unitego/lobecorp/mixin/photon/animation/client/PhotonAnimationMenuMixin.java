package org.unitego.lobecorp.mixin.photon.animation.client;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Tuple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;

/// 只为命名动画入口补充提示，保留原生菜单控件及样式。
@Mixin(TreeBuilder.Menu.class)
public abstract class PhotonAnimationMenuMixin {
	@Inject(method = "uiProvider", at = @At("RETURN"))
	private static void lobecorp$animationTip(Tuple<IGuiTexture, Component> node, CallbackInfoReturnable<UIElement> ci) {
		if (!(node.getB().getContents() instanceof TranslatableContents text)
				|| !PhotonAnimationTextUtil.key("title").equals(text.getKey())) {
			return;
		}

		ci.getReturnValue().getStyle().tooltips(PhotonAnimationTextUtil.key("title.tooltip"));
	}
}
