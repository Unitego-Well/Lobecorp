package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.configurator.EditAction;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ColorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.editor.ui.menu.MenuTab;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.RuntimeValue;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import com.lowdragmc.photon.gui.editor.view.FXObjectTreeNode;
import com.lowdragmc.photon.gui.editor.view.timeline.AnimationTrackEditor.AnimationTrackUIState;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.FlexDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.client.photon.runtime.PhotonBeamLengthAccess;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.util.photon.editor.PhotonCurveEditUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorShortcutUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTooltipUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEmitterSpawnerTextUtil;
import org.unitego.lobecorp.util.photon.clipboard.PhotonSystemClipboardUtil;
import org.unitego.lobecorp.mixin.photon.editor.client.AnimationTrackUIStateAccessor;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import org.joml.Vector2f;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.Locale;

@SuppressWarnings({"FieldMayBeFinal", "unused"})
public class PhotonEditorTools {
	/**
	 * 只关联编辑器预览实例；游戏内 FXRuntime 不进入此表。
	 */
	private static final Map<FXRuntime, WeakReference<PhotonEditorTools>> PREVIEWS = new WeakHashMap<>();
	/**
	 * 与原设置窗口一致的扩展对话框宽度，单位 GUI 像素。
	 */
	private static final int DIALOG_WIDTH = 350;
	/**
	 * 对话框内容的最大滚动高度，单位 GUI 像素。
	 */
	private static final int CONTENT_HEIGHT = 220;
	/**
	 * 文档段落之间的间距，单位 GUI 像素。
	 */
	private static final int DOCUMENT_GAP = 4;
	/**
	 * Shift 逐帧移动的 tick 步长。
	 */
	private static final int LARGE_STEP = 10;
	/**
	 * 参数面板默认白色，保留 Alpha。
	 */
	private static final int DEFAULT_COLOR = -1;
	/**
	 * 粒子模块的可复制配置路径，与 Photon 原字段对应。
	 */
	private static final List<String> MODULES = List.of("rotationOverLifetime", "colorOverLifetime", "sizeOverLifetime",
			"velocityOverLifetime", "forceOverLifetime", "uvAnimation", "renderer", "emission", "shape");
	/**
	 * 快捷键对应的独立功能开关。
	 */
	private static final Map<String, String> ACTION_FEATURES = Map.ofEntries(
			Map.entry("fit_selected", "fit"), Map.entry("fit_all", "fit"),
			Map.entry("step_back", "step"), Map.entry("step_forward", "step"),
			Map.entry("step_back_large", "step"), Map.entry("step_forward_large", "step"),
			Map.entry("duplicate", "precise"), Map.entry("move", "batch_move"), Map.entry("scale", "time_scale"),
			Map.entry("range_in", "range_preview"), Map.entry("range_out", "range_preview"),
			Map.entry("range_loop", "range_preview"), Map.entry("solo", "solo"), Map.entry("cancel", "precise"));
	private FXEditor editor;
	private long previewStart;
	private long previewEnd;
	private boolean loopPreview;
	private Map<RuntimeValue<?>, Object> overrides = new LinkedHashMap<>();
	private Map<IFXObject, Boolean> visibility = new LinkedHashMap<>();
	private Map<BeamEmitter, Float> beamLengths = new LinkedHashMap<>();
	private FXRuntime previewRuntime;
	private CompoundTag moduleClipboard;
	private String clipboardModule;
	private Dialog parameterDialog;
	private FXRuntime boundRuntime;
	private Map<AnimatedProperty, Map<Long, Integer>> linkedTangents = new WeakHashMap<>();
	/**
	 * 只登记扩展模块开关控件；弱键不会保留已移除的面板。
	 */
	private Map<UIElement, String> featureControls = new WeakHashMap<>();
	/**
	 * 扩展运行时控件和编辑器设置的翻译键前缀。
	 */
	private static final String CONTROL_PREFIX = "photon.lobecorp.";
	private static final String EDITOR_CONTROL_PREFIX = "photon.lobecorp.editor.";

	public static void syncTangent(TimelineContext ctx, AnimationTrackUIState state) {
		var tools = of(ctx.editor());
		if (tools == null || !tools.enabled("tangents"))
			return;
		var access = (AnimationTrackUIStateAccessor) state;
		var property = access.lobecorp$getDragProperty();
		int axis = access.lobecorp$getDragAxis(), index = access.lobecorp$getDragKey(), handle = access.lobecorp$getDragHandle();
		if (property == null || axis < 0 || index < 0 || handle == 0)
			return;
		var links = tools.linkedTangents.get(property);
		long id = (long) axis << 32 | index & 0xffffffffL;
		if (links == null || links.getOrDefault(id, -1) != property.keyCount(axis))
			return;
		var point = property.key(axis, index);
		var active = handle == 1 ? property.inHandle(axis, index) : property.outHandle(axis, index);
		var opposite = handle == 1 ? property.outHandle(axis, index) : property.inHandle(axis, index);
		if (active == null || opposite == null)
			return;
		var direction = new Vector2f(active).sub(point);
		if (direction.lengthSquared() == 0)
			return;
		var next = direction.normalize().mul(-new Vector2f(opposite).sub(point).length()).add(point);
		if (!Float.isFinite(next.x) || !Float.isFinite(next.y))
			return;
		if (handle == 1)
			property.setOutHandle(axis, index, next.x, next.y);
		else
			property.setInHandle(axis, index, next.x, next.y);
		ctx.refreshLaneLayout();
		ctx.refreshPreview();
	}

