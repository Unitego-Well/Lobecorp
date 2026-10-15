package org.unitego.lobecorp.client.photon.model.editor;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceFileImport;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.browser.AssetBrowser.DraggedAssets;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TreeList;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeNode;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.ShaderGraphMaterial;
import com.lowdragmc.photon.gui.editor.resource.MaterialResource;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GrassColor;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProjectEditor;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Material;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Tint;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelMaterialUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelMaterialUtil.Entry;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/// 显示当前模型的材质槽、着色器快照与贴图引用，所有编辑复用模型会话历史。
public class PhotonModelResourcesView extends View {
	/// PNG 交换格式的固定文件扩展名。
	private static final String PNG_SUFFIX = ".png";
	/// 导入图片在共享资源包中的默认分类目录。
	private static final String TEXTURE_DIRECTORY = "textures";
	/// 控件之间的间隔，单位为 GUI 像素。
	private static final float CONTROL_GAP = 2f;
	/// 操作按钮换行时保留的最小宽度，单位为 GUI 像素。
	private static final float ACTION_MIN_WIDTH = 64f;

	private final PhotonModelEditSession session;
	private final PhotonModelProjectEditor owner;
	private final Runnable onChanged;
	private final TreeList<TreeNode<NodeKey, String>> tree = new TreeList<>();
	private final Map<String, TreeNode<NodeKey, String>> nodes = new LinkedHashMap<>();
	private final TextElement empty = label("resources.empty");
	private final TextElement fallback = label("");
	private final TextElement status = label("");
	private List<Entry> entries = List.of();
	private String selectedId = "";
	private boolean inspecting;
	private boolean closed;
	private boolean editingMaterial;
	private ConfiguratorGroup materialEditor;

	private record NodeKey(String materialId, boolean texture) {
	}

	public PhotonModelResourcesView(PhotonModelEditSession session, PhotonModelProjectEditor owner, Runnable onChanged) {
		super(PhotonModelTextUtil.key("editor.resources"));
		this.session = session;
		this.owner = owner;
		this.onChanged = onChanged;
		layout(layout -> layout.gapAll(CONTROL_GAP));
		addChildren(row().addChildren(button("resources.add_slot", this::addSlot),
				button("resources.import_png", () -> choosePng(""))));
		addChild(row().addChild(button("resources.from_library", () -> chooseLibrary(""))));
		addChild(row().addChildren(
				button("resources.add_graph", () -> addMaterial(PhotonModelMaterialUtil.nextId(session.asset()),
						PhotonModelMaterialUtil.fromLibrary(new ShaderGraphMaterial()))),
				button("resources.add_custom", () -> addMaterial(PhotonModelMaterialUtil.nextId(session.asset()),
						PhotonModelMaterialUtil.fromLibrary(new CustomShaderMaterial())))));
		tree.setFlattenRoot(true).setStaticTree(true).setSupportMultipleSelection(false)
				.setNodeUISupplier(TreeList.iconTextTemplate(this::nodeIcon, this::nodeText))
				.setOnNodeUICreated((node, ui) -> installDropTarget(ui, node.getKey().materialId()))
				.setOnSelectedChanged(this::onSelected);
		var scroller = new ScrollerView();
		scroller.layout(layout -> {
			layout.widthPercent(100);
			layout.flex(1);
			layout.minHeight(0);
		});
		scroller.scrollerStyle(style -> style.adaptiveHeight(false));
		scroller.addScrollViewChild(tree);
		addChildren(empty, scroller, fallback, status,
				label("resources.shared_note"));
		installDropTarget(this, "");
		refresh();
	}

	public void refresh() {
		if (closed) {
			return;
		}

		var current = PhotonModelMaterialUtil.entries(session.asset());

		if (!current.equals(entries)) {
			rebuild(current);
		}

		if (!session.asset().materials.containsKey(selectedId)) {
			selectedId = entries.isEmpty() ? "" : entries.getFirst().id();
		}

		var node = nodes.get(selectedId);
		tree.setSelected(node == null ? Set.of() : Set.of(node), false);
		empty.setDisplay(entries.isEmpty());
		long inherited = PhotonModelMaterialUtil.fallbackFaces(session.asset());
		fallback.setDisplay(inherited > 0);
		fallback.setText(Component.translatable(PhotonModelTextUtil.key("resources.fallback_faces"), inherited));
	}

