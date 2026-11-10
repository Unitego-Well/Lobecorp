package org.unitego.lobecorp.client.photon.model.project;

import com.lowdragmc.lowdraglib2.client.scene.SceneRenderContext;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.client.utils.RenderBufferUtils;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.SceneObject;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.ISceneRendering;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.utils.TransformGizmo;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.lowdraglib2.math.ITransform;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import org.joml.Intersectionf;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.joml.Vector2f;
import org.joml.Vector4f;
import com.mojang.blaze3d.systems.RenderSystem;
import org.unitego.lobecorp.mixin.photon.editor.client.WorldSceneRendererProjectionAccessor;
import org.unitego.lobecorp.util.photon.model.PhotonModelPolygonUtil;
import org.unitego.lobecorp.registry.translation.photon.model.PhotonModelTranslations;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditMesh;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Edge;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.SelectionMode;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelTopologyUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelLoopCutUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelPrimitiveUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelHierarchyUtil;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Arrays;

/// 网格选择、手柄和环切预览复用原生场景；一次确认只提交一次当前工程事务。
public class PhotonModelWorkspaceMesh extends SceneObject implements ISceneRendering, AutoCloseable {
	private static final float PICK_PIXELS = 7;
	private static final float RAY_EPSILON = 1e-5f;
	private static final float DEFAULT_EXTRUDE = 0.1f;
	private static final float DEFAULT_MERGE = 0.01f;
	private static final int SELECTED_COLOR = 0xffff8800;
	private static final int EDGE_COLOR = 0x8090c0ff;
	private final FXEditor editor;
	private final PhotonModelProject project;
	private final PhotonModelWorkspaceSession session;
	private final Supplier<String> selectedPart;
	private final Consumer<String> selectPart;
	private final Set<Integer> faces;
	private final Set<Integer> vertices = new LinkedHashSet<>();
	private final Set<Edge> edges = new LinkedHashSet<>();
	private final SceneObject handle = new SceneObject();
	private final UIEventListener down = this::pick;
	private final UIEventListener move = this::hover;
	private final UIEventListener up = event -> {
		if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			finishGesture();
		}
	};
	private final UIEventListener end = _ -> finishGesture();
	private final UIEventListener keys = this::key;
	private SelectionMode mode = SelectionMode.PART;
	private PhotonModelEditMesh mesh;
	private PhotonModelEditMesh gesture;
	private PhotonModelEditMesh preview;
	private String revision = "";
	private String gestureRevision;
	private String part = "";
	private boolean enabled = true;
	private boolean closed;
	private boolean pivot;
	private boolean loop;
	private boolean loopUniform;
	private boolean loopFollow = true;
	private int cuts = 1;
	private float cutPercent = 50;
	private float extrude = DEFAULT_EXTRUDE;
	private float mergeDistance = DEFAULT_MERGE;
	private final Vector3f translation = new Vector3f();
	private final Vector3f rotation = new Vector3f();
	private final Vector3f scale = new Vector3f(1);
	private Vector3f center = new Vector3f();
	private Matrix4f partMatrix = new Matrix4f();
	private final Quaternionf orientation = new Quaternionf();
	private PhotonModelLoopCutUtil.Plan loopPlan;
	private final Matrix4f projection = new Matrix4f();
	private boolean projectionReady;
	private boolean boxMode;
	private Vector2f boxStart;
	private Vector2f boxEnd;
	private final UIEventListener drag = this::dragBox;
	private final UIElement boxOverlay = new UIElement();
	private final Set<Dialog> dialogs = new LinkedHashSet<>();

	public PhotonModelWorkspaceMesh(FXEditor editor, PhotonModelProject project, PhotonModelWorkspaceSession session,
	                                Supplier<String> selectedPart, Consumer<String> selectPart, Set<Integer> faces) {
		this.editor = editor;
		this.project = project;
		this.session = session;
		this.selectedPart = selectedPart;
		this.selectPart = selectPart;
		this.faces = faces;
		var scene = editor.sceneView.sceneEditor;
		scene.addEventListener(UIEvents.MOUSE_DOWN, down, true);
		scene.addEventListener(UIEvents.MOUSE_MOVE, move);
		scene.addEventListener(UIEvents.MOUSE_UP, up);
		scene.addEventListener(UIEvents.DRAG_END, end);
		scene.addEventListener(UIEvents.KEY_DOWN, keys, true);
		scene.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, drag);
		boxOverlay.setAllowHitTest(false).layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).widthPercent(100).heightPercent(100));
		boxOverlay.style(style -> style.backgroundTexture(GuiTexture.of((graphics, _, _, _, _) -> {
			if (boxStart != null && boxEnd != null) {
				var first = boxOverlay.worldToLocal(boxStart);
				var second = boxOverlay.worldToLocal(boxEnd);
				float left = Math.min(first.x, second.x);
				float top = Math.min(first.y, second.y);
				float right = Math.max(first.x, second.x);
				float bottom = Math.max(first.y, second.y);
				DrawerHelperClient.drawSolidRect(graphics, left, top, right - left, bottom - top, 0x30ff8800);
				DrawerHelperClient.drawSolidRect(graphics, left, top, right - left, 1, SELECTED_COLOR);
				DrawerHelperClient.drawSolidRect(graphics, left, bottom, right - left, 1, SELECTED_COLOR);
				DrawerHelperClient.drawSolidRect(graphics, left, top, 1, bottom - top, SELECTED_COLOR);
				DrawerHelperClient.drawSolidRect(graphics, right, top, 1, bottom - top, SELECTED_COLOR);
			}
		})));
		scene.addChild(boxOverlay);
		try {
			refresh();
		} catch (RuntimeException | Error failure) {
			close();
			throw failure;
		}
	}

	public void enabled(boolean value) {
		enabled = value;
		cancel();
		updateGizmo();
	}

	public Set<Integer> vertexSelection() {
		refresh();
		return selectedVertices(mesh);
	}

	public void refresh() {
		if (closed) {
			return;
		}
		var scene = editor.sceneView.sceneEditor;
		if (!scene.getSceneObjects().containsKey(id())) {
			scene.addSceneObject(this);
		}
		if (!revision.equals(session.revision()) || !part.equals(selectedPart.get())) {
			cancel();
			var next = PhotonModelEditMesh.read(session.asset());
			boolean topologyChanged = mesh == null || mesh.vertices.size() != next.vertices.size() || mesh.faces.size() != next.faces.size()
					|| !part.equals(selectedPart.get());
			if (!topologyChanged) {
				for (int index = 0; index < mesh.faces.size(); index++) {
					if (!Arrays.equals(mesh.faces.get(index).vertices, next.faces.get(index).vertices)
							|| !mesh.faces.get(index).partId.equals(next.faces.get(index).partId)) {
						topologyChanged = true;
						break;
					}
				}
			}
			mesh = next;
			if (topologyChanged) {
				vertices.clear();
				edges.clear();
				faces.clear();
			}
			revision = session.revision();
			part = selectedPart.get();
			updateGizmo();
		}
	}

	public UIElement tools() {
		var tools = new UIElement();
		tools.addChild(new Label().setText(PhotonModelTranslations.MESH_TOOLS));
		var selection = new Selector<SelectionMode>().setCandidates(List.of(SelectionMode.values()))
				.setSelected(mode, false).setCandidateUIProvider(value -> new Label().setText(PhotonModelTranslations.key(value.name().toLowerCase(Locale.ROOT))))
				.setOnValueChanged(value -> {
					cancel();
					mode = value;
					vertices.clear();
					edges.clear();
					faces.clear();
					updateGizmo();
				});
		tools.addChild(selection);
		button(tools, "select_all", this::selectAll);
		button(tools, "editor.box_select", () -> {
			cancel();
			boxMode = true;
			editor.sceneView.sceneEditor.disableTransformGizmo();
		});
		button(tools, "clear_selection", () -> {
			cancel();
			faces.clear();
			vertices.clear();
			edges.clear();
			updateGizmo();
		});
		number(tools, "distance", () -> extrude, value -> extrude = value.floatValue());
		button(tools, "extrude", () -> change(value -> {
			requireTopology(value);
			PhotonModelEditUtil.extrude(value, selectedFaces(value), extrude);
		}));
		button(tools, "duplicate", () -> change(value -> {
			requireTopology(value);
			PhotonModelEditUtil.duplicateFaces(value, selectedFaces(value), part);
		}));
		button(tools, "delete", this::deleteSelection);
		number(tools, "distance", () -> mergeDistance, value -> mergeDistance = value.floatValue());
		for (var merge : PhotonModelTopologyUtil.MergeMode.values()) {
			button(tools, "mesh.merge." + merge.name().toLowerCase(Locale.ROOT),
					() -> change(value -> PhotonModelTopologyUtil.merge(value, selectedVertices(value), merge, mergeDistance)));
		}
		button(tools, "mesh.collapse", () -> change(value -> PhotonModelTopologyUtil.collapse(value, new LinkedHashSet<>(edges))));
		button(tools, "mesh.dissolve", () -> change(value -> PhotonModelTopologyUtil.dissolve(value, new LinkedHashSet<>(edges))));
		button(tools, "mesh.face", () -> change(value -> PhotonModelTopologyUtil.createFace(value, part, selectedVertices(value))));
		button(tools, "mesh.triangulate", () -> change(value -> PhotonModelTopologyUtil.triangulate(value, new ArrayList<>(selectedFaces(value)))));
		tools.addChild(new BooleanConfigurator(PhotonModelTranslations.EDITOR_EDIT_PIVOT, () -> pivot,
				value -> {
					pivot = value;
					cancel();
					updateGizmo();
				}, false, false));
		vector(tools, "position", translation);
		vector(tools, "rotation_degrees", rotation);
		vector(tools, "scale", scale);
		button(tools, "apply", () -> change(value -> {
			var selected = selectedVertices(value);
			PhotonModelEditUtil.transformVertices(value, selected, selectedFaces(value), PhotonModelEditUtil.center(value, selected),
					translation, new Quaternionf().rotationZYX((float) Math.toRadians(rotation.z),
							(float) Math.toRadians(rotation.y), (float) Math.toRadians(rotation.x)), scale);
		}));
		for (int axis = 0; axis < 3; axis++) {
			int target = axis;
			button(tools, "axis_" + "xyz".charAt(axis), () -> change(value -> PhotonModelEditUtil.projectUV(value, selectedFaces(value), target)));
		}
		tools.addChild(new BooleanConfigurator(PhotonModelTranslations.LOOP_CUT_UNIFORM, () -> loopUniform, value -> loopUniform = value, false, false));
		tools.addChild(new BooleanConfigurator(PhotonModelTranslations.LOOP_CUT_FOLLOW, () -> loopFollow, value -> loopFollow = value, true, false));
		tools.addChild(new NumberConfigurator(PhotonModelTranslations.LOOP_CUT_COUNT, () -> cuts, value -> cuts = value.intValue(), 1, false)
				.setRange(1, PhotonModelLoopCutUtil.MAX_CUTS));
		tools.addChild(new NumberConfigurator(PhotonModelTranslations.LOOP_CUT_PERCENT, () -> cutPercent, value -> cutPercent = value.floatValue(), 50, false)
				.setRange(1, 99));
		button(tools, "loop_cut.preview", () -> {
			cancel();
			loop = true;
			editor.sceneView.sceneEditor.disableTransformGizmo();
		});
		button(tools, "cancel", this::cancel);
		for (var shape : PhotonModelPrimitiveUtil.Shape.values()) {
			button(tools, shape.name().toLowerCase(Locale.ROOT), () -> primitive(shape));
		}
		return tools;
	}

	private static void button(UIElement parent, String key, Runnable action) {
		parent.addChild(new Button().setText(PhotonModelTranslations.key(key)).setOnClick(_ -> action.run()));
	}

	private static void number(UIElement parent, String key, Supplier<Number> getter, Consumer<Number> setter) {
		parent.addChild(new NumberConfigurator(PhotonModelTranslations.key(key), getter, setter, 0, false).setRange(-Float.MAX_VALUE, Float.MAX_VALUE));
	}

	private static void vector(UIElement parent, String key, Vector3f vector) {
		parent.addChild(new Label().setText(PhotonModelTranslations.key(key)));
		for (int index = 0; index < 3; index++) {
			int axis = index;
			number(parent, "axis_" + "xyz".charAt(index), () -> vector.get(axis), value -> vector.setComponent(axis, value.floatValue()));
		}
	}

	public void primitive(PhotonModelPrimitiveUtil.Shape shape) {
		var defaults = PhotonModelPrimitiveUtil.Parameters.defaults();
		float[] dimensions = {defaults.radius(), defaults.width(), defaults.height(), defaults.angle(), defaults.verticalAngle()};
		int[] counts = {defaults.segments(), defaults.rings()};
		String expected = session.revision();
		var dialog = new Dialog().setTitle(PhotonModelTranslations.key(shape.name().toLowerCase(Locale.ROOT)));
		String[] names = {"primitive.radius", "width", "primitive.height", "primitive.angle", "primitive.vertical_angle"};
		for (int index = 0; index < dimensions.length; index++) {
			int component = index;
			dialog.addContent(new NumberConfigurator(PhotonModelTranslations.key(names[index]), () -> dimensions[component],
					value -> dimensions[component] = value.floatValue(), dimensions[index], false));
		}
		dialog.addContent(new NumberConfigurator(PhotonModelTranslations.PRIMITIVE_SEGMENTS, () -> counts[0],
				value -> counts[0] = value.intValue(), counts[0], false).setRange(PhotonModelPrimitiveUtil.MIN_SEGMENTS, PhotonModelPrimitiveUtil.MAX_SEGMENTS));
		dialog.addContent(new NumberConfigurator(PhotonModelTranslations.PRIMITIVE_RINGS, () -> counts[1],
				value -> counts[1] = value.intValue(), counts[1], false).setRange(1, PhotonModelPrimitiveUtil.MAX_RINGS));
		dialog.addButton(new Button().setText(PhotonModelTranslations.APPLY).setOnClick(_ -> {
			changeAt(expected, value -> PhotonModelPrimitiveUtil.add(value, shape, new PhotonModelPrimitiveUtil.Parameters(
					dimensions[0], dimensions[1], dimensions[2], dimensions[3], dimensions[4], counts[0], counts[1])));
			dialog.close();
		}));
		dialog.addButton(new Button().setText(PhotonModelTranslations.CANCEL).setOnClick(_ -> dialog.close()));
		dialogs.add(dialog);
		dialog.setOnClose(() -> dialogs.remove(dialog));
		dialog.show(editor);
	}

	private static void requireTopology(PhotonModelEditMesh value) {
		if (!value.morphs.isEmpty()) {
			throw new IllegalArgumentException(Component.translatable(PhotonModelTranslations.TOPOLOGY_LOCKED).getString());
		}
	}

	private void change(Consumer<PhotonModelEditMesh> operation) {
		refresh();
		cancel();
		changeAt(session.revision(), operation);
	}

	private void changeAt(String expected, Consumer<PhotonModelEditMesh> operation) {
		session.changeAt(expected, asset -> {
			var candidate = PhotonModelEditMesh.read(asset);
			operation.accept(candidate);
			candidate.validate();
			var result = candidate.document();
			asset.asMap().clear();
			result.entrySet().forEach(entry -> asset.add(entry.getKey(), entry.getValue()));
		});
		refresh();
	}

	private Set<Integer> selectedFaces(PhotonModelEditMesh value) {
		var result = new LinkedHashSet<Integer>();
		var selected = mode == SelectionMode.VERTEX || mode == SelectionMode.EDGE ? selectedVertices(value) : Set.<Integer>of();

		for (int index : PhotonModelEditUtil.partFaces(value, part)) {
			if (mode == SelectionMode.PART || mode == SelectionMode.FACE && faces.contains(index)
					|| !selected.isEmpty() && Arrays.stream(value.faces.get(index).vertices).allMatch(selected::contains)) {
				result.add(index);
			}
		}

		return result;
	}

	private Set<Integer> selectedVertices(PhotonModelEditMesh value) {
		if (mode == SelectionMode.VERTEX) {
			return new LinkedHashSet<>(vertices);
		}
		if (mode == SelectionMode.EDGE) {
			var result = new LinkedHashSet<Integer>();
			for (var edge : edges) {
				result.add(edge.first());
				result.add(edge.second());
			}
			return result;
		}
		return PhotonModelEditUtil.selectedVertices(value, part, new LinkedHashSet<>(vertices), selectedFaces(value), mode);
	}

	private void deleteSelection() {
		cancel();
		change(value -> {
			if (mode == SelectionMode.PART || mode == SelectionMode.FACE) {
				PhotonModelEditUtil.deleteFaces(value, selectedFaces(value));
			} else {
				PhotonModelEditUtil.deleteVertices(value, selectedVertices(value));
			}
		});
	}

	private void selectAll() {
		refresh();
		cancel();
		faces.clear();
		faces.addAll(PhotonModelEditUtil.partFaces(mesh, part));
		vertices.clear();
		vertices.addAll(PhotonModelEditUtil.selectedVertices(mesh, part, Set.of(), Set.of(), SelectionMode.PART));
		edges.clear();
		for (int index : faces) {
			var face = mesh.faces.get(index);
			for (int corner = 0; corner < face.vertices.length; corner++) {
				edges.add(Edge.of(face.vertices[corner], face.vertices[(corner + 1) % face.vertices.length]));
			}
		}
		updateGizmo();
	}

	private Matrix4f matrix(String id, PhotonModelEditMesh value) {
		var target = value.parts.stream().filter(item -> item.id.equals(id)).findFirst().orElseThrow();
		var result = target.parentId.isEmpty() ? new Matrix4f() : matrix(target.parentId, value);
		return result.translate(target.transform.translation).translate(target.pivot)
				.rotate(new Quaternionf(target.transform.rotation).normalize()).scale(target.transform.scale).translate(new Vector3f(target.pivot).negate());
	}

	private Matrix4f world() {
		var target = editor.runtime == null ? null : editor.runtime.objects.get(project.model().id());
		return target == null ? new Matrix4f() : new Matrix4f(target.transform().localToWorldMatrix());
	}

	private Vector3f point(String id, int vertex, PhotonModelEditMesh value) {
		return world().mul(matrix(id, value)).transformPosition(new Vector3f(value.vertices.get(vertex)));
	}

	private void pick(UIEvent event) {
		if (!enabled || closed || event.target != editor.sceneView.sceneEditor.scene) {
			return;
		}
		if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && gesture != null) {
			cancel();
			return;
		}
		if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT || editor.sceneView.sceneEditor.getTransformGizmo().isDragging()) {
			return;
		}
		refresh();
		if (boxMode) {
			boxStart = new Vector2f(event.x, event.y);
			boxEnd = new Vector2f(boxStart);
			gestureRevision = revision;
			if (!event.isShiftDown()) {
				vertices.clear();
				edges.clear();
				faces.clear();
			}
			editor.sceneView.sceneEditor.startDrag(this, null);
			event.stopImmediatePropagation();
			return;
		}
		if (loop) {
			hover(event);
			if (loopPlan != null) {
				var plan = loopPlan;
				String expected = revision;
				cancel();
				changeAt(expected, value -> PhotonModelLoopCutUtil.apply(value, plan));
			}
			event.stopImmediatePropagation();
			return;
		}
		var ray = editor.sceneView.sceneEditor.getMouseRay().orElse(null);
		if (ray == null) {
			return;
		}
		int found = -1;
		float closest = Float.POSITIVE_INFINITY;
		for (int index = 0; index < mesh.faces.size(); index++) {
			var face = mesh.faces.get(index);
			if (mode != SelectionMode.PART && !face.partId.equals(part)) {
				continue;
			}
			var triangles = PhotonModelPolygonUtil.triangulate(mesh.vertices, face.vertices);
			for (int triangle = 0; triangle < triangles.length; triangle += 3) {
				float distance = Intersectionf.intersectRayTriangle(ray.startPos(), ray.getDirection(),
						point(face.partId, face.vertices[triangles[triangle]], mesh), point(face.partId, face.vertices[triangles[triangle + 1]], mesh),
						point(face.partId, face.vertices[triangles[triangle + 2]], mesh), RAY_EPSILON);
				if (distance >= 0 && distance < closest) {
					closest = distance;
					found = index;
				}
			}
		}
		if (mode == SelectionMode.PART && found >= 0) {
			selectPart.accept(mesh.faces.get(found).partId);
			refresh();
		} else if (mode == SelectionMode.FACE) {
			if (!event.isShiftDown()) {
				faces.clear();
			}
			if (found >= 0 && !faces.add(found)) {
				faces.remove(found);
			}
		} else if (mode == SelectionMode.VERTEX || mode == SelectionMode.EDGE) {
			selectGeometry(event);
		}
		updateGizmo();
		event.stopImmediatePropagation();
	}

	private record Hit(Edge edge, float ratio, float distance) {
	}

	private Hit nearestEdge() {
		var scene = editor.sceneView.sceneEditor;
		var ray = scene.getMouseRay().orElse(null);
		if (ray == null) {
			return null;
		}
		var direction = new Vector3f(ray.getDirection()).normalize();
		Hit hit = null;
		for (int index : PhotonModelEditUtil.partFaces(mesh, part)) {
			var face = mesh.faces.get(index);
			for (int corner = 0; corner < face.vertices.length; corner++) {
				var edge = Edge.of(face.vertices[corner], face.vertices[(corner + 1) % face.vertices.length]);
				var a = point(part, edge.first(), mesh);
				var b = point(part, edge.second(), mesh);
				var delta = new Vector3f(b).sub(a);
				var fromRay = new Vector3f(a).sub(ray.startPos());
				float along = direction.dot(delta);
				float denominator = delta.lengthSquared() - along * along;
				float ratio = denominator <= RAY_EPSILON ? 0 : Math.clamp((along * direction.dot(fromRay) - delta.dot(fromRay)) / denominator, 0, 1);
				var onEdge = new Vector3f(delta).mul(ratio).add(a);
				float t = direction.dot(new Vector3f(onEdge).sub(ray.startPos()));
				var onRay = new Vector3f(direction).mul(Math.max(0, t)).add(ray.startPos());
				float distance = onEdge.distance(onRay);
				float threshold = threshold(onEdge);
				if (t >= 0 && threshold > 0 && distance <= threshold && (hit == null || distance / threshold < hit.distance())) {
					hit = new Hit(edge, ratio, distance / threshold);
				}
			}
		}
		return hit;
	}

	private float threshold(Vector3f point) {
		var scene = editor.sceneView.sceneEditor.scene;
		var renderer = scene.<WorldSceneRenderer>getRenderer();
		return renderer == null ? 0 : 2 * renderer.getViewHalfHeight(renderer.getEyePos().distance(point))
				* PICK_PIXELS / Math.max(1, scene.getContentHeight());
	}

	private void selectGeometry(UIEvent event) {
		if (!event.isShiftDown()) {
			vertices.clear();
			edges.clear();
		}
		if (mode == SelectionMode.EDGE) {
			var hit = nearestEdge();
			if (hit != null && !edges.add(hit.edge())) {
				edges.remove(hit.edge());
			}
			vertices.clear();
			edges.forEach(edge -> {
				vertices.add(edge.first());
				vertices.add(edge.second());
			});
			return;
		}
		var ray = editor.sceneView.sceneEditor.getMouseRay().orElseThrow();
		var direction = new Vector3f(ray.getDirection()).normalize();
		int nearest = -1;
		float best = 1;
		for (int index : PhotonModelEditUtil.selectedVertices(mesh, part, Set.of(), Set.of(), SelectionMode.PART)) {
			var position = point(part, index, mesh);
			float t = new Vector3f(position).sub(ray.startPos()).dot(direction);
			float distance = position.distance(new Vector3f(direction).mul(Math.max(0, t)).add(ray.startPos()));
			float threshold = threshold(position);
			if (t >= 0 && threshold > 0 && distance / threshold <= best) {
				best = distance / threshold;
				nearest = index;
			}
		}
		if (nearest >= 0 && !vertices.add(nearest)) {
			vertices.remove(nearest);
		}
	}

	private void hover(UIEvent event) {
		if (!loop || !enabled || event.target != editor.sceneView.sceneEditor.scene) {
			return;
		}
		if (!revision.equals(session.revision()) || !part.equals(selectedPart.get())) {
			cancel();
			return;
		}
		loopPlan = null;
		var hit = nearestEdge();
		if (hit != null) {
			try {
				float ratio = loopUniform ? -1 : loopFollow ? Math.clamp(hit.ratio(), 0.01f, 0.99f) : cutPercent / 100;
				loopPlan = PhotonModelLoopCutUtil.plan(mesh, part, hit.edge(), ratio, cuts);
			} catch (IllegalArgumentException invalid) {
				editor.sceneView.sceneEditor.setScreenTips(Component.translatable(PhotonModelTranslations.LOOP_CUT_INVALID).getString());
			}
		}
	}

	private void updateGizmo() {
		var scene = editor.sceneView.sceneEditor;
		var selected = mesh == null ? Set.<Integer>of() : selectedVertices(mesh);
		if (closed || !enabled || loop || boxMode || part.isEmpty() || selected.isEmpty()) {
			if (scene.getTransformGizmo().getTargetTransform() == handle.transform()) {
				scene.disableTransformGizmo();
			}
			return;
		}
		partMatrix = world().mul(matrix(part, mesh));
		float determinant = partMatrix.determinant3x3();
		if (!partMatrix.isFinite() || !Float.isFinite(determinant) || determinant == 0) {
			if (scene.getTransformGizmo().getTargetTransform() == handle.transform()) {
				scene.disableTransformGizmo();
			}
			return;
		}
		var localCenter = pivot || mode == SelectionMode.PART ? mesh.parts.stream().filter(item -> item.id.equals(part)).findFirst().orElseThrow().pivot
				: PhotonModelEditUtil.center(mesh, selected);
		center = partMatrix.transformPosition(new Vector3f(localCenter));
		var basis = new Matrix4f(partMatrix);
		if (determinant < 0) {
			basis.scale(-1, 1, 1);
		}
		basis.getUnnormalizedRotation(orientation).normalize();
		if (!Float.isFinite(orientation.lengthSquared())) {
			scene.disableTransformGizmo();
			return;
		}
		handle.transform().position(new Vector3f(center));
		handle.transform().rotation(new Quaternionf(orientation));
		handle.transform().scale(new Vector3f(1));
		scene.setTransformGizmoTarget(handle.transform(), this::gesture);
		scene.enableTransformGizmo();
		if (scene.getTransformGizmoMode() == TransformGizmo.Mode.NONE) {
			scene.setTransformGizmoMode(TransformGizmo.Mode.TRANSLATE);
		}
	}

	private void gesture() {
		if (closed || !enabled || !revision.equals(session.revision()) || !part.equals(selectedPart.get())) {
			cancel();
			return;
		}
		if (gesture == null) {
			gesture = mesh.copy();
			gestureRevision = revision;
		}
		preview = null;
		try {
			var inverse = new Matrix4f(partMatrix).invert();
			if (!inverse.isFinite()) {
				return;
			}
			var candidate = gesture.copy();
			if (pivot) {
				PhotonModelEditUtil.movePartPivot(candidate, part, inverse.transformPosition(handle.transform().position()));
			} else {
				var delta = new Matrix4f().translation(handle.transform().position()).rotate(handle.transform().rotation())
						.scale(handle.transform().scale()).rotate(new Quaternionf(orientation).invert()).translate(new Vector3f(center).negate());
				if (mode == SelectionMode.PART) {
					var target = candidate.parts.stream().filter(value -> value.id.equals(part)).findFirst().orElseThrow();
					var parent = world();
					if (!target.parentId.isEmpty()) {
						parent.mul(matrix(target.parentId, gesture));
					}
					var local = new Matrix4f().translation(new Vector3f(target.pivot).negate())
							.mul(parent.invert()).mul(delta).mul(partMatrix).translate(target.pivot);
					target.transform = PhotonModelHierarchyUtil.decompose(local);
				} else {
					PhotonModelEditUtil.transformVertices(candidate, selectedVertices(gesture), selectedFaces(gesture), inverse.mul(delta).mul(partMatrix));
				}
			}
			candidate.validate();
			preview = candidate;
		} catch (IllegalArgumentException invalid) {
			editor.sceneView.sceneEditor.setScreenTips(Component.translatable(PhotonModelTranslations.EDITOR_INVALID_GEOMETRY).getString());
		}
	}

	private void finishGesture() {
		if (boxStart != null && boxEnd != null) {
			finishBox();
			return;
		}
		if (preview == null || gesture == null) {
			if (gesture != null) {
				cancel();
			}
			return;
		}
		var candidate = preview;
		String expected = gestureRevision;
		cancel();
		changeAt(expected, value -> {
            value.vertices = candidate.vertices;
			value.retainVertexSources(candidate);
			value.faces = candidate.faces;
			value.parts = candidate.parts;
			value.weights = candidate.weights;
			value.morphs = candidate.morphs;
		});
	}

	private void key(UIEvent event) {
		if (closed || !enabled || event.target != editor.sceneView.sceneEditor.scene) {
			return;
		}
		switch (event.keyCode) {
			case GLFW.GLFW_KEY_ESCAPE -> cancel();
			case GLFW.GLFW_KEY_A -> selectAll();
			case GLFW.GLFW_KEY_DELETE -> deleteSelection();
			default -> {
				return;
			}
		}
		event.stopPropagation();
	}

	public void cancel() {
		var scene = editor.sceneView.sceneEditor;
		if (scene.getTransformGizmo().getTargetTransform() == handle.transform()) {
			scene.disableTransformGizmo();
		}
		gesture = null;
		preview = null;
		gestureRevision = null;
		loopPlan = null;
		loop = false;
		boxMode = false;
		boxStart = null;
		boxEnd = null;
		if (!closed && mesh != null) {
			updateGizmo();
		}
	}

	private void dragBox(UIEvent event) {
		if (boxStart != null) {
			if (!revision.equals(session.revision())) {
				cancel();
				return;
			}
			boxEnd = new Vector2f(event.x, event.y);
			event.stopPropagation();
		}
	}

	private Vector2f project(Vector3f point) {
		if (!projectionReady) {
			return null;
		}
		var clip = projection.transform(new Vector4f(point, 1));
		if (!clip.isFinite() || clip.w <= 0) {
			return null;
		}
		var scene = editor.sceneView.sceneEditor.scene;
		return scene.localToWorld(new Vector2f(scene.getContentX() + (clip.x / clip.w + 1) * scene.getContentWidth() / 2,
				scene.getContentY() + (1 - clip.y / clip.w) * scene.getContentHeight() / 2));
	}

	private void finishBox() {
		if (!revision.equals(session.revision()) || !part.equals(selectedPart.get())) {
			cancel();
			return;
		}
		if (mode == SelectionMode.PART) {
			String selected = "";
			for (var face : mesh.faces) {
				boolean complete = true;
				for (int vertex : face.vertices) {
					complete &= insideBox(point(face.partId, vertex, mesh));
				}
				if (complete) {
					selected = face.partId;
					break;
				}
			}
			cancel();
			if (!selected.isEmpty()) {
				selectPart.accept(selected);
				refresh();
			}
			return;
		}
		var contained = new LinkedHashSet<Integer>();
		for (int vertex : PhotonModelEditUtil.selectedVertices(mesh, part, Set.of(), Set.of(), SelectionMode.PART)) {
			if (insideBox(point(part, vertex, mesh))) {
				contained.add(vertex);
			}
		}
		if (mode == SelectionMode.VERTEX) {
			vertices.addAll(contained);
		}
		for (int index : PhotonModelEditUtil.partFaces(mesh, part)) {
			var face = mesh.faces.get(index);
			boolean complete = true;
			for (int corner = 0; corner < face.vertices.length; corner++) {
				int first = face.vertices[corner];
				int second = face.vertices[(corner + 1) % face.vertices.length];
				complete &= contained.contains(first);
				if (mode == SelectionMode.EDGE && contained.contains(first) && contained.contains(second)) {
					edges.add(Edge.of(first, second));
				}
			}
			if (complete && mode == SelectionMode.FACE) {
				faces.add(index);
			}
		}
		cancel();
		updateGizmo();
	}

	private boolean insideBox(Vector3f position) {
		var point = project(position);
		return point != null && point.x >= Math.min(boxStart.x, boxEnd.x) && point.x <= Math.max(boxStart.x, boxEnd.x)
				&& point.y >= Math.min(boxStart.y, boxEnd.y) && point.y <= Math.max(boxStart.y, boxEnd.y);
	}

	@Override
	public void drawInternal(SceneRenderContext context) {
		if (!enabled || closed || mesh == null) {
			return;
		}
		projection.set(((WorldSceneRendererProjectionAccessor) context.renderer()).lobecorp$projectionMatrix()).mul(RenderSystem.getModelViewMatrix());
		projectionReady = projection.isFinite();
		var current = preview == null ? mesh : preview;
		var buffer = context.bufferSource().getBuffer(RenderTypes.lines());
		for (int index : PhotonModelEditUtil.partFaces(current, part)) {
			var face = current.faces.get(index);
			for (int corner = 0; corner < face.vertices.length; corner++) {
				var edge = Edge.of(face.vertices[corner], face.vertices[(corner + 1) % face.vertices.length]);
				int color = mode == SelectionMode.PART || faces.contains(index) || edges.contains(edge) ? SELECTED_COLOR : EDGE_COLOR;
				RenderBufferUtils.drawLines(context.poseStack(), buffer,
						List.of(point(part, edge.first(), current), point(part, edge.second(), current)), color, color, 2, 2);
			}
		}
		for (int vertex : selectedVertices(current)) {
			var point = point(part, vertex, current);
			float radius = threshold(point) / 3;
			RenderBufferUtils.drawLines(context.poseStack(), buffer,
					List.of(new Vector3f(point).add(-radius, 0, 0), new Vector3f(point).add(radius, 0, 0)), SELECTED_COLOR, SELECTED_COLOR, 3, 3);
		}
		if (loopPlan != null) {
			var matrix = world().mul(matrix(part, mesh));
			for (var segment : loopPlan.segments()) {
				RenderBufferUtils.drawLines(context.poseStack(), buffer,
						List.of(matrix.transformPosition(segment.first()), matrix.transformPosition(segment.second())), SELECTED_COLOR, SELECTED_COLOR, 3, 3);
			}
		}
		context.bufferSource().endLastBatch();
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		for (var dialog : List.copyOf(dialogs)) {
			dialog.close();
		}
		dialogs.clear();
		cancel();
		var scene = editor.sceneView.sceneEditor;
		scene.removeEventListener(UIEvents.MOUSE_DOWN, down, true);
		scene.removeEventListener(UIEvents.MOUSE_MOVE, move);
		scene.removeEventListener(UIEvents.MOUSE_UP, up);
        scene.removeEventListener(UIEvents.DRAG_END, end);
		scene.removeEventListener(UIEvents.KEY_DOWN, keys, true);
		scene.removeEventListener(UIEvents.DRAG_SOURCE_UPDATE, drag);
		if (scene.getTransformGizmo().getTargetTransform() == handle.transform()) {
			scene.setTransformGizmoTarget((ITransform) null);
		}
		scene.removeSceneObject(this);
		boxOverlay.removeSelf();
	}
}