	public static boolean handlesPreviewEnd(FXTimelineView timeline) {
		var tools = of(timeline.fxEditor);
		return tools != null && tools.enabled("range_preview") && tools.loopPreview && tools.previewEnd > tools.previewStart;
	}

	public void bindRuntime() {
		if (boundRuntime != null)
			PREVIEWS.remove(boundRuntime);
		boundRuntime = editor.runtime;
		if (boundRuntime != null)
			PREVIEWS.put(boundRuntime, new WeakReference<>(this));
	}

	public static void applyPreview(FXRuntime runtime) {
		var reference = PREVIEWS.get(runtime);
		var tools = reference == null ? null : reference.get();
		if (tools != null)
			tools.applyOverrides(runtime);
	}

	public static boolean visible(IFXObject object) {
		if (!(object.getScene() instanceof FXRuntime runtime))
			return true;
		var reference = PREVIEWS.get(runtime);
		var tools = reference == null ? null : reference.get();
		return tools == null || tools.previewVisible(runtime, object);
	}

	public static boolean controlEnabled(String name, UIElement element) {
		String feature = name.startsWith(PhotonEmitterSpawnerTextUtil.PREFIX) ? PhotonEmitterSpawnerTextUtil.FEATURE
				: name.contains("rotation_cycle") ? "rotation_cycle"
				: name.contains("view_depth_sort") ? "depth_sort"
				: name.contains("cycle_offset") || name.contains("cycle_time_offset") ? "time_offset" : null;
		if (feature == null && name.contains("lifetime_cycle")) {
			for (var parent = element.getParent(); parent != null; parent = parent.getParent()) {
				if (!(parent instanceof PhotonConfiguratorNameAccess access) || access.lobecorp$name() == null)
					continue;
				String group = access.lobecorp$name().toLowerCase(Locale.ROOT);
				if (group.contains("colorover")) {
					feature = "color_cycle";
					break;
				}
				if (group.contains("sizeover")) {
					feature = "size_cycle";
					break;
				}
				if (group.contains("velocityover")) {
					feature = "velocity_cycle";
					break;
				}
				if (group.contains("forceover")) {
					feature = "force_cycle";
					break;
				}
				if (group.contains("uvanimation")) {
					feature = "uv_cycle";
					break;
				}
			}
		}
		return feature == null || enabled(element, feature);
	}

	public PhotonEditorTools(FXEditor editor) {
		this.editor = editor;
		editor.selfAndAllChildren().filter(PhotonConfiguratorNameAccess.class::isInstance)
				.forEach(element -> registerControl(element, ((PhotonConfiguratorNameAccess) element).lobecorp$name()));
		editor.addEventListener(UIEvents.KEY_DOWN, this::onKeyDown, true);
		editor.addEventListener(UIEvents.TICK, event -> tick());
	}

	public static void updateControlRegistration(UIElement element, String name, boolean attached) {
		var editor = findEditor(element);
		var tools = editor == null ? null : of(editor);
		if (tools == null) {
			return;
		}

		if (!attached) {
			tools.featureControls.remove(element);
			return;
		}

		tools.registerControl(element, name);
	}