	public boolean isInspecting() {
		return inspecting;
	}

	public void clearInspection() {
		inspecting = false;
		materialEditor = null;
	}

	public void inspectSelected() {
		if (closed) {
			return;
		}

		refresh();

		if (editingMaterial) {
			return;
		}

		inspecting = true;
		materialEditor = null;
		owner.inspectorView.clear();
		var inspector = owner.inspectorView.inspector.scrollerView;
		inspector.clearAllScrollViewChildren();
		var material = session.asset().materials.get(selectedId);

		if (material == null) {
			inspector.addScrollViewChild(label("resources.empty"));
			return;
		}

		String id = selectedId;
		inspector.addScrollViewChild(label("").setText(Component.literal(id)));

		if (material.materialData.isEmpty()) {
			buildTextureControls(inspector, id, material);
		} else {
			safe(() -> buildMaterialControls(inspector, id, material));
		}

		inspector.addScrollViewChild(new SelectorConfigurator<>(PhotonModelTextUtil.key("resources.tint"),
				() -> material.tint, value -> safe(() -> edit("resources.edit_slot", asset ->
				PhotonModelMaterialUtil.requireSlot(asset, id).tint = value, id)), material.tint, false,
				Arrays.asList(Tint.values()), value -> PhotonModelTextUtil.key("resources.tint." + value.name().toLowerCase(Locale.ROOT))));
		inspector.addScrollViewChild(new BooleanConfigurator(PhotonModelTextUtil.key("resources.overlay"),
				() -> material.overlay, value -> safe(() -> edit("resources.edit_slot", asset ->
				PhotonModelMaterialUtil.requireSlot(asset, id).overlay = value, id)), material.overlay, false));
		inspector.addScrollViewChild(button("resources.assign", () -> assign(id)));
		inspector.addScrollViewChild(row().addChildren(button("resources.rename", () -> rename(id)),
				button("resources.delete_slot", () ->
						edit("resources.delete_slot", asset -> PhotonModelMaterialUtil.delete(asset, id), ""))));
	}

	private void buildTextureControls(ScrollerView inspector, String id, Material material) {
		var texture = new AtomicReference<>(material.texture);
		inspector.addScrollViewChild(new StringConfigurator(PhotonModelTextUtil.key("resources.texture"),
				texture::get, texture::set, material.texture, false).setResourceLocation(true));
		inspector.addScrollViewChild(button("resources.apply_texture", () ->
				edit("resources.apply_texture", asset -> PhotonModelMaterialUtil.replaceTexture(asset, id, texture.get()), id)));
		inspector.addScrollViewChild(row().addChildren(
				button("resources.replace_library", () -> chooseLibrary(id)),
				button("resources.replace_png", () -> choosePng(id))));
	}

	private void buildMaterialControls(ScrollerView inspector, String id, Material material) {
		var group = new ConfiguratorGroup(PhotonModelTextUtil.key("resources.shader_settings"));
		group.setCollapse(false).setCanCollapse(false);
		var editable = PhotonModelMaterialUtil.editable(material);
		editable.buildConfigurator(group);
		materialEditor = group;
		group.addEventListener(Configurator.CHANGE_EVENT, event -> {
			event.stopPropagation();

			if (closed || !inspecting || materialEditor != group) {
				return;
			}

			safe(() -> {
				editingMaterial = true;

				try {
					session.change("resources.edit_shader", asset -> PhotonModelMaterialUtil.updateMaterial(asset, id, editable));
					status.setText(Component.empty());
				} finally {
					editingMaterial = false;
				}
			});
		});
		inspector.addScrollViewChild(group);
		inspector.addScrollViewChild(row().addChildren(
				button("resources.replace_library", () -> chooseLibrary(id)),
				button("resources.replace_png", () -> choosePng(id))));
	}

	public void close() {
		closed = true;
		inspecting = false;
		materialEditor = null;
		nodes.clear();
		entries = List.of();
	}

