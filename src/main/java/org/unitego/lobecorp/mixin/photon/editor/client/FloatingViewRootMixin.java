package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.floating.FloatingViewRoot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;

@Mixin(FloatingViewRoot.class)
public abstract class FloatingViewRootMixin {
    @WrapMethod(method = "setHostedEditor(Lcom/lowdragmc/lowdraglib2/editor/ui/Editor;)V")
    private void lobecorp$refreshHostedControls(@Nullable Editor hostedEditor, Operation<Void> original) {
        var root = (FloatingViewRoot) (Object) this;
        var previous = root.getHostedEditor();
        original.call(hostedEditor);

        if (previous != root.getHostedEditor()) {
            PhotonEditorTools.refreshControls(root);
        }
    }
}
