package org.unitego.lobecorp.client.photon.model.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.client.shader.LDLibRenderPipelines;
import com.lowdragmc.lowdraglib2.gui.texture.renderstate.FloatColoredTriangleRenderState;
import com.lowdragmc.lowdraglib2.utils.ColorUtils;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.NbtOps;
import net.minecraft.client.gui.render.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.registry.translation.photon.model.PhotonModelTranslations;
import org.unitego.lobecorp.util.photon.model.PhotonModelAssetUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelCodec;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

/// UV 画布保留独立选择和手势预览，多角点只通过当前工程的一次 revision 事务提交。
public class PhotonModelWorkspaceUV extends UIElement {
	private static final float PICK_RADIUS = 7;
	/// 视图倍率与像素网格间距保持原 UV 制作界面的单位。
	private static final float MIN_ZOOM = 0.8f;
	private static final float MIN_GRID_SPACING = 3;
	private static final float SCROLLER_SIZE = 8;
	private static final float ZOOM_FACTOR = 1.2f;
	private static final float TEXTURE_ZOOM_DIVISOR = 2;
	/// UV 拖动的吸附精度，单位为纹理像素。
	private static final float PIXEL_SHIFT_STEP = 1;
	private static final float PIXEL_CONTROL_STEP = 0.5f;
	private static final float PIXEL_FINE_STEP = 0.1f;
	private static final int BACKGROUND_COLOR = 0xff202830;
	private static final int GRID_COLOR = 0x80384450;
	/// 材质SNBT只在revision变化时解码，单项预算与原生资源编辑输入上限一致。
	private static final int MAX_MATERIAL_BYTES = 2 * 1024 * 1024;
	private static final int SELECTED_FACE_COLOR = 0x40ffba50;
	private final PhotonModelWorkspaceSession session;
	private final Consumer<String> inspect;
	private final Supplier<String> selectedPart;
	private final Supplier<Set<Integer>> selectedFaces;
	private final Consumer<Set<Integer>> selectFaces;
	private final UIElement canvas = new UIElement();
	private final Selector<String> textures = new Selector<>();
	private final Label feedback = new Label();
	private final Map<String, String> materialTextures = new LinkedHashMap<>();
	private final Map<String, String> materialSources = new LinkedHashMap<>();
	private final Map<String, String> materialErrors = new LinkedHashMap<>();
	private List<List<Corner>> fillTriangles = List.of();
	private final Scroller horizontal = new Scroller.Horizontal().setRange(0, 1);
	private final Scroller vertical = new Scroller.Vertical().setRange(0, 1);
	private final Set<Corner> selection = new LinkedHashSet<>();
	private final Map<Corner, Vector2f> dragPoints = new LinkedHashMap<>();
	private Set<Corner> boxBefore = Set.of();
	private Set<Integer> sharedFaces = Set.of();
	private String part = "";
	private String viewRevision = "";
	private String textureKey = "";
	private @Nullable Identifier texture;
	private int textureWidth = 1;
	private int textureHeight = 1;
	private boolean grid = true;
	private float zoom = 1;
	private final Vector2f offset = new Vector2f();
	private final Vector2f delta = new Vector2f();
	private @Nullable Vector2f start;
	private @Nullable Vector2f boxEnd;
	private @Nullable String gestureRevision;
	private boolean panning;
	private boolean selectingBox;
	private final Vector2f uvTranslation = new Vector2f();
	private final Vector2f uvScale = new Vector2f(1);
	private float uvRotation;

	private record Corner(int face, int corner) {
	}

