package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FXTimelineView.class)
@SuppressWarnings("UnnecessaryModifier")
public interface FXTimelineViewAccessor {
	@Accessor("clipboardKind")
	public static void lobecorp$setClipboardKind(int kind) {
		throw new AssertionError();
	}
}
