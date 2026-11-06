package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldSceneRenderer.class)
public interface WorldSceneRendererProjectionAccessor {
    @Accessor("projectionMatrix")
    Matrix4f lobecorp$projectionMatrix();
}