	private void rebuild(List<Entry> current) {
		var expanded = tree.getExpandedNodes().stream().map(TreeNode::getKey).toList();
		boolean first = entries.isEmpty();
		entries = current;
		nodes.clear();
		var root = new TreeNode<NodeKey, String>(new NodeKey("", false));

		for (var entry : entries) {
			var node = root.createChild(new NodeKey(entry.id(), false));
			node.createChild(new NodeKey(entry.id(), true));
			nodes.put(entry.id(), node);
		}

		tree.setRoot(root);

		for (var node : nodes.values()) {
			if (first || expanded.contains(node.getKey())) {
				tree.expandNode(node);
			}
		}
	}

	private Component nodeText(TreeNode<NodeKey, String> node) {
		var key = node.getKey();
		var material = session.asset().materials.get(key.materialId());

		if (material == null) {
			return Component.empty();
		}

		if (key.texture()) {
			return material.materialData.isEmpty() ? Component.literal(material.texture)
					: Component.translatable(PhotonModelTextUtil.key("resources.shader_snapshot"));
		}

		long uses = PhotonModelMaterialUtil.usage(session.asset(), key.materialId());
		return uses == 0 ? Component.translatable(PhotonModelTextUtil.key("resources.slot_unused"), key.materialId())
				: Component.translatable(PhotonModelTextUtil.key("resources.slot_usage"), key.materialId(), uses);
	}

	private IGuiTexture nodeIcon(TreeNode<NodeKey, String> node) {
		var material = session.asset().materials.get(node.getKey().materialId());

		if (material == null) {
			return IGuiTexture.EMPTY;
		}

		if (!material.materialData.isEmpty()) {
			return DynamicTexture.of(() -> PhotonModelMaterialUtil.resolve(material).preview());
		}

		var location = Identifier.tryParse(material.texture);

		if (location == null) {
			return IGuiTexture.MISSING_TEXTURE;
		}

		return SpriteTexture.of(location).setColor(material.tint == Tint.GRASS
				? 0xFF000000 | GrassColor.getDefaultColor() : -1);
	}

	private void onSelected(Set<TreeNode<NodeKey, String>> selection) {
		if (selection.isEmpty()) {
			return;
		}

		selectedId = selection.iterator().next().getKey().materialId();
		inspectSelected();
		onChanged.run();
	}

	private void addSlot() {
		String suggestion = PhotonModelMaterialUtil.nextId(session.asset());
		Dialog.stringEditorDialog(PhotonModelTextUtil.key("resources.add_slot"), suggestion,
				PhotonModelMaterialUtil::validName, name -> {
					if (name != null) {
						safe(() -> addMaterial(name, PhotonModelMaterialUtil.defaultMaterial()));
					}
				}).show(owner);
	}

	private void addMaterial(String id, Material material) {
		edit("resources.add_slot", asset -> PhotonModelMaterialUtil.add(asset, id, material), id);
	}

	private void chooseLibrary(String targetId) {
		var selected = new AtomicReference<IMaterial>();
		var dialog = MaterialResource.INSTANCE.getResourceInstance().createSelectorDialog(0, 0,
				selected::set, () -> {
				});
		// 原生选择器会在点击资源时立即回调；暂存到确认时才写入模型历史。
		dialog.buttonContainer.clearAllChildren();
		dialog.addButton(new Button().setText("ldlib.gui.tips.confirm").setOnClick(_ -> {
			if (selected.get() != null) {
				safe(() -> importMaterial(selected.get(), targetId));
			}

			dialog.close();
		}));
		dialog.addButton(new Button().setText("ldlib.gui.tips.cancel").setOnClick(_ -> dialog.close()));
		dialog.show(owner);
	}

	private void importMaterial(IMaterial source, String targetId) {
		if (closed) {
			return;
		}

		var material = PhotonModelMaterialUtil.fromLibrary(source);

		if (targetId.isEmpty()) {
			addMaterial(PhotonModelMaterialUtil.nextId(session.asset()), material);
		} else {
			edit("resources.replace_material", asset ->
					PhotonModelMaterialUtil.replaceMaterial(asset, targetId, material), targetId);
		}
	}