	private void registerControl(UIElement element, String name) {
		if (!isFeatureControlName(name)) {
			return;
		}

		featureControls.put(element, name);
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean isFeatureControlName(String name) {
		return name != null && name.startsWith(CONTROL_PREFIX) && !name.startsWith(EDITOR_CONTROL_PREFIX);
	}

	public static FXEditor findEditor(UIElement element) {
		for (UIElement current = element; current != null; current = current.getParent()) {
			if (current instanceof FXEditor editor)
				return editor;
		}
		return null;
	}

	public static boolean enabled(UIElement element, String feature) {
		var editor = findEditor(element);
		return editor == null || PhotonEditorSettings.of(editor).enabled(feature);
	}

	private boolean enabled(String feature) {
		return PhotonEditorSettings.of(editor).enabled(feature);
	}

	private static String key(String name) {
		return PhotonEditorTextUtil.key(name);
	}

	private static PhotonEditorTools of(Editor editor) {
		return ((PhotonEditorAccess) editor).lobecorp$tools();
	}

	public static void installMenus(FXEditor editor) {
		editor.menuContainer.addChild(directTab(editor, "documents", () -> of(editor).documents()));
		editor.menuContainer.addChild(directTab(editor, "settings", editor::openSettingsPanel));
		editor.menuContainer.addChild(new MenuTab(editor) {
			@Override
			protected Component getComponent() {
				return Component.translatable(key("tools"));
			}

			@Override
			protected TreeBuilder.Menu createDefaultMenu() {
				var menu = TreeBuilder.Menu.start();
				of(editor).toolMenu(menu);
				return menu;
			}
		}.createMenuTab());
	}

	private static UIElement directTab(FXEditor editor, String name, Runnable action) {
		return new MenuTab(editor) {
			@Override
			protected Component getComponent() {
				return Component.translatable(key(name));
			}

			@Override
			protected TreeBuilder.Menu createDefaultMenu() {
				return TreeBuilder.Menu.start();
			}

			@Override
			public UIElement createMenuTab() {
				return super.createMenuTab().addEventListener(UIEvents.MOUSE_DOWN, event -> {
					event.stopImmediatePropagation();
					if (event.button == 0)
						action.run();
				}, true);
			}
		}.createMenuTab();
	}

	private void leaf(TreeBuilder.Menu menu, String feature, Runnable action) {
		if (enabled(feature))
			menu.leaf(key("feature." + feature), () -> safely(action));
	}

	private void toolMenu(TreeBuilder.Menu menu) {
		leaf(menu, "precise", () -> editSelection(false));
		leaf(menu, "batch_move", () -> editSelection(false));
		leaf(menu, "time_scale", () -> editSelection(true));
		if (enabled("fit")) {
			menu.leaf(key("action.fit_selected"), () -> safely(() -> fit(false)));
			menu.leaf(key("action.fit_all"), () -> safely(() -> fit(true)));
		}
		if (enabled("tangents"))
			menu.branch(key("feature.tangents"), branch -> {
				for (String preset : List.of("linear", "ease_in", "ease_out", "ease_both", "step", "flat", "align", "break", "reset")) {
					branch.leaf(key(preset), () -> safely(() -> tangents(preset)));
				}
			});
		leaf(menu, "range_preview", this::rangeDialog);
		leaf(menu, "parameters", this::parameterPanel);
		leaf(menu, "batch_properties", this::batchPanel);
		leaf(menu, "presets", this::presetPanel);
		leaf(menu, "solo", this::toggleSolo);
		leaf(menu, "seed", this::seedPanel);
		leaf(menu, "step", () -> step(1));
		leaf(menu, "diagnostics", this::diagnostics);
	}

	private Dialog dialog(String title, UIElement content) {
		var dialog = new Dialog().width(TaffyDimension.length(DIALOG_WIDTH)).setTitle(key(title));
		var scroller = new ScrollerView();
		scroller.layout(layout -> layout.widthPercent(100).height(CONTENT_HEIGHT).minHeight(0));
		scroller.addScrollViewChild(content.layout(layout -> layout.widthPercent(100)));
		dialog.addContent(scroller);
		dialog.addButton(new Button().setText(key("close")).setOnClick(event -> dialog.close()));
		return dialog;
	}

	private void documents() {
		var content = new UIElement().layout(layout -> layout.flexDirection(FlexDirection.COLUMN).gapAll(DOCUMENT_GAP));
		content.addChild(documentText(Component.translatable(key("help"))));
		content.addChild(documentText(Component.translatable(key("details"))));
		content.addChild(documentText(Component.translatable(key("clipboard_help"))));
		for (var action : PhotonEditorSettings.DEFAULT_KEYS.keySet().stream().sorted().toList()) {
			content.addChild(documentText(Component.translatable(key("action." + action))
					.append(Component.literal(": " + PhotonEditorSettings.of(editor).binding(action)))));
		}
		dialog("documents", content).show(editor);
	}

	private static UIElement documentText(Component text) {
		return new TextElement().setText(text)
				.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(true).textWrap(TextWrap.WRAP))
				.layout(layout -> layout.widthPercent(100).flexShrink(0));
	}

	private void safely(Runnable action) {
		try {
			action.run();
		} catch (IllegalArgumentException error) {
			message();
		}
	}

	private void message() {
		dialog("tools", new TextElement().setText(key("invalid"))).show(editor);
	}

	private void onKeyDown(UIEvent event) {
		if (!enabled("shortcuts") || editor.runtime == null)
			return;
		for (UIElement current = event.target; current != null && current != editor; current = current.getParent()) {
			if (current instanceof TextField || current instanceof TextArea || current instanceof Dialog)
				return;
		}
		var settings = PhotonEditorSettings.of(editor);
		for (String action : PhotonEditorSettings.DEFAULT_KEYS.keySet()) {
			if (!enabled(ACTION_FEATURES.get(action)) || !PhotonEditorShortcutUtil.matches(settings.binding(action), event))
				continue;
			if (action.equals("cancel") && ((PhotonTimelineEditAccess) editor.timelineView).lobecorp$states().values().stream()
					.noneMatch(state -> state instanceof AnimationTrackUIState st
							&& ((AnimationTrackUIStateAccessor) st).lobecorp$getDragSnapshot() != null))
				return;
			if (!action.equals("solo") && !action.equals("cancel") && !editor.timelineView.isSelfOrChildHover()
					&& !isWithin(event.target, editor.timelineView))
				return;
			safely(() -> runAction(action));
			event.stopImmediatePropagation();
			return;
		}
	}

	private static boolean isWithin(UIElement element, UIElement parent) {
		for (var current = element; current != null; current = current.getParent())
			if (current == parent)
				return true;
		return false;
	}

	private void runAction(String action) {
		switch (action) {
			case "fit_selected" -> fit(false);
			case "fit_all" -> fit(true);
			case "step_back" -> step(-1);
			case "step_forward" -> step(1);
			case "step_back_large" -> step(-LARGE_STEP);
			case "step_forward_large" -> step(LARGE_STEP);
			case "move" -> editSelection(false);
			case "scale" -> editSelection(true);
			case "duplicate" -> ((PhotonTimelineEditAccess) editor.timelineView).lobecorp$duplicateNative();
			case "range_in" -> previewStart = editor.timelineView.currentTimeTicks();
			case "range_out" -> previewEnd = editor.timelineView.currentTimeTicks();
			case "range_loop" -> {
				if (previewEnd <= previewStart)
					throw new IllegalArgumentException();
				loopPreview = !loopPreview;
			}
			case "solo" -> toggleSolo();
			case "cancel" -> cancelDrag();
			default -> {
			}
		}
	}

	private List<PhotonCurveEditUtil.Selection> selections() {
		var result = PhotonCurveEditUtil.selections(editor.timelineView);
		if (result.isEmpty())
			throw new IllegalArgumentException();
		return result;
	}

