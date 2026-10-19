package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.gui.ui.debugger.UIDebuggerWindowState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.resource.DevelopmentResourceUtil;

import java.io.File;

/// 本机状态留在游戏目录，不跟随可编辑资源进入源码目录。
@Mixin(UIDebuggerWindowState.class)
public abstract class UIDebuggerWindowStateDevelopmentResourcesMixin {
	@WrapOperation(method = "file", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/LDLib2;getAssetsDir()Ljava/io/File;"))
	private static File lobecorp$keepLocalState(Operation<File> original) {
		return DevelopmentResourceUtil.editorStateAssetsDirectory(original.call());
	}
}
