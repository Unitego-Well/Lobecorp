package org.unitego.lobecorp.client.photon.model.editor;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.client.shader.LDLibRenderPipelines;
import dev.vfyjxf.taffy.style.TaffyPosition;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import net.minecraft.client.Minecraft;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelSnapUtil;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelPreviewMaterialUtil;
import net.minecraft.network.chat.Component;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.unitego.lobecorp.util.photon.model.PhotonModelPolygonUtil;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;
import com.lowdragmc.lowdraglib2.gui.texture.renderstate.FloatBlitRenderState;
import com.lowdragmc.lowdraglib2.utils.ColorUtils;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class PhotonModelUVCanvas extends UIElement {
	/// UV 角点的拾取容差，单位为 GUI 像素。
	private static final float PICK_RADIUS = 7f;
	/// 高分辨率纹理缩小时合并网格线，最小间隔以 GUI 像素计。
	private static final float MIN_GRID_SPACING = 3f;
	/// UV 视图最小缩放倍率。
	private static final float MIN_ZOOM = 0.8f;
	/// 纹理长边像素数除以此值，得到最大缩放倍率。
	private static final float TEXTURE_ZOOM_DIVISOR = 2f;
	/// 小纹理的最大缩放倍率至少允许恢复到一倍。
	private static final float MIN_MAX_ZOOM = 1f;
	/// 原生滚动条占用的 GUI 像素宽度。
	private static final float SCROLLER_SIZE = 8f;
	/// 每格滚轮的视图缩放倍率。
	private static final float ZOOM_FACTOR = 1.2f;
	/// UV 单位区域的背景、网格、普通边和已选边颜色。
	private static final int BACKGROUND_COLOR = 0xFF202830;
	/// UV 网格背景线使用约 50% 不透明度。
	private static final int GRID_COLOR = 0x80384450;
	/// UV 面边框的 ARGB 颜色。
	private static final int EDGE_COLOR = 0x66FFFFFF;
	/// 未选 UV 边的黑色外描边，与白色内线保持相同透明度。
	private static final int EDGE_OUTLINE_COLOR = 0x66000000;
	/// 已选 UV 边的半透明白色内线。
	private static final int SELECTED_EDGE_COLOR = 0x80FFFFFF;
	/// 已选 UV 边的半透明黑色外描边，保证浅色贴图上的对比。
	private static final int SELECTED_EDGE_OUTLINE_COLOR = 0x80000000;
	/// UV 边包含黑色外描边的总宽度，单位为实际屏幕像素。
	private static final float EDGE_OUTLINE_WIDTH = 3f;
	/// UV 边白色内线宽度，单位为实际屏幕像素。
	private static final float EDGE_LINE_WIDTH = 1f;
	/// UV 辅助线宽，单位为 GUI 像素，不改变角点尺寸。
	private static final float LINE_WIDTH = 0.5f;
	/// 已选 UV 顶点使用白色内方块和黑色外框。
	private static final int SELECTED_POINT_COLOR = 0xFFFFFFFF;
	/// 已选 UV 顶点外框使用不透明黑色。
	private static final int SELECTED_POINT_OUTLINE_COLOR = 0xFF000000;
	/// 选面填充约 25% 不透明度，不改变实际材质。
	private static final int SELECTED_FACE_COLOR = 0x40FFBA50;
	/// 顶点黑色边框的厚度，单位为实际屏幕像素。
	private static final float POINT_BORDER_WIDTH = 1f;
	/// UV 角点的屏幕显示半径。
	private static final float POINT_RADIUS = 2f;
	private final PhotonModelEditSession session;
	private final Set<Corner> selected = new LinkedHashSet<>();
	private final Selector<String> textureSelector = new Selector<>();
	private final Scroller horizontalScroll = new Scroller.Horizontal().setRange(0, 1);
	private final Scroller verticalScroll = new Scroller.Vertical().setRange(0, 1);
	private Map<Integer, String> faceTextures = Map.of();
	private String textureKey = "";
	private String texturePart = "";
	private Set<Integer> textureFaces = Set.of();
	private boolean showGrid = true;
	/// 仅作用于视图的缩放与偏移；偏移以正方形视区边长为单位，不写入模型资产。
	private float zoom = 1f;
	private final Vector2f viewOffset = new Vector2f();
	private int textureWidth = 1;
	private int textureHeight = 1;
	private PhotonModelAsset dragBefore;
	private Vector2f dragStart;
	private boolean faceSelection;
	private Vector2f boxStart;
	private Vector2f boxEnd;
	private boolean boxMode;
	private Set<Corner> boxBefore = Set.of();
	@Nullable
	private Identifier texture;
	/// 几何缓存必须跟随版本号失效；实时拖动会原地修改资产。
	private long geometryRevision = Long.MIN_VALUE;
	private String geometryPart = "";
	private String geometryTexture = "";
	private Map<Integer, String> geometryFaceTextures = Map.of();
	private final Map<UVEdge, Set<Integer>> geometryEdges = new LinkedHashMap<>();
	private final Map<Vector2f, Set<Corner>> geometryCorners = new LinkedHashMap<>();
	private List<UVFill> geometryFills = List.of();
	private Set<Corner> renderedCorners = Set.of();
	private Set<Integer> renderedFaces = Set.of();
	@Nullable
	private UVView renderedView;
	private List<UVVertex> fillVertices = List.of();
	private List<UVVertex> overlayVertices = List.of();

	private record Corner(int face, int corner) {
	}

	private record UVEdge(Vector2f first, Vector2f second) {
	}

	private record UVFill(List<Vector2f> triangles, Set<Integer> faces) {
	}

	private record UVFillGeometry(List<Vector2f> triangles, boolean folded) {
	}

	private record UVFillKey(List<Vector2f> boundary, List<List<Vector2f>> triangles) {
	}

	private record UVVertex(float x, float y, int color) {
	}

	/// 投影和线宽只依赖视图状态，不参与资产几何缓存。
	private record UVView(float x, float y, float side, float spanX, float spanY, float offsetX, float offsetY,
	                      int width, int height, float guiScale, boolean grid) {
		private Vector2f project(Vector2f uv) {
			return project(uv.x, uv.y);
		}

		private Vector2f project(float u, float v) {
			return new Vector2f(x + (u * spanX + offsetX) * side, y + (v * spanY + offsetY) * side);
		}
	}

	/// 复用原生管线，将独立线段和角点作为四边形一次提交，避免逐线升层。
	@NullMarked
	private record UVRenderState(RenderPipeline pipeline, Matrix3x2f pose, List<UVVertex> vertices,
	                             int elementColor, @Nullable ScreenRectangle scissorArea,
	                             @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}

		@Override
		public void buildVertices(VertexConsumer consumer) {
			int primitiveSize = pipeline == LDLibRenderPipelines.GUI_TRIANGLE ? 3 : 4;
			boolean reflected = pose.determinant() < 0;

			for (int first = 0; first < vertices.size(); first += primitiveSize) {
				for (int corner = 0; corner < primitiveSize; corner++) {
					int offset = reflected && corner > 0 ? primitiveSize - corner : corner;
					var vertex = vertices.get(first + offset);
					consumer.addVertexWith2DPose(pose, vertex.x, vertex.y)
							.setColor(ColorUtils.mulColor(vertex.color, elementColor));
				}
			}
		}
	}

	public PhotonModelUVCanvas(PhotonModelEditSession session) {
		this.session = session;
		textureSelector.setCandidateUIProvider(value -> {
			var label = new Label();
			label.setText(value == null || value.isEmpty() ? Component.translatable(PhotonModelTextUtil.key("none"))
					: Component.literal(value));
			TimelineContext.styleLabel(label);
			return label;
		}).setOnValueChanged(this::selectTexture);
		textureSelector.layout(layout -> {
			layout.widthPercent(100);
			layout.minWidth(0);
		});
		textureSelector.style(style -> style.tooltips(Component.translatable(PhotonModelTextUtil.key("uv_texture"))));
		layout(layout -> {
			layout.flex(1);
			layout.minWidth(0);
			layout.minHeight(0);
		});
		setOverflowVisible(false);
		horizontalScroll.setOnValueChanged(value -> scrollView(0, value));
		verticalScroll.setOnValueChanged(value -> scrollView(1, value));
		addChildren(horizontalScroll, verticalScroll);
		updateScrollers();
		addEventListener(UIEvents.MOUSE_WHEEL, this::zoomView);
		addEventListener(UIEvents.MOUSE_DOWN, this::beginDrag);
		addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::drag);
		addEventListener(UIEvents.MOUSE_UP, this::endDrag);
		addEventListener(UIEvents.DRAG_END, this::endDrag);
		setFocusable(true);
	}

	public void boxSelection() {
		boxMode = true;
	}

	public void cancelBoxSelection() {
		if (boxStart != null) {
			selected.clear();
			selected.addAll(boxBefore);
		}

		boxBefore = Set.of();
		boxStart = null;
		boxMode = false;
	}

	@Override
	protected void onLayoutChanged() {
		super.onLayoutChanged();
		float side = viewportSide();
		float left = viewportX() - getContentX();
		float top = viewportY() - getContentY();
		horizontalScroll.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
				.left(left).top(top + side).width(side).height(SCROLLER_SIZE));
		verticalScroll.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
				.left(left + side).top(top).width(SCROLLER_SIZE).height(side));
		updateScrollers();
	}

	private float viewportSide() {
		return Math.max(0, Math.min(getContentWidth(), getContentHeight()) - SCROLLER_SIZE);
	}

	private float viewportX() {
		return getContentX() + (getContentWidth() - viewportSide() - SCROLLER_SIZE) / 2;
	}

	private float viewportY() {
		return getContentY() + (getContentHeight() - viewportSide() - SCROLLER_SIZE) / 2;
	}

	/// 保持纹理比例，一倍缩放时长边适配正方形视区。
	private float textureSpan(int axis) {
		return zoom * (axis == 0 ? textureWidth : textureHeight) / Math.max(textureWidth, textureHeight);
	}

	private float maxZoom() {
		return Math.max(MIN_MAX_ZOOM, Math.max(textureWidth, textureHeight) / TEXTURE_ZOOM_DIVISOR);
	}

	private boolean isOutsideViewport(float x, float y) {
		var point = getLocalMouse(x, y);
		float side = viewportSide();
		return side <= 0 || point.x < viewportX() || point.x >= viewportX() + side
				|| point.y < viewportY() || point.y >= viewportY() + side;
	}

	private void updateScrollers() {
		updateScroller(horizontalScroll, 0);
		updateScroller(verticalScroll, 1);
	}

	private void updateScroller(Scroller scroller, int axis) {
		float span = textureSpan(axis);
		float range = Math.max(0, span - 1);
		float offset = range > 0 ? Math.clamp(viewOffset.get(axis), -range, 0) : (1 - span) / 2;
		viewOffset.setComponent(axis, offset);
		scroller.setScrollBarSize(100 / Math.max(1, span));
		scroller.setValue(range > 0 ? -offset / range : 0f, false);
		scroller.setActive(range > 0);
		scroller.headButton.setActive(range > 0);
		scroller.tailButton.setActive(range > 0);
		scroller.scrollContainer.setActive(range > 0);
		scroller.scrollBar.setActive(range > 0);
	}

	private void scrollView(int axis, float value) {
		float range = Math.max(0, textureSpan(axis) - 1);

		if (range <= 0 || !Float.isFinite(value) || dragBefore != null) {
			return;
		}

		viewOffset.setComponent(axis, -Math.clamp(value, 0f, 1f) * range);
	}

	public void setFaceSelection(boolean faceSelection) {
		this.faceSelection = faceSelection;
		selected.clear();
	}

	public Toggle gridToggle() {
		return new Toggle().setText(PhotonModelTextUtil.key("uv_grid")).setOn(showGrid, false)
				.setOnToggleChanged(value -> showGrid = value);
	}

	public Selector<String> textureSelector() {
		return textureSelector;
	}

	/// 只切换 UV 预览纹理及对应面，不修改共享材质和面的材质分配。
	public void refreshTextures(PhotonModelEmitter emitter) {
		var current = new LinkedHashMap<Integer, String>();

		for (int face : PhotonModelEditUtil.partFaces(session.asset(), session.selectedPart)) {
			var reference = PhotonModelPreviewMaterialUtil.getTexture(session.asset(), emitter, face);
			current.put(face, reference == null ? "" : reference.toString());
		}

		boolean facesChanged = !textureFaces.equals(session.selectedFaces);
		textureFaces = Set.copyOf(session.selectedFaces);

		if (current.equals(faceTextures) && texturePart.equals(session.selectedPart) && !facesChanged) {
			return;
		}

		boolean partChanged = !texturePart.equals(session.selectedPart);
		if (partChanged) {
			selected.clear();
			boxStart = null;
		}
		texturePart = session.selectedPart;
		if (!current.equals(faceTextures)) {
			faceTextures = current;
		}
		var candidates = current.values().stream().distinct().toList();
		textureSelector.setCandidates(candidates);

		if (partChanged || facesChanged || !candidates.contains(textureKey)) {
			String preferred = session.selectedFaces.stream().filter(current::containsKey)
					.map(current::get).findFirst().orElse(candidates.isEmpty() ? "" : candidates.getFirst());
			selectTexture(preferred);
		}

		textureSelector.setSelected(textureKey, false);
		selected.removeIf(corner -> !visibleFace(corner.face));
	}

	private void selectTexture(@Nullable String key) {
		textureKey = key == null ? "" : key;
		texture = textureKey.isEmpty() ? null : Identifier.tryParse(textureKey);
		refreshTextureSize();
		selected.removeIf(corner -> !visibleFace(corner.face));
	}

	private void refreshTextureSize() {
		int previousWidth = textureWidth;
		int previousHeight = textureHeight;

		if (texture == null) {
			textureWidth = textureHeight = 1;
		} else {
			var gpuTexture = Minecraft.getInstance().getTextureManager().getTexture(texture).getTexture();
			textureWidth = Math.max(1, gpuTexture.getWidth(0));
			textureHeight = Math.max(1, gpuTexture.getHeight(0));
		}

		if (previousWidth != textureWidth || previousHeight != textureHeight) {
			zoom = Math.clamp(zoom, MIN_ZOOM, maxZoom());
			updateScrollers();
		}
	}

	private boolean visibleFace(int face) {
		return face >= 0 && face < session.asset().faces.size()
				&& session.asset().faces.get(face).partId.equals(session.selectedPart)
				&& faceTextures.containsKey(face) && Objects.equals(faceTextures.get(face), textureKey);
	}

	private Vector2f toUV(float x, float y) {
		var point = getLocalMouse(x, y);
		float side = Math.max(1, viewportSide());
		return new Vector2f((point.x - viewportX()) / side, (point.y - viewportY()) / side)
				.sub(viewOffset).div(textureSpan(0), textureSpan(1));
	}

	private Vector2f toScreen(Vector2f uv) {
		float side = viewportSide();
		return new Vector2f(viewportX() + (uv.x * textureSpan(0) + viewOffset.x) * side,
				viewportY() + (uv.y * textureSpan(1) + viewOffset.y) * side);
	}

	private void zoomView(UIEvent event) {
		if (isOutsideViewport(event.x, event.y) || !Float.isFinite(event.deltaY)) {
			return;
		}

		event.stopPropagation();

		if (dragBefore != null || boxStart != null) {
			return;
		}

		refreshTextureSize();
		float nextZoom = Math.clamp(zoom * (float) Math.pow(ZOOM_FACTOR, event.deltaY), MIN_ZOOM, maxZoom());
		var anchor = toUV(event.x, event.y);
		float previousX = textureSpan(0);
		float previousY = textureSpan(1);
		zoom = nextZoom;
		viewOffset.add(anchor.mul(previousX - textureSpan(0), previousY - textureSpan(1)));
		updateScrollers();
	}

	private void beginDrag(UIEvent event) {
		if (event.button != 0 || isOutsideViewport(event.x, event.y)) {
			return;
		}

		selected.removeIf(corner -> corner.face >= session.asset().faces.size()
				|| corner.corner >= session.asset().faces.get(corner.face).uv.length);
		var local = getLocalMouse(event.x, event.y);
		focus();
		if (boxMode) {
			beginBox(event, local);
			return;
		}
		Corner nearest = null;
		float distance = PICK_RADIUS * PICK_RADIUS;

		for (int faceIndex = 0; faceIndex < session.asset().faces.size(); faceIndex++) {
			var face = session.asset().faces.get(faceIndex);

			if (!visibleFace(faceIndex)) {
				continue;
			}

			for (int corner = 0; corner < face.uv.length; corner++) {
				float current = toScreen(face.uv[corner]).distanceSquared(local);

				if (current < distance) {
					distance = current;
					nearest = new Corner(faceIndex, corner);
				}
			}
		}

		if (faceSelection && nearest == null) {
			var uv = toUV(event.x, event.y);

			for (int index : PhotonModelEditUtil.partFaces(session.asset(), session.selectedPart)) {
				if (visibleFace(index) && insideFace(session.asset().faces.get(index), uv)) {
					nearest = new Corner(index, 0);
				}
			}
		}

		if (nearest == null) {
			beginBox(event, local);
			return;
		}

		if (!event.isShiftDown()) {
			selected.clear();
		}

		if (faceSelection) {
			for (int corner = 0; corner < session.asset().faces.get(nearest.face).uv.length; corner++) {
				selected.add(new Corner(nearest.face, corner));
			}
		} else {
			selected.add(nearest);
		}

		session.selectedFaces.clear();
		selected.forEach(corner -> session.selectedFaces.add(corner.face));
		session.selectionChanged();
		dragBefore = session.asset().copy();
		dragStart = toUV(event.x, event.y);
		session.beginLiveEdit();
		startDrag(this, null);
		event.stopPropagation();
	}

	private void drag(UIEvent event) {
		if (boxStart != null) {
			boxEnd = getLocalMouse(event.x, event.y);
			boxEnd.x = Math.clamp(boxEnd.x, viewportX(), viewportX() + viewportSide());
			boxEnd.y = Math.clamp(boxEnd.y, viewportY(), viewportY() + viewportSide());
			event.stopPropagation();
			return;
		}

		if (dragBefore == null) {
			return;
		}

		var delta = toUV(event.x, event.y).sub(dragStart);
		float step = PhotonModelSnapUtil.pixelStep(event.isShiftDown(), event.isCtrlDown());

		if (step > 0) {
			refreshTextureSize();
			delta.x = boundedDelta(delta.x, 0, step / textureWidth);
			delta.y = boundedDelta(delta.y, 1, step / textureHeight);
		}
		session.liveEdit(asset -> {
			for (var corner : selected) {
				asset.faces.get(corner.face).uv[corner.corner]
						.set(dragBefore.faces.get(corner.face).uv[corner.corner]).add(delta);
			}
		});
		event.stopPropagation();
	}

	/// 限制共同位移，保持多选 UV 的相对位置和面形状。
	private float boundedDelta(float delta, int axis, float step) {
		float minimum = Float.NEGATIVE_INFINITY;
		float maximum = Float.POSITIVE_INFINITY;

		for (var corner : selected) {
			float value = dragBefore.faces.get(corner.face).uv[corner.corner].get(axis);
			minimum = Math.max(minimum, -value);
			maximum = Math.min(maximum, 1 - value);
		}

		float first = (float) Math.ceil(minimum / step);
		float last = (float) Math.floor(maximum / step);
		return first <= last ? Math.clamp(Math.round(delta / step), first, last) * step : 0;
	}

	private void endDrag(UIEvent event) {
		if (boxStart != null) {
			finishBox();
			event.stopPropagation();
			return;
		}

		if (dragBefore == null) {
			return;
		}

		dragBefore = null;
		session.endLiveEdit("editor.uv_drag");
		event.stopPropagation();
	}

	private void beginBox(UIEvent event, Vector2f local) {
		boxBefore = new LinkedHashSet<>(selected);
		if (!event.isShiftDown()) {
			selected.clear();
		}
		boxStart = new Vector2f(local);
		boxEnd = new Vector2f(local);
		startDrag(this, null);
		event.stopPropagation();
	}

	private void finishBox() {
		float left = Math.min(boxStart.x, boxEnd.x);
		float right = Math.max(boxStart.x, boxEnd.x);
		float top = Math.min(boxStart.y, boxEnd.y);
		float bottom = Math.max(boxStart.y, boxEnd.y);
		for (int index : PhotonModelEditUtil.partFaces(session.asset(), session.selectedPart)) {
			if (!visibleFace(index)) {
				continue;
			}
			var face = session.asset().faces.get(index);
			boolean inside = true;
			for (int corner = 0; corner < face.uv.length; corner++) {
				var point = toScreen(face.uv[corner]);
				boolean hit = point.x >= left && point.x <= right && point.y >= top && point.y <= bottom;
				inside &= hit;
				if (hit && !faceSelection) {
					selected.add(new Corner(index, corner));
				}
			}
			if (inside && faceSelection) {
				for (int corner = 0; corner < face.uv.length; corner++) {
					selected.add(new Corner(index, corner));
				}
			}
		}
		boxStart = null;
		boxMode = false;
		boxBefore = Set.of();
		session.selectedFaces.clear();
		selected.forEach(corner -> session.selectedFaces.add(corner.face));
		session.selectionChanged();
	}

	private boolean insideFace(PhotonModelAsset.Face face, Vector2f point) {
		boolean inside = false;

		for (int i = 0, previous = face.uv.length - 1; i < face.uv.length; previous = i++) {
			var a = face.uv[i];
			var b = face.uv[previous];

			if ((a.y > point.y) != (b.y > point.y)
					&& point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x) {
				inside = !inside;
			}
		}

		return inside;
	}

	public void selectAll() {
		selected.clear();

		for (int face : PhotonModelEditUtil.partFaces(session.asset(), session.selectedPart)) {
			if (!visibleFace(face)) {
				continue;
			}

			for (int corner = 0; corner < session.asset().faces.get(face).uv.length; corner++) {
				selected.add(new Corner(face, corner));
			}
		}

		session.selectedFaces.clear();
		selected.forEach(corner -> session.selectedFaces.add(corner.face));
		session.selectionChanged();
	}

	public void transform(Vector2f translation, float degrees, Vector2f scale) {
		if (selected.isEmpty()) {
			return;
		}

		refreshTextureSize();
		var pixelTranslation = new Vector2f(translation).div(textureWidth, textureHeight);
		var center = new Vector2f();
		selected.forEach(corner -> center.add(session.asset().faces.get(corner.face).uv[corner.corner]));
		center.div(selected.size());
		float cosine = (float) Math.cos(Math.toRadians(degrees));
		float sine = (float) Math.sin(Math.toRadians(degrees));
		session.change("editor.uv_transform", asset -> {
			for (var corner : selected) {
				var uv = asset.faces.get(corner.face).uv[corner.corner];
				var relative = new Vector2f(uv).sub(center).mul(scale);
				uv.set(relative.x * cosine - relative.y * sine, relative.x * sine + relative.y * cosine)
						.add(center).add(pixelTranslation);
			}
		});
	}

	public void clearSelection() {
		selected.clear();
	}

	@Override
	protected void drawBackgroundAdditional(IGUIContext guiContext) {
		super.drawBackgroundAdditional(guiContext);

		if (!(guiContext instanceof GUIContext context)) {
			return;
		}

		refreshTextureSize();
		float side = viewportSide();

		if (side <= 0) {
			return;
		}

		context.enableScissor(viewportX(), viewportY(), side, side);

		try {
			drawViewport(context);
		} finally {
			context.disableScissor();
		}
	}

	private void drawViewport(GUIContext context) {
		var view = new UVView(viewportX(), viewportY(), viewportSide(), textureSpan(0), textureSpan(1),
				viewOffset.x, viewOffset.y, textureWidth, textureHeight,
				Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()), showGrid);
		DrawerHelperClient.drawSolidRect(context, view.x, view.y, view.side, view.side, BACKGROUND_COLOR);

		if (texture != null) {
			var origin = view.project(0, 0);
			context.blit(RenderPipelines.GUI_TEXTURED, texture, origin.x, origin.y, view.side * view.spanX,
					view.side * view.spanY, 0, 0, 1, 1, 0xFFFFFFFF);
		}

		refreshBatches(view);
		submitBatch(context, view, LDLibRenderPipelines.GUI_TRIANGLE, fillVertices);
		submitBatch(context, view, RenderPipelines.GUI, overlayVertices);

		if (boxStart != null) {
			DrawerHelperClient.drawBorder(context, Math.min(boxStart.x, boxEnd.x), Math.min(boxStart.y, boxEnd.y),
					Math.abs(boxStart.x - boxEnd.x), Math.abs(boxStart.y - boxEnd.y), SELECTED_POINT_COLOR, 1);
		}
	}

	/// 拓扑与 UV 坐标变化时才去重和三角化；缓存不持有资产中的可变向量。
	private void refreshGeometry() {
		if (geometryRevision == session.revision() && geometryPart.equals(session.selectedPart)
				&& geometryTexture.equals(textureKey) && geometryFaceTextures == faceTextures) {
			return;
		}

		selected.removeIf(corner -> !visibleFace(corner.face)
				|| corner.corner < 0 || corner.corner >= session.asset().faces.get(corner.face).uv.length);
		geometryEdges.clear();
		geometryCorners.clear();
		var polygons = new LinkedHashMap<UVFillKey, UVFill>();

		for (int index : faceTextures.keySet()) {
			if (!visibleFace(index)) {
				continue;
			}

			var face = session.asset().faces.get(index);

			for (int corner = 0; corner < face.uv.length; corner++) {
				var first = copyUV(face.uv[corner]);
				var second = copyUV(face.uv[(corner + 1) % face.uv.length]);
				geometryCorners.computeIfAbsent(first, _ -> new LinkedHashSet<>()).add(new Corner(index, corner));

				if (!first.equals(second)) {
					var edge = compareUV(first, second) < 0 ? new UVEdge(first, second) : new UVEdge(second, first);
					geometryEdges.computeIfAbsent(edge, _ -> new LinkedHashSet<>()).add(index);
				}
			}

			if (face.uv.length >= 3) {
				var geometry = triangulateFill(face);
				polygons.computeIfAbsent(fillKey(face, geometry),
						_ -> new UVFill(geometry.triangles, new LinkedHashSet<>())).faces.add(index);
			}
		}

		var fills = new ArrayList<UVFill>();

		for (var fill : polygons.values()) {
			fills.add(new UVFill(fill.triangles, Set.copyOf(fill.faces)));
		}

		geometryFills = List.copyOf(fills);
		geometryRevision = session.revision();
		geometryPart = session.selectedPart;
		geometryTexture = textureKey;
		geometryFaceTextures = faceTextures;
		renderedView = null;
	}

	/// 静止时直接复用顶点；选择或视图变化只重新投影，不重新三角化。
	private void refreshBatches(UVView view) {
		refreshGeometry();

		if (view.equals(renderedView) && renderedCorners.equals(selected) && renderedFaces.equals(session.selectedFaces)) {
			return;
		}

		var faces = new LinkedHashSet<>(session.selectedFaces);
		selected.forEach(corner -> faces.add(corner.face));
		var fills = new ArrayList<UVVertex>();

		for (var fill : geometryFills) {
			if (fill.faces.stream().noneMatch(faces::contains)) {
				continue;
			}

			for (var uv : fill.triangles) {
				var point = view.project(uv);
				fills.add(new UVVertex(point.x, point.y, SELECTED_FACE_COLOR));
			}
		}

		var overlay = new ArrayList<UVVertex>();

		if (view.grid) {
			appendGrid(overlay, view);
		}

		appendEdges(overlay, view);
		appendCorners(overlay, view);
		fillVertices = List.copyOf(fills);
		overlayVertices = List.copyOf(overlay);
		renderedCorners = Set.copyOf(selected);
		renderedFaces = Set.copyOf(session.selectedFaces);
		renderedView = view;
	}

	private void submitBatch(GUIContext context, UVView view, RenderPipeline pipeline, List<UVVertex> vertices) {
		if (vertices.isEmpty()) {
			return;
		}

		var pose = context.pose.copyPose();
		var scissor = context.peekScissor();
		var bounds = FloatBlitRenderState.getBounds(view.x, view.y, view.x + view.side, view.y + view.side, pose, scissor);

		if (bounds != null) {
			context.addGuiElement(new UVRenderState(pipeline, pose, vertices, context.getElementColor(), scissor, bounds));
		}
	}

	private void appendGrid(List<UVVertex> vertices, UVView view) {
		int stepX = Math.max(1, (int) Math.ceil(view.width * MIN_GRID_SPACING / Math.max(1, view.side * view.spanX)));
		int stepY = Math.max(1, (int) Math.ceil(view.height * MIN_GRID_SPACING / Math.max(1, view.side * view.spanY)));
		int firstX = Math.max(0, (int) Math.floor(-view.offsetX / view.spanX * view.width / stepX) * stepX);
		int firstY = Math.max(0, (int) Math.floor(-view.offsetY / view.spanY * view.height / stepY) * stepY);
		int lastX = Math.min(view.width, (int) Math.ceil((1 - view.offsetX) / view.spanX * view.width));
		int lastY = Math.min(view.height, (int) Math.ceil((1 - view.offsetY) / view.spanY * view.height));

		for (int pixel = firstX; pixel <= lastX; pixel += stepX) {
			float fraction = (float) pixel / view.width;
			appendLine(vertices, view, view.project(fraction, 0), view.project(fraction, 1), GRID_COLOR, LINE_WIDTH);
		}

		for (int pixel = firstY; pixel <= lastY; pixel += stepY) {
			float fraction = (float) pixel / view.height;
			appendLine(vertices, view, view.project(0, fraction), view.project(1, fraction), GRID_COLOR, LINE_WIDTH);
		}
	}

	/// 保持黑边、白边、角点的顺序，重合边仍只绘制一次。
	private void appendEdges(List<UVVertex> vertices, UVView view) {
		float halfPixel = 0.5f / view.guiScale;

		for (var entry : geometryEdges.entrySet()) {
			int color = entry.getValue().stream().anyMatch(session.selectedFaces::contains)
					? SELECTED_EDGE_OUTLINE_COLOR : EDGE_OUTLINE_COLOR;
			appendLine(vertices, view, view.project(entry.getKey().first), view.project(entry.getKey().second),
					color, EDGE_OUTLINE_WIDTH * halfPixel);
		}

		for (var entry : geometryEdges.entrySet()) {
			int color = entry.getValue().stream().anyMatch(session.selectedFaces::contains) ? SELECTED_EDGE_COLOR : EDGE_COLOR;
			appendLine(vertices, view, view.project(entry.getKey().first), view.project(entry.getKey().second),
					color, EDGE_LINE_WIDTH * halfPixel);
		}
	}

	private void appendCorners(List<UVVertex> vertices, UVView view) {
		float border = POINT_BORDER_WIDTH / view.guiScale;
		float innerRadius = Math.max(0, POINT_RADIUS - border);

		for (var entry : geometryCorners.entrySet()) {
			var point = view.project(entry.getKey());
			boolean isSelected = entry.getValue().stream().anyMatch(selected::contains);
			int outline = isSelected ? SELECTED_POINT_OUTLINE_COLOR : EDGE_OUTLINE_COLOR;
			int color = isSelected ? SELECTED_POINT_COLOR : EDGE_COLOR;
			appendSquare(vertices, view, point, POINT_RADIUS, outline);
			appendSquare(vertices, view, point, innerRadius, color);
		}
	}

	private void appendSquare(List<UVVertex> vertices, UVView view, Vector2f point, float radius, int color) {
		if (point.x + radius < view.x || point.x - radius > view.x + view.side
				|| point.y + radius < view.y || point.y - radius > view.y + view.side) {
			return;
		}

		vertices.add(new UVVertex(point.x - radius, point.y - radius, color));
		vertices.add(new UVVertex(point.x - radius, point.y + radius, color));
		vertices.add(new UVVertex(point.x + radius, point.y + radius, color));
		vertices.add(new UVVertex(point.x + radius, point.y - radius, color));
	}

	/// 独立线段使用四边形，避免连续折线产生额外连接或改变三角带绕序。
	private void appendLine(List<UVVertex> vertices, UVView view, Vector2f first, Vector2f second, int color, float halfWidth) {
		if (Math.max(first.x, second.x) + halfWidth < view.x || Math.min(first.x, second.x) - halfWidth > view.x + view.side
				|| Math.max(first.y, second.y) + halfWidth < view.y || Math.min(first.y, second.y) - halfWidth > view.y + view.side) {
			return;
		}

		float dx = second.x - first.x;
		float dy = second.y - first.y;
		float length = (float) Math.hypot(dx, dy);

		if (length == 0 || !Float.isFinite(length)) {
			return;
		}

		float normalX = -dy / length * halfWidth;
		float normalY = dx / length * halfWidth;
		vertices.add(new UVVertex(first.x + normalX, first.y + normalY, color));
		vertices.add(new UVVertex(second.x + normalX, second.y + normalY, color));
		vertices.add(new UVVertex(second.x - normalX, second.y - normalY, color));
		vertices.add(new UVVertex(first.x - normalX, first.y - normalY, color));
	}

	/// 普通重合面按边界去重；折叠覆盖必须区分实际几何对角线。
	private UVFillKey fillKey(PhotonModelAsset.Face face, UVFillGeometry geometry) {
		if (!geometry.folded) {
			var positions = new ArrayList<Vector3f>(face.uv.length);
			int[] corners = new int[face.uv.length];

			for (int corner = 0; corner < face.uv.length; corner++) {
				var uv = face.uv[corner];
				positions.add(new Vector3f(uv.x, uv.y, 0));
				corners[corner] = corner;
			}

			try {
				PhotonModelPolygonUtil.triangulate(positions, corners);
				return new UVFillKey(orderedPolygon(face.uv), List.of());
			} catch (IllegalArgumentException _) {
				// 边界折叠或交叉时，按实际三角覆盖区分填充。
			}
		}

		return new UVFillKey(List.of(), orderedTriangles(geometry.triangles));
	}

	private List<Vector2f> orderedPolygon(Vector2f[] corners) {
		int first = 0;

		for (int index = 1; index < corners.length; index++) {
			if (compareUV(corners[index], corners[first]) < 0) {
				first = index;
			}
		}

		var next = corners[(first + 1) % corners.length];
		var previous = corners[Math.floorMod(first - 1, corners.length)];
		int direction = compareUV(next, previous) <= 0 ? 1 : -1;
		var polygon = new ArrayList<Vector2f>(corners.length);

		for (int index = 0; index < corners.length; index++) {
			polygon.add(copyUV(corners[Math.floorMod(first + index * direction, corners.length)]));
		}

		return List.copyOf(polygon);
	}

	/// 折叠后的相同边界可能使用不同几何对角线。
	private List<List<Vector2f>> orderedTriangles(List<Vector2f> vertices) {
		var triangles = new ArrayList<List<Vector2f>>();

		for (int index = 0; index < vertices.size(); index += 3) {
			var corners = new ArrayList<>(vertices.subList(index, index + 3));
			corners.sort(this::compareUV);
			triangles.add(List.copyOf(corners));
		}

		triangles.sort(this::compareTriangle);
		return List.copyOf(triangles);
	}

	private int compareTriangle(List<Vector2f> first, List<Vector2f> second) {
		for (int corner = 0; corner < 3; corner++) {
			int order = compareUV(first.get(corner), second.get(corner));

			if (order != 0) {
				return order;
			}
		}

		return 0;
	}

	private int compareUV(Vector2f first, Vector2f second) {
		int horizontal = Float.compare(first.x, second.x);
		return horizontal == 0 ? Float.compare(first.y, second.y) : horizontal;
	}

	/// 镜像会产生负零；缓存键将其视为零，避免重合边点重复叠加透明度。
	private Vector2f copyUV(Vector2f uv) {
		return new Vector2f(uv.x == 0 ? 0 : uv.x, uv.y == 0 ? 0 : uv.y);
	}

	private UVFillGeometry triangulateFill(PhotonModelAsset.Face face) {
		int[] triangles;

		try {
			triangles = PhotonModelPolygonUtil.triangulate(session.asset().vertices, face.vertices);
		} catch (IllegalArgumentException _) {
			// 编辑中的无效几何仍保留 UV 边与顶点，不向渲染线程抛出异常。
			return new UVFillGeometry(List.of(), true);
		}

		var vertices = new ArrayList<Vector2f>(triangles.length);
		boolean positive = false;
		boolean negative = false;

		for (int index = 0; index < triangles.length; index += 3) {
			var first = copyUV(face.uv[triangles[index]]);
			var second = copyUV(face.uv[triangles[index + 1]]);
			var third = copyUV(face.uv[triangles[index + 2]]);
			double winding = ((double) second.x - first.x) * (third.y - first.y)
					- ((double) second.y - first.y) * (third.x - first.x);

			if (winding == 0 || !Double.isFinite(winding)) {
				continue;
			}

			positive |= winding > 0;
			negative |= winding < 0;
			// 沿用模型面的三角拓扑，允许 UV 折叠；Y 轴向下的 GUI 只统一绘制绕序。
			vertices.add(first);
			vertices.add(winding < 0 ? second : third);
			vertices.add(winding < 0 ? third : second);
		}

		return new UVFillGeometry(List.copyOf(vertices), positive && negative);
	}
}