	private void editSelection(boolean scaling) {
		var selection = selections();
		var keys = PhotonCurveEditUtil.keys(selection);
		float start = (float) keys.stream().mapToDouble(k -> k.point().x).min().orElseThrow();
		float[] time = {0}, value = {0}, factor = {1};
		var group = new ConfiguratorGroup("", false).hideTitle();
		if (scaling)
			group.addConfigurator(new NumberConfigurator(key("factor"), () -> factor[0],
					v -> factor[0] = v.floatValue(), 1, false).setRange(PhotonCurveEditUtil.KEY_GAP, Float.MAX_VALUE));
		else {
			group.addConfigurator(new NumberConfigurator(key("time_delta"), () -> time[0],
					v -> time[0] = v.floatValue(), 0, false));
			if (keys.stream().noneMatch(k -> k.axis() == PhotonCurveEditUtil.COLOR_AXIS))
				group.addConfigurator(new NumberConfigurator(key("value_delta"), () -> value[0],
						v -> value[0] = v.floatValue(), 0, false));
			if (keys.size() == 1) {
				var point = keys.getFirst().point();
				group.addConfigurator(new NumberConfigurator(key("time"), () -> start + time[0],
						v -> time[0] = v.floatValue() - start, point.x, true).setRange(0, Float.MAX_VALUE));
				if (keys.getFirst().axis() != PhotonCurveEditUtil.COLOR_AXIS)
					group.addConfigurator(new NumberConfigurator(key("value"), () -> point.y + value[0],
							v -> value[0] = v.floatValue() - point.y, point.y, true));
			}
		}
		var currentRuntime = editor.runtime;
		var dialog = dialog(scaling ? "feature.time_scale" : "feature.precise", group);
		dialog.addButton(new Button().setText(key("apply")).setOnClick(event -> safely(() -> {
			if (editor.runtime != currentRuntime)
				throw new IllegalArgumentException();
			PhotonCurveEditUtil.transform(editor.timelineView, selection, time[0], value[0], factor[0]);
			dialog.close();
		})));
		dialog.show(editor);
	}

	private void tangents(String preset) {
		var selection = selections();
		if (!preset.equals("break"))
			PhotonCurveEditUtil.tangent(editor.timelineView, selection, preset.equals("reset") ? "linear" : preset);
		for (var selected : selection) {
			var links = linkedTangents.computeIfAbsent(selected.property(), property -> new LinkedHashMap<>());
			for (long id : selected.keys()) {
				int axis = (int) (id >>> 32);
				if (axis < 0)
					continue;
				if (preset.equals("align"))
					links.put(id, selected.property().keyCount(axis));
				else
					links.remove(id);
			}
		}
	}

	private void fit(boolean all) {
		if (editor.runtime == null)
			return;
		var access = (PhotonTimelineEditAccess) editor.timelineView;
		double start = Double.POSITIVE_INFINITY, end = 0;
		var selected = all ? List.<PhotonCurveEditUtil.Selection>of() : PhotonCurveEditUtil.selections(editor.timelineView);
		var properties = new LinkedHashMap<AnimatedProperty, Set<Long>>();
		if (!all && !selected.isEmpty()) {
			for (var selection : selected)
				properties.put(selection.property(), selection.keys());
		} else {
			for (var track : editor.runtime.fxData.timeline().leafTracks(true)) {
				if (track instanceof AnimationTrack animation)
					for (var property : animation.properties())
						properties.put(property, Set.of());
				if (all)
					end = Math.max(end, track.contentEnd());
			}
		}
		for (var entry : properties.entrySet()) {
			var property = entry.getKey();
			if (entry.getValue().isEmpty()) {
				for (double time : property.keyframeTimes()) {
					start = Math.min(start, time);
					end = Math.max(end, time);
				}
			} else
				for (long id : entry.getValue()) {
					var point = PhotonCurveEditUtil.keys(selected).stream()
							.filter(k -> k.selection().property() == property && ((long) k.axis() << 32 | k.index() & 0xffffffffL) == id)
							.findFirst().orElseThrow().point();
					start = Math.min(start, point.x);
					end = Math.max(end, point.x);
				}
			PhotonCurveEditUtil.fit(property, entry.getValue());
		}
		access.lobecorp$states().forEach((track, state) -> {
			var trackEditor = access.lobecorp$trackEditor(track);
			if (trackEditor instanceof PhotonCurveViewAccess curve)
				properties.keySet().forEach(curve::lobecorp$resetView);
		});
		access.lobecorp$fitTime(all || !Double.isFinite(start) ? 0 : start, end);
		editor.timelineView.refreshLaneLayout();
	}

	private void cancelDrag() {
		var timeline = editor.timelineView;
		var access = (PhotonTimelineEditAccess) timeline;
		access.lobecorp$states().forEach((track, state) -> {
			var trackEditor = access.lobecorp$trackEditor(track);
			if (trackEditor instanceof PhotonCurveViewAccess curve && state instanceof AnimationTrackUIState st)
				curve.lobecorp$cancelCurve(timeline, st);
		});
		access.lobecorp$cancelClipDrag();
		if (editor.getModularUI() != null)
			editor.getModularUI().getDragHandler().stopDrag();
	}

	private void step(int ticks) {
		var manager = editor.sceneView.particleManager;
		manager.pause();
		editor.sceneView.requestSimulateTo(Math.max(0, manager.getTime() + ticks));
	}

