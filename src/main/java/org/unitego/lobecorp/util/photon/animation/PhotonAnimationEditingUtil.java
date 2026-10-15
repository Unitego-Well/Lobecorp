package org.unitego.lobecorp.util.photon.animation;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.CommandEvents;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationTab;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationWorkspace;
import org.unitego.lobecorp.util.photon.clipboard.PhotonResourceClipboardUtil;

public class PhotonAnimationEditingUtil {
	public static @Nullable PhotonAnimationWorkspace workspace(UIElement element) {
		var owner = editor(element);
		return owner == null ? null : PhotonAnimationTab.editing(owner);
	}

	public static @Nullable FXEditor editor(UIElement element) {
		for (var parent = element; parent != null; parent = parent.getParent()) {
			if (parent instanceof FXEditor owner) {
				return owner;
			}
		}

		return null;
	}

	public static void guardObjectCommand(FXEditor owner, UIEvent event) {
		if (PhotonAnimationTab.editing(owner) == null || PhotonResourceClipboardUtil.textTarget(event.target)) {
			return;
		}

		for (var element = event.target; element != null; element = element.getParent()) {
			if (element == owner.timelineView || element == owner.inspectorView) {
				return;
			}
		}

		if (CommandEvents.PASTE.equals(event.command) || CommandEvents.CUT.equals(event.command)
				|| CommandEvents.COPY.equals(event.command) || event.keyCode == GLFW.GLFW_KEY_DELETE) {
			event.stopImmediatePropagation();
		}
	}
}