	private void choosePng(String targetId) {
		Dialog.showFileDialog(PhotonModelTextUtil.key("resources.import_png"), LDLib2.getAssetsDir(), true,
				Dialog.suffixFilter(PNG_SUFFIX), file -> {
					if (file != null) {
						safe(() -> importPng(file, targetId));
					}
				}).show(owner);
	}

	private void importPng(File file, String targetId) {
		if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(PNG_SUFFIX)) {
			throw new IllegalArgumentException(Component.translatable(PhotonModelTextUtil.key("resources.png_only")).getString());
		}

		ResourceFileImport.resolveOrImport(owner, file, TEXTURE_DIRECTORY, location -> safe(() -> {
			if (closed) {
				return;
			}

			if (targetId.isEmpty()) {
				var material = new Material();
				material.texture = location.toString();
				addMaterial(PhotonModelMaterialUtil.nextId(session.asset()), material);
			} else {
				edit("resources.apply_texture", asset ->
						PhotonModelMaterialUtil.replaceTexture(asset, targetId, location.toString()), targetId);
			}
		}), () -> {
		});
	}

	private void rename(String id) {
		Dialog.stringEditorDialog(PhotonModelTextUtil.key("resources.rename"), id,
				PhotonModelMaterialUtil::validName, name -> {
					if (name != null && !name.equals(id)) {
						safe(() -> edit("resources.rename", asset -> PhotonModelMaterialUtil.rename(asset, id, name), name));
					}
				}).show(owner);
	}

	private void assign(String id) {
		var faces = new LinkedHashSet<>(session.selectedFaces);
		edit("resources.assign", asset -> PhotonModelMaterialUtil.assign(asset, id, faces), id);
	}

	private void edit(String label, Consumer<PhotonModelAsset> action, String selection) {
		if (closed) {
			return;
		}

		session.change(label, action);
		selectedId = selection;
		status.setText(Component.empty());
		refresh();
		onChanged.run();
		inspectSelected();
	}

	private void installDropTarget(UIElement target, String targetId) {
		target.addEventListener(UIEvents.DRAG_PERFORM, event -> {
			if (event.dragHandler == null) {
				return;
			}

			var dragged = event.dragHandler.draggingObject;

			if (dragged instanceof IMaterial material) {
				event.stopPropagation();
				safe(() -> importMaterial(material, targetId));
			} else if (dragged instanceof DraggedAssets(var files) && !files.isEmpty()) {
				event.stopPropagation();
				safe(() -> importPng(files.getFirst(), targetId));
			}
		});
		target.addEventListener(UIEvents.FILE_DROP, event -> onFilesDropped(event, targetId));
	}

	private void onFilesDropped(UIEvent event, String targetId) {
		if (event.droppedFiles == null || event.droppedFiles.isEmpty()) {
			return;
		}

		event.stopPropagation();
		safe(() -> importPng(event.droppedFiles.getFirst(), targetId));
	}

	private Button button(String key, Runnable action) {
		var button = new Button().setText(PhotonModelTextUtil.key(key)).setOnClick(_ -> safe(action));
		button.layout(layout -> layout.minWidth(ACTION_MIN_WIDTH));
		button.text.textStyle(style -> style.adaptiveWidth(false).textWrap(TextWrap.HOVER_ROLL));
		button.text.layout(layout -> {
			layout.flex(1);
			layout.minWidth(0);
		});
		button.text.setOverflowVisible(false);
		return button;
	}

	private TextElement label(String key) {
		return new Label().setText(key.isEmpty() ? Component.empty() : Component.translatable(PhotonModelTextUtil.key(key)))
				.textStyle(style -> style.adaptiveHeight(true).textWrap(TextWrap.WRAP));
	}

	private UIElement row() {
		return new UIElement().layout(layout -> {
			layout.flexDirection(FlexDirection.ROW);
			layout.wrap(FlexWrap.WRAP);
			layout.gapAll(CONTROL_GAP);
			layout.widthPercent(100);
		});
	}

	private void safe(Runnable action) {
		try {
			action.run();
		} catch (RuntimeException error) {
			var message = error.getMessage();
			status.setText(Component.translatable(PhotonModelTextUtil.key("error"),
					message == null ? error.getClass().getSimpleName() : message));
		}
	}
}
