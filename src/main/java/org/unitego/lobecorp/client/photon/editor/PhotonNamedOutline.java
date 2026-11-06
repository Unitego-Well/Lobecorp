package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import dev.vfyjxf.taffy.style.FlexDirection;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Definition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/// 复用移除前列表的原生行、拖动条和右键菜单，排序通过现有 UUID 草稿事务提交。
public class PhotonNamedOutline extends UIElement {
	private final FXEditor editor;
	private final Consumer<UUID> select;
	private final Runnable create;
	private final Runnable duplicate;
	private final Runnable rename;
	private final Runnable delete;
	private final Consumer<List<UUID>> reorder;
	private final ScrollerView scroller = new ScrollerView();
	private ArrayConfiguratorGroup<Definition> animations;

	public PhotonNamedOutline(FXEditor editor, Consumer<UUID> select, Runnable create, Runnable duplicate,
	                          Runnable rename, Runnable delete, Consumer<List<UUID>> reorder) {
		this.editor = editor;
		this.select = select;
		this.create = create;
		this.duplicate = duplicate;
		this.rename = rename;
		this.delete = delete;
		this.reorder = reorder;
		layout(layout -> layout.widthPercent(100).flex(1).minHeight(0).flexDirection(FlexDirection.COLUMN));
		scroller.layout(layout -> layout.widthPercent(100).flex(1).minHeight(0));
		scroller.scrollerStyle(style -> style.adaptiveHeight(false));
		addEventListener(UIEvents.DRAG_END, _ -> commitOrder(), true);
		addEventListener(UIEvents.MOUSE_DOWN, this::background);
	}

	public void rebuild(List<Definition> definitions, UUID selected) {
		clearAllChildren();
		var list = new ArrayConfiguratorGroup<Definition>("", false, List::of,
				(getter, _) -> name(getter.get()), false) {
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
		list.layout(layout -> layout.widthPercent(100).flex(1).minHeight(0));
		list.configuratorContainer.layout(layout -> layout.widthPercent(100).flexShrink(0));
		scroller.clearAllScrollViewChildren();
		scroller.addScrollViewChild(list.configuratorContainer);
		list.addChildAt(scroller, 1);
		list.buttonGroup.layout(layout -> layout.flexShrink(0));
		list.addButton.setDisplay(true);
		list.removeButton.setDisplay(true);
		for (var definition : definitions) {
			var item = list.new ItemConfigurator(definition, list.configuratorProvider);
			list.addConfigurator(item);
			item.layout(layout -> layout.flexShrink(0));
			item.addEventListener(UIEvents.MOUSE_DOWN, event -> {
				if (event.button == 1) {
					list.setSelected(item);
					select.accept(definition.id());
					var menu = TreeBuilder.Menu.start();
					menu.leaf("photon.lobecorp.animation.copy", duplicate);
					menu.leaf("photon.lobecorp.animation.rename", rename);
					menu.leaf("photon.lobecorp.animation.delete", delete);
					editor.openMenu(this, event.x, event.y, menu);
					event.stopPropagation();
				}
			});
			if (definition.id().equals(selected)) {
				list.setSelected(item);
			}
		}
		list.setOnSelectedChanged(definition -> select.accept(definition == null ? null : definition.id()));
		addChild(list);
	}

	private static Configurator name(Definition definition) {
		var row = new Configurator();
		row.layout(layout -> layout.widthPercent(100));
		var label = new Label().setText(definition.name(), false);
		TimelineContext.styleLabel(label);
		label.layout(layout -> layout.widthPercent(100));
		row.addInlineChild(label);
		return row;
	}

	private void background(UIEvent event) {
		if (event.button != 0 || animations == null) {
			return;
		}
		var ui = getModularUI();
		if (ui != null && ui.getDragHandler().isDragging()) {
			return;
		}
		if (event.target == this || event.target == animations || event.target == animations.configuratorContainer
				|| event.target == scroller || event.target == scroller.viewPort || event.target == scroller.viewContainer) {
			animations.setSelected(null);
		}
	}

	private void commitOrder() {
		if (animations == null) {
			return;
		}
		var ordered = new ArrayList<UUID>();
		for (var field : animations.getConfigurators()) {
			if (field instanceof ArrayConfiguratorGroup<?>.ItemConfigurator item && item.object instanceof Definition definition) {
				ordered.add(definition.id());
			}
		}
		reorder.accept(List.copyOf(ordered));
	}
}
