package org.unitego.lobecorp.mixin.minecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.api.client.gui.IGuiGraphicsExtractorExpand;

@NullMarked
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements IGuiGraphicsExtractorExpand {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private TextureAtlas guiSprites;

    @Override
    public Minecraft lobecorp$getMinecraft() {
        return minecraft;
    }

    @Override
    public TextureAtlas lobecorp$getGuiSprites() {
        return guiSprites;
    }
}
