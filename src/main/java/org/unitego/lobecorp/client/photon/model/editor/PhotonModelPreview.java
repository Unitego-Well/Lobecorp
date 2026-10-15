package org.unitego.lobecorp.client.photon.model.editor;

import com.lowdragmc.lowdraglib2.client.scene.SceneRenderContext;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.client.shader.LDLibRenderPipelines;
import com.lowdragmc.lowdraglib2.client.utils.RenderBufferUtils;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.SceneEditor;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.SceneObject;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.ISceneRendering;
import com.lowdragmc.lowdraglib2.math.ITransform;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;

import java.util.Optional;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.utils.TransformGizmo;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.unitego.lobecorp.Lobecorp;
import org.joml.Intersectionf;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Part;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelPolygonUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil.SampleParameters;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil.SampledModel;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelLoopCutUtil;

import java.util.function.Consumer;

import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.SelectionMode;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Edge;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import org.unitego.lobecorp.util.photon.model.editor.PhotonModelSnapUtil;

import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class PhotonModelPreview extends SceneObject implements ISceneRendering {
	/// 视口选点容差，单位为 GUI 像素。
	private static final float PICK_RADIUS = 7f;
	/// 环切端点留白比例，避免生成重合顶点和零面积面。
	private static final float LOOP_END_MARGIN = 0.01f;
	/// 底部操作提示的高度与留白，单位为 GUI 像素，避开轴向与场景信息浮层。
	private static final float SCREEN_TIPS_HEIGHT = 36f;
	private static final float SCREEN_TIPS_BOTTOM = 32f;
	/// 射线与三角形平行判定容差。
	private static final float RAY_EPSILON = 0.00001f;
	/// 已选部件或面的预览颜色。
	private static final int SELECTED_COLOR = 0xE0FFBA50;
	/// 已选边使用不透明橙色，增强白模表面的选中对比。
	private static final int SELECTED_EDGE_COLOR = 0xFFFF8800;
	/// 几何模式下所属部件的轮廓颜色，半透明以突出已选元素。
	private static final int PART_OUTLINE_COLOR = 0xB0FFBA50;
	/// 未选网格与顶点的辅助颜色，透明度约为四分之一。
	private static final int UNSELECTED_COLOR = 0x40A0D8FF;
	/// 未选骨骼使用半透明青色，穿过模型表面仍可辨认。
	private static final int BONE_COLOR = 0xB0A0D8FF;
	/// 父子关节重合判定容差，单位为模型坐标，避免零长度线产生无效法线。
	private static final float BONE_LINK_EPSILON = 0.00001f;
	/// 已选顶点与边端点的黄色标记。
	private static final int SELECTED_POINT_COLOR = 0xFFFFFF00;
	/// 部件轴心使用青色标记，区别于黄色几何选点。
	private static final int PIVOT_COLOR = 0xFF40FFFF;
	/// 轴心十字在模型坐标中的半长，保证可与普通顶点区分。
	private static final float PIVOT_RADIUS = 0.06f;
	/// 已选面的半透明覆盖颜色，不修改真实材质。
	private static final int SELECTED_FACE_COLOR = 0x30FFBA50;
	/// 选点标记的模型坐标半径。
	private static final float POINT_RADIUS = 0.015f;
	/// 预览边线宽度，26.1 原生线管线以屏幕像素解释。
	private static final float EDGE_WIDTH = 1f;
	/// 边编辑模式突出可操作轮廓，单位为屏幕像素。
	private static final float EDIT_EDGE_WIDTH = 2.5f;
	/// 已选边进一步加粗，单位为屏幕像素。
	private static final float SELECTED_EDGE_WIDTH = 4f;
	/// 沿用原生线着色器，辅助轮廓不参与模型深度写入。
	private static final RenderType SELECTION_LINES = RenderType.create(
			Lobecorp.id("model_selection_lines").toString(),
			RenderSetup.builder(RenderTypes.linesTranslucent().pipeline().toBuilder()
					.withLocation(Lobecorp.id("pipeline/model_selection_lines"))
					.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
					.build()).bufferSize(256).createRenderSetup());
	/// 复用原生三角标记管线并关闭深度写入，避免选择辅助物遮挡真实模型。
	private static final RenderType SELECTION_MARKERS = RenderType.create(
			Lobecorp.id("model_selection_markers").toString(),
			RenderSetup.builder(LDLibRenderPipelines.POSITION_COLOR_NO_DEPTH.toBuilder()
					.withLocation(Lobecorp.id("pipeline/model_selection_markers"))
					.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
					.build()).sortOnUpload().bufferSize(256).createRenderSetup());

	private final PhotonModelEditSession session;
	private final SceneEditor editor;
	private final PhotonModelEmitter emitter;
	private final UIElement boxOverlay = new UIElement() {
		@Override
		protected void drawBackgroundAdditional(IGUIContext context) {
			drawBox(context);
		}
	};
	private final UIEventListener mouseDown = this::onMouseDown;
	private final UIEventListener boxMouseDown = this::onBoxMouseDown;
	private final UIEventListener mouseUp = this::onMouseUp;
	private final UIEventListener mouseDrag = this::onMouseDrag;
	private final UIEventListener loopMove = this::onLoopMove;
	private Consumer<PhotonModelLoopCutUtil.Plan> loopApply;
	private PhotonModelLoopCutUtil.Plan loopPlan;
	private float loopRatio;
	private int loopCount;
	private boolean loopFollowMouse;
	private String loopTips = "";

	private record EdgeHover(Edge edge, float ratio) {
	}

	private boolean attached;
	private final SceneObject handle = new SceneObject();
	private PhotonModelAsset gizmoBefore;
	private Set<Integer> gizmoVertices = Set.of();
	private Set<Integer> gizmoFaces = Set.of();
	private Runnable selectionPicked = () -> {
	};
	private Vector3f gizmoCenter = new Vector3f();
	private Matrix4f gizmoPartMatrix = new Matrix4f();
	private Matrix4f gizmoPartInverse = new Matrix4f();
	private PhotonModelPartTransform partTransform;
	private String gizmoPartId = "";
	private boolean editingPivot;
	private long sampledRevision = -1;
	private float sampledTime = Float.NaN;
	private String sampledClip = "";
	private boolean sampledPose;
	private boolean sampledPlaying;
	private SampledModel sampled;
	private boolean animationMode;
	private boolean boxMode;
	private Vector2f boxStart;
	private Vector2f boxEnd;
	private String morphPreview = "";
	private final Matrix4f viewProjection = new Matrix4f();
	private boolean projectionReady;
	private Vector3f directDragStart;
	private Vector3f directDragNormal;

	/// 同一资产边在不同部件中可显示在不同位置，必须分别保留。
	private record PartEdge(String partId, Edge edge) {
	}

	/// 选择与几何手柄绑定原生场景编辑器，不再创建第二个视口。
	public PhotonModelPreview(PhotonModelEditSession session, SceneEditor editor, PhotonModelEmitter emitter) {
		this.session = session;
		this.editor = editor;
		this.emitter = emitter;
	}

	public void initialize() {
		if (attached) {
			return;
		}

		attached = true;
		editor.addSceneObject(this);
		transform().position(emitter.transform().position());
		transform().rotation(emitter.transform().rotation());
		transform().scale(emitter.transform().scale());
		handle.transform().parent(transform(), false);
		boxOverlay.setAllowHitTest(false).layout(layout -> {
			layout.positionType(TaffyPosition.ABSOLUTE);
			layout.widthPercent(100);
			layout.heightPercent(100);
		});
		editor.addChild(boxOverlay);
		// 锁定版本未挂接原生提示控件；放在场景之后，避免延迟场景绘制覆盖文本。
		editor.screenTips.setAllowHitTest(false).layout(layout -> layout.left(0).bottom(SCREEN_TIPS_BOTTOM)
				.widthPercent(100).height(SCREEN_TIPS_HEIGHT));
		editor.screenTips.textStyle(style -> style.adaptiveWidth(false).textWrap(TextWrap.WRAP));
		editor.addChild(editor.screenTips);
		editor.addEventListener(UIEvents.MOUSE_DOWN, boxMouseDown, true);
		editor.addEventListener(UIEvents.MOUSE_DOWN, mouseDown);
		editor.addEventListener(UIEvents.MOUSE_UP, mouseUp);
		editor.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, mouseDrag);
		editor.addEventListener(UIEvents.MOUSE_MOVE, loopMove);
		updateSelectionGizmo();
	}

	public void close() {
		if (!attached) {
			return;
		}

		attached = false;
		projectionReady = false;
		directDragStart = null;
		editor.removeEventListener(UIEvents.MOUSE_DOWN, boxMouseDown, true);
		editor.removeEventListener(UIEvents.MOUSE_DOWN, mouseDown);
		editor.removeEventListener(UIEvents.MOUSE_UP, mouseUp);
		editor.removeEventListener(UIEvents.DRAG_SOURCE_UPDATE, mouseDrag);
		editor.removeEventListener(UIEvents.MOUSE_MOVE, loopMove);
		cancelLoopCut();
		editor.setTransformGizmoTarget((ITransform) null);
		editor.disableTransformGizmo();
		editor.removeSceneObject(this);
		boxOverlay.removeSelf();
		editor.screenTips.removeSelf();
		handle.transform().parent(null, false);
	}

	public TransformGizmo getTransformGizmo() {
		return editor.getTransformGizmo();
	}

	public boolean isEditingGeometry() {
		return directDragStart != null || editor.getTransformGizmo().isDragging();
	}

	public void setAnimationMode(boolean animationMode) {
		cancelLoopCut();

		if (animationMode) {
			setEditingPivot(false);
		}

		this.animationMode = animationMode;
		sampledRevision = -1;
		updateSelectionGizmo();
	}

	public void setSelectionPicked(Runnable listener) {
		selectionPicked = listener;
	}

	public void boxSelection() {
		boxMode = true;
	}

	public void previewMorph(String id) {
		morphPreview = id;
		sampledRevision = -1;
	}

	public boolean isEditingPivot() {
		return editingPivot;
	}

	public void setEditingPivot(boolean editingPivot) {
		finishGizmoEdit();
		directDragStart = null;
		this.editingPivot = editingPivot && !animationMode && session.part() != null
				&& session.selectionMode == SelectionMode.PART;

		if (this.editingPivot) {
			editor.setTransformGizmoMode(TransformGizmo.Mode.TRANSLATE);
		}

		updateSelectionGizmo();
	}

	@Override
	public void updateFrame(float partialTicks) {
		super.updateFrame(partialTicks);

		if (editingPivot && editor.getTransformGizmoMode() != TransformGizmo.Mode.TRANSLATE) {
			finishGizmoEdit();
			editingPivot = false;
			session.selectionChanged();
		}

		if (gizmoBefore != null && directDragStart == null && !editor.getTransformGizmo().isDragging()) {
			finishGizmoEdit();
		}
	}

	public void updateSelectionGizmo() {
		partTransform = null;
		boolean partMode = session.selectionMode == SelectionMode.PART;

		if (!partMode) {
			editingPivot = false;
		}

		if (animationMode || loopApply != null || session.part() == null
				|| !partMode && session.vertices().isEmpty()) {
			editor.disableTransformGizmo();
			return;
		}

		var matrices = PhotonModelSamplerUtil.basePartMatrices(session.asset());
		var matrix = matrices.get(session.selectedPart);

		if (matrix == null) {
			editor.disableTransformGizmo();
			return;
		}

		var inverse = new Matrix4f(matrix).invert();
		gizmoPartMatrix = matrix;
		gizmoPartId = session.selectedPart;

		if (!inverse.isFinite() && (editingPivot || !partMode)) {
			editor.disableTransformGizmo();
			editor.setScreenTips(Component.translatable(PhotonModelTextUtil.key("editor.singular_part_transform")).getString());
			return;
		}

		gizmoPartInverse = inverse;
		gizmoCenter = new Vector3f();

		if (partMode) {
			matrix.transformPosition(session.part().pivot, gizmoCenter);
		} else {
			for (int vertex : session.vertices()) {
				gizmoCenter.add(partVertex(session.selectedPart, vertex));
			}

			gizmoCenter.div(session.vertices().size());
		}

		if (partMode && !editingPivot) {
			var part = session.part();
			var parentMatrix = new Matrix4f(emitter.transform().localToWorldMatrix());

			if (!part.parentId.isEmpty()) {
				parentMatrix.mul(matrices.get(part.parentId));
			}

			if (!new Matrix4f(parentMatrix).invert().isFinite()) {
				editor.disableTransformGizmo();
				editor.setScreenTips(Component.translatable(PhotonModelTextUtil.key("editor.singular_part_transform")).getString());
				return;
			}

			partTransform = new PhotonModelPartTransform(part, parentMatrix, parentRotation(part));
			editor.setTransformGizmoTarget(partTransform, this::updateGizmoEdit);
		} else {
			handle.transform().localPosition(new Vector3f(gizmoCenter));
			handle.transform().localRotation(new Quaternionf());
			handle.transform().localScale(new Vector3f(1));
			editor.setTransformGizmoTarget(handle.transform(), this::updateGizmoEdit);
		}

		editor.enableTransformGizmo();

		if (editor.getTransformGizmoMode() == TransformGizmo.Mode.NONE) {
			editor.setTransformGizmoMode(TransformGizmo.Mode.TRANSLATE);
		}
	}

	/// 与原生 Transform 一致，只沿父链组合旋转，不从含非均匀缩放的矩阵提取四元数。
	private Quaternionf parentRotation(Part part) {
		var rotation = new Quaternionf();
		String parentId = part.parentId;

		while (!parentId.isEmpty()) {
			String id = parentId;
			var parent = session.asset().parts.stream().filter(current -> current.id.equals(id)).findFirst().orElseThrow();
			rotation.premul(new Quaternionf(parent.transform.rotation).normalize());
			parentId = parent.parentId;
		}

		return emitter.transform().rotation().mul(rotation).normalize();
	}

	private void updateGizmoEdit() {
		if (editingPivot && editor.getTransformGizmoMode() != TransformGizmo.Mode.TRANSLATE) {
			setEditingPivot(false);
			session.selectionChanged();
			return;
		}

		if (gizmoBefore == null) {
			gizmoBefore = session.asset().copy();
			gizmoVertices = session.vertices();
			gizmoFaces = Set.copyOf(session.selectedFaces);
			session.beginLiveEdit();
		}

		if (partTransform != null) {
			updatePartTransform();
			return;
		}

		var translation = new Vector3f(handle.transform().localPosition()).sub(gizmoCenter);
		float step = PhotonModelSnapUtil.modelStep(UIElement.isShiftDown(), UIElement.isControlDown());

		if ((directDragStart != null || editor.getTransformGizmoMode() == TransformGizmo.Mode.TRANSLATE) && step > 0) {
			translation.set(PhotonModelSnapUtil.snap(translation.x, step),
					PhotonModelSnapUtil.snap(translation.y, step), PhotonModelSnapUtil.snap(translation.z, step));
			handle.transform().localPosition(new Vector3f(gizmoCenter).add(translation));
		}

		if (editingPivot) {
			var candidate = gizmoBefore.copy();
			var part = candidate.parts.stream().filter(current -> current.id.equals(gizmoPartId)).findFirst().orElseThrow();
			var pivot = gizmoPartInverse.transformPosition(new Vector3f(gizmoCenter).add(translation));
			PhotonModelEditUtil.movePartPivot(candidate, part.id, pivot);
			publishParts(candidate);
			return;
		}

		var rotation = new Quaternionf(handle.transform().localRotation());
		var scale = new Vector3f(handle.transform().localScale());
		var delta = new Matrix4f().translation(gizmoCenter).translate(translation).rotate(rotation).scale(scale)
				.translate(new Vector3f(gizmoCenter).negate());
		var transform = new Matrix4f(gizmoPartInverse).mul(delta).mul(gizmoPartMatrix);
		var candidate = gizmoBefore.copy();
		var faces = new LinkedHashSet<>(gizmoFaces);

		try {
			PhotonModelEditUtil.transformVertices(candidate, gizmoVertices, faces, transform);
			candidate.validate();
		} catch (IllegalArgumentException error) {
			editor.setScreenTips(Component.translatable(PhotonModelTextUtil.key("editor.invalid_geometry")).getString());
			return;
		}

		// 每帧从拖动前的完整几何生成，验证成功后才发布，避免半更新面进入渲染。
		session.liveEdit(current -> {
			current.vertices = candidate.vertices;
			current.faces = candidate.faces;
		});
		session.selectedFaces.clear();
		session.selectedFaces.addAll(faces);
	}

	private void updatePartTransform() {
		if (directDragStart != null || editor.getTransformGizmoMode() == TransformGizmo.Mode.TRANSLATE) {
			float step = PhotonModelSnapUtil.modelStep(UIElement.isShiftDown(), UIElement.isControlDown());

			if (step > 0) {
				var point = emitter.transform().worldToLocalMatrix().transformPosition(partTransform.position());
				var delta = point.sub(gizmoCenter);
				delta.set(PhotonModelSnapUtil.snap(delta.x, step), PhotonModelSnapUtil.snap(delta.y, step),
						PhotonModelSnapUtil.snap(delta.z, step));
				partTransform.position(world(new Vector3f(gizmoCenter).add(delta)));
			}
		}

		var candidate = gizmoBefore.copy();
		var part = candidate.parts.stream().filter(current -> current.id.equals(gizmoPartId)).findFirst().orElseThrow();
		partTransform.applyTo(part);
		publishParts(candidate);
	}

	private void publishParts(PhotonModelAsset candidate) {
		try {
			candidate.validate();
		} catch (IllegalArgumentException error) {
			editor.setScreenTips(Component.translatable(PhotonModelTextUtil.key("editor.invalid_geometry")).getString());
			return;
		}

		session.liveEdit(current -> current.parts = candidate.parts);
	}

	private void onBoxMouseDown(UIEvent event) {
		if (event.button == 1 && (editor.getTransformGizmo().isDragging() || directDragStart != null)) {
			editor.getTransformGizmo().cancelDrag();
			directDragStart = null;
			gizmoBefore = null;
			session.cancelLiveEdit();
			updateSelectionGizmo();
			event.stopImmediatePropagation();
			return;
		}

		if (loopApply != null && event.button == 0 && event.target == editor.scene && !animationMode) {
			onLoopMove(event);
			if (loopPlan != null) {
				var apply = loopApply;
				var plan = loopPlan;
				cancelLoopCut();
				apply.accept(plan);
			}
			event.stopImmediatePropagation();
			return;
		}

		if (boxMode && event.button == 0 && event.target == editor.scene && !animationMode) {
			boxStart = new Vector2f(event.x, event.y);
			boxEnd = new Vector2f(boxStart);
			editor.startDrag(this, null);
			event.stopImmediatePropagation();
		}
	}

	private void onMouseDown(UIEvent event) {
		if (animationMode || event.button != 0 || event.target != editor.scene || event.propagationStopped) {
			return;
		}

		if (pick(event) && !editingPivot && !session.vertices().isEmpty()) {
			var renderer = editor.scene.<WorldSceneRenderer>getRenderer();
			if (renderer != null) {
				directDragNormal = new Vector3f(renderer.getLookAt()).sub(renderer.getEyePos()).normalize();
				directDragStart = dragPlanePoint(event);
				if (directDragStart != null) {
					editor.focus();
					editor.startDrag(this, null);
					event.stopPropagation();
				}
			}
		}
	}

	public boolean pick(UIEvent event) {
		boolean hit;
		if (session.selectionMode == SelectionMode.VERTEX) {
			hit = selectVertex(event);
		} else if (session.selectionMode == SelectionMode.EDGE) {
			hit = selectEdge(event);
		} else {
			hit = selectFace(event);
		}

		updateSelectionGizmo();
		session.selectionChanged();
		if (hit) {
			selectionPicked.run();
		}
		return hit;
	}

	private Optional<Vector2f> project(Vector3f point) {
		if (!projectionReady) {
			return Optional.empty();
		}

		var clip = viewProjection.transform(new Vector4f(world(point), 1));
		if (!clip.isFinite() || clip.w <= 0) {
			return Optional.empty();
		}

		var scene = editor.scene;
		float x = scene.getContentX() + (clip.x / clip.w + 1) * scene.getContentWidth() / 2;
		float y = scene.getContentY() + (1 - clip.y / clip.w) * scene.getPaddingHeight() / 2;
		return Optional.of(scene.localToWorld(new Vector2f(x, y)));
	}

	/// 在真实场景绘制阶段捕获矩阵，事件处理阶段不读取已恢复成 GUI 的渲染矩阵。
	public void captureProjection(Matrix4f matrix) {
		viewProjection.set(matrix);
		projectionReady = matrix.isFinite();
	}

	private Vector3f world(Vector3f point) {
		return emitter.transform().localToWorldMatrix().transformPosition(new Vector3f(point));
	}

	private Vector3f partVertex(String partId, int vertex) {
		return sampled().partVertices().get(partId).get(vertex);
	}

	private boolean selectEdge(UIEvent event) {
		var hover = edgeAt(event);
		Edge nearest = hover == null ? null : hover.edge();
		if (!event.isShiftDown()) {
			session.selectedEdges.clear();
		}

		if (nearest != null && !session.selectedEdges.add(nearest)) {
			session.selectedEdges.remove(nearest);
		}

		edgeVertices();
		return nearest != null;
	}

	/// 环切悬停只计算候选网格，点击后由编辑器提交一条历史记录。
	public void beginLoopCut(float ratio, int count, boolean followMouse, Consumer<PhotonModelLoopCutUtil.Plan> apply) {
		cancelLoopCut();
		loopRatio = ratio;
		loopCount = count;
		loopFollowMouse = followMouse;
		loopApply = apply;
		loopTips = PhotonModelTextUtil.key("loop_cut.help");
		editor.disableTransformGizmo();
		editor.focus();
		editor.setScreenTips(loopTips);
	}

	public boolean isLoopCutActive() {
		return loopApply != null;
	}

	@Override
	public void updateTick() {
		if (isLoopCutActive()) {
			editor.setScreenTips(loopTips);
		}
	}

	public void cancelLoopCut() {
		loopApply = null;
		loopPlan = null;
		loopTips = "";
		if (attached) {
			editor.setScreenTips("");
			updateSelectionGizmo();
		}
	}

	private void onLoopMove(UIEvent event) {
		if (loopApply == null || animationMode || event.target != editor.scene) {
			return;
		}
		var hover = edgeAt(event);
		loopPlan = null;
		loopTips = PhotonModelTextUtil.key("loop_cut.help");
		if (hover == null) {
			editor.setScreenTips(loopTips);
			return;
		}
		float ratio = loopFollowMouse ? Math.clamp(hover.ratio(), LOOP_END_MARGIN, 1 - LOOP_END_MARGIN) : loopRatio;
		try {
			loopPlan = PhotonModelLoopCutUtil.plan(session.asset(), session.selectedPart, hover.edge(), ratio, loopCount);
		} catch (IllegalArgumentException error) {
			loopTips = PhotonModelTextUtil.key("loop_cut.invalid");
		}
		editor.setScreenTips(loopTips);
	}

	private EdgeHover edgeAt(UIEvent event) {
		EdgeHover nearest = null;
		float closest = PICK_RADIUS * PICK_RADIUS;
		var asset = session.asset();
		for (int index : PhotonModelEditUtil.partFaces(asset, session.selectedPart)) {
			var face = asset.faces.get(index);
			for (int corner = 0; corner < face.vertices.length; corner++) {
				var edge = Edge.of(face.vertices[corner], face.vertices[(corner + 1) % face.vertices.length]);
				var first = project(partVertex(face.partId, edge.first()));
				var second = project(partVertex(face.partId, edge.second()));
				if (first.isEmpty() || second.isEmpty()) {
					continue;
				}
				var segment = new Vector2f(second.get()).sub(first.get());
				float ratio = segment.lengthSquared() == 0 ? 0 : Math.clamp(
						new Vector2f(event.x, event.y).sub(first.get()).dot(segment) / segment.lengthSquared(), 0, 1);
				float distance = new Vector2f(segment).mul(ratio).add(first.get()).distanceSquared(event.x, event.y);
				if (distance < closest) {
					closest = distance;
					nearest = new EdgeHover(edge, perspectiveEdgeRatio(face.partId, edge, ratio));
				}
			}
		}
		return nearest;
	}

	/// 屏幕比例不能直接用于透视投影下的模型边插值。
	private float perspectiveEdgeRatio(String partId, Edge edge, float screenRatio) {
		float firstW = viewProjection.transform(new Vector4f(world(partVertex(partId, edge.first())), 1)).w;
		float secondW = viewProjection.transform(new Vector4f(world(partVertex(partId, edge.second())), 1)).w;
		return screenRatio * firstW / ((1 - screenRatio) * secondW + screenRatio * firstW);
	}

	private void drawLoopCut(SceneRenderContext context) {
		if (loopPlan == null) {
			return;
		}
		var matrix = PhotonModelSamplerUtil.basePartMatrices(session.asset()).get(loopPlan.partId());
		if (matrix == null) {
			return;
		}
		var buffer = context.bufferSource().getBuffer(SELECTION_LINES);
		for (var segment : loopPlan.segments()) {
			RenderBufferUtils.drawLines(context.poseStack(), buffer,
					List.of(matrix.transformPosition(segment.first()), matrix.transformPosition(segment.second())),
					SELECTED_POINT_COLOR, SELECTED_POINT_COLOR, SELECTED_EDGE_WIDTH, SELECTED_EDGE_WIDTH);
		}
		context.bufferSource().endLastBatch();
	}


	private void edgeVertices() {
		session.selectedVertices.clear();
		session.selectedEdges.forEach(edge -> {
			session.selectedVertices.add(edge.first());
			session.selectedVertices.add(edge.second());
		});
	}

	private boolean selectVertex(UIEvent event) {
		float closest = PICK_RADIUS * PICK_RADIUS;
		int found = -1;

		for (int index : PhotonModelEditUtil.selectedVertices(session.asset(), session.selectedPart,
				Set.of(), Set.of(), SelectionMode.PART)) {
			var point = project(partVertex(session.selectedPart, index));

			if (point.isPresent()) {
				float distance = point.get().distanceSquared(event.x, event.y);

				if (distance < closest) {
					closest = distance;
					found = index;
				}
			}
		}

		if (!event.isShiftDown()) {
			session.selectedVertices.clear();
		}

		if (found >= 0 && !session.selectedVertices.add(found)) {
			session.selectedVertices.remove(found);
		}
		return found >= 0;
	}

	private boolean selectFace(UIEvent event) {
		var ray = editor.getMouseRay();

		if (ray.isEmpty()) {
			return false;
		}

		float closest = Float.POSITIVE_INFINITY;
		int found = -1;
		var asset = session.asset();

		for (int index = 0; index < asset.faces.size(); index++) {
			var face = asset.faces.get(index);

			if (session.selectionMode == SelectionMode.FACE && !face.partId.equals(session.selectedPart)) {
				continue;
			}

			var triangles = PhotonModelPolygonUtil.triangulate(asset.vertices, face.vertices);

			for (int i = 0; i < triangles.length; i += 3) {
				float distance = Intersectionf.intersectRayTriangle(ray.get().startPos(), ray.get().getDirection(),
						world(partVertex(face.partId, face.vertices[triangles[i]])), world(partVertex(face.partId, face.vertices[triangles[i + 1]])),
						world(partVertex(face.partId, face.vertices[triangles[i + 2]])), RAY_EPSILON);

				if (distance >= 0 && distance < closest) {
					closest = distance;
					found = index;
				}
			}
		}

		if (session.selectionMode == SelectionMode.PART && found >= 0) {
			session.selectedPart = asset.faces.get(found).partId;
		} else {
			if (!event.isShiftDown()) {
				session.selectedFaces.clear();
			}

			if (found >= 0 && !session.selectedFaces.add(found)) {
				session.selectedFaces.remove(found);
			}
		}
		return found >= 0;
	}

	private SampledModel sampled() {
		if (sampled == null || sampledRevision != session.revision() || sampledTime != session.time
				|| sampledPose != animationMode || sampledPlaying != session.playing
				|| !sampledClip.equals(session.selectedClip)) {
			var asset = session.asset();

			if (!animationMode) {
				asset = asset.copy();
				asset.weights.clear();
				asset.morphs.forEach(morph -> morph.weight = 0);
				asset.parts.forEach(part -> {
					part.anchor = PhotonModelAsset.Anchor.NONE;
					part.stretch.enabled = false;
				});
			}

			var parameters = new SampleParameters();
			parameters.loop = session.playing;

			if (animationMode) {
				parameters.partOverrides.putAll(session.partPose);
				parameters.boneOverrides.putAll(session.bonePose);
				parameters.morphOverrides.putAll(session.morphPose);
			}

			if (animationMode && !morphPreview.isEmpty()) {
				parameters.morphOverrides.put(morphPreview, 1f);
			}

			sampled = PhotonModelSamplerUtil.sample(asset, animationMode ? session.selectedClip : "", session.time, parameters);
			sampledRevision = session.revision();
			sampledTime = session.time;
			sampledPose = animationMode;
			sampledPlaying = session.playing;
			sampledClip = session.selectedClip;
		}

		return sampled;
	}

	@Override
	public void draw(SceneRenderContext context) {
		transform().position(emitter.transform().position());
		transform().rotation(emitter.transform().rotation());
		transform().scale(emitter.transform().scale());
		context.poseStack().pushPose();
		context.poseStack().mulPose(emitter.transform().localToWorldMatrix());
		try {
			drawInternal(context);
		} catch (IllegalArgumentException error) {
			editor.setScreenTips(Component.translatable(PhotonModelTextUtil.key("editor.invalid_geometry")).getString());
		} finally {
			context.poseStack().popPose();
		}
	}

	/// 表面交给真实 Photon 发射器；场景对象只提交编辑辅助标记。
	@Override
	public void drawInternal(SceneRenderContext context) {
		if (!animationMode) {
			drawSelection(context);
			drawPivot(context);
			drawLoopCut(context);
		}

		drawBones(context);
	}

	private void drawPivot(SceneRenderContext context) {
		if (session.selectionMode != SelectionMode.PART || session.part() == null
				|| !gizmoPartId.equals(session.selectedPart)) {
			return;
		}

		var point = partTransform != null
				? emitter.transform().worldToLocalMatrix().transformPosition(partTransform.position())
				: editingPivot ? new Vector3f(handle.transform().localPosition())
				: gizmoPartMatrix.transformPosition(session.part().pivot, new Vector3f());

		if (!point.isFinite()) {
			return;
		}

		var lines = context.bufferSource().getBuffer(SELECTION_LINES);

		for (int axis = 0; axis < 3; axis++) {
			var offset = new Vector3f().setComponent(axis, PIVOT_RADIUS);
			RenderBufferUtils.drawLines(context.poseStack(), lines,
					List.of(new Vector3f(point).sub(offset), new Vector3f(point).add(offset)),
					PIVOT_COLOR, PIVOT_COLOR, EDIT_EDGE_WIDTH, EDIT_EDGE_WIDTH);
		}

		drawPoint(context, context.bufferSource().getBuffer(SELECTION_MARKERS), point, PIVOT_COLOR);
		context.bufferSource().endLastBatch();
	}

	/// 骨骼使用网格采样的同一姿态与时钟，关节点和父子连线不写模型深度。
	private void drawBones(SceneRenderContext context) {
		if (session.asset().bones.isEmpty()) {
			return;
		}

		var matrices = sampled().boneWorldMatrices();
		var positions = new LinkedHashMap<String, Vector3f>();
		matrices.forEach((id, matrix) -> positions.put(id, matrix.getTranslation(new Vector3f())));
		var lineBuffer = context.bufferSource().getBuffer(SELECTION_LINES);

		for (var bone : session.asset().bones) {
			var point = positions.get(bone.id);
			var parent = positions.get(bone.parentId);

			if (point == null || parent == null || !point.isFinite() || !parent.isFinite()
					|| point.distanceSquared(parent) <= BONE_LINK_EPSILON * BONE_LINK_EPSILON) {
				continue;
			}

			boolean selected = bone.id.equals(session.selectedBone);
			int color = selected ? SELECTED_EDGE_COLOR : BONE_COLOR;
			float width = selected ? SELECTED_EDGE_WIDTH : EDIT_EDGE_WIDTH;
			RenderBufferUtils.drawLines(context.poseStack(), lineBuffer, List.of(parent, point),
					color, color, width, width);
		}

		var markerBuffer = context.bufferSource().getBuffer(SELECTION_MARKERS);

		for (var entry : positions.entrySet()) {
			if (!entry.getKey().equals(session.selectedBone) && entry.getValue().isFinite()) {
				drawPoint(context, markerBuffer, entry.getValue(), BONE_COLOR);
			}
		}

		context.bufferSource().endLastBatch();

		// 重合关节最后绘制选中项，避免新建的同位置骨骼盖住选中反馈。
		var selectedPoint = positions.get(session.selectedBone);

		if (selectedPoint != null && selectedPoint.isFinite()) {
			drawPoint(context, context.bufferSource().getBuffer(SELECTION_MARKERS), selectedPoint, SELECTED_EDGE_COLOR);
			context.bufferSource().endLastBatch();
		}
	}

	@Nullable
	public SampledModel sample(float time) {
		session.time = time;

		try {
			return sampled();
		} catch (RuntimeException error) {
			editor.setScreenTips(Component.translatable(PhotonModelTextUtil.key("error"), String.valueOf(error.getMessage())).getString());
			return null;
		}
	}

	private void drawSelection(SceneRenderContext context) {
		var asset = session.asset();
		var edges = new LinkedHashMap<PartEdge, Integer>();

		for (int index = 0; index < asset.faces.size(); index++) {
			var face = asset.faces.get(index);
			int color = UNSELECTED_COLOR;

			if (face.partId.equals(session.selectedPart)) {
				color = session.selectionMode == SelectionMode.PART ? SELECTED_COLOR : PART_OUTLINE_COLOR;
			}

			if (session.selectionMode == SelectionMode.FACE && session.selectedFaces.contains(index)) {
				color = SELECTED_COLOR;
			}

			for (int corner = 0; corner < face.vertices.length; corner++) {
				var edge = Edge.of(face.vertices[corner], face.vertices[(corner + 1) % face.vertices.length]);
				int edgeColor = session.selectionMode == SelectionMode.EDGE && face.partId.equals(session.selectedPart)
						&& session.selectedEdges.contains(edge)
						? SELECTED_EDGE_COLOR : color;
				edges.merge(new PartEdge(face.partId, edge), edgeColor,
						(first, second) -> first >>> 24 >= second >>> 24 ? first : second);
			}
		}

		var lineBuffer = context.bufferSource().getBuffer(SELECTION_LINES);

		for (var entry : edges.entrySet()) {
			var partEdge = entry.getKey();
			var edge = partEdge.edge();
			int color = entry.getValue();
			float width = session.selectionMode == SelectionMode.EDGE ? EDIT_EDGE_WIDTH : EDGE_WIDTH;

			if (session.selectionMode == SelectionMode.EDGE && partEdge.partId().equals(session.selectedPart)
					&& session.selectedEdges.contains(edge)) {
				width = SELECTED_EDGE_WIDTH;
			}

			RenderBufferUtils.drawLines(context.poseStack(), lineBuffer,
					List.of(partVertex(partEdge.partId(), edge.first()), partVertex(partEdge.partId(), edge.second())),
					color, color, width, width);
		}

		var markerBuffer = context.bufferSource().getBuffer(SELECTION_MARKERS);

		if (session.selectionMode == SelectionMode.FACE) {
			drawSelectedFaces(context, markerBuffer);
		} else if (session.selectionMode == SelectionMode.VERTEX || session.selectionMode == SelectionMode.EDGE) {
			var vertices = session.selectionMode == SelectionMode.VERTEX
					? PhotonModelEditUtil.selectedVertices(asset, session.selectedPart, Set.of(), Set.of(), SelectionMode.PART)
					: session.selectedVertices;

			for (int index : vertices) {
				drawPoint(context, markerBuffer, partVertex(session.selectedPart, index),
						session.selectedVertices.contains(index) ? SELECTED_POINT_COLOR : UNSELECTED_COLOR);
			}
		}

		context.bufferSource().endLastBatch();
	}

	private void drawPoint(SceneRenderContext context, VertexConsumer buffer, Vector3f point, int color) {
		RenderBufferUtils.shapeCube(context.poseStack(), buffer, point.x - POINT_RADIUS, point.y - POINT_RADIUS,
				point.z - POINT_RADIUS, point.x + POINT_RADIUS, point.y + POINT_RADIUS, point.z + POINT_RADIUS,
				((color >>> 16) & 0xFF) / 255f, ((color >>> 8) & 0xFF) / 255f,
				(color & 0xFF) / 255f, (color >>> 24) / 255f);
	}

	private void drawSelectedFaces(SceneRenderContext context, VertexConsumer buffer) {
		var asset = session.asset();
		var pose = context.poseStack().last().pose();

		for (int index : session.selectedFaces) {
			var face = asset.faces.get(index);
			var triangles = PhotonModelPolygonUtil.triangulate(asset.vertices, face.vertices);

			for (int corner : triangles) {
				var point = partVertex(face.partId, face.vertices[corner]);
				buffer.addVertex(pose, point.x, point.y, point.z).setColor(SELECTED_FACE_COLOR);
			}
		}
	}

	private void onMouseUp(UIEvent event) {
		if (event.button == 0) {
			directDragStart = null;
		}

		if (boxStart != null && event.button == 0) {
			finishBox();
			boxStart = null;
			boxEnd = null;
			boxMode = false;
			updateSelectionGizmo();
			session.selectionChanged();
			event.stopPropagation();
			return;
		}

		if (event.button == 0 || event.button == 1) {
			finishGizmoEdit();
		}
	}

	private void finishGizmoEdit() {
		if (gizmoBefore == null) {
			return;
		}

		gizmoBefore = null;

		try {
			session.endLiveEdit(editingPivot ? "editor.move_pivot" : "editor.transform");
		} catch (RuntimeException error) {
			editor.setScreenTips(Component.translatable(PhotonModelTextUtil.key("error"), String.valueOf(error.getMessage())).getString());
		}

		updateSelectionGizmo();
	}

	private void onMouseDrag(UIEvent event) {
		if (boxStart != null) {
			boxEnd = new Vector2f(event.x, event.y);
			event.stopPropagation();
		} else if (directDragStart != null) {
			var point = dragPlanePoint(event);
			if (point != null) {
				var delta = point.sub(directDragStart);
				new Matrix4f(emitter.transform().localToWorldMatrix()).invert().transformDirection(delta);
				var position = new Vector3f(gizmoCenter).add(delta);

				if (partTransform != null) {
					partTransform.position(world(position));
				} else {
					handle.transform().localPosition(position);
				}
				updateGizmoEdit();
			}
			event.stopPropagation();
		}
	}

	/// 直接拖动采用穿过选区中心、面向相机的平面，与原生 Gizmo 共用几何事务和吸附。
	private Vector3f dragPlanePoint(UIEvent event) {
		var scene = editor.scene;
		var mouse = scene.worldToLocal(new Vector2f(event.x, event.y));
		if (!projectionReady || scene.getContentWidth() <= 0 || scene.getPaddingHeight() <= 0) {
			return null;
		}

		float x = (mouse.x - scene.getContentX()) / scene.getContentWidth() * 2 - 1;
		float y = 1 - (mouse.y - scene.getContentY()) / scene.getPaddingHeight() * 2;
		var inverse = new Matrix4f(viewProjection).invert();
		var near = inverse.transform(new Vector4f(x, y, -1, 1));
		var far = inverse.transform(new Vector4f(x, y, 1, 1));
		if (!near.isFinite() || !far.isFinite() || near.w == 0 || far.w == 0) {
			return null;
		}

		var origin = new Vector3f(near.x, near.y, near.z).div(near.w);
		var direction = new Vector3f(far.x, far.y, far.z).div(far.w).sub(origin);
		float denominator = direction.dot(directDragNormal);
		if (Math.abs(denominator) < RAY_EPSILON) {
			return null;
		}

		float distance = world(gizmoCenter).sub(origin).dot(directDragNormal) / denominator;
		return origin.fma(distance, direction);
	}

	private boolean insideBox(Vector3f point) {
		var projected = project(point);
		return projected.isPresent() && projected.get().x >= Math.min(boxStart.x, boxEnd.x)
				&& projected.get().x <= Math.max(boxStart.x, boxEnd.x)
				&& projected.get().y >= Math.min(boxStart.y, boxEnd.y)
				&& projected.get().y <= Math.max(boxStart.y, boxEnd.y);
	}

	private void finishBox() {
		var asset = session.asset();

		for (int index : PhotonModelEditUtil.partFaces(asset, session.selectedPart)) {
			var face = asset.faces.get(index);

			if (session.selectionMode == SelectionMode.FACE) {
				var center = new Vector3f();

				for (int vertex : face.vertices) {
					center.add(partVertex(face.partId, vertex));
				}

				if (insideBox(center.div(face.vertices.length))) {
					session.selectedFaces.add(index);
				}
			} else {
				for (int corner = 0; corner < face.vertices.length; corner++) {
					if (session.selectionMode == SelectionMode.EDGE) {
						var edge = Edge.of(face.vertices[corner], face.vertices[(corner + 1) % face.vertices.length]);

						if (insideBox(new Vector3f(partVertex(face.partId, edge.first()))
								.add(partVertex(face.partId, edge.second())).mul(0.5f))) {
							session.selectedEdges.add(edge);
						}
					} else if (insideBox(partVertex(face.partId, face.vertices[corner]))) {
						session.selectedVertices.add(face.vertices[corner]);
					}
				}
			}
		}

		if (session.selectionMode == SelectionMode.EDGE) {
			edgeVertices();
		}
	}

	public void drawBox(IGUIContext guiContext) {
		if (boxStart != null && guiContext instanceof GUIContext context) {
			var start = editor.worldToLocal(new Vector2f(boxStart));
			var end = editor.worldToLocal(new Vector2f(boxEnd));
			DrawerHelperClient.drawBorder(context, Math.min(start.x, end.x), Math.min(start.y, end.y),
					Math.abs(start.x - end.x), Math.abs(start.y - end.y), SELECTED_COLOR, 1);
		}
	}
}
