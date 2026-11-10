package org.unitego.lobecorp.client.photon.model.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TreeList;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.util.TreeNode;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;
import com.lowdragmc.photon.gui.editor.FXEditor;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.registry.translation.photon.model.PhotonModelTranslations;
import org.unitego.lobecorp.util.photon.model.PhotonModelWorkspaceEditUtil;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.Map;
import java.util.Locale;

import org.unitego.lobecorp.util.photon.model.editor.PhotonModelPrimitiveUtil;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.google.gson.JsonParser;
import org.joml.Vector3f;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.DELETE;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.NEW;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.RENAME;
import static org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslations.ACTION_DUPLICATE;

/// 04a3bab 模型编辑器的左侧工作区与右侧检视沿用原生停靠树，数据仍由当前工程事务持有。
public class PhotonModelWorkspace implements AutoCloseable {
	private final FXEditor editor;
	private final PhotonModelProject project;
	private final PhotonModelWorkspaceSession session;
	private final Map<View, Boolean> views = new LinkedHashMap<>();
	private final View hierarchy = view("editor.hierarchy");
	private final View uv = view("uv");
	private final View inspector = view("editor");
	private final TreeList<TreeNode<String, String>> tree = new TreeList<>();
	private final Map<String, TreeNode<String, String>> treeNodes = new LinkedHashMap<>();
	private String treeShape = "";
	private final ScrollerView properties = scroll();
	private final TextElement status = new TextElement();
	private final UIElement materialList = new UIElement();
	private final UIElement animationList = new UIElement();
	private final PhotonModelWorkspaceUV uvCanvas;
	private final PhotonModelWorkspaceMesh meshTools;
	private final PhotonModelWorkspaceResources resourceTools;
	private final PhotonModelWorkspaceTimeline timeline;
	private final PhotonModelWorkspaceScene animationScene;
	private ISubscription panels;
	private boolean refreshPending;
	private boolean inspectorInvalidated;
	private boolean closed;
	@SuppressWarnings("resource") // 工作区持有并在close中关闭网格工具，此处只刷新借用。
	private final UIEventListener refreshListener = _ -> {
		if (!closed) {
			meshTools().refresh();
		}
		if (refreshPending && !closed) {
			refreshPending = false;
			refresh();
		}
	};
	private String selectedPath = "";
	private String inspectorShape = "";
	private String inspectorPath = "";
	private String selectedPartId = "";
	private final Set<Integer> selectedFaces = new LinkedHashSet<>();
	private final Set<Dialog> dialogs = new LinkedHashSet<>();

	public PhotonModelWorkspace(FXEditor editor, PhotonModelProject project) {
		this.editor = editor;
		this.project = project;
		var resources = view("editor.resources");
		var animations = view("timeline.animations");
		session = new PhotonModelWorkspaceSession(editor, project, value -> status.setText(Component.literal(value)));
		uvCanvas = new PhotonModelWorkspaceUV(session, this::inspect, () -> selectedPartId, () -> Set.copyOf(selectedFaces), faces -> {
			selectedFaces.clear();
			selectedFaces.addAll(faces);
		});
		timeline = new PhotonModelWorkspaceTimeline(editor, session, this::inspect);
		resourceTools = new PhotonModelWorkspaceResources(editor, session);
		meshTools = new PhotonModelWorkspaceMesh(editor, project, session, () -> selectedPartId, this::selectMeshPart, selectedFaces);
		animationScene = new PhotonModelWorkspaceScene(editor, () -> {
			meshTools.enabled(false);
			animationScene().showScene(true);
		}, () -> {
			animationScene().showScene(false);
			meshTools.enabled(true);
		});
		animations.setId("model.animations");
		animations.setIcon(new TextTexture("A"));
		try {
			configureHierarchy();
			uv.addChild(uvCanvas);
			resources.addChild(scroll(materialList));
			animations.addChild(scroll(animationList));
			inspector.setName(editor.inspectorView.getName());
			inspector.setIcon(editor.inspectorView.getIcon());
			inspector.addChildren(properties, status);
			views.put(hierarchy, true);
			views.put(uv, true);
			views.put(resources, true);
			views.put(animations, true);
			views.put(inspector, false);
			install();
			var parts = array(session.asset(), "parts");
			if (!parts.isEmpty()) {
				selectedPath = "/parts/0";
				var first = parts.get(0).getAsJsonObject();
				selectedPartId = first.has("id") ? first.get("id").getAsString() : PhotonModelDefinition.DEFAULT_PART;
			}
			refresh();
			editor.addEventListener(UIEvents.TICK, refreshListener);
		} catch (RuntimeException | Error failure) {
			try {
				close();
			} catch (RuntimeException cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}
			throw failure;
		}
	}