	private void rangeDialog() {
		if (previewEnd <= previewStart)
			previewEnd = Math.max(previewStart + 1, editor.runtime == null ? 1
					: (long) Math.ceil(editor.runtime.fxData.timeline().getDuration()));
		var group = new ConfiguratorGroup("", false).hideTitle();
		group.addConfigurator(new NumberConfigurator(key("preview_start"), () -> previewStart,
				v -> previewStart = v.longValue(), 0, true).setRange(0, Long.MAX_VALUE));
		group.addConfigurator(new NumberConfigurator(key("preview_end"), () -> previewEnd,
				v -> previewEnd = v.longValue(), previewEnd, true).setRange(1, Long.MAX_VALUE));
		group.addConfigurator(new BooleanConfigurator(key("preview_loop"), () -> loopPreview,
				v -> loopPreview = v && previewEnd > previewStart, false, true));
		dialog("feature.range_preview", group).show(editor);
	}

	private void tick() {
		featureControls.forEach((element, name) -> {
			boolean display = controlEnabled(name, element);
			if (element.isDisplayed() != display) {
				element.setDisplay(display);
			}
		});

		if (editor.runtime == null) {
			return;
		}

		if (!enabled("parameters") && parameterDialog != null) {
			parameterDialog.close();
		}

		if (!enabled("solo") && !visibility.isEmpty()) {
			restoreSolo();
		}

		if (enabled("range_preview") && loopPreview && previewEnd > previewStart) {
			var manager = editor.sceneView.particleManager;
			boolean playing = manager.isPlaying();
			long time = manager.getTime();
			if (playing && (time > previewEnd || time < previewStart)) {
				editor.sceneView.simulateTo(previewStart);
				manager.play();
			}
		}
	}

	public void applyOverrides(FXRuntime runtime) {
		if (runtime != previewRuntime || !enabled("parameters"))
			return;
		overrides.forEach(RuntimeValue::setRaw);
		beamLengths.forEach((beam, length) -> ((PhotonBeamLengthAccess) beam.runtime()).lobecorp$setBeamLength(length));
	}

	public boolean previewVisible(FXRuntime runtime, IFXObject object) {
		if (runtime != previewRuntime || visibility.isEmpty() || !enabled("solo"))
			return true;
		return visibility.getOrDefault(object, true);
	}

	private List<IFXObject> selectedObjects() {
		return editor.hierarchyView.treeList.getSelected().stream().map(FXObjectTreeNode::getKey).toList();
	}

	private List<ParticleEmitter> selectedParticles() {
		var result = selectedObjects().stream().filter(ParticleEmitter.class::isInstance).map(ParticleEmitter.class::cast).toList();
		if (result.isEmpty())
			throw new IllegalArgumentException();
		return result;
	}

	private void parameterPanel() {
		if (parameterDialog != null) {
			parameterDialog.close();
			parameterDialog = null;
		}
		var objects = selectedObjects();
		if (objects.isEmpty())
			throw new IllegalArgumentException();
		previewRuntime = editor.runtime;
		var content = new UIElement();
		content.addChild(new TextElement().setText(key("parameter_tip")));
		for (var object : objects) {
			var group = new ConfiguratorGroup(object.getName(), false);
			if (object instanceof ParticleEmitter particle) {
				float[] size = {sample(particle.runtime().startSize.get().x), sample(particle.runtime().startSize.get().y),
						sample(particle.runtime().startSize.get().z)};
				for (int axis = 0; axis < size.length; axis++) {
					int component = axis;
					group.addConfigurator(new NumberConfigurator("ParticleConfig.startSize." + "XYZ".charAt(axis), () -> size[component],
							value -> {
								size[component] = value.floatValue();
								overrides.put(particle.runtime().startSize, new NumberFunction3(size[0], size[1], size[2]));
								replayPreview();
							}, size[axis], false).setRange(0, Float.MAX_VALUE).setTips(key("parameter_tip")));
				}
				float[] speed = {sample(particle.runtime().startSpeed.get())};
				group.addConfigurator(new NumberConfigurator("ParticleConfig.startSpeed", () -> speed[0], value -> {
					speed[0] = value.floatValue();
					overrides.put(particle.runtime().startSpeed, NumberFunction.constant(speed[0]));
					replayPreview();
				}, speed[0], false).setTips(key("parameter_tip")));
				int[] color = {particle.runtime().startColor.get().get(0, () -> 0f).intValue()};
				group.addConfigurator(new ColorConfigurator("ParticleConfig.startColor", () -> color[0], value -> {
					color[0] = value;
					overrides.put(particle.runtime().startColor, NumberFunction.color(value));
					replayPreview();
				}, color[0], false).setTips(key("parameter_tip")));
			} else if (object instanceof BeamEmitter beam) {
				float[] length = {beam.getConfig().getEnd().length()}, width = {sample(beam.runtime().width.get())};
				group.addConfigurator(new NumberConfigurator("BeamConfig.end", () -> length[0], value -> {
					length[0] = value.floatValue();
					beamLengths.put(beam, length[0]);
					replayPreview();
				}, length[0], false).setRange(0, Float.MAX_VALUE).setTips(key("parameter_tip")));
				group.addConfigurator(new NumberConfigurator("BeamConfig.width", () -> width[0], value -> {
					width[0] = value.floatValue();
					overrides.put(beam.runtime().width, NumberFunction.constant(width[0]));
					replayPreview();
				}, width[0], false).setRange(0, Float.MAX_VALUE).setTips(key("parameter_tip")));
			}
			content.addChild(group);
		}
		parameterDialog = dialog("feature.parameters", content);
		parameterDialog.addButton(new Button().setText(key("reset")).setOnClick(event -> clearParameters()));
		parameterDialog.setOnClose(() -> {
			clearParameters();
			parameterDialog = null;
		});
		parameterDialog.show(editor);
	}

