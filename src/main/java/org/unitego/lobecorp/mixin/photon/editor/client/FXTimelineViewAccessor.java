package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FXTimelineView.class)
@SuppressWarnings("UnnecessaryModifier")
public interface FXTimelineViewAccessor {
	@Accessor("headersContainer")
	public UIElement lobecorp$headersContainer();

	@Accessor("lanesContainer")
	public UIElement lobecorp$lanesContainer();

	@Accessor("rowHeight")
	public int lobecorp$rowHeight();

	@Accessor("clipboardKind")
	public static void lobecorp$setClipboardKind(int kind) {
		throw new AssertionError();
	}
}
