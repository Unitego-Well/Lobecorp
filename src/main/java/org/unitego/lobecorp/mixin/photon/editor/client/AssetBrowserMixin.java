package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.browser.AssetBrowser;
import com.lowdragmc.lowdraglib2.editor.ui.browser.ResourceBehaviorCache;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.util.photon.clipboard.PhotonResourceClipboardUtil;
import org.unitego.lobecorp.util.resource.DevelopmentResourceUiUtil;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Mixin(AssetBrowser.class)
public abstract class AssetBrowserMixin {
	@Shadow
	@Final
	private ResourceBehaviorCache behaviors;

	@Inject(method = "<init>(Lcom/lowdragmc/lowdraglib2/editor/ui/Editor;Ljava/io/File;)V", at = @At("TAIL"))
	private void lobecorp$developmentRoots(Editor editor, File root, CallbackInfo ci) {
		if (!(editor instanceof FXEditor) || editor instanceof PhotonEffectEditor) {
			DevelopmentResourceUiUtil.assetBrowser((AssetBrowser) (Object) this);
		}
	}

	@ModifyReturnValue(method = "createFileMenu", at = @At("RETURN"))
	private TreeBuilder.Menu lobecorp$resourceClipboard(TreeBuilder.Menu menu, File target) {
		if (target == null) {
			return menu;
		}

		var behavior = behaviors.forFile(target);
		var path = behavior == null ? null : behaviors.pathOf(target);
		if (behavior != null && behavior.isLoaded(path)) {
			PhotonResourceClipboardUtil.menu(behavior.container(), path, menu);
		}
		return menu;
	}

	@WrapOperation(method = "renameEntry", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/editor/ui/resource/ResourceProviderContainer;startInlineRename(Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;Ljava/lang/String;Ljava/util/function/Predicate;Ljava/util/function/Consumer;)V"))
	private void lobecorp$renameDialog(UIElement cell, String initial, Predicate<Character> charValidator, Consumer<String> onCommit, Operation<Void> original) {
		if (!PhotonResourceClipboardUtil.renameDialog((UIElement) (Object) this, initial, charValidator, onCommit)) {
			original.call(cell, initial, charValidator, onCommit);
		}
	}
}