	private static float sample(NumberFunction function) {
		return function.get(0, () -> 0f).floatValue();
	}

	private void replayPreview() {
		if (editor.runtime == previewRuntime) {
			long time = editor.sceneView.particleManager.getTime();
			replayAt(time);
		}
	}

	private void replayAt(long time) {
		boolean playing = editor.sceneView.particleManager.isPlaying();
		editor.sceneView.particleManager.pause();
		editor.sceneView.simulateTo(0);
		editor.sceneView.simulateTo(time);
		if (playing)
			editor.sceneView.particleManager.play();
	}

	private void clearParameters() {
		overrides.keySet().forEach(RuntimeValue::clear);
		for (var beam : beamLengths.keySet())
			((PhotonBeamLengthAccess) beam.runtime()).lobecorp$setBeamLength(null);
		overrides.clear();
		beamLengths.clear();
		if (editor.runtime == previewRuntime && editor.runtime != null) {
			long time = editor.sceneView.particleManager.getTime();
			replayAt(time);
		}
	}

	private void toggleSolo() {
		if (!visibility.isEmpty()) {
			restoreSolo();
			return;
		}
		var selected = selectedObjects();
		if (selected.isEmpty() || editor.runtime == null)
			throw new IllegalArgumentException();
		previewRuntime = editor.runtime;
		for (var sceneObject : editor.runtime.getAllSceneObjects()) {
			if (!(sceneObject instanceof IFXObject object))
				continue;
			boolean keep = selected.stream().anyMatch(s -> s == object || s.transform().isInheritedParent(object.transform())
					|| object.transform().isInheritedParent(s.transform()));
			visibility.put(object, keep);
		}
	}

	private void restoreSolo() {
		visibility.clear();
	}

	public void clearPreview() {
		linkedTangents.clear();
		if (boundRuntime != null)
			PREVIEWS.remove(boundRuntime);
		boundRuntime = null;
		loopPreview = false;
		previewStart = 0;
		previewEnd = 0;
		if (parameterDialog != null)
			parameterDialog.close();
		clearParameters();
		restoreSolo();
		previewRuntime = null;
	}