	public PhotonModelWorkspaceUV(PhotonModelWorkspaceSession session, Consumer<String> inspect,
	                              Supplier<String> selectedPart, Supplier<Set<Integer>> selectedFaces,
	                              Consumer<Set<Integer>> selectFaces) {
		this.session = session;
		this.inspect = inspect;
		this.selectedPart = selectedPart;
		this.selectedFaces = selectedFaces;
		this.selectFaces = selectFaces;
		layout(layout -> layout.widthPercent(100).flex(1).minHeight(0).flexDirection(FlexDirection.COLUMN));
		setOverflowVisible(false);
		textures.setCandidateUIProvider(value -> new Label().setText(value == null || value.isEmpty()
				? Component.translatable(PhotonModelTranslations.NONE) : Component.literal(value)));
		textures.setOnValueChanged(this::selectTexture);
		textures.layout(layout -> layout.widthPercent(100).minWidth(0));
		addChild(textures);
		feedback.layout(layout -> layout.widthPercent(100));
		feedback.setDisplay(false);
		addChild(feedback);
		addChild(new BooleanConfigurator(PhotonModelTranslations.UV_GRID, () -> grid, value -> grid = value, true, false));
		canvas.layout(layout -> layout.widthPercent(100).flex(1).minHeight(80));
		canvas.setOverflowVisible(false);
		canvas.setFocusable(true);
		horizontal.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(0).right(SCROLLER_SIZE).bottom(0).height(SCROLLER_SIZE));
		vertical.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).top(0).right(0).bottom(SCROLLER_SIZE).width(SCROLLER_SIZE));
		horizontal.setOnValueChanged(value -> scroll(0, value));
		vertical.setOnValueChanged(value -> scroll(1, value));
		canvas.addChildren(horizontal, vertical);
		canvas.style(style -> style.backgroundTexture(GuiTexture.of(this::draw)));
		canvas.addEventListener(UIEvents.MOUSE_DOWN, this::begin);
		canvas.addEventListener(UIEvents.MOUSE_WHEEL, this::zoomView);
		canvas.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::drag);
		canvas.addEventListener(UIEvents.MOUSE_UP, _ -> finish());
		canvas.addEventListener(UIEvents.DRAG_END, _ -> finish());
		canvas.addEventListener(UIEvents.KEY_DOWN, event -> {
			if (event.keyCode == GLFW.GLFW_KEY_ESCAPE) {
				cancel();
				event.stopPropagation();
			} else if (event.keyCode == GLFW.GLFW_KEY_A && event.isCtrlDown() && start == null) {
				selection.clear();
				selection.addAll(points().keySet());
				publishSelection();
				event.stopPropagation();
			}
		});
		addChild(canvas);
		var tools = new ScrollerView();
		tools.layout(layout -> layout.widthPercent(100).heightPercent(45).minHeight(0));
		tools.scrollerStyle(style -> style.adaptiveHeight(false));
		String[] labels = {"uv_translate_u", "uv_translate_v", "uv_scale_u", "uv_scale_v", "uv_rotation"};
		for (int index = 0; index < labels.length; index++) {
			int component = index;
			tools.addScrollViewChild(new NumberConfigurator(PhotonModelTranslations.key(labels[index]),
					() -> component < 2 ? uvTranslation.get(component) : component < 4 ? uvScale.get(component - 2) : uvRotation,
					value -> {
						if (component < 2) {
							uvTranslation.setComponent(component, value.floatValue());
						} else if (component < 4) {
							uvScale.setComponent(component - 2, value.floatValue());
						} else {
							uvRotation = value.floatValue();
						}
					}, index == 2 || index == 3 ? 1 : 0, false).setRange(-Float.MAX_VALUE, Float.MAX_VALUE));
		}
		tools.addScrollViewChild(new Button().setText(PhotonModelTranslations.APPLY).setOnClick(_ -> transformSelection()));
		addChild(tools);
		refresh();
	}

	private void transformSelection() {
		refresh();
		cancel();
		var available = points();
		var targets = new LinkedHashSet<>(selection);
		if (targets.isEmpty()) {
			return;
		}
		var center = new Vector2f();
		targets.forEach(corner -> center.add(available.get(corner)));
		center.div(targets.size());
		float radians = (float) Math.toRadians(uvRotation);
		float cosine = (float) Math.cos(radians);
		float sine = (float) Math.sin(radians);
		var changes = new LinkedHashMap<Corner, Vector2f>();
		for (var corner : targets) {
			var value = new Vector2f(available.get(corner)).sub(center).mul(uvScale);
			var rotated = new Vector2f(value.x * cosine - value.y * sine, value.x * sine + value.y * cosine)
					.add(center).add(uvTranslation.x / textureWidth, uvTranslation.y / textureHeight);
			if (!Float.isFinite(rotated.x) || !Float.isFinite(rotated.y)) {
				return;
			}
			changes.put(corner, rotated);
		}
		session.change(asset -> changes.forEach((corner, value) -> {
			var pair = asset.getAsJsonArray("faces").get(corner.face()).getAsJsonObject().getAsJsonArray("uv").get(corner.corner()).getAsJsonArray();
			pair.set(0, new JsonPrimitive(value.x));
			pair.set(1, new JsonPrimitive(value.y));
		}));
	}

	public void refresh() {
		var currentPart = selectedPart.get();
		var faces = Set.copyOf(selectedFaces.get());
		boolean assetChanged = !session.revision().equals(viewRevision);
		if (assetChanged || !part.equals(currentPart)) {
			cancel();
			part = currentPart;
			viewRevision = session.revision();
			if (assetChanged) {
				preparePreview();
			}
			var candidates = new LinkedHashSet<String>();
			for (int index = 0; index < faces().size(); index++) {
				if (inPart(index)) {
					candidates.add(faceTexture(index));
				}
			}
			if (candidates.isEmpty()) {
				candidates.add("");
			}
			textures.setCandidates(new ArrayList<>(candidates));
			if (!candidates.contains(textureKey)) {
				selectTexture(candidates.getFirst());
			}
			textures.setSelected(textureKey, false);
			var available = points();
			selection.removeIf(corner -> !available.containsKey(corner));
		}
		if (!faces.equals(sharedFaces)) {
			cancel();
			sharedFaces = faces;
			var preferred = faces.stream().filter(index -> index >= 0 && index < faces().size() && inPart(index))
					.map(this::faceTexture).findFirst();
			if (preferred.isPresent() && !preferred.get().equals(textureKey)) {
				selectTexture(preferred.get());
				textures.setSelected(textureKey, false);
			}
			selection.clear();
			points().keySet().stream().filter(corner -> faces.contains(corner.face())).forEach(selection::add);
		}
	}

	public void cancel() {
		if (selectingBox) {
			selection.clear();
			selection.addAll(boxBefore);
		}
		start = null;
		boxEnd = null;
		gestureRevision = null;
		panning = false;
		selectingBox = false;
		boxBefore = Set.of();
		dragPoints.clear();
		delta.zero();
	}

	@Override
	protected void onRemoved() {
		cancel();
		super.onRemoved();
	}

	private JsonArray faces() {
		var value = session.asset().getAsJsonArray("faces");
		return value == null ? new JsonArray() : value;
	}

	private boolean inPart(int index) {
		var face = faces().get(index).getAsJsonObject();
		return part.isEmpty() || part.equals(face.has("partId") ? face.get("partId").getAsString() : PhotonModelDefinition.DEFAULT_PART);
	}

	private String faceTexture(int index) {
		var face = faces().get(index).getAsJsonObject();
		return face.has("material") ? materialTextures.getOrDefault(face.get("material").getAsString(), "") : "";
	}

	private void preparePreview() {
		var errors = new ArrayList<String>();
		var retained = new LinkedHashSet<String>();
		var materials = session.asset().getAsJsonObject("materials");
		if (materials != null) {
			for (var entry : materials.entrySet()) {
				var key = entry.getKey();
				retained.add(key);
				var source = entry.getValue().toString();
				if (!source.equals(materialSources.get(key))) {
					materialSources.put(key, source);
					materialErrors.remove(key);
					try {
						materialTextures.put(key, materialTexture(entry.getValue().getAsJsonObject()));
					} catch (Exception invalid) {
						materialTextures.remove(key);
						materialErrors.put(key, invalid.getMessage() == null ? invalid.toString() : invalid.getMessage());
					}
				}
				if (materialErrors.containsKey(key)) {
					errors.add(key + ": " + materialErrors.get(key));
				}
			}
		}
		materialSources.keySet().retainAll(retained);
		materialTextures.keySet().retainAll(retained);
		materialErrors.keySet().retainAll(retained);
		prepareFill(errors);
		feedback.setText(Component.literal(String.join("\n", errors)));
		feedback.setDisplay(!errors.isEmpty());
	}

	private static String materialTexture(JsonObject value) throws Exception {
		if (!value.has("materialData") || value.get("materialData").getAsString().isEmpty()) {
			return value.has("texture") ? value.get("texture").getAsString() : "";
		}
		var snbt = value.get("materialData").getAsString();
		if (snbt.length() > MAX_MATERIAL_BYTES || snbt.getBytes(StandardCharsets.UTF_8).length > MAX_MATERIAL_BYTES) {
			throw new IllegalArgumentException("材质输入超过UV预览预算。");
		}
		var raw = PhotonModelAssetUtil.material(snbt);
		if (!raw.getStringOr("type", "").equals("texture")) {
			return "";
		}
		var material = IMaterial.CODEC.parse(NbtOps.INSTANCE, raw).getOrThrow();
		if (!(material instanceof TextureMaterial textureMaterial)) {
			throw new IllegalArgumentException("纹理材质未按注册类型解码。");
		}
		return textureMaterial.getTexture().toString();
	}

	private void prepareFill(List<String> errors) {
		var triangles = new ArrayList<List<Corner>>();
		try {
			var prepared = PhotonModelTopologyUtil.prepare(PhotonModelCodec.read(session.asset()));
			for (var group : prepared.groups()) {
				for (int index = 0; index < group.indices().size(); index += 3) {
					var corners = new ArrayList<Corner>();
					for (int vertex = 0; vertex < 3; vertex++) {
						var point = group.corners().get(group.indices().get(index + vertex));
						corners.add(new Corner(point.face(), point.corner()));
					}
					triangles.add(List.copyOf(corners));
				}
			}
		} catch (Exception invalid) {
			errors.add("UV填充准备失败：" + invalid.getMessage());
		}
		fillTriangles = List.copyOf(triangles);
	}

	private boolean hidden(int index) {
		return index < 0 || index >= faces().size() || !inPart(index) || !textureKey.equals(faceTexture(index));
	}

	private Map<Corner, Vector2f> points() {
		var result = new LinkedHashMap<Corner, Vector2f>();
		for (int index = 0; index < faces().size(); index++) {
			if (hidden(index)) {
				continue;
			}
			var uv = faces().get(index).getAsJsonObject().getAsJsonArray("uv");
			if (uv == null) {
				continue;
			}
			for (int corner = 0; corner < uv.size(); corner++) {
				var pair = uv.get(corner).getAsJsonArray();
				result.put(new Corner(index, corner), new Vector2f(pair.get(0).getAsFloat(), pair.get(1).getAsFloat()));
			}
		}
		return result;
	}

	private void selectTexture(@Nullable String key) {
		cancel();
		textureKey = key == null ? "" : key;
		texture = textureKey.isEmpty() ? null : Identifier.tryParse(textureKey);
		textureWidth = 1;
		textureHeight = 1;
		if (texture != null) {
			var image = Minecraft.getInstance().getTextureManager().getTexture(texture).getTexture();
			textureWidth = Math.max(1, image.getWidth(0));
			textureHeight = Math.max(1, image.getHeight(0));
		}
		zoom = Math.clamp(zoom, MIN_ZOOM, Math.max(1, Math.max(textureWidth, textureHeight) / TEXTURE_ZOOM_DIVISOR));
		offset.zero();
		selection.removeIf(corner -> hidden(corner.face()));
	}

	private float side() {
		float available = Math.min(canvas.getContentWidth(), canvas.getContentHeight()) - SCROLLER_SIZE;
		return Math.max(1, available);
	}

	private Vector2f origin() {
		var extent = span();
		return new Vector2f(canvas.getContentX() + (canvas.getContentWidth() - SCROLLER_SIZE - extent.x) / 2,
				canvas.getContentY() + (canvas.getContentHeight() - SCROLLER_SIZE - extent.y) / 2);
	}

	private Vector2f span() {
		float longest = Math.max(textureWidth, textureHeight);
		return new Vector2f(side() * zoom * textureWidth / longest, side() * zoom * textureHeight / longest);
	}

	private Vector2f screen(Vector2f uv) {
		return new Vector2f(uv).mul(span()).add(origin()).add(offset);
	}

	private Vector2f toUV(Vector2f point) {
		return new Vector2f(point).sub(origin()).sub(offset).div(span());
	}

	private Vector2f local(UIEvent event) {
		return canvas.getLocalMouse(event.x, event.y);
	}

	private boolean outside(Vector2f point) {
		return point.x < canvas.getContentX() || point.y < canvas.getContentY()
				|| point.x >= canvas.getContentX() + canvas.getContentWidth() - SCROLLER_SIZE
				|| point.y >= canvas.getContentY() + canvas.getContentHeight() - SCROLLER_SIZE;
	}

	private void zoomView(UIEvent event) {
		var point = local(event);
		if (outside(point) || !Float.isFinite(event.deltaY) || start != null) {
			return;
		}
		var uv = toUV(point);
		zoom = (float) Math.clamp(zoom * Math.pow(ZOOM_FACTOR, event.deltaY), MIN_ZOOM, Math.max(1, Math.max(textureWidth, textureHeight) / TEXTURE_ZOOM_DIVISOR));
		offset.add(point.sub(screen(uv)));
		syncScrollers();
		event.stopPropagation();
	}

	private void begin(UIEvent event) {
		refresh();
		var point = local(event);
		if (outside(point) || event.button != 0 && event.button != 2 || event.target != canvas) {
			return;
		}
		cancel();
		canvas.focus();
		gestureRevision = session.revision();
		panning = event.button == 2;
		start = panning ? point : toUV(point);
		if (!panning) {
			Corner nearest = null;
			float distance = PICK_RADIUS * PICK_RADIUS;
			for (var entry : points().entrySet()) {
				float current = screen(entry.getValue()).distanceSquared(point);
				if (current <= distance) {
					distance = current;
					nearest = entry.getKey();
				}
			}
			if (nearest == null) {
				selectingBox = true;
				boxBefore = new LinkedHashSet<>(selection);
				if (!event.isShiftDown()) {
					selection.clear();
				}
				boxEnd = toUV(point);
			} else {
				if (!selection.contains(nearest) && !event.isShiftDown()) {
					selection.clear();
				}
				selection.add(nearest);
				var available = points();
				selection.forEach(corner -> dragPoints.put(corner, new Vector2f(available.get(corner))));
				publishSelection();
				inspect.accept("/faces/" + nearest.face());
			}
		}
		canvas.startDrag(this, null);
		event.stopPropagation();
	}

	private void drag(UIEvent event) {
		if (start == null || !session.revision().equals(gestureRevision)) {
			cancel();
			return;
		}
		var point = local(event);
		if (!Float.isFinite(point.x) || !Float.isFinite(point.y)) {
			return;
		}
		if (panning) {
			offset.add(new Vector2f(point).sub(start));
			start.set(point);
			syncScrollers();
		} else if (selectingBox) {
			point.x = Math.clamp(point.x, canvas.getContentX(), canvas.getContentX() + Math.max(0, canvas.getContentWidth() - SCROLLER_SIZE));
			point.y = Math.clamp(point.y, canvas.getContentY(), canvas.getContentY() + Math.max(0, canvas.getContentHeight() - SCROLLER_SIZE));
			boxEnd = toUV(point);
		} else {
			delta.set(toUV(point)).sub(start);
			float pixels = event.isShiftDown() ? event.isCtrlDown() ? PIXEL_FINE_STEP : PIXEL_SHIFT_STEP : event.isCtrlDown() ? PIXEL_CONTROL_STEP : 0;
			if (pixels > 0) {
				delta.x = bounded(delta.x, 0, pixels / textureWidth);
				delta.y = bounded(delta.y, 1, pixels / textureHeight);
			}
		}
		event.stopPropagation();
	}

	private float bounded(float value, int axis, float step) {
		float minimum = Float.NEGATIVE_INFINITY;
		float maximum = Float.POSITIVE_INFINITY;
		for (var point : dragPoints.values()) {
			minimum = Math.max(minimum, -point.get(axis));
			maximum = Math.min(maximum, 1 - point.get(axis));
		}
		float first = (float) Math.ceil(minimum / step);
		float last = (float) Math.floor(maximum / step);
		return first <= last ? Math.clamp(Math.round(value / step), first, last) * step : 0;
	}

	private void finish() {
		if (start == null) {
			return;
		}
		if (!session.revision().equals(gestureRevision)) {
			cancel();
			return;
		}
		if (selectingBox && boxEnd != null) {
			for (var entry : points().entrySet()) {
				var point = entry.getValue();
				if (point.x >= Math.min(start.x, boxEnd.x) && point.x <= Math.max(start.x, boxEnd.x)
						&& point.y >= Math.min(start.y, boxEnd.y) && point.y <= Math.max(start.y, boxEnd.y)) {
					selection.add(entry.getKey());
				}
			}
			selectingBox = false;
			publishSelection();
		} else if (!panning && !dragPoints.isEmpty() && delta.lengthSquared() > 0) {
			var changes = new LinkedHashMap<Corner, Vector2f>();
			dragPoints.forEach((corner, point) -> changes.put(corner, new Vector2f(point).add(delta)));
			var expected = gestureRevision;
			cancel();
			session.changeAt(expected, asset -> changes.forEach((corner, point) -> {
				var pair = asset.getAsJsonArray("faces").get(corner.face()).getAsJsonObject().getAsJsonArray("uv").get(corner.corner()).getAsJsonArray();
				pair.set(0, new JsonPrimitive(point.x));
				pair.set(1, new JsonPrimitive(point.y));
			}));
		}
		cancel();
	}

	private void publishSelection() {
		var selected = new LinkedHashSet<Integer>();
		selection.forEach(corner -> selected.add(corner.face()));
		sharedFaces = Set.copyOf(selected);
		selectFaces.accept(sharedFaces);
	}

	private void scroll(int axis, float value) {
		if (start != null || !Float.isFinite(value)) {
			return;
		}
		float viewport = axis == 0 ? canvas.getContentWidth() : canvas.getContentHeight();
		float range = Math.max(0, span().get(axis) - Math.max(1, viewport - SCROLLER_SIZE));
		offset.setComponent(axis, (0.5f - Math.clamp(value, 0, 1)) * range);
	}

	private void syncScrollers() {
		for (int axis = 0; axis < 2; axis++) {
			var scroller = axis == 0 ? horizontal : vertical;
			float viewport = Math.max(1, (axis == 0 ? canvas.getContentWidth() : canvas.getContentHeight()) - SCROLLER_SIZE);
			float extent = span().get(axis);
			float range = Math.max(0, extent - viewport);
			offset.setComponent(axis, Math.clamp(offset.get(axis), -range / 2, range / 2));
			scroller.setScrollBarSize(100 * Math.min(1, viewport / extent));
			scroller.setValue(range > 0 ? 0.5f - offset.get(axis) / range : 0, false);
			scroller.setActive(range > 0);
			scroller.headButton.setActive(range > 0);
			scroller.tailButton.setActive(range > 0);
			scroller.scrollContainer.setActive(range > 0);
			scroller.scrollBar.setActive(range > 0);
		}
	}

	private void draw(GUIContext graphics, float x, float y, float width, float height) {
		refresh();
		syncScrollers();
		syncScrollers();
		DrawerHelperClient.drawSolidRect(graphics, x, y, width, height, BACKGROUND_COLOR);
		if (width <= 0 || height <= 0) {
			return;
		}
		graphics.enableScissor(x, y, Math.max(0, width - SCROLLER_SIZE), Math.max(0, height - SCROLLER_SIZE));
		try {
			var first = origin().add(offset);
			var extent = span();
			if (texture != null) {
				graphics.innerBlit(RenderPipelines.GUI_TEXTURED, texture, first.x, first.x + extent.x,
						first.y, first.y + extent.y, 0, 1, 0, 1, -1);
			}
			var available = points();
			drawFill(graphics, available);
			if (grid) {
				drawGrid(graphics, first, extent);
			}
			var outlines = new LinkedHashMap<Integer, List<Vector2f>>();
			for (var entry : available.entrySet()) {
				var point = dragPoints.containsKey(entry.getKey()) ? new Vector2f(dragPoints.get(entry.getKey())).add(delta) : entry.getValue();
				outlines.computeIfAbsent(entry.getKey().face(), _ -> new ArrayList<>()).add(screen(point));
			}
			drawEdges(graphics, outlines);
			for (var entry : available.entrySet()) {
				var value = dragPoints.containsKey(entry.getKey()) ? new Vector2f(dragPoints.get(entry.getKey())).add(delta) : entry.getValue();
				var point = screen(value);
				if (selection.contains(entry.getKey())) {
					DrawerHelperClient.drawSolidRect(graphics, point.x - 3, point.y - 3, 6, 6, 0xff000000);
					DrawerHelperClient.drawSolidRect(graphics, point.x - 2, point.y - 2, 4, 4, -1);
				}
			}
			if (selectingBox && start != null && boxEnd != null) {
				var a = screen(start);
				var b = screen(boxEnd);
				DrawerHelperClient.drawSolidRect(graphics, Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(a.x - b.x), Math.abs(a.y - b.y), 0x40ffba50);
			}
		} finally {
			graphics.disableScissor();
		}
	}

	private void drawEdges(GUIContext graphics, Map<Integer, List<Vector2f>> outlines) {
		var edges = new LinkedHashMap<List<Vector2f>, Boolean>();
		for (var entry : outlines.entrySet()) {
			var points = entry.getValue();
			boolean selected = sharedFaces.contains(entry.getKey()) || selection.stream().anyMatch(corner -> corner.face() == entry.getKey());
			for (int index = 0; index < points.size(); index++) {
				var a = points.get(index);
				var b = points.get((index + 1) % points.size());
				boolean forward = a.x < b.x || a.x == b.x && a.y <= b.y;
				var edge = forward ? List.of(a, b) : List.of(b, a);
				edges.merge(edge, selected, Boolean::logicalOr);
			}
		}
		for (var entry : edges.entrySet()) {
			int outline = entry.getValue() ? 0x80000000 : 0x66000000;
			int line = entry.getValue() ? 0x80ffffff : 0x66ffffff;
			DrawerHelperClient.drawLines(graphics, entry.getKey(), outline, outline, 3);
			DrawerHelperClient.drawLines(graphics, entry.getKey(), line, line, 1);
		}
	}

	private void drawFill(GUIContext graphics, Map<Corner, Vector2f> available) {
		var selected = new LinkedHashSet<>(sharedFaces);
		selection.forEach(corner -> selected.add(corner.face()));
		var seen = new LinkedHashSet<List<Vector2f>>();
		for (var triangle : fillTriangles) {
			if (!selected.contains(triangle.getFirst().face()) || triangle.stream().anyMatch(corner -> !available.containsKey(corner))) {
				continue;
			}
			var uv = new ArrayList<Vector2f>();
			for (var corner : triangle) {
				var point = dragPoints.containsKey(corner) ? new Vector2f(dragPoints.get(corner)).add(delta) : new Vector2f(available.get(corner));
				uv.add(new Vector2f(point.x == 0 ? 0 : point.x, point.y == 0 ? 0 : point.y));
			}
			var key = new ArrayList<>(uv);
			key.sort(Comparator.comparingDouble((Vector2f point) -> point.x).thenComparingDouble(point -> point.y));
			if (!seen.add(List.copyOf(key))) {
				continue;
			}
			var a = screen(uv.get(0));
			var b = screen(uv.get(1));
			var c = screen(uv.get(2));
			double winding = ((double) b.x - a.x) * (c.y - a.y) - ((double) b.y - a.y) * (c.x - a.x);
			if (!Double.isFinite(winding) || winding == 0) {
				continue;
			}
			boolean reverse = (winding > 0) != (graphics.currentPose().determinant() < 0);
			int color = ColorUtils.mulColor(SELECTED_FACE_COLOR, graphics.getElementColor());
			var state = new FloatColoredTriangleRenderState(LDLibRenderPipelines.GUI_TRIANGLE, TextureSetup.noTexture(),
					graphics.pose.copyPose(), a, reverse ? c : b, reverse ? b : c, color, color, color, graphics.peekScissor());
			if (state.bounds() != null) {
				graphics.addGuiElement(state);
			}
		}
	}

	private void drawGrid(GUIContext graphics, Vector2f first, Vector2f extent) {
		int stepX = Math.max(1, (int) Math.ceil(MIN_GRID_SPACING * textureWidth / extent.x));
		int stepY = Math.max(1, (int) Math.ceil(MIN_GRID_SPACING * textureHeight / extent.y));
		for (int pixel = 0; pixel <= textureWidth; pixel += stepX) {
			DrawerHelperClient.drawSolidRect(graphics, first.x + pixel * extent.x / textureWidth, first.y, 0.5f, extent.y, GRID_COLOR);
		}
		for (int pixel = 0; pixel <= textureHeight; pixel += stepY) {
			DrawerHelperClient.drawSolidRect(graphics, first.x, first.y + pixel * extent.y / textureHeight, extent.x, 0.5f, GRID_COLOR);
		}
	}
}
