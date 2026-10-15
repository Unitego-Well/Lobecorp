package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.photon.gui.editor.FXEditor;
import dev.vfyjxf.taffy.style.FlexDirection;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;

import java.util.LinkedHashMap;
import java.util.Map;

public class PhotonAnimationPanels {
	private final FXEditor owner;
	private final Map<View, View> originals = new LinkedHashMap<>();
	private boolean active;
	private boolean closed;

	public PhotonAnimationPanels(PhotonAnimationWorkspace workspace, UIElement animations, UIElement inspector) {
		owner = workspace.owner();
		addPanel(workspace, animations, owner.hierarchyView, "outline_panel");
		addPanel(workspace, inspector, owner.inspectorView, "inspector_panel");
	}

	private void addPanel(PhotonAnimationWorkspace workspace, UIElement content, View original, String key) {
		var view = new View(PhotonAnimationTextUtil.key(key), original.getIcon()) {
			@Override
			public Tab createTab() {
				var tab = super.createTab();
				PhotonAnimationUiUtil.tooltip(tab, key);
				return tab;
			}
		};
		view.getLayout().widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN).minHeight(0);
		view.setFocusable(true);
		view.addChild(content.layout(layout -> layout.widthPercent(100).heightPercent(100).flex(1).minHeight(0)));
		workspace.bindCommands(view);
		originals.put(view, original);
	}

	public void setActive(boolean value) {
		if (closed || value == active) {
			return;
		}

		active = value;

		if (value) {
			originals.forEach(this::place);
		}
	}

	private void place(View view, View original) {
		if (view.getViewContainer() == null) {
			owner.placeView(view, () -> container(original));
		}
	}

	private ViewContainer container(View original) {
		var container = original.getViewContainer();

		if (container != null) {
			return container;
		}

		if (original == owner.hierarchyView) {
			return owner.leftWindow.getLeftTop();
		}

		return original == owner.inspectorView ? owner.rightWindow.getRightTop() : owner.bottomWindow.getLeftTop();
	}

	public void close() {
		if (closed) {
			return;
		}

		setActive(false);
		closed = true;

		for (var view : originals.keySet()) {
			owner.getViewFallbacks().remove(view);
			view.removeSelf();
		}

		originals.clear();
	}
}
