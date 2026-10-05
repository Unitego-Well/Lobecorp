package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.clipboard.PhotonResourceClipboardUtil;

import java.util.function.Consumer;
import java.util.function.Predicate;

@Mixin(ResourceProviderContainer.class)
public abstract class ResourceProviderContainerMixin<T> {
	@Unique
	@SuppressWarnings("unchecked")
	private ResourceProviderContainer<T> lobecorp$container() {
		return (ResourceProviderContainer<T>) (Object) this;
	}

	@ModifyReturnValue(method = "getMenu", at = @At("RETURN"))
	private TreeBuilder.Menu lobecorp$clipboardMenu(TreeBuilder.Menu menu) {
		var container = lobecorp$container();
		PhotonResourceClipboardUtil.menu(container, container.getSelected(), menu);
		return menu;
	}

	@ModifyReturnValue(method = "createResourceUI", at = @At("RETURN"))
	private UIElement lobecorp$clipboardKeys(UIElement cell, IResourcePath key) {
		var container = lobecorp$container();
		if (!PhotonResourceClipboardUtil.enabled(container))
			return cell;
		cell.setFocusable(true);
		cell.addEventListener(UIEvents.MOUSE_DOWN, event -> {
			if (event.button == 0 && PhotonResourceClipboardUtil.enabled(container) && !PhotonResourceClipboardUtil.textTarget(event.target))
				cell.focus();
		});
		cell.addEventListener(UIEvents.VALIDATE_COMMAND, event -> PhotonResourceClipboardUtil.command(container, event));
		cell.addEventListener(UIEvents.EXECUTE_COMMAND, event -> PhotonResourceClipboardUtil.command(container, event));
		return cell;
	}

	@WrapOperation(method = "renameResource", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/editor/ui/resource/ResourceProviderContainer;startInlineRename(Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;Ljava/lang/String;Ljava/util/function/Predicate;Ljava/util/function/Consumer;)V"))
	private void lobecorp$renameDialog(UIElement cell, String initial, Predicate<Character> charValidator, Consumer<String> onCommit, Operation<Void> original) {
		if (!PhotonResourceClipboardUtil.renameDialog(lobecorp$container(), initial, charValidator, onCommit))
			original.call(cell, initial, charValidator, onCommit);
	}
}