	private static View view(String key) {
		var view = new View(PhotonModelTranslations.key(key));
		view.layout(layout -> layout.flexDirection(FlexDirection.COLUMN).minHeight(0));
		return view;
	}

	private static ScrollerView scroll() {
		var result = new ScrollerView();
		result.layout(layout -> layout.widthPercent(100).flex(1).minHeight(0));
		result.scrollerStyle(style -> style.adaptiveHeight(false));
		result.viewContainer.layout(layout -> layout.flexDirection(FlexDirection.COLUMN).gapAll(3));
		return result;
	}

	private static ScrollerView scroll(UIElement content) {
		content.layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));
		var result = scroll();
		result.addScrollViewChild(content);
		return result;
	}

	private Component displayName(TreeNode<String, String> node) {
		String name = node.getContent() == null ? node.getKey() : node.getContent();
		if (node.getKey().startsWith("/parts/")) {
			try {
				UUID.fromString(name);
				int index = Integer.parseInt(node.getKey().substring("/parts/".length()));
				return Component.translatable(PhotonModelTranslations.EDITOR_HIERARCHY_LEGACY_PART, index + 1);
			} catch (IllegalArgumentException ignored) {
				return Component.literal(name);
			}
		}
		return Component.literal(name);
	}

	private void configureHierarchy() {
		tree.setFlattenRoot(true).setStaticTree(true).setSupportMultipleSelection(false)
				.setNodeUISupplier(TreeList.textTemplate(this::displayName))
				.setOnNodeUICreated((node, ui) -> ui.style(style -> style.tooltips(Component.literal(node.getContent() == null ? node.getKey() : node.getContent()))))
				.setOnDoubleClickNode(node -> {
					selectHierarchy(node.getKey());
					show(uv, true);
				})
				.setOnSelectedChanged(nodes -> {
					if (!nodes.isEmpty()) {
						selectHierarchy(nodes.iterator().next().getKey());
					}
				});
		tree.addEventListener(UIEvents.MOUSE_DOWN, event -> {
			if (event.button != 1) {
				return;
			}
			var node = tree.getHoveredNode();
			boolean part = node != null && node.getKey().matches("/parts/[0-9]+");
			if (node != null) {
				selectHierarchy(node.getKey());
			}
			var menu = TreeBuilder.Menu.start();
			String target = part ? selectedPartId : "";
			String revision = session.revision();
			menu.branch(PhotonModelTranslations.PRIMITIVE, branch -> {
				branch.leaf(PhotonModelTranslations.CUBE, () -> session.changeAt(revision, asset -> PhotonModelWorkspaceEditUtil.primitive(asset, true, target)));
				branch.leaf(PhotonModelTranslations.PLANE, () -> session.changeAt(revision, asset -> PhotonModelWorkspaceEditUtil.primitive(asset, false, target)));
				for (var shape : PhotonModelPrimitiveUtil.Shape.values()) {
					branch.leaf(PhotonModelTranslations.key(shape.name().toLowerCase(Locale.ROOT)), () -> meshTools.primitive(shape));
				}
			});
			if (part) {
				menu.leaf(ACTION_DUPLICATE, () -> session.changeAt(revision, asset -> PhotonModelWorkspaceEditUtil.duplicatePart(asset, target)));
				menu.leaf(PhotonModelTranslations.MESH_INSIDE_OUT, () -> session.changeAt(revision, asset -> {
					for (var value : asset.getAsJsonArray("parts")) {
						var item = value.getAsJsonObject();
						if (target.equals(item.get("id").getAsString())) {
							item.addProperty("insideOut", !item.has("insideOut") || !item.get("insideOut").getAsBoolean());
						}
					}
				}));
				menu.leaf(DELETE, () -> session.changeAt(revision, asset -> PhotonModelWorkspaceEditUtil.removePart(asset, target)));
				menu.leaf(PhotonModelTranslations.EDITOR_DELETE_SUBTREE, () -> session.changeAt(revision,
						asset -> PhotonModelWorkspaceEditUtil.removePart(asset, target, true)));
				menu.leaf(PhotonModelTranslations.EDITOR_DELETE_PROMOTE, () -> session.changeAt(revision,
						asset -> PhotonModelWorkspaceEditUtil.removePart(asset, target, false)));
			}
			editor.openMenu(tree, event.x, event.y, menu);
			event.stopPropagation();
		}, true);
		hierarchy.addChild(scroll(tree));
		var actions = new UIElement().layout(layout -> layout.flexDirection(FlexDirection.ROW).gapAll(3));
		actions.addChild(new Button().setText(NEW).setOnClick(_ -> session.change(asset -> {
			var parts = ensureArray(asset, "parts");
			var part = new JsonObject();
			part.addProperty("id", nextId(parts, "part"));
			part.addProperty("parentId", "");
			parts.add(part);
		})));
		actions.addChild(new Button().setText(DELETE).setOnClick(_ -> removeSelected()));
		hierarchy.addChild(actions);
		for (var kind : List.of("bones", "morphs")) {
			hierarchy.addChild(new Button().setText(PhotonModelTranslations.key(kind.equals("bones") ? "add_bone" : "add_morph")).setOnClick(_ ->
					session.change(asset -> {
						var entries = ensureArray(asset, kind);
						var value = new JsonObject();
						value.addProperty("id", nextId(entries, kind.equals("bones") ? "bone" : "shape"));
						if (kind.equals("bones")) {
							value.addProperty("parentId", "");
							value.add("bind", JsonParser.parseString("{\"translation\":[0,0,0],\"rotation\":[0,0,0,1],\"scale\":[1,1,1]}"));
						} else {
							value.addProperty("weight", 0);
							value.add("deltas", new JsonObject());
						}
						entries.add(value);
					})));
		}
	}

	private void install() {
		editor.hierarchyView.removeSelf();
		editor.inspectorView.removeSelf();
		editor.timelineView.removeSelf();
		editor.getViewFallbacks().remove(editor.inspectorView);
		editor.getViewFallbacks().remove(editor.timelineView);
		editor.placeView(timeline, () -> editor.bottomWindow.getLeftTop());
		editor.placeView(animationScene, () -> editor.centerWindow.getLeftTop());
		for (var entry : views.entrySet()) {
			var target = entry.getValue() ? editor.leftWindow.getLeftTop() : editor.rightWindow.getRightTop();
			editor.placeView(entry.getKey(), () -> target);
		}
		editor.sceneView.setFloatable(false);
		editor.leftWindow.getLeftTop().selectView(hierarchy);
		editor.rightWindow.getRightTop().selectView(inspector);
		panels = editor.viewMenu.registerMenuCreator((_, menu) ->
				menu.branch(PhotonModelTranslations.EDITOR_PANELS, branch -> {
					for (var entry : views.entrySet()) {
						branch.leaf(entry.getKey().getName(), () -> show(entry.getKey(), entry.getValue()));
					}
					branch.leaf(editor.sceneView.getName(), () -> show(editor.sceneView, null));
					branch.leaf(timeline.getName(), () -> show(timeline, null));
					branch.leaf(animationScene.getName(), animationScene::open);
					branch.leaf(editor.resourceView.getName(), () -> show(editor.resourceView, null));
				}));
	}

	private void show(View view, Boolean left) {
		if (editor.getFloatingViews().isFloating(view)) {
			editor.getFloatingViews().dockBack(view);
		}
		if (view.getViewContainer() == null) {
			if (left == null) {
				editor.redockView(view);
			} else {
				editor.placeView(view, () -> left ? editor.leftWindow.getLeftTop() : editor.rightWindow.getRightTop());
			}
		}
		var container = view.getViewContainer();
		if (container != null) {
			container.selectView(view);
		}
	}

	private PhotonModelWorkspaceScene animationScene() {
		return animationScene;
	}

	private PhotonModelWorkspaceMesh meshTools() {
		return meshTools;
	}

	private void selectMeshPart(String id) {
		var parts = array(session.asset(), "parts");
		for (int index = 0; index < parts.size(); index++) {
			if (id.equals(PhotonModelWorkspaceEditUtil.partId(parts.get(index).getAsJsonObject()))) {
				selectHierarchy("/parts/" + index);
				return;
			}
		}
	}

	public void invalidate() {
		refreshPending = true;
		inspectorInvalidated |= !session.isCommitting();
	}

	private void refresh() {
		session.refresh();
		refreshHierarchy();

		materialList.clearAllChildren();
		var materials = session.asset().getAsJsonObject("materials");
		if (materials != null) {
			for (var name : materials.keySet()) {
				materialList.addChild(new Button().setText(Component.literal(name))
						.setOnClick(_ -> inspect("/materials/" + PhotonModelWorkspaceFields.encode(name))));
			}
		}
		materialList.addChild(new Button().setText(PhotonModelTranslations.RESOURCES_ADD_SLOT).setOnClick(_ -> addMaterial()));
		resourceTools.actions(materialList, "");
		animationList.clearAllChildren();
		var clips = array(session.asset(), "clips");
		for (int index = 0; index < clips.size(); index++) {
			int selected = index;
			var id = clips.get(index).getAsJsonObject().get("id").getAsString();
			var clipButton = new Button().setText(Component.literal(id)).setOnClick(_ -> {
				session.playback(id);
				inspect("/clips/" + selected);
				timeline.selectClip(id);
				animationScene.open();
				show(timeline, null);
			});
			clipButton.addEventListener(UIEvents.MOUSE_DOWN, event -> {
				if (event.button == 1) {
					animationMenu(clipButton, id, event.x, event.y);
					event.stopPropagation();
				}
			});
			animationList.addChild(clipButton);
		}
		animationList.addChild(new Button().setText(NEW).setOnClick(_ -> session.change(asset -> {
			var values = ensureArray(asset, "clips");
			var clip = new JsonObject();
			clip.addProperty("id", nextId(values, "animation"));
			clip.addProperty("duration", PhotonModelDefinition.DEFAULT_CLIP_TICKS);
			clip.addProperty("loop", true);
			values.add(clip);
		})));
		uvCanvas.refresh();
		meshTools.refresh();
		timeline.refresh();
		if (!selectedPath.isEmpty()) {
			var value = session.valueOr(selectedPath, JsonNull.INSTANCE);
			if (inspectorInvalidated || !shape(value).equals(inspectorShape)) {
				inspectorPath = "";
				inspect(selectedPath);
			}
		}
		inspectorInvalidated = false;
	}

	private void refreshHierarchy() {
		var parts = array(session.asset(), "parts");
		String signature = parts.asList().stream().map(value -> {
			var part = value.getAsJsonObject();
			return (part.has("id") ? part.get("id").getAsString() : PhotonModelDefinition.DEFAULT_PART)
					+ "/" + (part.has("parentId") ? part.get("parentId").getAsString() : "");
		}).toList() + "/" + array(session.asset(), "bones").asList().stream().map(value -> value.getAsJsonObject().get("id")).toList()
				+ "/" + array(session.asset(), "morphs").asList().stream().map(value -> value.getAsJsonObject().get("id")).toList();
		if (!signature.equals(treeShape)) {
			treeShape = signature;
			treeNodes.clear();
			var root = new TreeNode<String, String>("");
			addParts(root, parts, "");
			addGroup(root, "bones");
			addGroup(root, "morphs");
			tree.setRoot(root);
		}
		boolean partSelection = selectedPath.startsWith("/parts/");
		var selected = partSelection ? null : treeNodes.get(selectedPath);
		if (partSelection && !selectedPartId.isEmpty()) {
			for (int index = 0; index < parts.size(); index++) {
				var part = parts.get(index).getAsJsonObject();
				if (selectedPartId.equals(PhotonModelWorkspaceEditUtil.partId(part))) {
					selectedPath = "/parts/" + index;
					selected = treeNodes.get(selectedPath);
					break;
				}
			}
		}
		if (partSelection && selected == null) {
			selectedPartId = "";
			selectedPath = "";
			inspectorPath = "";
			inspectorShape = "";
			properties.clearAllScrollViewChildren();
		}
		tree.setSelected(selected == null ? Set.of() : Set.of(selected), false);
		if (selected != null) {
			tree.expandNodeAlongPath(selected);
		}
	}

	private void addParts(TreeNode<String, String> parent, JsonArray parts, String parentId) {
		for (int index = 0; index < parts.size(); index++) {
			var part = parts.get(index).getAsJsonObject();
			var id = part.has("id") ? part.get("id").getAsString() : PhotonModelDefinition.DEFAULT_PART;
			var parentName = part.has("parentId") ? part.get("parentId").getAsString() : "";
			if (parentName.equals(parentId)) {
				var node = parent.createChild("/parts/" + index);
				parent.addContent(node.getKey(), id);
				treeNodes.put(node.getKey(), node);
				addParts(node, parts, id);
			}
		}
	}

	private void addGroup(TreeNode<String, String> root, String key) {
		var values = array(session.asset(), key);
		if (values.isEmpty()) {
			return;
		}
		var group = root.createChild("/" + key);
		root.addContent(group.getKey(), key);
		for (int index = 0; index < values.size(); index++) {
			var value = values.get(index).getAsJsonObject();
			String path = "/" + key + "/" + index;
			group.addContent(path, value.get("id").getAsString());
			treeNodes.put(path, group.getOrCreateChild(path));
		}
	}

	private static String shape(JsonElement value) {
		if (value.isJsonObject()) {
			return value.getAsJsonObject().entrySet().stream().map(entry -> entry.getKey() + ":"
					+ (entry.getKey().equals("id") ? entry.getValue().toString() : shape(entry.getValue()))).toList().toString();
		}
		if (value.isJsonArray()) {
			return value.getAsJsonArray().asList().stream().map(PhotonModelWorkspace::shape).toList().toString();
		}
		return value.isJsonNull() ? "null" : value.getAsJsonPrimitive().isBoolean() ? "boolean"
				: value.getAsJsonPrimitive().isNumber() ? "number" : "string";
	}

	private void selectHierarchy(String path) {
		if (path.matches("/parts/[0-9]+")) {
			var part = session.value(path).getAsJsonObject();
			selectedPartId = part.has("id") ? part.get("id").getAsString() : PhotonModelDefinition.DEFAULT_PART;
			selectedFaces.clear();
		}
		inspect(path);
	}

	private void inspect(String path) {
		selectedPath = path;
		var selectedValue = session.valueOr(path, JsonNull.INSTANCE);
		String currentShape = shape(selectedValue);
		if (path.equals(inspectorPath) && currentShape.equals(inspectorShape)) {
			return;
		}
		inspectorPath = path;
		inspectorShape = currentShape;
		properties.clearAllScrollViewChildren();
		try {
			if (path.matches("/parts/[0-9]+")) {
				var part = session.value(path).getAsJsonObject();
				String currentName = part.has("id") ? part.get("id").getAsString() : PhotonModelDefinition.DEFAULT_PART;
				String[] draftName = {currentName};
				properties.addScrollViewChild(new StringConfigurator(PhotonModelTranslations.NAME, () -> draftName[0],
						name -> draftName[0] = name, currentName, false));
				properties.addScrollViewChild(new Button().setText(RENAME).setOnClick(_ -> {
					String before = session.revision();
					session.change(asset -> PhotonModelWorkspaceEditUtil.renamePart(asset, currentName, draftName[0]));
					if (!before.equals(session.revision())) {
						selectedPartId = draftName[0];
					}
				}));
			}
			PhotonModelWorkspaceFields.build(properties.viewContainer, session, path);
			skeletalTools(path);
			if (path.matches("/parts/[0-9]+")) {
				properties.addScrollViewChild(meshTools.tools());
			}
			if (path.startsWith("/materials/")) {
				String material = path.substring("/materials/".length()).replace("~1", "/").replace("~0", "~");
				resourceTools.actions(properties.viewContainer, material);
				String[] name = {material};
				properties.addScrollViewChild(new StringConfigurator(PhotonModelTranslations.NAME, () -> name[0],
						value -> name[0] = value, material, false));
				properties.addScrollViewChild(new Button().setText(PhotonModelTranslations.RESOURCES_RENAME)
						.setOnClick(_ -> session.change(asset -> PhotonModelWorkspaceEditUtil.renameMaterial(asset, material, name[0]))));
				properties.addScrollViewChild(new Button().setText(PhotonModelTranslations.RESOURCES_DELETE_SLOT)
						.setOnClick(_ -> session.change(asset -> PhotonModelWorkspaceEditUtil.removeMaterial(asset, material))));
				properties.addScrollViewChild(new Button().setText(PhotonModelTranslations.RESOURCES_ASSIGN).setOnClick(_ -> session.change(asset -> {
					var faces = asset.getAsJsonArray("faces");
					for (int index : Set.copyOf(selectedFaces)) {
						faces.get(index).getAsJsonObject().addProperty("material", material);
					}
				})));
			}
		} catch (RuntimeException invalid) {
			status.setText(Component.literal(invalid.getMessage() == null ? invalid.toString() : invalid.getMessage()));
		}
	}

	private void skeletalTools(String path) {
		if (path.matches("/(bones|morphs)/[0-9]+")) {
			String kind = path.substring(1, path.indexOf('/', 1));
			String id = session.value(path).getAsJsonObject().get("id").getAsString();
			String[] name = {id};
			properties.addScrollViewChild(new StringConfigurator(PhotonModelTranslations.NAME, () -> name[0],
					value -> name[0] = value, id, false));
			properties.addScrollViewChild(new Button().setText(RENAME)
					.setOnClick(_ -> session.change(asset -> PhotonModelWorkspaceEditUtil.renameNode(asset, kind, id, name[0]))));
		}
		if (path.matches("/bones/[0-9]+")) {
			String id = session.value(path).getAsJsonObject().get("id").getAsString();
			float[] weight = {1};
			properties.addScrollViewChild(new NumberConfigurator(PhotonModelTranslations.WEIGHT, () -> weight[0],
					value -> weight[0] = value.floatValue(), 1, false).setRange(0, 1));
			properties.addScrollViewChild(new Button().setText(PhotonModelTranslations.EDITOR_ASSIGN_WEIGHT).setOnClick(_ -> {
				var vertices = meshTools.vertexSelection();
				session.change(asset -> {
					var weights = asset.has("weights") ? asset.getAsJsonObject("weights") : new JsonObject();
					for (int vertex : vertices) {
						var name = Integer.toString(vertex);
						var entries = weights.has(name) ? weights.getAsJsonArray(name) : new JsonArray();
						var current = entries.asList().stream().map(JsonElement::getAsJsonObject)
								.filter(value -> id.equals(value.get("boneId").getAsString())).findFirst().orElse(null);
						if (current == null) {
							current = new JsonObject();
							current.addProperty("boneId", id);
							entries.add(current);
						}
						current.addProperty("value", weight[0]);
						weights.add(name, entries);
					}
					asset.add("weights", weights);
				});
			}));
		}
		if (path.matches("/morphs/[0-9]+")) {
			String id = session.value(path).getAsJsonObject().get("id").getAsString();
			var delta = new Vector3f();
			for (int axis = 0; axis < 3; axis++) {
				int component = axis;
				properties.addScrollViewChild(new NumberConfigurator(PhotonModelTranslations.key("axis_" + "xyz".charAt(axis)), () -> delta.get(component),
						value -> delta.setComponent(component, value.floatValue()), 0, false));
			}
			properties.addScrollViewChild(new Button().setText(PhotonModelTranslations.EDIT_MORPH).setOnClick(_ -> {
				var vertices = meshTools.vertexSelection();
				session.change(asset -> {
					var morph = array(asset, "morphs").asList().stream().map(JsonElement::getAsJsonObject)
							.filter(value -> id.equals(value.get("id").getAsString())).findFirst().orElseThrow();
					var deltas = morph.has("deltas") ? morph.getAsJsonObject("deltas") : new JsonObject();
					for (int vertex : vertices) {
						var value = new JsonArray();
						value.add(delta.x);
						value.add(delta.y);
						value.add(delta.z);
						deltas.add(Integer.toString(vertex), value);
					}
					morph.add("deltas", deltas);
				});
			}));
		}
	}

	private void removeSelected() {
		if (!selectedPath.matches("/(parts|bones|morphs)/[0-9]+")) {
			return;
		}
		var path = selectedPath.split("/");
		if (path[1].equals("parts")) {
			var part = session.value(selectedPath).getAsJsonObject();
			String id = part.has("id") ? part.get("id").getAsString() : PhotonModelDefinition.DEFAULT_PART;
			session.change(asset -> PhotonModelWorkspaceEditUtil.removePart(asset, id));
		} else {
			session.change(asset -> PhotonModelWorkspaceEditUtil.removeNode(asset, path[1], Integer.parseInt(path[2])));
		}
	}

	private void addMaterial() {
		session.change(asset -> {
			var materials = asset.has("materials") ? asset.getAsJsonObject("materials") : new JsonObject();
			var materialName = new StringBuilder("material");
			while (materials.has(materialName.toString())) {
				materialName.append("_copy");
			}
			var material = new JsonObject();
			material.addProperty("texture", "");
			material.addProperty("materialData", "");
			material.addProperty("tint", "NONE");
			material.addProperty("overlay", false);
			materials.add(materialName.toString(), material);
			asset.add("materials", materials);
		});
	}

	private void animationMenu(UIElement anchor, String id, float x, float y) {
		String expected = session.revision();
		var menu = TreeBuilder.Menu.start();
		menu.leaf(PhotonModelTranslations.TIMELINE_RENAME, () -> {
			String[] draft = {id};
			var dialog = new Dialog().setTitle(PhotonModelTranslations.TIMELINE_RENAME);
			dialog.addContent(new StringConfigurator(PhotonModelTranslations.NAME, () -> draft[0], value -> draft[0] = value, id, false));
			dialog.addButton(new Button().setText("ldlib.gui.tips.confirm").setOnClick(_ -> {
				String selected = project.model().config.clip.equals(id) ? draft[0] : project.model().config.clip;
				session.changeAssetAndPlaybackAt(expected, asset -> {
					if (!draft[0].matches("[a-z_]+") || array(asset, "clips").asList().stream().anyMatch(value ->
							!id.equals(value.getAsJsonObject().get("id").getAsString()) && draft[0].equals(value.getAsJsonObject().get("id").getAsString()))) {
						throw new IllegalArgumentException("动画名称无效或已存在。");
					}
					clip(asset, id).addProperty("id", draft[0]);
				}, selected);
				dialog.close();
			}));
			dialog.addButton(new Button().setText("ldlib.gui.tips.cancel").setOnClick(_ -> dialog.close()));
			dialogs.add(dialog);
			dialog.setOnClose(() -> dialogs.remove(dialog));
			dialog.show(editor);
		});
		menu.leaf(PhotonModelTranslations.DUPLICATE, () -> session.changeAt(expected, asset -> {
			var values = array(asset, "clips");
			var copied = clip(asset, id).deepCopy();
			copied.addProperty("id", nextId(values, "animation"));
			values.add(copied);
		}));
		menu.leaf(PhotonModelTranslations.DELETE, () -> session.changeAssetAndPlaybackAt(expected,
				asset -> array(asset, "clips").asList().removeIf(value -> id.equals(value.getAsJsonObject().get("id").getAsString())),
				project.model().config.clip.equals(id) ? "" : project.model().config.clip));
		for (int direction : List.of(-1, 1)) {
			menu.leaf(Component.translatable(PhotonModelTranslations.TIMELINE_REORDER).append(direction < 0 ? " ↑" : " ↓"), () -> session.changeAt(expected, asset -> {
				var values = array(asset, "clips");
				int index = values.asList().indexOf(clip(asset, id));
				int next = index + direction;
				if (next >= 0 && next < values.size()) {
					var value = values.get(index);
					values.set(index, values.get(next));
					values.set(next, value);
				}
			}));
		}
		editor.openMenu(anchor, x, y, menu);
	}

	private static JsonObject clip(JsonObject asset, String id) {
		return array(asset, "clips").asList().stream().map(JsonElement::getAsJsonObject)
				.filter(value -> id.equals(value.get("id").getAsString())).findFirst().orElseThrow();
	}

	private static JsonArray array(JsonObject asset, String key) {
		if (key.equals("parts")) {
			return PhotonModelWorkspaceEditUtil.parts(asset);
		}
		return asset.has(key) ? asset.getAsJsonArray(key) : new JsonArray();
	}

	private static JsonArray ensureArray(JsonObject asset, String key) {
		if (!asset.has(key)) {
			asset.add(key, new JsonArray());
		}
		return asset.getAsJsonArray(key);
	}

	private static String nextId(JsonArray values, String prefix) {
		var name = new StringBuilder(prefix);
		while (true) {
			String candidate = name.toString();
			if (values.asList().stream().noneMatch(value -> value.getAsJsonObject().get("id").getAsString().equals(candidate))) {
				return candidate;
			}
			name.append("_copy");
		}
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		editor.removeEventListener(UIEvents.TICK, refreshListener);
		uvCanvas.cancel();
		timeline.close();
		for (var dialog : List.copyOf(dialogs)) {
			dialog.close();
		}
		dialogs.clear();
		meshTools.close();
		resourceTools.close();
		animationScene.close();
		for (var view : List.of(timeline, animationScene)) {
			editor.getViewFallbacks().remove(view);
			view.removeSelf();
		}
		if (panels != null) {
			panels.unsubscribe();
		}
		for (var view : List.copyOf(views.keySet())) {
			editor.getViewFallbacks().remove(view);
			if (editor.getFloatingViews().isFloating(view)) {
				editor.getFloatingViews().dockBack(view);
			}
			view.removeSelf();
		}
		views.clear();
		if (editor.getCurrentProject() == project) {
			editor.placeView(editor.inspectorView, () -> editor.rightWindow.getRightTop());
			editor.placeView(editor.timelineView, () -> editor.bottomWindow.getLeftTop());
		}
		if (editor.getCurrentProject() == project && editor.hierarchyView.getViewContainer() == null) {
			editor.placeView(editor.hierarchyView, () -> editor.leftWindow.getLeftTop());
		}
	}
}
