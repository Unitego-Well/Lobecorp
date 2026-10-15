package org.unitego.lobecorp.client.photon.model.editor;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TreeList;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib2.gui.util.TreeNode;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// 按模型部件的实际父子关系显示层级，选中状态与同一编辑会话共享。
public class PhotonModelHierarchyView extends View {
	private final PhotonModelEditSession session;
	private final PhotonModelEditor modelEditor;
	private final TreeList<TreeNode<String, String>> tree = new TreeList<>();
	private final Map<String, TreeNode<String, String>> nodes = new LinkedHashMap<>();
	private List<HierarchyEntry> hierarchy = List.of();

	private record HierarchyEntry(String id, String parentId) {
	}

	public PhotonModelHierarchyView(PhotonModelEditSession session, PhotonModelEditor modelEditor) {
		super(PhotonModelTextUtil.key("editor.hierarchy"));
		this.session = session;
		this.modelEditor = modelEditor;
		var scroller = new ScrollerView();
		scroller.layout(layout -> {
			layout.widthPercent(100);
			layout.flex(1);
			layout.minHeight(0);
		});
		scroller.scrollerStyle(style -> style.adaptiveHeight(false));
		tree.setFlattenRoot(true).setStaticTree(true).setSupportMultipleSelection(false).setRightClickToExpand(false)
				.setNodeUISupplier(TreeList.textTemplate(this::displayName))
				.setOnNodeUICreated((node, ui) -> ui.style(style -> style.tooltips(Component.literal(node.getKey()))))
				.setOnDoubleClickNode(node -> modelEditor.inspectHierarchyPart(node.getKey()))
				.setOnSelectedChanged(this::onSelected);
		scroller.addEventListener(UIEvents.MOUSE_DOWN, this::onMouseDown, true);
		scroller.addScrollViewChild(tree);
		addChild(scroller);
	}

	public void refresh() {
		var current = session.asset().parts.stream().map(part -> new HierarchyEntry(part.id, part.parentId)).toList();

		if (!current.equals(hierarchy)) {
			rebuild(current);
		}

		var selected = nodes.get(session.selectedPart);
		tree.setSelected(selected == null ? Set.of() : Set.of(selected), false);

		if (selected != null) {
			tree.expandNodeAlongPath(selected);
		}
	}

	private void rebuild(List<HierarchyEntry> current) {
		var expanded = tree.getExpandedNodes().stream().map(TreeNode::getKey).toList();
		boolean firstLoad = hierarchy.isEmpty();
		hierarchy = current;
		nodes.clear();
		var root = new TreeNode<String, String>("");
		addChildren(root, "");
		tree.setRoot(root);

		for (var node : nodes.values()) {
			if (firstLoad || expanded.contains(node.getKey())) {
				tree.expandNodeAlongPath(node);
				tree.expandNode(node);
			}
		}
	}

	private void addChildren(TreeNode<String, String> parent, String parentId) {
		for (var entry : hierarchy) {
			if (!entry.parentId().equals(parentId)) {
				continue;
			}

			var node = parent.createChild(entry.id());
			nodes.put(entry.id(), node);
			addChildren(node, entry.id());
		}
	}

	private Component displayName(TreeNode<String, String> node) {
		String id = node.getKey();

		try {
			UUID.fromString(id);
		} catch (IllegalArgumentException _) {
			return Component.literal(id);
		}

		int index = hierarchy.stream().map(HierarchyEntry::id).toList().indexOf(id);
		return Component.translatable(PhotonModelTextUtil.key("editor.hierarchy.legacy_part"), index + 1);
	}

	private void onMouseDown(UIEvent event) {
		if (event.button != 1 || getEditor() == null) {
			return;
		}

		var hovered = tree.getHoveredNode();
		String partId = hovered == null ? "" : hovered.getKey();

		if (hovered != null) {
			tree.setSelected(Set.of(hovered), true);
		}

		getEditor().openMenu(this, event.x, event.y, createMenu(partId));
		event.stopPropagation();
	}

	private TreeBuilder.Menu createMenu(String partId) {
		var menu = TreeBuilder.Menu.start();
		menu.branch(Icons.ADD_FILE, PhotonModelTextUtil.key("editor.hierarchy.add"),
				branch -> modelEditor.appendPrimitiveMenu(branch, partId));

		if (partId.isEmpty()) {
			return menu;
		}

		menu.crossLine();
		menu.leaf(PhotonModelTextUtil.key("editor.hierarchy.inspect"), () -> modelEditor.inspectHierarchyPart(partId));
		menu.leaf(PhotonModelTextUtil.key("editor.rename_part"), () -> modelEditor.renameHierarchyPart(partId));
		modelEditor.appendPartRenderingMenu(menu, partId);
		menu.branch(PhotonModelTextUtil.key("mesh.tools"), branch -> modelEditor.appendPartInversionMenu(branch, partId));
		menu.crossLine();
		menu.leaf(Icons.REMOVE_FILE, PhotonModelTextUtil.key("editor.delete_subtree"),
				() -> modelEditor.deleteHierarchyPart(partId, true));
		menu.leaf(Icons.REMOVE_FILE, PhotonModelTextUtil.key("editor.delete_promote"),
				() -> modelEditor.deleteHierarchyPart(partId, false));
		return menu;
	}

	private void onSelected(Set<TreeNode<String, String>> selected) {
		if (selected.isEmpty()) {
			return;
		}

		modelEditor.inspectHierarchyPart(selected.iterator().next().getKey(), false);
	}
}
