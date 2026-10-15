package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import dev.vfyjxf.taffy.style.FlexDirection;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

public class PhotonAnimationOutline extends UIElement {
	private final PhotonAnimationWorkspace workspace;
	private final Runnable create;
	private final Runnable duplicate;
	private final Runnable rename;
	private final Runnable delete;
	private @Nullable ArrayConfiguratorGroup<PhotonAnimationDefinition> animations;
	private final ScrollerView scroller = PhotonAnimationUiUtil.scroller(new UIElement());

	public PhotonAnimationOutline(PhotonAnimationWorkspace workspace, Runnable create, Runnable duplicate,
	                              Runnable rename, Runnable delete) {
		this.workspace = workspace;
		this.create = create;
		this.duplicate = duplicate;
		this.rename = rename;
		this.delete = delete;
		getLayout().widthPercent(100).heightPercent(100).flex(1).minHeight(0).flexDirection(FlexDirection.COLUMN);
		addEventListener(UIEvents.DRAG_END, _ -> commitOrder(), true);
		addEventListener(UIEvents.MOUSE_DOWN, this::clearSelectionOnBackground);
	}

	public void rebuild(PhotonAnimationLibrary library, @Nullable UUID selected) {
		clearAllChildren();
		var list = new ArrayConfiguratorGroup<PhotonAnimationDefinition>("", false, List::of,
				(getter, _) -> animationName(getter.get()), false) {
			@Override
			protected void onAdd(@Nonnull UIEvent event) {
				create.run();
			}

			@Override
			protected void onRemove(@Nonnull UIEvent event) {
				delete.run();
			}
		};
		animations = list;
		list.hideTitle();
		list.getLayout().widthPercent(100).heightPercent(100).flex(1).minHeight(0);
		var content = list.configuratorContainer;
		content.getLayout().widthPercent(100).setFlexShrink(0);
		scroller.clearAllScrollViewChildren();
		scroller.addScrollViewChild(content);
		list.addChildAt(scroller, 1);
		list.buttonGroup.getLayout().setFlexShrink(0);
		list.addButton.setDisplay(true);
		list.removeButton.setDisplay(true);
		PhotonAnimationUiUtil.tooltip(list.addButton, "new");
		PhotonAnimationUiUtil.tooltip(list.removeButton, "delete");

		for (var animation : library.animations) {
			var item = list.new ItemConfigurator(animation, list.configuratorProvider);
			list.addConfigurator(item);
			item.getLayout().setFlexShrink(0);
			item.addEventListener(UIEvents.MOUSE_DOWN, event -> {
				if (event.button == 1) {
					list.setSelected(item);
					openMenu(event, animation);
				}
			});

			if (animation.id.equals(selected)) {
				list.setSelected(item);
			}
		}

		list.setOnSelectedChanged(animation -> workspace.selectAnimation(animation == null ? null : animation.id));
		addChild(list);
	}

	private void clearSelectionOnBackground(UIEvent event) {
		if (event.button != 0 || animations == null) {
			return;
		}

		var ui = getModularUI();

		if (ui != null && ui.getDragHandler().isDragging()) {
			return;
		}

		var target = event.target;

		if (target == this || target == animations || target == animations.configuratorContainer
				|| target == scroller || target == scroller.viewPort || target == scroller.viewContainer
				|| target == scroller.viewContainer.getParent()) {
			animations.setSelected(null);
		}
	}

	private Configurator animationName(PhotonAnimationDefinition animation) {
		var row = new Configurator();
		row.getLayout().widthPercent(100);
		var label = new Label().setText(animation.name, false);
		TimelineContext.styleLabel(label);
		label.getLayout().widthPercent(100);
		PhotonAnimationUiUtil.tooltip(label, "outline_panel");
		row.addInlineChild(label);
		return row;
	}

	private void openMenu(UIEvent event, PhotonAnimationDefinition animation) {
		if (event.button != 1) {
			return;
		}

		workspace.selectAnimation(animation.id);
		var menu = TreeBuilder.Menu.start();
		menu.leaf(PhotonAnimationTextUtil.key("copy"), duplicate);
		menu.leaf(PhotonAnimationTextUtil.key("rename"), rename);
		menu.leaf(PhotonAnimationTextUtil.key("delete"), delete);
		workspace.owner().openMenu(this, event.x, event.y, menu);
		event.stopPropagation();
	}

	private void commitOrder() {
		if (animations == null) {
			return;
		}

		var ordered = new ArrayList<PhotonAnimationDefinition>();

		for (var configurator : animations.getConfigurators()) {
			if (configurator instanceof ArrayConfiguratorGroup<?>.ItemConfigurator item
					&& item.object instanceof PhotonAnimationDefinition animation) {
				ordered.add(animation);
			}
		}

		workspace.reorderAnimations(ordered);
	}

	public void close() {
		animations = null;
		clearAllChildren();
	}
}
