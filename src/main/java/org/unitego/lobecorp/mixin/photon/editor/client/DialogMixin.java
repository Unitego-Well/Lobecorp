package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorHost;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.FileNode;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorNotificationUtil;
import org.unitego.lobecorp.util.resource.DevelopmentResourceUiUtil;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Mixin(Dialog.class)
public abstract class DialogMixin {
	@Unique
	private boolean lobecorp$timedNotification;

	@Inject(method = "showFileDialog(Ljava/lang/String;Ljava/io/File;ZLjava/io/File;Ljava/util/function/Predicate;ILjava/util/function/Consumer;)Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;",
			at = @At(value = "RETURN", ordinal = 1))
	private static void lobecorp$developmentRoots(String title, File dir, boolean isSelector,
	                                              File defaultValue, Predicate<FileNode> valid, int features, Consumer<File> result,
	                                              CallbackInfoReturnable<Dialog> cir, @Local(name = "textField") TextField textField,
	                                              @Local(name = "navigate") Consumer<File> navigate) {
		var dialog = cir.getReturnValue();
		boolean[] installed = {false};
		dialog.addEventListener(UIEvents.ADDED, event -> {
			if (event.target != dialog || installed[0] || !lobecorp$developmentRootsEnabled(dialog)) {
				return;
			}

			installed[0] = true;
			DevelopmentResourceUiUtil.fileDialog(dialog, dir, isSelector, textField, navigate);
		});
	}

	@Unique
	private static boolean lobecorp$developmentRootsEnabled(Dialog dialog) {
		for (var element = dialog.getParent(); element != null; element = element.getParent()) {
			if (element instanceof EditorWindow window) {
				return lobecorp$developmentRootsEditor(window.getCurrentEditor());
			}

			if (element instanceof EditorHost host) {
				return lobecorp$developmentRootsEditor(host.getHostedEditor());
			}
		}

		var ui = dialog.getModularUI();
		if (ui == null) {
			return false;
		}

		if (ui.ui.rootElement instanceof EditorHost host) {
			return lobecorp$developmentRootsEditor(host.getHostedEditor());
		}

		EditorWindow owner = null;
		for (var window : ui.getElementsByType(EditorWindow.class)) {
			if (!lobecorp$availableWindow(window)) {
				continue;
			}

			if (owner != null) {
				return false;
			}

			owner = window;
		}

		return owner == null ? ui.getElementsByType(FXEditor.class).isEmpty()
				: lobecorp$developmentRootsEditor(owner.getCurrentEditor());
	}

	@Unique
	private static boolean lobecorp$developmentRootsEditor(Editor editor) {
		return editor != null && (!(editor instanceof FXEditor) || editor instanceof PhotonEffectEditor);
	}

	@Unique
	private static boolean lobecorp$availableWindow(EditorWindow window) {
		for (UIElement parent = window; parent != null; parent = parent.getParent()) {
			if (!parent.isDisplayed() || !parent.isActive()) {
				return false;
			}
		}

		return true;
	}

	@Inject(method = "showNotification(Ljava/lang/String;F)Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;", at = @At("RETURN"))
	private static void lobecorp$markTimed(String info, float duration, CallbackInfoReturnable<Dialog> cir) {
		((DialogMixin) (Object) cir.getReturnValue()).lobecorp$timedNotification = true;
	}

	@WrapMethod(method = "show(Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;)Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;")
	private Dialog lobecorp$stackNotifications(UIElement parent, Operation<Dialog> original) {
		if (!lobecorp$timedNotification) {
			return original.call(parent);
		}

		var dialog = (Dialog) (Object) this;
		var actualParent = PhotonEditorNotificationUtil.parent(dialog, parent);
		if (actualParent == null) {
			return original.call(parent);
		}

		if (dialog.getParent() == actualParent) {
			return dialog;
		}

		return original.call(actualParent);
	}

	@WrapOperation(method = "show(Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;)Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;",
			at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;focus()V"))
	private void lobecorp$preserveNotificationFocus(Dialog dialog, Operation<Void> original) {
		if (lobecorp$timedNotification && PhotonEditorNotificationUtil.isNotificationStack(dialog.getParent())) {
			return;
		}

		original.call(dialog);
	}

	@WrapMethod(method = "close")
	private void lobecorp$removeEmptyStack(Operation<Void> original) {
		var parent = ((Dialog) (Object) this).getParent();
		original.call();
		PhotonEditorNotificationUtil.removeEmptyStack(parent);
	}
}