	private void seedPanel() {
		var group = new ConfiguratorGroup("", false).hideTitle();
		group.addConfigurator(new NumberConfigurator("photon.gui.editor.fx_info.seed", editor.sceneView.effect::getSeed,
				value -> {
					long time = editor.timelineView.currentTimeTicks();
					editor.sceneView.effect.setSeed(value.longValue());
					editor.sceneView.particleManager.setTimeOffset(value.longValue() == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value.longValue()));
					replayAt(time);
				}, editor.sceneView.effect.getSeed(), true).setTips(key("feature.seed") + PhotonEditorTooltipUtil.TOOLTIP_SUFFIX));
		dialog("feature.seed", group).show(editor);
	}

	private void batchPanel() {
		var particles = selectedParticles();
		float[] speed = {sample(particles.getFirst().config.getStartSpeed())}, size = {sample(particles.getFirst().config.getStartSize().x)};
		int[] color = {DEFAULT_COLOR}, order = {particles.getFirst().config.renderer.getOrderInLayer()};
		boolean[] checks = new boolean[6];
		var source = particles.getFirst();
		var group = new ConfiguratorGroup("", false).hideTitle();
		group.addConfigurator(new BooleanConfigurator("ParticleConfig.startSpeed", () -> checks[0], v -> checks[0] = v, false, false).setTips(key("batch_tip")));
		group.addConfigurator(new NumberConfigurator("ParticleConfig.startSpeed", () -> speed[0], v -> speed[0] = v.floatValue(), speed[0], false).setTips(key("batch_tip")));
		group.addConfigurator(new BooleanConfigurator("ParticleConfig.startSize", () -> checks[1], v -> checks[1] = v, false, false).setTips(key("batch_tip")));
		group.addConfigurator(new NumberConfigurator("ParticleConfig.startSize", () -> size[0], v -> size[0] = v.floatValue(), size[0], false).setRange(0, Float.MAX_VALUE).setTips(key("batch_tip")));
		group.addConfigurator(new BooleanConfigurator("ParticleConfig.startColor", () -> checks[2], v -> checks[2] = v, false, false).setTips(key("batch_tip")));
		group.addConfigurator(new ColorConfigurator("ParticleConfig.startColor", () -> color[0], v -> color[0] = v, color[0], false).setTips(key("batch_tip")));
		group.addConfigurator(new BooleanConfigurator("photon.emitter.config.renderer.orderInLayer", () -> checks[3], v -> checks[3] = v, false, false).setTips(key("batch_tip")));
		group.addConfigurator(new NumberConfigurator("photon.emitter.config.renderer.orderInLayer", () -> order[0], v -> order[0] = v.intValue(), order[0], false).setTips(key("batch_tip")));
		group.addConfigurator(new BooleanConfigurator(key("batch_materials"), () -> checks[4], v -> checks[4] = v, false, false));
		group.addConfigurator(new BooleanConfigurator(key("batch_cycles"), () -> checks[5], v -> checks[5] = v, false, false));
		group.addChild(new TextElement().setText(Component.translatable(key("batch_source")).append(Component.literal(": " + source.getName()))));
		var dialog = dialog("feature.batch_properties", new UIElement().addChildren(new TextElement().setText(key("batch_tip")), group));
		var runtime = editor.runtime;
		dialog.addButton(new Button().setText(key("apply")).setOnClick(event -> safely(() -> {
			if (editor.runtime != runtime)
				throw new IllegalArgumentException();
			var targets = particles.stream().map(particle -> (IPersistedSerializable) particle.config).toList();
			var sourceRenderer = readModule(source, "renderer");
			var cycles = new LinkedHashMap<String, CompoundTag>();
			if (checks[5])
				for (String name : MODULES) {
					if (name.equals("renderer") || name.equals("emission") || name.equals("shape"))
						continue;
					var fields = new CompoundTag();
					var data = readModule(source, name);
					for (String field : data.keySet())
						if (field.startsWith("lobecorp$") && field.toLowerCase(Locale.ROOT).contains("cycle")) {
							var tag = data.get(field);
							if (tag != null)
								fields.put(field, tag.copy());
						}
					cycles.put(name, fields);
				}
			mutate(targets, () -> {
				for (var particle : particles) {
					if (checks[0])
						particle.config.setStartSpeed(NumberFunction.constant(speed[0]));
					if (checks[1])
						particle.config.setStartSize(new NumberFunction3(size[0], size[0], size[0]));
					if (checks[2])
						particle.config.setStartColor(NumberFunction.color(color[0]));
					if (checks[3])
						particle.config.renderer.setOrderInLayer(order[0]);
					if (checks[4]) {
						var renderer = readModule(particle, "renderer");
						var materials = sourceRenderer.get("materials");
						if (materials == null)
							throw new IllegalArgumentException();
						renderer.put("materials", materials.copy());
						writeModule(particle, "renderer", renderer);
					}
					cycles.forEach((name, fields) -> {
						var data = readModule(particle, name);
						for (String field : fields.keySet()) {
							var tag = fields.get(field);
							if (tag != null)
								data.put(field, tag.copy());
						}
						writeModule(particle, name, data);
					});
				}
			});
			dialog.close();
		})));
		dialog.show(editor);
	}

	private IPersistedSerializable module(ParticleEmitter particle, String name) {
		return switch (name) {
			case "rotationOverLifetime" -> particle.config.rotationOverLifetime;
			case "colorOverLifetime" -> particle.config.colorOverLifetime;
			case "sizeOverLifetime" -> particle.config.sizeOverLifetime;
			case "velocityOverLifetime" -> particle.config.velocityOverLifetime;
			case "forceOverLifetime" -> particle.config.forceOverLifetime;
			case "uvAnimation" -> particle.config.uvAnimation;
			case "renderer" -> particle.config;
			case "emission" -> particle.config.emission;
			case "shape" -> particle.config.shape;
			default -> throw new IllegalArgumentException();
		};
	}

	private CompoundTag readModule(ParticleEmitter particle, String name) {
		var provider = Platform.getFrozenRegistry();
		if (name.equals("renderer"))
			return PersistedParser.serializeNBT(particle.config, provider).getCompoundOrEmpty("renderer").copy();
		return PersistedParser.serializeNBT(module(particle, name), provider).copy();
	}

	private void writeModule(ParticleEmitter particle, String name, CompoundTag tag) {
		var provider = Platform.getFrozenRegistry();
		if (name.equals("renderer")) {
			var original = PersistedParser.serializeNBT(particle.config, provider);
			original.put("renderer", tag.copy());
			PersistedParser.deserializeNBT(original, particle.config, provider);
		} else
			PersistedParser.deserializeNBT(tag.copy(), module(particle, name), provider);
	}

	private void presetPanel() {
		var particles = selectedParticles();
		String[] name = {""}, module = {MODULES.getFirst()};
		var settings = PhotonEditorSettings.of(editor);
		var group = new ConfiguratorGroup("", false).hideTitle();
		var selector = new Selector<String>().setCandidates(MODULES).setSelected(module[0], false)
				.setOnValueChanged(value -> module[0] = value);
		selector.getStyle().tooltips(key("module") + PhotonEditorTooltipUtil.TOOLTIP_SUFFIX);
		group.addChild(selector);
		group.addConfigurator(new StringConfigurator(key("preset_name"), () -> name[0], v -> name[0] = v, "", false));
		var presets = new Selector<String>().setCandidates(settings.presets().keySet().stream().sorted().toList())
				.setOnValueChanged(value -> name[0] = value);
		presets.getStyle().tooltips(key("feature.presets") + PhotonEditorTooltipUtil.TOOLTIP_SUFFIX);
		group.addChild(presets);
		var runtime = editor.runtime;
		var dialog = dialog("feature.presets", group);
		dialog.addButton(new Button().setText(key("copy_module")).setOnClick(event -> safely(() -> {
			if (editor.runtime != runtime)
				throw new IllegalArgumentException();
			if (particles.isEmpty())
				throw new IllegalArgumentException();
			moduleClipboard = readModule(particles.getFirst(), module[0]);
			clipboardModule = module[0];
			if (enabled("system_clipboard")) {
				var data = new CompoundTag();
				data.putString("module", module[0]);
				data.put("value", moduleClipboard);
				PhotonSystemClipboardUtil.write(PhotonSystemClipboardUtil.MODULE, data);
			}
		})));
		dialog.addButton(new Button().setText(key("paste_module")).setOnClick(event -> safely(() -> {
			if (editor.runtime != runtime || particles.isEmpty())
				throw new IllegalArgumentException();
			if (enabled("system_clipboard")) {
				var data = PhotonSystemClipboardUtil.read(PhotonSystemClipboardUtil.MODULE);
				if (data == null || !module[0].equals(data.getStringOr("module", "")) || !data.contains("value"))
					throw new IllegalArgumentException();
				applyModule(particles, module[0], data.getCompoundOrEmpty("value"));
			} else {
				if (moduleClipboard == null || !module[0].equals(clipboardModule))
					throw new IllegalArgumentException();
				applyModule(particles, module[0], moduleClipboard);
			}
		})));
		dialog.addButton(new Button().setText(key("save_preset")).setOnClick(event -> safely(() -> {
			if (editor.runtime != runtime || name[0].isBlank())
				throw new IllegalArgumentException();
			var tag = new CompoundTag();
			tag.putString("module", module[0]);
			tag.put("data", readModule(particles.getFirst(), module[0]));
			settings.presets().put(name[0], tag);
			editor.editorSettings.saveAllSettingsToFile();
			presets.setCandidates(settings.presets().keySet().stream().sorted().toList());
		})));
		dialog.addButton(new Button().setText(key("load_preset")).setOnClick(event -> safely(() -> {
			var tag = settings.presets().get(name[0]);
			if (editor.runtime != runtime || tag == null || !module[0].equals(tag.getStringOr("module", "")))
				throw new IllegalArgumentException();
			applyModule(particles, module[0], tag.getCompoundOrEmpty("data"));
		})));
		dialog.show(editor);
	}

	private void applyModule(List<ParticleEmitter> particles, String name, CompoundTag tag) {
		mutate(particles.stream().map(p -> (IPersistedSerializable) p.config).toList(),
				() -> particles.forEach(p -> writeModule(p, name, tag)));
	}

	private void mutate(List<IPersistedSerializable> targets, Runnable change) {
		var provider = Platform.getFrozenRegistry();
		var before = targets.stream().map(t -> PersistedParser.serializeNBT(t, provider).copy()).toList();
		try {
			change.run();
		} catch (RuntimeException error) {
			for (int i = 0; i < targets.size(); i++)
				PersistedParser.deserializeNBT(before.get(i), targets.get(i), provider);
			throw error;
		}
		var after = targets.stream().map(t -> PersistedParser.serializeNBT(t, provider).copy()).toList();
		editor.historyView.pushHistory(Component.translatable(key("tools")), EditAction.of(
				() -> {
					for (int i = 0; i < targets.size(); i++)
						PersistedParser.deserializeNBT(after.get(i), targets.get(i), provider);
					editor.timelineView.refreshPreview();
				}, () -> {
					for (int i = 0; i < targets.size(); i++)
						PersistedParser.deserializeNBT(before.get(i), targets.get(i), provider);
					editor.timelineView.refreshPreview();
				}), false);
		editor.timelineView.refreshPreview();
	}

	private void diagnostics() {
		if (editor.runtime == null)
			throw new IllegalArgumentException();
		var content = new UIElement().addChild(new TextElement().setText(key("diagnostic_tip")));
		for (var sceneObject : editor.runtime.getAllSceneObjects()) {
			if (!(sceneObject instanceof Emitter emitter))
				continue;
			RendererSetting renderer = emitter instanceof ParticleEmitter particle ? particle.config.renderer
					: emitter instanceof BeamEmitter beam ? beam.getConfig().renderer : null;
			if (renderer == null)
				continue;
			var group = new ConfiguratorGroup(emitter.getName(), false);
			group.addChild(new TextElement().setText(Component.translatable("photon.emitter.config.renderer.orderInLayer")
					.append(Component.literal(": " + renderer.getOrderInLayer()))));
			group.addChild(new TextElement().setText(Component.translatable("RendererSetting.layer")
					.append(Component.literal(": " + renderer.getLayer()))));
			group.addChild(new TextElement().setText(Component.translatable("photon.emitter.config.renderer.vertexSortingMode")
					.append(Component.literal(": " + renderer.getVertexSortingMode()))));
			for (var material : renderer.getMaterials()) {
				group.addChild(new TextElement().setText(Component.translatable(key("depth_test")).append(Component.literal(": " + material.isDepthTest()))));
				group.addChild(new TextElement().setText(Component.translatable(key("depth_write")).append(Component.literal(": " + material.isDepthMask()))));
				group.addChild(new TextElement().setText(Component.translatable("MaterialSetting.blendMode")
						.append(Component.literal(": " + material.getBlendMode().toBlendFunction()))));
				if (material.getMaterial() instanceof TextureMaterial texture) {
					var resource = texture.getTexture();
					boolean present = Minecraft.getInstance().getResourceManager().getResource(resource).isPresent();
					group.addChild(new Button().setText(Component.literal(resource + (present ? "" : " ⚠")))
							.setOnClick(event -> locate(emitter)));
				}
			}
			content.addChild(group);
		}
		dialog("feature.diagnostics", content).show(editor);
	}

	private void locate(IFXObject object) {
		var node = findNode(editor.hierarchyView.getRootNode(), object);
		if (node == null)
			return;
		var parents = new ArrayList<FXObjectTreeNode>();
		for (var parent = node.getParent(); parent != null; parent = parent.getParent())
			parents.addFirst(parent);
		parents.forEach(editor.hierarchyView.treeList::expandNode);
		editor.hierarchyView.treeList.setSelected(Set.of(node), true);
	}

	private static FXObjectTreeNode findNode(FXObjectTreeNode node, IFXObject object) {
		if (node == null || node.getKey() == object)
			return node;
		for (var child : node.getChildren()) {
			var found = findNode(child, object);
			if (found != null)
				return found;
		}
		return null;
	}
}
