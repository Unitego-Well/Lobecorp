package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.photon.gui.editor.FXEditor;
import dev.vfyjxf.taffy.style.FlexDirection;

import java.util.*;

/// 制作面板沿用原编辑器停靠树；工程关闭和切换时只释放自身视图与预览。
public class PhotonEditorDockedPanel extends View {
	public final UIElement contentContainer = new UIElement();
	private final UIElement buttons = new UIElement();
	private final Slot slot;
	private final List<View> companions = new ArrayList<>();
	private final Map<View, Slot> companionSlots = new IdentityHashMap<>();
	private FXEditor owner;
	private Runnable cleanup = () -> {
	};
	private boolean closed;
	private Object group;
	private boolean ownsGroup;
	public PhotonEditorDockedPanel(String title, Slot slot) {
		super(title);
		this.slot = slot;
		layout(layout -> layout.flexDirection(FlexDirection.COLUMN).minHeight(0));
		contentContainer.layout(layout -> layout.widthPercent(100).flex(1).minHeight(0).flexDirection(FlexDirection.COLUMN));
		buttons.layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(3).flexShrink(0));
		addChildren(contentContainer, buttons);
		setCanRemove(true);
		setFocusable(true);
		setOnRemove(this::close);
	}

	private static void select(View view) {
		var container = view.getViewContainer();
		if (container != null) {
			container.selectView(view);
		}
	}

	private static ViewContainer container(FXEditor editor, Slot slot) {
		return switch (slot) {
			case LEFT -> editor.leftWindow.getLeftTop();
			case RIGHT -> editor.rightWindow.getRightTop();
			case CENTER -> editor.centerWindow.getLeftTop();
			case BOTTOM -> editor.bottomWindow.getLeftTop();
		};
	}

	public static void closeAll(FXEditor editor) {
		for (var view : List.copyOf(editor.getViewFallbacks().keySet())) {
			if (view instanceof PhotonEditorDockedPanel panel) {
				panel.close();
			}
		}
	}

	public void addContent(UIElement element) {
		contentContainer.addChild(element);
	}

	public void addButton(Button button) {
		buttons.addChild(button);
	}

	public void group(Object value, boolean owns) {
		group = value;
		ownsGroup = owns;
	}

	public void setOnClose(Runnable action) {
		cleanup = action;
	}

	public void inspector(String title, UIElement content) {
		var view = new View(title);
		view.layout(layout -> layout.flexDirection(FlexDirection.COLUMN).minHeight(0));
		if (content instanceof TabView) {
			view.addChild(content);
			companion(view);
			return;
		}
		var scroll = new ScrollerView();
		scroll.layout(layout -> layout.widthPercent(100).flex(1).minHeight(0));
		scroll.scrollerStyle(style -> style.adaptiveHeight(false));
		scroll.addScrollViewChild(content.layout(layout -> layout.widthPercent(100).minHeight(0)));
		view.addChild(scroll);
		companion(view);
	}

	public void companion(View view) {
		companion(view, Slot.RIGHT);
	}

	public void companion(View view, Slot slot) {
		companions.add(view);
		companionSlots.put(view, slot);
	}

	public void show(FXEditor editor) {
		owner = editor;
		for (var candidate : List.copyOf(editor.getViewFallbacks().keySet())) {
			if (candidate instanceof PhotonEditorDockedPanel panel && panel != this
					&& panel.getName().equals(getName()) && Objects.equals(panel.getId(), getId())) {
				var container = panel.getViewContainer();
				if (container != null) {
					container.selectView(panel);
				}
				close();
				return;
			}
		}
		editor.placeView(this, () -> container(editor, slot));
		for (var companion : companions) {
			editor.placeView(companion, () -> container(editor, companionSlots.get(companion)));
		}
		var container = getViewContainer();
		if (container != null) {
			container.selectView(this);
		}
	}

	public void activate(boolean active) {
		if (owner == null || closed) {
			return;
		}
		if (active) {
			select(this);
			for (var companion : companions) {
				if (companionSlots.get(companion) != Slot.CENTER) {
					select(companion);
				}
			}
		} else {
			select(owner.hierarchyView);
			select(owner.inspectorView);
			select(owner.timelineView);
		}
	}

	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		try {
			if (ownsGroup && owner != null) {
				for (var view : List.copyOf(owner.getViewFallbacks().keySet())) {
					if (view instanceof PhotonEditorDockedPanel panel && panel != this && panel.group == group) {
						panel.close();
					}
				}
			}
			cleanup.run();
		} finally {
			for (var companion : companions) {
				remove(companion);
			}
			remove(this);
		}
	}

	private void remove(View view) {
		if (owner != null) {
			owner.getViewFallbacks().remove(view);
			if (owner.getFloatingViews().isFloating(view)) {
				owner.getFloatingViews().dockBack(view);
			}
		}
		view.removeSelf();
	}

	public enum Slot {
		LEFT, RIGHT, CENTER, BOTTOM
	}
}
