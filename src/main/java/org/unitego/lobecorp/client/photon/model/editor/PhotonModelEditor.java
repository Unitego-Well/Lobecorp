package org.unitego.lobecorp.client.photon.model.editor;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.configurator.EditAction;
import com.lowdragmc.lowdraglib2.configurator.ConfiguratorParser;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.SerializableRecordAction;
import com.lowdragmc.lowdraglib2.configurator.accessors.Vector3fAccessor;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.keymap.KeymapContext;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProjectEditor;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelDefaultUtil;

import java.util.EnumMap;

import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelPlaybackConfig;
import org.unitego.lobecorp.config.photon.emitter.model.PhotonModelEmitterConfig;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Transform;
import org.unitego.lobecorp.registry.photon.client.LcPhotonEditorActions;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorShortcutUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelIOUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelRendererUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelFileUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelPrimitiveUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelPrimitiveUtil.Shape;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelPrimitiveUtil.Parameters;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelTopologyUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelTopologyUtil.MergeMode;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelLoopCutUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelHierarchyUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Primitive;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.SelectionMode;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Edge;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class PhotonModelEditor {
	/// 可编辑模型工程的文件后缀。
	public static final String PROJECT_SUFFIX = PhotonModelFileUtil.PROJECT_SUFFIX;
	/// 标准静态模型交换后缀。
	public static final String OBJ_SUFFIX = ".obj";
	/// 控件行高，单位为 GUI 像素。
	private static final float ROW_HEIGHT = 18f;
	/// 操作按钮在窄面板中换行时保留的最小宽度，单位为 GUI 像素。
	private static final float ACTION_MIN_WIDTH = 64f;
	/// 选择模式按钮宽度，单位为 GUI 像素。
	private static final float MODE_WIDTH = 38f;
	/// 左侧 UV 画布允许缩小时保留的最小高度，单位为 GUI 像素。
	private static final float UV_CANVAS_MIN_HEIGHT = 80f;
	/// UV 工具占面板高度的比例，剩余空间留给画布。
	private static final float UV_TOOLS_HEIGHT_PERCENT = 45f;
	/// 默认面挤出距离，单位为模型坐标。
	private static final float EXTRUDE_DISTANCE = 0.1f;
	/// 数值框滚轮的默认步长。
	private static final float NUMBER_STEP = 0.05f;
	/// 右键点击与原生相机拖动的区分阈值，单位为 GUI 像素。
	private static final float CONTEXT_CLICK_TOLERANCE = 3f;
	/// 模型操作弹窗宽度，单位为 GUI 像素。
	private static final float DIALOG_WIDTH = 280f;
	/// 按距离合并的默认阈值，单位为模型坐标。
	private static final float MERGE_DISTANCE = 0.01f;
	/// 环切位置默认位于边的中点，界面使用百分比。
	private static final float LOOP_PERCENT = 50f;
	private static final float PERCENT_SCALE = 100f;

	private final PhotonModelProject project;
	private final PhotonModelProjectEditor owner;
	private final FXEditor host;
	private final PhotonModelEditSession session;
	private final ScrollerView inspector;
	private @Nullable ConfiguratorGroup rendererEditor;
	private final ScrollerView uvTools = new ScrollerView();
	private final PhotonModelAnimationWorkspace animationWorkspace;
	private final PhotonModelAnimationSceneView animationScene;
	private boolean switchingWorkspace;
	private final PhotonModelHierarchyView hierarchyView;
	private final View uvView = new View(PhotonModelTextUtil.key("uv"));
	private final TextElement status = new Label().textStyle(style -> style.adaptiveHeight(true).textWrap(TextWrap.WRAP));
	private final PhotonModelPreview preview;
	private final PhotonModelSceneController sceneController;
	private final PhotonModelResourcesView modelResourcesView;
	private final PhotonModelUVCanvas uvCanvas;
	private final List<UIElement> historyKeyTargets;
	private final @Nullable ISubscription shortcuts;
	private String workspaceName = "";
	private final UIElement sceneTools = row();
	private final EnumMap<SelectionMode, Toggle> selectionToggles = new EnumMap<>(SelectionMode.class);
	private Vector2f contextStart;
	private final UIEventListener sceneContextDown = this::onSceneContextDown;
	private final UIEventListener sceneContextUp = this::onSceneContextUp;
	private float extrudeDistance = EXTRUDE_DISTANCE;
	private float weight = 1;
	private Vector3f translation = new Vector3f();
	private Vector3f rotation = new Vector3f();
	private Vector3f scale = new Vector3f(1);
	private Vector3f morphDelta = new Vector3f();
	private boolean editBindPose;
	private final Vector2f uvTranslation = new Vector2f();
	private final Vector2f uvScale = new Vector2f(1);
	private float uvRotation;

	public PhotonModelEditor(PhotonModelProject project, PhotonModelProjectEditor owner) {
		this.project = project;
		this.owner = owner;
		var emitter = project.previewEmitter();
		host = project.sourceEditor();
		session = project.session();
		inspector = owner.inspectorView.inspector.scrollerView;
		sceneController = new PhotonModelSceneController(session, emitter, owner);
		preview = sceneController.preview();
		animationWorkspace = new PhotonModelAnimationWorkspace(session, sceneController,
				this::onAnimationSelected, () -> showWorkspace("animation"));
		animationScene = new PhotonModelAnimationSceneView(owner, () -> showWorkspace("animation"),
				() -> showWorkspace("model"));
		uvCanvas = new PhotonModelUVCanvas(session);
		hierarchyView = new PhotonModelHierarchyView(session, this);
		modelResourcesView = new PhotonModelResourcesView(session, owner, this::onChanged);
		historyKeyTargets = List.of(owner.sceneView, hierarchyView, uvView, animationWorkspace.animationsView(), animationWorkspace.timelineView(),
				owner.inspectorView, owner.historyView, owner.resourceView, modelResourcesView);

		try {
			configureContent();
			configureViews();
			showWorkspace("model");
			session.onChanged(this::onSessionChanged);
			installSceneTools();

			shortcuts = LcPhotonEditorActions.registerModel(owner, this::canRunShortcut, this::runShortcut);
		} catch (RuntimeException | Error failure) {
			close();
			throw failure;
		}
	}

	/// 发射器入口也打开独立模型标签，提交时才更新原特效定义。
	public static void open(PhotonModelEmitter emitter, UIElement parent) {
		var origin = parent instanceof Editor editor ? editor : parent.getFirstAncestorOfType(Editor.class);

		if (origin == null) {
			throw new IllegalStateException(PhotonModelTextUtil.key("project.editor_required"));
		}

		PhotonModelProjectEditor.open(origin,
				new PhotonModelProject(emitter, origin instanceof FXEditor editor ? editor : null), null);
	}

	public PhotonModelSceneController sceneController() {
		return sceneController;
	}

	public View modelResourcesView() {
		return modelResourcesView;
	}

	public View hierarchyView() {
		return hierarchyView;
	}

	public View uvView() {
		return uvView;
	}

	public View animationView() {
		return animationWorkspace.timelineView();
	}

	public View animationListView() {
		return animationWorkspace.animationsView();
	}

	public View animationSceneView() {
		return animationScene;
	}

	public void showDocuments() {
		var text = Component.translatable(PhotonModelTextUtil.key("editor.help"));

		for (var action : owner.getKeymap().getActions()) {
			text.append(Component.literal("\n\n")).append(action.displayName())
					.append(Component.literal(": " + PhotonEditorShortcutUtil.binding(owner, action.id())));
		}

		Dialog.showNotification(PhotonModelTextUtil.key("editor"), text.getString(), null).show(owner);
	}

	public void close() {
		rendererEditor = null;

		if (shortcuts != null) {
			shortcuts.unsubscribe();
		}

		owner.sceneView.sceneEditor.removeEventListener(UIEvents.MOUSE_DOWN, sceneContextDown, true);
		owner.sceneView.sceneEditor.removeEventListener(UIEvents.MOUSE_UP, sceneContextUp, true);
		sceneTools.removeSelf();
		owner.sceneView.sceneEditor.gizmoBar.layout(layout -> layout.top(18));
		modelResourcesView.close();
		animationScene.close();
		animationWorkspace.close();
		sceneController.close();
		session.playing = false;
		session.onChanged(() -> {
		});
	}

	private void configureContent() {
		inspector.layout(layout -> {
			layout.widthPercent(100);
			layout.minHeight(0);
		});
		inspector.viewContainer.layout(layout -> {
			layout.gapAll(3);
			layout.widthPercent(100);
		});
	}

	private void configureViews() {
		uvCanvas.layout(layout -> layout.minHeight(UV_CANVAS_MIN_HEIGHT));
		uvTools.layout(layout -> {
			layout.widthPercent(100);
			layout.heightPercent(UV_TOOLS_HEIGHT_PERCENT);
			layout.minHeight(0);
		});
		uvTools.scrollerStyle(style -> style.adaptiveHeight(false));
		uvTools.viewContainer.layout(layout -> layout.gapAll(3));
		buildUVTools(uvTools);
		uvView.addChildren(uvCanvas.textureSelector(), uvCanvas.gridToggle(), uvCanvas, uvTools);
		uvView.addEventListener(UIEvents.MOUSE_DOWN, _ -> showWorkspace("uv"), true);
		animationWorkspace.refresh();

	}

	private boolean canRunShortcut(String action, KeymapContext context) {
		if (context.isTextInputFocused() || context.isFocusWithin(Dialog.class) || owner.getCurrentProject() != project) {
			return false;
		}

		var focused = owner.getKeymapDispatcher().getFocusedElement();

		if (action.equals("undo") || action.equals("redo")) {
			return historyKeyTargets.stream().anyMatch(target -> isWithin(focused, target));
		}

		if (action.equals("delete_key")) {
			return isWithin(focused, animationWorkspace.timelineView());
		}

		if ((action.equals("box_select") || action.equals("cancel")) && isWithin(focused, uvCanvas)) {
			return true;
		}

		return !workspaceName.equals("animation") && isWithin(focused, owner.sceneView.sceneEditor);
	}

	private static boolean isWithin(UIElement element, UIElement parent) {
		for (var current = element; current != null; current = current.getParent()) {
			if (current == parent) {
				return true;
			}
		}

		return false;
	}

	private void runShortcut(String action) {
		switch (action) {
			case "undo" -> session.undo();
			case "redo" -> session.redo();
			case "select_all" -> safe(this::selectAll);
			case "duplicate" -> safe(this::duplicateSelection);
			case "delete" -> safe(this::deleteSelection);
			case "delete_key" -> {
				if (animationWorkspace.timelineView() instanceof PhotonModelTimelineView timeline) {
					timeline.deleteSelectedKeyAction();
				}
			}
			case "box_select" -> {
				if (isWithin(owner.getKeymapDispatcher().getFocusedElement(), uvCanvas)) {
					uvCanvas.boxSelection();
				} else {
					preview.boxSelection();
				}
			}
			case "cancel" -> {
				if (isWithin(owner.getKeymapDispatcher().getFocusedElement(), uvCanvas)) {
					uvCanvas.cancelBoxSelection();
				} else if (preview.isLoopCutActive()) {
					preview.cancelLoopCut();
				} else {
					safe(this::clearSelection);
				}
			}
			default -> {
			}
		}
	}

	private void showWorkspace(String name) {
		if (switchingWorkspace || workspaceName.equals(name)) {
			return;
		}

		switchingWorkspace = true;

		try {
			modelResourcesView.clearInspection();
			animationWorkspace.setAnimationMode(name.equals("animation"));
			workspaceName = name;

			if (name.equals("animation")) {
				selectWorkspaceView(name);
				animationScene.showScene(true);
			} else {
				animationScene.showScene(false);
				selectWorkspaceView(name);
			}

			refreshInspector();
		} finally {
			switchingWorkspace = false;
		}
	}

	private void selectWorkspaceView(String name) {
		var view = switch (name) {
			case "uv" -> uvView;
			case "animation" -> animationWorkspace.animationsView();
			default -> hierarchyView;
		};
		if (name.equals("animation")) {
			animationScene.open();
			var timelineContainer = animationWorkspace.timelineView().getViewContainer();

			if (timelineContainer != null) {
				timelineContainer.selectView(animationWorkspace.timelineView());
			}
		} else {
			var sceneContainer = owner.sceneView.getViewContainer();

			if (sceneContainer != null) {
				sceneContainer.selectView(owner.sceneView);
			}
		}

		var container = view.getViewContainer();

		if (container != null) {
			container.selectView(view);
		}
	}

	private void onChanged() {
		selectionToggles.forEach((mode, toggle) -> toggle.setOn(mode == session.selectionMode, false));
		hierarchyView.refresh();
		modelResourcesView.refresh();
		uvCanvas.refreshTextures(project.previewEmitter());
		status.setText(Component.translatable(PhotonModelTextUtil.key("editor.selection_status"),
				session.asset().vertices.size(), session.asset().faces.size(), session.vertices().size()));

		if (!preview.isEditingGeometry()) {
			preview.updateSelectionGizmo();
		}
	}

	private void refreshInspector() {
		rendererEditor = null;
		modelResourcesView.clearInspection();
		owner.inspectorView.clear();
		inspector.clearAllScrollViewChildren();

		switch (workspaceName) {
			case "model" -> buildModelTools();
			case "uv" -> buildPartProperties();
			default -> buildAnimationTools();
		}

		inspector.addScrollViewChild(status);
		onChanged();
	}

	private void onSessionChanged() {
		sceneController.refresh();
		animationWorkspace.refresh();

		if (modelResourcesView.isInspecting()) {
			modelResourcesView.inspectSelected();
			onChanged();
		} else {
			refreshInspector();
		}
	}

	private void selectPart(String id) {
		session.selectedPart = id;
		session.selectedFaces.clear();
		session.selectedVertices.clear();
		session.selectedEdges.clear();
		uvCanvas.clearSelection();
		showWorkspace(workspaceName.equals("animation") ? "animation" : "model");
		session.selectionChanged();
	}

	/// 层级修改入口复用部件检视与主视窗的同一选择。
	public void inspectHierarchyPart(String partId) {
		inspectHierarchyPart(partId, true);
	}

	public void inspectHierarchyPart(String partId, boolean openUV) {
		safe(() -> {
			session.selectionMode = SelectionMode.PART;
			selectPart(partId);

			if (openUV) {
				showPickedUV();
			}
		});
	}

	public void renameHierarchyPart(String partId) {
		inspectHierarchyPart(partId);
		Dialog.stringEditorDialog(PhotonModelTextUtil.key("editor.rename_part"), partId,
				name -> !name.isBlank() && session.asset().parts.stream()
						.noneMatch(part -> !part.id.equals(partId) && part.id.equals(name.trim())),
				name -> safe(() -> {
					session.selectedPart = partId;
					renamePart(name.trim());
				})).show(owner);
	}

	public void addHierarchyPrimitive(String parentId, Primitive primitive) {
		safe(() -> {
			topology("primitive", asset -> {
				PhotonModelEditUtil.addPrimitive(asset, primitive);
				asset.parts.getLast().parentId = parentId;
				PhotonModelDefaultUtil.assignWhiteMaterial(asset, asset.parts.getLast().id);
			});
			inspectHierarchyPart(session.asset().parts.getLast().id);
		});
	}

	public void deleteHierarchyPart(String partId, boolean includeChildren) {
		safe(() -> {
			session.change(includeChildren ? "editor.delete_subtree" : "editor.delete_promote",
					asset -> PhotonModelHierarchyUtil.deletePart(asset, partId, includeChildren));
			session.selectedFaces.clear();
			session.selectedVertices.clear();
			session.selectedEdges.clear();
			uvCanvas.clearSelection();
			showWorkspace("model");
			session.selectionChanged();
		});
	}

	private void onAnimationSelected(String ignored) {
		showWorkspace("animation");
		refreshInspector();
	}

	private void buildModelTools() {
		if (project.sourceEmitter() != null) {
			inspector.addScrollViewChild(button("apply", this::apply));
		}

		buildPlaybackTools();
		buildRendererTools();
		buildPartProperties();
	}

	private void buildRendererTools() {
		var editable = PhotonModelRendererUtil.copy(project.renderer());
		var group = new ConfiguratorGroup(PhotonModelTextUtil.key("default_renderer"), true);
		ConfiguratorParser.createConfigurators(group, editable);
		rendererEditor = group;
		group.addEventListener(Configurator.CHANGE_EVENT, event -> {
			event.stopPropagation();

			if (rendererEditor != group) {
				return;
			}

			safe(() -> {
				var before = PhotonModelRendererUtil.copy(project.renderer());
				var after = PhotonModelRendererUtil.copy(editable);
				project.setRenderer(after);
				owner.historyView.pushHistory(Component.translatable(PhotonModelTextUtil.key("default_renderer")),
						EditAction.of(() -> {
							project.setRenderer(after);
							refreshInspector();
						}, () -> {
							project.setRenderer(before);
							refreshInspector();
						}), false);
			});
		});
		inspector.addScrollViewChild(group);
	}

	private void installSceneTools() {
		owner.sceneView.sceneEditor.setFocusable(true);
		sceneTools.layout(layout -> layout.minHeight(ROW_HEIGHT).setFlexShrink(0));
		var group = new Toggle.ToggleGroup().setAllowEmpty(false);

		for (var mode : SelectionMode.values()) {
			var toggle = new Toggle().setText(PhotonModelTextUtil.key(mode.name().toLowerCase(Locale.ROOT)))
					.setToggleGroup(group).setOn(mode == session.selectionMode, false)
					.setOnToggleChanged(on -> {
						if (on) {
							if (mode != SelectionMode.PART) {
								preview.setEditingPivot(false);
							}

							session.selectionMode = mode;
							showWorkspace("model");
							session.selectionChanged();
						}
					});
			toggle.layout(layout -> layout.width(MODE_WIDTH).height(ROW_HEIGHT).setFlexShrink(0));
			toggle.toggleLabel.layout(layout -> layout.flex(1).minWidth(0));
			toggle.toggleLabel.textStyle(style -> style.adaptiveWidth(false));
			selectionToggles.put(mode, toggle);
			sceneTools.addChild(toggle);
		}

		sceneTools.addChildren(button("extrude", () -> openTransformDialog(true)),
				button("editor.transform", () -> openTransformDialog(false)),
				button("stretch", this::openStretchDialog),
				button("loop_cut", this::openLoopCutDialog));
		owner.sceneView.sceneEditor.addChildAt(sceneTools, 1);
		sceneTools.addEventListener(UIEvents.LAYOUT_CHANGED, _ -> owner.sceneView.sceneEditor.gizmoBar
				.layout(layout -> layout.top(sceneTools.getPositionY() + sceneTools.getSizeHeight() + 2)));
		owner.sceneView.sceneEditor.addEventListener(UIEvents.MOUSE_DOWN, sceneContextDown, true);
		owner.sceneView.sceneEditor.addEventListener(UIEvents.MOUSE_UP, sceneContextUp, true);
		preview.setSelectionPicked(this::showPickedUV);
	}

	private void clearSelection() {
		preview.cancelLoopCut();
		session.selectedFaces.clear();
		session.selectedVertices.clear();
		session.selectedEdges.clear();
		if (session.selectionMode == SelectionMode.PART) {
			session.selectedPart = "";
		}
		session.selectionChanged();
	}

	private void openStretchDialog() {
		var part = session.part();
		if (part == null) {
			return;
		}

		var dialog = new Dialog().setTitle(PhotonModelTextUtil.key("stretch"));
		configureDialog(dialog);
		dialog.addContent(new Toggle().setText(PhotonModelTextUtil.key("enabled"))
				.setOn(part.stretch.enabled, false).setOnToggleChanged(on -> partChange(current -> {
					current.stretch.enabled = on;
					current.stretch.axis = session.asset().forwardAxis;
				})));
		dialog.addContent(number("stretch_start", part.stretch.start,
				value -> partChange(current -> current.stretch.start = value)));
		dialog.addContent(number("stretch_end", part.stretch.end,
				value -> partChange(current -> current.stretch.end = value)));
		dialog.addButton(new Button().setText("gui.done").setOnClick(_ -> dialog.close()));
		dialog.show(owner);
	}

	private void showPickedUV() {
		if (session.selectionMode == SelectionMode.PART || session.selectionMode == SelectionMode.FACE) {
			showWorkspace("uv");
			uvCanvas.refreshTextures(project.previewEmitter());
		}
	}

	private void openTransformDialog(boolean extrude) {
		showWorkspace("model");
		var dialog = new Dialog().setTitle(PhotonModelTextUtil.key(extrude ? "extrude" : "editor.transform"));
		configureDialog(dialog);

		if (extrude) {
			dialog.addContent(number("distance", extrudeDistance, value -> extrudeDistance = value));
		} else {
			dialog.addContent(vector("position", translation, value -> translation = value));
			dialog.addContent(vector("rotation", rotation, value -> rotation = value));
			dialog.addContent(vector("scale", scale, value -> scale = value));
		}

		dialog.addButton(button(extrude ? "extrude" : "editor.transform", () -> {
			if (extrude) {
				topology("extrude", asset -> PhotonModelEditUtil.extrude(asset, faces(), extrudeDistance));
			} else {
				transformSelection();
			}
			dialog.close();
		}));
		dialog.addButton(new Button().setText("gui.cancel").setOnClick(_ -> dialog.close()));
		dialog.show(owner);
	}

	private void onSceneContextDown(UIEvent event) {
		if (event.button == 1 && event.target == owner.sceneView.sceneEditor.scene) {
			contextStart = preview.isEditingGeometry() ? null : new Vector2f(event.x, event.y);
		}
	}

	private void onSceneContextUp(UIEvent event) {
		if (event.button != 1 || contextStart == null) {
			return;
		}

		boolean clicked = contextStart.distanceSquared(event.x, event.y) <= CONTEXT_CLICK_TOLERANCE * CONTEXT_CLICK_TOLERANCE;
		contextStart = null;

		if (!clicked || workspaceName.equals("animation")) {
			return;
		}

		if (session.selectedVertices.isEmpty() && session.selectedFaces.isEmpty()) {
			preview.pick(event);
		}
		var menu = TreeBuilder.Menu.start();
		menu.branch(PhotonModelTextUtil.key("editor.hierarchy.add"), branch -> appendPrimitiveMenu(branch, ""));
		appendMeshMenu(menu);

		if (session.part() != null) {
			appendPartRenderingMenu(menu, session.selectedPart);
			if (session.selectionMode == SelectionMode.PART) {
				String id = session.selectedPart;
				menu.leaf(PhotonModelTextUtil.key("editor.delete_subtree"), () -> deleteHierarchyPart(id, true));
				menu.leaf(PhotonModelTextUtil.key("editor.delete_promote"), () -> deleteHierarchyPart(id, false));
			} else {
				menu.leaf(PhotonModelTextUtil.key("delete"), () -> safe(this::deleteSelection));
			}
		}

		owner.openMenu(owner.sceneView.sceneEditor, event.x, event.y, menu);
	}

	/// 层级与视窗共用同一添加菜单，曲面设置精细度，骨骼打开绑定属性。
	public void appendPrimitiveMenu(TreeBuilder.Menu menu, String parentId) {
		for (var primitive : List.of(Primitive.PLANE, Primitive.CUBE)) {
			menu.leaf(PhotonModelTextUtil.key(primitive.name().toLowerCase(Locale.ROOT)),
					() -> addHierarchyPrimitive(parentId, primitive));
		}

		for (var shape : Shape.values()) {
			menu.leaf(PhotonModelTextUtil.key(shape.name().toLowerCase(Locale.ROOT)),
					() -> openPrimitiveDialog(parentId, shape));
		}

		menu.crossLine();
		menu.leaf(PhotonModelTextUtil.key("add_bone"), () -> safe(this::addBone));
	}

	private void openPrimitiveDialog(String parentId, Shape shape) {
		var defaults = Parameters.defaults();
		float[] sizes = {defaults.radius(), defaults.width(), defaults.height(), defaults.angle(), defaults.verticalAngle()};
		int[] counts = {defaults.segments(), defaults.rings()};
		var dialog = new Dialog().setTitle(PhotonModelTextUtil.key(shape.name().toLowerCase(Locale.ROOT)));
		configureDialog(dialog);
		dialog.addContent(number("primitive.radius", sizes[0], value -> sizes[0] = value));
		if (shape == Shape.ARC || shape == Shape.RING) {
			dialog.addContent(number("width", sizes[1], value -> sizes[1] = value));
		}
		if (shape == Shape.CYLINDER || shape == Shape.CONE || shape == Shape.CYLINDRICAL_PATCH) {
			dialog.addContent(number("primitive.height", sizes[2], value -> sizes[2] = value));
		}
		if (shape == Shape.ARC || shape == Shape.CYLINDRICAL_PATCH || shape == Shape.SPHERICAL_PATCH) {
			dialog.addContent(number("primitive.angle", sizes[3], value -> sizes[3] = value));
		}
		if (shape == Shape.SPHERICAL_PATCH) {
			dialog.addContent(number("primitive.vertical_angle", sizes[4], value -> sizes[4] = value));
		}
		dialog.addContent(integer("primitive.segments", counts[0], PhotonModelPrimitiveUtil.MIN_SEGMENTS,
				PhotonModelPrimitiveUtil.MAX_SEGMENTS, value -> counts[0] = value));
		if (shape != Shape.ARC) {
			int minimum = switch (shape) {
				case RING -> 3;
				case SPHERE, SPHERICAL_PATCH -> 2;
				default -> 1;
			};
			dialog.addContent(integer("primitive.rings", counts[1], minimum,
					PhotonModelPrimitiveUtil.MAX_RINGS, value -> counts[1] = value));
		}
		dialog.addButton(button("editor.hierarchy.add", () -> {
			topology("primitive", asset -> {
				var part = PhotonModelPrimitiveUtil.add(asset, shape,
						new Parameters(sizes[0], sizes[1], sizes[2], sizes[3], sizes[4], counts[0], counts[1]));
				part.parentId = parentId;
				PhotonModelDefaultUtil.assignWhiteMaterial(asset, part.id);
			});
			inspectHierarchyPart(session.asset().parts.getLast().id);
			dialog.close();
		}));
		dialog.addButton(new Button().setText("gui.cancel").setOnClick(_ -> dialog.close()));
		dialog.show(owner);
	}

	private UIElement integer(String name, int value, int minimum, int maximum, Consumer<Integer> setter) {
		return new NumberConfigurator(PhotonModelTextUtil.key(name), () -> value,
				number -> setter.accept(number.intValue()), value, false).setRange(minimum, maximum).setWheel(1);
	}

	private void appendMeshMenu(TreeBuilder.Menu menu) {
		menu.branch(PhotonModelTextUtil.key("mesh.tools"), branch -> {
			appendPartInversionMenu(branch, session.selectedPart);
			branch.branch(PhotonModelTextUtil.key("mesh.merge"), merge -> {
				for (var mode : MergeMode.values()) {
					merge.leaf(PhotonModelTextUtil.key("mesh.merge." + mode.name().toLowerCase(Locale.ROOT)),
							() -> safe(() -> mergeVertices(mode)));
				}
			});
			branch.leaf(PhotonModelTextUtil.key("mesh.dissolve"), () -> safe(() ->
					topology("mesh.dissolve", asset -> PhotonModelTopologyUtil.dissolve(asset, Set.copyOf(session.selectedEdges)))));
			branch.leaf(PhotonModelTextUtil.key("mesh.collapse"), () -> safe(() ->
					topology("mesh.collapse", asset -> PhotonModelTopologyUtil.collapse(asset, Set.copyOf(session.selectedEdges)))));
			branch.leaf(PhotonModelTextUtil.key("mesh.face"), () -> safe(() ->
					topology("mesh.face", asset -> PhotonModelTopologyUtil.createFace(asset,
							session.selectedPart, new LinkedHashSet<>(session.selectedVertices)))));
			branch.leaf(PhotonModelTextUtil.key("mesh.triangulate"), () -> safe(() ->
					topology("mesh.triangulate", asset -> PhotonModelTopologyUtil.triangulate(asset, List.copyOf(faces())))));
			branch.leaf(PhotonModelTextUtil.key("loop_cut"), this::openLoopCutDialog);
		});
	}

	public void appendPartRenderingMenu(TreeBuilder.Menu menu, String partId) {
		var part = session.asset().parts.stream().filter(current -> current.id.equals(partId)).findFirst().orElse(null);

		if (part == null) {
			return;
		}

		menu.leaf(part.shading ? Icons.CHECKBOX_MARKED : Icons.CHECKBOX_BLANK, PhotonModelTextUtil.key("editor.shading"),
				() -> safe(() -> partChange(partId, current -> current.shading = !current.shading)));
		menu.leaf(part.doubleSided ? Icons.CHECKBOX_MARKED : Icons.CHECKBOX_BLANK, PhotonModelTextUtil.key("editor.double_sided"),
				() -> safe(() -> partChange(partId, current -> current.doubleSided = !current.doubleSided)));
	}

	public void appendPartInversionMenu(TreeBuilder.Menu menu, String partId) {
		var part = session.asset().parts.stream().filter(current -> current.id.equals(partId)).findFirst().orElse(null);

		if (part == null) {
			return;
		}

		menu.leaf(PhotonModelTextUtil.key("mesh.inside_out"),
				() -> safe(() -> partChange(partId, current -> current.insideOut = !current.insideOut)));
	}

	private void mergeVertices(MergeMode mode) {
		var vertices = new LinkedHashSet<>(session.selectedVertices);
		if (mode != MergeMode.DISTANCE) {
			topology("mesh.merge", asset -> PhotonModelTopologyUtil.merge(asset, vertices, mode, 0));
			return;
		}
		float[] distance = {MERGE_DISTANCE};
		var dialog = new Dialog().setTitle(PhotonModelTextUtil.key("mesh.merge.distance"));
		configureDialog(dialog);
		dialog.addContent(number("distance", distance[0], value -> distance[0] = value));
		dialog.addButton(button("mesh.merge", () -> {
			topology("mesh.merge", asset -> PhotonModelTopologyUtil.merge(asset, vertices, mode, distance[0]));
			dialog.close();
		}));
		dialog.addButton(new Button().setText("gui.cancel").setOnClick(_ -> dialog.close()));
		dialog.show(owner);
	}

	private void openLoopCutDialog() {
		showWorkspace("model");
		int[] count = {1};
		float[] percent = {LOOP_PERCENT};
		boolean[] uniform = {false};
		boolean[] followMouse = {true};
		var dialog = new Dialog().setTitle(PhotonModelTextUtil.key("loop_cut"));
		configureDialog(dialog);
		dialog.addContent(label("loop_cut.help"));
		dialog.addContent(new Toggle().setText(PhotonModelTextUtil.key("loop_cut.uniform"))
				.setOn(false, false).setOnToggleChanged(value -> uniform[0] = value));
		dialog.addContent(integer("loop_cut.count", count[0], 1, PhotonModelLoopCutUtil.MAX_CUTS, value -> count[0] = value));
		dialog.addContent(new Toggle().setText(PhotonModelTextUtil.key("loop_cut.follow"))
				.setOn(true, false).setOnToggleChanged(value -> followMouse[0] = value));
		dialog.addContent(number("loop_cut.percent", percent[0], value -> percent[0] = value));
		dialog.addButton(button("loop_cut.preview", () -> {
			preview.beginLoopCut(uniform[0] ? -1 : percent[0] / PERCENT_SCALE, count[0],
					!uniform[0] && followMouse[0], plan -> safe(() ->
							topology("loop_cut", asset -> PhotonModelLoopCutUtil.apply(asset, plan))));
			dialog.close();
		}));
		dialog.addButton(new Button().setText("gui.cancel").setOnClick(_ -> dialog.close()));
		dialog.show(owner);
	}


	public void appendFileMenu(TreeBuilder.Menu menu) {
		menu.crossLine();
		menu.leaf(PhotonModelTextUtil.key("import_obj"), () -> fileDialog("import_obj", true));
		menu.leaf(PhotonModelTextUtil.key("export_obj"), () -> fileDialog("export_obj", false));
	}

	private void buildPartProperties() {
		var part = session.part();

		if (part == null) {
			return;
		}

		inspector.addScrollViewChild(text("name", part.id, this::renamePart));
		inspector.addScrollViewChild(text("parent", part.parentId,
				value -> partChange(current -> current.parentId = value)));
		var euler = part.transform.rotation.getEulerAnglesXYZ(new Vector3f()).mul((float) Math.toDegrees(1));
		inspector.addScrollViewChild(vector("rotation_degrees", euler,
				value -> partChange(current -> current.transform.rotation.identity().rotateXYZ(
						(float) Math.toRadians(value.x), (float) Math.toRadians(value.y), (float) Math.toRadians(value.z)))));
		inspector.addScrollViewChild(vector("scale", part.transform.scale,
				value -> partChange(current -> current.transform.scale.set(value))));
		String partId = part.id;
		inspector.addScrollViewChild(vector("pivot", part.pivot,
				value -> session.change("editor.move_pivot", asset -> PhotonModelEditUtil.movePartPivot(asset, partId, value))));
		inspector.addScrollViewChild(new Toggle().setText(PhotonModelTextUtil.key("editor.edit_pivot"))
				.setOn(preview.isEditingPivot(), false).setOnToggleChanged(on -> safe(() -> {
					if (on) {
						session.selectionMode = SelectionMode.PART;
						showWorkspace("model");
					}

					preview.setEditingPivot(on);
					session.selectionChanged();
				})).layout(layout -> layout.minHeight(ROW_HEIGHT).setFlexShrink(0)));
		inspector.addScrollViewChild(label("editor.pivot_help"));
		inspector.addScrollViewChild(enumSelector(Anchor.values(), part.anchor,
				value -> partChange(current -> current.anchor = value)));
		inspector.addScrollViewChild(number("opacity", part.opacity,
				value -> partChange(current -> current.opacity = value)));
		inspector.addScrollViewChild(new Toggle().setText(PhotonModelTextUtil.key("editor.shading"))
				.setOn(part.shading, false).setOnToggleChanged(on -> safe(() ->
						partChange(partId, current -> current.shading = on))));
		inspector.addScrollViewChild(new Toggle().setText(PhotonModelTextUtil.key("editor.double_sided"))
				.setOn(part.doubleSided, false).setOnToggleChanged(on -> safe(() ->
						partChange(partId, current -> current.doubleSided = on))));
		inspector.addScrollViewChild(label("mesh.tools"));
		inspector.addScrollViewChild(button("mesh.inside_out",
				() -> partChange(partId, current -> current.insideOut = !current.insideOut)));
		inspector.addScrollViewChild(number("length", session.asset().referenceLength,
				value -> session.change("length", asset -> asset.referenceLength = value)));
		inspector.addScrollViewChild(number("width", session.asset().referenceWidth,
				value -> session.change("width", asset -> asset.referenceWidth = value)));
		inspector.addScrollViewChild(number("forward_axis", session.asset().forwardAxis,
				value -> session.change("forward_axis", asset -> {
					asset.forwardAxis = Math.round(value);
					asset.parts.forEach(current -> {
						if (current.stretch.enabled) {
							current.stretch.axis = asset.forwardAxis;
						}
					});
				})));
	}

	private void partChange(Consumer<Part> change) {
		partChange(session.selectedPart, change);
	}

	private void partChange(String partId, Consumer<Part> change) {
		session.change("part", asset -> change.accept(asset.parts.stream().filter(part -> part.id.equals(partId))
				.findFirst().orElseThrow()));
	}

	private void renamePart(String newId) {
		String oldId = session.selectedPart;
		session.change("name", asset -> {
			asset.parts.stream().filter(part -> part.id.equals(oldId)).findFirst().orElseThrow().id = newId;
			asset.parts.forEach(part -> {
				if (part.parentId.equals(oldId)) {
					part.parentId = newId;
				}
			});
			asset.faces.forEach(face -> {
				if (face.partId.equals(oldId)) {
					face.partId = newId;
				}
			});
			asset.clips.forEach(clip -> {
				var track = clip.partTracks.remove(oldId);

				if (track != null) {
					clip.partTracks.put(newId, track);
				}
			});
		});
		session.selectedPart = newId;
		refreshInspector();
	}

	private void selectAll() {
		session.selectedVertices.clear();
		session.selectedFaces.clear();
		session.selectedEdges.clear();

		for (int index : PhotonModelEditUtil.partFaces(session.asset(), session.selectedPart)) {
			session.selectedFaces.add(index);

			for (int vertex : session.asset().faces.get(index).vertices) {
				session.selectedVertices.add(vertex);
			}

			var face = session.asset().faces.get(index);

			for (int corner = 0; corner < face.vertices.length; corner++) {
				session.selectedEdges.add(Edge.of(face.vertices[corner], face.vertices[(corner + 1) % face.vertices.length]));
			}
		}

		session.selectionChanged();
	}

	private Set<Integer> faces() {
		return session.selectionMode == SelectionMode.PART
				? new LinkedHashSet<>(PhotonModelEditUtil.partFaces(session.asset(), session.selectedPart))
				: new LinkedHashSet<>(session.selectedFaces);
	}

	private void deleteSelection() {
		var selectedFaces = faces();
		var selectedVertices = session.vertices();
		topology("delete", asset -> {
			if (session.selectionMode == SelectionMode.VERTEX || session.selectionMode == SelectionMode.EDGE) {
				PhotonModelEditUtil.deleteVertices(asset, selectedVertices);
			} else {
				PhotonModelEditUtil.deleteFaces(asset, selectedFaces);
			}
		});
		session.selectedFaces.clear();
		session.selectedVertices.clear();
		session.selectedEdges.clear();
		refreshInspector();
	}

	private void duplicateSelection() {
		if (session.selectionMode == SelectionMode.VERTEX || session.selectionMode == SelectionMode.EDGE) {
			return;
		}

		var selected = faces();
		String newId = session.selectionMode == SelectionMode.PART ? UUID.randomUUID().toString() : session.selectedPart;
		topology("duplicate", asset -> {
			if (session.selectionMode == SelectionMode.PART) {
				var part = session.part().copy();
				part.id = newId;
				asset.parts.add(part);
			}

			PhotonModelEditUtil.duplicateFaces(asset, selected, newId);
		});
		session.selectedPart = newId;
		refreshInspector();
	}

	private void topology(String label, Consumer<PhotonModelAsset> operation) {
		if (!session.asset().morphs.isEmpty()) {
			throw new IllegalStateException(Component.translatable(PhotonModelTextUtil.key("topology_locked")).getString());
		}

		session.change(label, operation);
		uvCanvas.clearSelection();
		session.selectedFaces.clear();
		session.selectedVertices.clear();
		session.selectedEdges.clear();
		session.selectionChanged();
	}

	private void transformSelection() {
		var quaternion = new Quaternionf().rotateXYZ((float) Math.toRadians(rotation.x),
				(float) Math.toRadians(rotation.y), (float) Math.toRadians(rotation.z));

		if (session.selectionMode == SelectionMode.PART) {
			partChange(part -> {
				part.transform.translation.add(translation);
				part.transform.rotation.mul(quaternion).normalize();
				part.transform.scale.mul(scale);
			});
			return;
		}

		var selected = session.vertices();
		var pivot = PhotonModelEditUtil.center(session.asset(), selected);
		var selectedFaces = new LinkedHashSet<>(session.selectedFaces);
		session.change("editor.transform", asset -> PhotonModelEditUtil.transformVertices(asset, selected, selectedFaces, pivot,
				translation, quaternion, scale));
		session.selectedFaces.clear();
		session.selectedFaces.addAll(selectedFaces);
		uvCanvas.clearSelection();
	}

	private void buildUVTools(ScrollerView tools) {
		var group = new Toggle.ToggleGroup().setAllowEmpty(false);
		var modes = row();

		for (var mode : List.of(SelectionMode.VERTEX, SelectionMode.FACE)) {
			var toggle = new Toggle().setText(PhotonModelTextUtil.key(mode.name().toLowerCase(Locale.ROOT)))
					.setOn(mode == SelectionMode.VERTEX, false).setToggleGroup(group)
					.setOnToggleChanged(on -> {
						if (on) {
							uvCanvas.setFaceSelection(mode == SelectionMode.FACE);
						}
					});
			toggle.layout(layout -> layout.minWidth(ACTION_MIN_WIDTH).height(ROW_HEIGHT).setFlexShrink(0));
			modes.addChild(toggle);
		}

		tools.addScrollViewChild(modes);
		tools.addScrollViewChild(button("select_all", uvCanvas::selectAll));
		tools.addScrollViewChild(button("clear_selection", uvCanvas::clearSelection));
		tools.addScrollViewChild(label("editor.uv_help"));
		tools.addScrollViewChild(number("uv_translate_u", uvTranslation.x, value -> uvTranslation.x = value));
		tools.addScrollViewChild(number("uv_translate_v", uvTranslation.y, value -> uvTranslation.y = value));
		tools.addScrollViewChild(number("uv_scale_u", uvScale.x, value -> uvScale.x = value));
		tools.addScrollViewChild(number("uv_scale_v", uvScale.y, value -> uvScale.y = value));
		tools.addScrollViewChild(number("uv_rotation", uvRotation, value -> uvRotation = value));
		tools.addScrollViewChild(button("editor.uv_transform", () -> uvCanvas.transform(uvTranslation, uvRotation, uvScale)));

		for (int axis = 0; axis < 3; axis++) {
			int projectionAxis = axis;
			tools.addScrollViewChild(new Button().setText(Component.translatable(
					PhotonModelTextUtil.key("editor.uv_project_axis"), String.valueOf("XYZ".charAt(axis)))).setOnClick(_ -> safe(() -> {
				var selected = session.selectedFaces.isEmpty() ? faces() : new LinkedHashSet<>(session.selectedFaces);
				session.change("uv_planar", asset -> PhotonModelEditUtil.projectUV(asset, selected, projectionAxis));
			})));
		}
	}

	private void buildAnimationTools() {
		buildPlaybackTools();
		buildClips();
		inspector.addScrollViewChild(button(editBindPose ? "pose" : "bind_pose", () -> {
			editBindPose = !editBindPose;
			session.clearPose();
			refreshInspector();
		}));
		buildTransformEditor(false);
		buildBones();
		buildMorphs();
	}

	private void buildPlaybackTools() {
		var playback = project.playback();
		var clips = new ArrayList<String>();
		clips.add("");
		session.asset().clips.forEach(clip -> clips.add(clip.id));

		if (!clips.contains(playback.clip)) {
			clips.add(playback.clip);
			inspector.addScrollViewChild(label("invalid_default_clip"));
		}

		inspector.addScrollViewChild(label("default_playback"));
		inspector.addScrollViewChild(new SelectorConfigurator<>(PhotonModelTextUtil.key("default_clip"),
				() -> project.playback().clip, value -> {
			changePlayback(current -> current.clip = value);
			refreshInspector();
		}, "", true, clips,
				value -> value == null ? "" : value.isEmpty()
						? Component.translatable(PhotonModelTextUtil.key("clip_none")).getString() : value));
		inspector.addScrollViewChild(new BooleanConfigurator(PhotonModelTextUtil.key("enabled"),
				() -> project.playback().enabled, value -> changePlayback(current -> current.enabled = value), true, true));
		inspector.addScrollViewChild(new NumberConfigurator(PhotonModelTextUtil.key("duration"),
				() -> project.playback().duration, value -> changePlayback(current -> current.duration = value.intValue()),
				PhotonModelEmitterConfig.DEFAULT_DURATION, true).setRange(1, Integer.MAX_VALUE));
		inspector.addScrollViewChild(new NumberConfigurator(PhotonModelTextUtil.key("start_delay"),
				() -> project.playback().startDelay, value -> changePlayback(current -> current.startDelay = value.intValue()),
				0, true).setRange(0, Integer.MAX_VALUE));
		inspector.addScrollViewChild(new BooleanConfigurator(PhotonModelTextUtil.key("looping"),
				() -> project.playback().looping, value -> changePlayback(current -> current.looping = value), true, true));
		inspector.addScrollViewChild(new BooleanConfigurator(PhotonModelTextUtil.key("pause"),
				() -> project.playback().paused, value -> changePlayback(current -> current.paused = value), false, true));
		inspector.addScrollViewChild(new NumberConfigurator(PhotonModelTextUtil.key("playback_speed"),
				() -> project.playback().playbackSpeed, value -> changePlayback(current -> current.playbackSpeed = value.floatValue()),
				PhotonModelEmitterConfig.DEFAULT_PLAYBACK_SPEED, true).setRange(0, Float.MAX_VALUE));
		inspector.addScrollViewChild(new NumberConfigurator(PhotonModelTextUtil.key("animation_offset"),
				() -> project.playback().animationOffset, value -> changePlayback(current -> current.animationOffset = value.floatValue()),
				0f, true));
	}

	private void changePlayback(Consumer<PhotonModelPlaybackConfig> edit) {
		var before = project.playback().copy();
		var after = before.copy();
		edit.accept(after);
		after.validate();
		project.setPlayback(after);
		owner.historyView.pushHistory(Component.translatable(PhotonModelTextUtil.key("default_playback")),
				EditAction.of(() -> {
					project.setPlayback(after);
					refreshInspector();
				}, () -> {
					project.setPlayback(before);
					refreshInspector();
				}), false);
	}

	private void buildClips() {
		inspector.addScrollViewChild(label("clips"));

		var clip = session.clip();

		if (clip == null) {
			return;
		}

		inspector.addScrollViewChild(text("name", clip.id, this::renameClip));
		inspector.addScrollViewChild(number("duration", clip.duration,
				value -> session.change("clip", asset -> asset.clips.stream().filter(current -> current.id.equals(session.selectedClip))
						.findFirst().orElseThrow().duration = value)));
		inspector.addScrollViewChild(button("animation_loop", () -> {
			session.change("clip", asset -> asset.clips.stream().filter(current -> current.id.equals(session.selectedClip))
					.findFirst().orElseThrow().loop = !session.clip().loop);
			refreshInspector();
		}));
	}

	private void renameClip(String name) {
		animationWorkspace.renameSelected(name);
		refreshInspector();
	}

	private void buildTransformEditor(boolean bone) {
		if (bone ? session.bone() == null : session.part() == null) {
			return;
		}

		Transform transform = editBindPose ? bone ? session.bone().bind : session.part().transform : session.pose(bone);

		if (transform == null) {
			return;
		}

		inspector.addScrollViewChild(label(bone ? editBindPose ? "bind_pose" : "pose" : "part"));
		inspector.addScrollViewChild(vector("position", transform.translation,
				value -> transformChange(bone, current -> current.translation.set(value))));
		var euler = transform.rotation.getEulerAnglesXYZ(new Vector3f()).mul((float) (180 / Math.PI));
		inspector.addScrollViewChild(vector("rotation", euler,
				value -> transformChange(bone, current -> current.rotation.identity().rotateXYZ(
						(float) Math.toRadians(value.x), (float) Math.toRadians(value.y), (float) Math.toRadians(value.z)))));
		inspector.addScrollViewChild(vector("scale", transform.scale,
				value -> transformChange(bone, current -> current.scale.set(value))));

		if (!editBindPose) {
			inspector.addScrollViewChild(button(bone ? "bone_key" : "part_key", () -> {
				session.recordTransform(bone);
				refreshInspector();
			}));
		}
	}

	private void transformChange(boolean bone, Consumer<Transform> edit) {
		if (!editBindPose) {
			session.changePose(bone, edit);
			return;
		}

		session.change("editor.transform", asset -> {
			if (bone) {
				edit.accept(asset.bones.stream().filter(current -> current.id.equals(session.selectedBone)).findFirst().orElseThrow().bind);
			} else {
				edit.accept(asset.parts.stream().filter(current -> current.id.equals(session.selectedPart)).findFirst().orElseThrow().transform);
			}
		});
	}

	private void addBone() {
		session.addBone();
		editBindPose = true;
		session.clearPose();
		showWorkspace("animation");
		refreshInspector();
	}

	private void buildBones() {
		inspector.addScrollViewChild(label("bones"));

		for (var bone : session.asset().bones) {
			inspector.addScrollViewChild(new Button().setText(bone.id, false).setOnClick(_ -> {
				session.selectedBone = bone.id;
				refreshInspector();
			}));
		}

		inspector.addScrollViewChild(button("add_bone", this::addBone));
		var bone = session.bone();

		if (bone == null) {
			return;
		}

		inspector.addScrollViewChild(text("name", bone.id, this::renameBone));
		inspector.addScrollViewChild(text("parent", bone.parentId, value -> session.change("bone",
				asset -> asset.bones.stream().filter(current -> current.id.equals(session.selectedBone))
						.findFirst().orElseThrow().parentId = value)));
		buildTransformEditor(true);
		inspector.addScrollViewChild(number("weight", weight, value -> weight = Math.clamp(value, 0, 1)));
		inspector.addScrollViewChild(button("bind", () -> session.assignWeight(weight)));
		inspector.addScrollViewChild(button("editor.delete_bone", this::deleteBone));
	}

	private void renameBone(String name) {
		String old = session.selectedBone;
		session.change("name", asset -> {
			asset.bones.stream().filter(bone -> bone.id.equals(old)).findFirst().orElseThrow().id = name;
			asset.bones.forEach(bone -> {
				if (bone.parentId.equals(old)) {
					bone.parentId = name;
				}
			});
			asset.weights.values().forEach(weights -> weights.forEach(weight -> {
				if (weight.boneId.equals(old)) {
					weight.boneId = name;
				}
			}));
			asset.clips.forEach(clip -> {
				var track = clip.boneTracks.remove(old);

				if (track != null) {
					clip.boneTracks.put(name, track);
				}
			});
		});
		session.selectedBone = name;
		refreshInspector();
	}

	private void deleteBone() {
		String id = session.selectedBone;
		session.change("editor.delete_bone", asset -> {
			asset.bones.removeIf(bone -> bone.id.equals(id));
			asset.bones.forEach(bone -> {
				if (bone.parentId.equals(id)) {
					bone.parentId = "";
				}
			});
			asset.weights.values().forEach(weights -> weights.removeIf(weight -> weight.boneId.equals(id)));
			asset.clips.forEach(clip -> clip.boneTracks.remove(id));
		});
		refreshInspector();
	}

	private void buildMorphs() {
		inspector.addScrollViewChild(label("morphs"));

		for (var morph : session.asset().morphs) {
			inspector.addScrollViewChild(new Button().setText(morph.id, false).setOnClick(_ -> {
				session.selectedMorph = morph.id;
				refreshInspector();
			}));
		}

		inspector.addScrollViewChild(button("add_morph", () -> {
			session.addMorph();
			refreshInspector();
		}));
		var morph = session.morph();

		if (morph == null) {
			return;
		}

		inspector.addScrollViewChild(text("name", morph.id, this::renameMorph));
		inspector.addScrollViewChild(number("morph_weight", editBindPose ? morph.weight : session.morphWeight(), value -> {
			preview.previewMorph("");

			if (editBindPose) {
				session.change("morph_weight", asset -> asset.morphs.stream().filter(current -> current.id.equals(session.selectedMorph))
						.findFirst().orElseThrow().weight = value);
			} else {
				session.changeMorphPose(value);
			}
		}));
		inspector.addScrollViewChild(vector("editor.morph_delta", morphDelta, value -> morphDelta = value));
		inspector.addScrollViewChild(button("edit_morph", () -> {
			session.setMorphDelta(morphDelta);
			preview.previewMorph(session.selectedMorph);
		}));
		inspector.addScrollViewChild(button("base_shape", () -> preview.previewMorph("")));

		if (!editBindPose) {
			inspector.addScrollViewChild(button("morph_key", () -> {
				session.recordMorph();
				preview.previewMorph("");
				refreshInspector();
			}));
		}

		inspector.addScrollViewChild(button("editor.delete_morph", () -> {
			String id = session.selectedMorph;
			session.change("editor.delete_morph", asset -> {
				asset.morphs.removeIf(current -> current.id.equals(id));
				asset.clips.forEach(clip -> clip.morphTracks.remove(id));
			});
			refreshInspector();
		}));
	}

	private void renameMorph(String name) {
		String old = session.selectedMorph;
		session.change("name", asset -> {
			asset.morphs.stream().filter(morph -> morph.id.equals(old)).findFirst().orElseThrow().id = name;
			asset.clips.forEach(clip -> {
				var track = clip.morphTracks.remove(old);

				if (track != null) {
					clip.morphTracks.put(name, track);
				}
			});
		});
		session.selectedMorph = name;
		refreshInspector();
	}

	private void apply() {
		if (!project.canApplyToSource()) {
			status.setText(Component.translatable(PhotonModelTextUtil.key("project.source_unavailable")));
			return;
		}

		session.asset().validate();
		project.playback().validate(session.asset());
		var target = project.sourceEmitter();

		if (target == null) {
			return;
		}

		var before = SerializableRecordAction.of(target.config);
		target.setAsset(session.asset().copy());
		project.applyPlayback(target.config);
		project.applyRenderer(target.config);
		var after = SerializableRecordAction.of(target.config);

		if (host != null) {
			host.historyView.pushHistory(Component.translatable(PhotonModelTextUtil.key("apply")),
					EditAction.of(() -> {
						after.loadSnapshot();
						host.reloadEffect();
					}, () -> {
						before.loadSnapshot();
						host.reloadEffect();
					}), false);
			host.reloadEffect();
		}

		status.setText(Component.translatable(PhotonModelTextUtil.key("apply")));
	}

	private void fileDialog(String title, boolean read) {
		Dialog.showFileDialog(PhotonModelTextUtil.key(title), LDLib2.getAssetsDir(), read, Dialog.suffixFilter(OBJ_SUFFIX),
				file -> {
					if (file == null) {
						return;
					}

					safe(() -> {
						if (read) {
							var text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
							session.replaceFromFile(PhotonModelIOUtil.readObj(text, true));
							refreshInspector();
						} else {
							save(file);
						}
					});
				}).show(owner);
	}

	private void save(File selected) throws IOException {
		var file = selected.getName().endsWith(OBJ_SUFFIX) ? selected : new File(selected.getPath() + OBJ_SUFFIX);
		var path = file.toPath();

		if (Files.exists(path)) {
			Dialog.showCheckBox(PhotonModelTextUtil.key("editor.overwrite"), Component.literal(path.toString()), confirmed -> {
				if (confirmed) {
					safe(() -> write(path.toFile()));
				}
			}).show(owner);
			return;
		}

		write(file);
	}

	private void write(File file) throws IOException {
		session.asset().validate();
		var serialized = PhotonModelIOUtil.writeObj(session.asset(), true);
		Files.writeString(file.toPath(), serialized, StandardCharsets.UTF_8);
		status.setText(Component.translatable(PhotonModelTextUtil.key("saved"), file.getName()));
	}

	private Button button(String name, Action action) {
		var button = new Button().setText(PhotonModelTextUtil.key(name)).setOnClick(_ -> safe(action));
		button.layout(layout -> {
			layout.minHeight(ROW_HEIGHT);
			layout.minWidth(ACTION_MIN_WIDTH);
			layout.setFlexShrink(0);
		});
		button.text.textStyle(style -> style.adaptiveWidth(false).textWrap(TextWrap.HOVER_ROLL));
		button.text.layout(layout -> {
			layout.flex(1);
			layout.minWidth(0);
		});
		button.text.setOverflowVisible(false);
		return button;
	}

	private TextElement label(String name) {
		return new Label().setText(PhotonModelTextUtil.key(name))
				.textStyle(style -> style.adaptiveHeight(true).textWrap(TextWrap.WRAP));
	}

	private UIElement row() {
		return new UIElement().layout(layout -> {
			layout.flexDirection(FlexDirection.ROW);
			layout.wrap(FlexWrap.WRAP);
			layout.gapAll(2);
			layout.widthPercent(100);
		});
	}

	private void configureDialog(Dialog dialog) {
		dialog.overlay.layout(layout -> layout.width(DIALOG_WIDTH));
		dialog.contentContainer.layout(layout -> layout.widthPercent(100).gapAll(3)
				.alignItems(AlignItems.STRETCH));
	}

	private UIElement number(String name, float value, Consumer<Float> setter) {
		return new NumberConfigurator(PhotonModelTextUtil.key(name), () -> value,
				number -> safe(() -> setter.accept(number.floatValue())), value, false).setWheel(NUMBER_STEP);
	}

	private UIElement text(String name, String value, Consumer<String> setter) {
		return new StringConfigurator(PhotonModelTextUtil.key(name), () -> value,
				text -> safe(() -> setter.accept(text)), value, false);
	}

	private UIElement vector(String name, Vector3f value, Consumer<Vector3f> setter) {
		var current = new Vector3f(value);
		var configurator = new Vector3fAccessor().create(PhotonModelTextUtil.key(name), () -> current,
				vector -> safe(() -> {
					current.set(vector);
					setter.accept(new Vector3f(vector));
				}), false, null, null);

		for (var child : configurator.inlineContainer.getChildren()) {
			if (child instanceof NumberConfigurator number) {
				number.setWheel(NUMBER_STEP);
			}
		}

		return configurator;
	}

	private <T extends Enum<T>> UIElement enumSelector(T[] values, T selected, Consumer<T> setter) {
		return new SelectorConfigurator<>("", () -> selected, value -> safe(() -> setter.accept(value)),
				selected, false, Arrays.asList(values), value -> PhotonModelTextUtil.key(value.name().toLowerCase(Locale.ROOT)));
	}

	private void safe(Action action) {
		try {
			action.run();
		} catch (Exception error) {
			String message = String.valueOf(error.getMessage());
			status.setText(Component.translatable(PhotonModelTextUtil.key("error"),
					message.startsWith(PhotonModelTextUtil.PREFIX) ? Component.translatable(message) : message));
		}
	}

	@FunctionalInterface
	private interface Action {
		// 按项目规范显式声明接口成员的公共可见性。
		@SuppressWarnings("UnnecessaryModifier")
		public void run() throws Exception;
	}
}
