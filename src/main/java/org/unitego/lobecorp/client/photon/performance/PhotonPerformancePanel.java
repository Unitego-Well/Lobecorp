package org.unitego.lobecorp.client.photon.performance;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.gui.editor.FXEditor;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTextUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceWindowAnalysisUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceWindowUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceWindowUtil.CpuRow;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceWindowUtil.GpuRow;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceWindowUtil.Result;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Aggregate;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Counter;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Mark;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Mode;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Node;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.View;

import java.util.ArrayList;
import java.util.ArrayDeque;

import com.lowdragmc.photon.gui.editor.view.FXObjectTreeNode;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/// 固定行复用与有界刷新；世界采样开始后关闭查看界面，编辑器面板可继续操作时间轴。
public class PhotonPerformancePanel {
	private static final int PANEL_WIDTH = 620;
	private static final int PANEL_HEIGHT = 520;
	private static final int MARGIN = 8;
	private static final int PAGE_SIZE = 32;
	private static final int ROW_HEIGHT = 18;
	private static final double NS_PER_MS = 1_000_000d;
	// 分页顺序同时决定两行标签的位置；第 5 项起换行。
	private static final List<String> PAGES = List.of("overview", "objects", "models", "animations", "operations", "gpu", "resources", "export");
	private @Nullable FXEditor editor;
	private UIElement root = new UIElement();
	private final PhotonPerformanceWindowAnalysisUtil analysis = new PhotonPerformanceWindowAnalysisUtil();
	private final PhotonPerformanceFrameChart chart = new PhotonPerformanceFrameChart(this::chooseWindow, this::clearWindow, this::exportWindow);
	private final TextElement heading = new TextElement();
	private final UIElement ordering = row();
	private final Button selfOrder = button("sort_self", () -> order(0));
	private final Button allocationOrder = button("sort_alloc", () -> order(2));
	private @Nullable Result window;
	// 帧窗口使用 1-based 帧号；0 表示未选择。
	private int firstFrame;
	private int lastFrame;
	private long submittedSession;
	private @Nullable String windowError;
	private TextElement status = new TextElement();
	private TextElement details = new TextElement();
	private TextElement pagination = new TextElement();
	private List<Button> rows = new ArrayList<>();
	private List<Object> entries = new ArrayList<>();
	private int[] rowNodes = new int[PAGE_SIZE];
	private Map<Integer, String> paths = new HashMap<>();
	private Map<Integer, Integer> contexts = new HashMap<>();
	private int[] ancestorSearch = new int[PhotonPerformanceTraceUtil.MAX_DEPTH * 2];
	private @Nullable View view;
	private long viewedSession;
	private long refreshedNs;
	private String page = "overview";
	private String filter = "";
	private String label = PhotonPerformanceCapture.EDITOR_LABEL;
	private double seconds = PhotonPerformanceCapture.DEFAULT_SECONDS;
	private int pageIndex;
	private int selected;
	private int sort;
	private Runnable close = () -> {
	};

	private PhotonPerformancePanel(@Nullable FXEditor editor) {
		this.editor = editor;
		if (editor == null) {
			label = PhotonPerformanceCapture.WORLD_LABEL;
		}
		build();
	}

	public static void open(FXEditor editor) {
		new PhotonPerformancePanel(editor).show(editor);
	}

	public static void openWorld() {
		var minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			var host = new UIElement();
			host.layout(layout -> layout.widthPercent(100).heightPercent(100));
			var panel = new PhotonPerformancePanel(null);
			minecraft.setScreen(new ModularUIScreen(new ModularUI(UI.of(host)), text("title")) {
				@Override
				public void removed() {
					panel.analysis.close();
					super.removed();
				}
			});
			panel.show(host);
		});
	}

	private void show(UIElement host) {
		var minecraft = Minecraft.getInstance();
		int width = Math.max(220, Math.min(PANEL_WIDTH, minecraft.getWindow().getGuiScaledWidth() - MARGIN * 2));
		int height = Math.max(150, Math.min(PANEL_HEIGHT, minecraft.getWindow().getGuiScaledHeight() - MARGIN * 2));
		root.layout(layout -> layout.widthPercent(100).heightPercent(100).flex(1).minHeight(0));
		var dialog = new Dialog().setTitle(key("title")).allowInteraction()
				.windowMode(MARGIN, MARGIN, width, height).setAutoClose(false).setClickOutsideClose(false);
		dialog.addContent(root);
		close = editor == null ? () -> minecraft.setScreen(null) : dialog::close;
		dialog.show(host);
		refresh();
	}

	private void build() {
		root.layout(layout -> layout.flexDirection(FlexDirection.COLUMN).minHeight(0));
		var settings = row();
		var duration = new TextField().setText(Double.toString(seconds));
		duration.setNumbersOnlyDouble(PhotonFrameCapture.minSeconds(), PhotonFrameCapture.maxSeconds());
		duration.setTextResponder(value -> {
			try {
				seconds = Double.parseDouble(value);
			} catch (NumberFormatException ignored) {
				seconds = PhotonPerformanceCapture.DEFAULT_SECONDS;
			}
		});
		duration.layout(layout -> layout.width(65));
		var captureLabel = new TextField().setText(label).setTextRegexValidator("[a-z0-9_]+");
		captureLabel.setTextResponder(value -> label = value);
		captureLabel.layout(layout -> layout.flex(1).minWidth(0));
		var durationLabel = new TextElement().setText(key("seconds"));
		durationLabel.layout(layout -> layout.width(80).height(ROW_HEIGHT));
		var nameLabel = new TextElement().setText(key("label"));
		nameLabel.layout(layout -> layout.width(65).height(ROW_HEIGHT));
		settings.addChildren(durationLabel, duration, nameLabel, captureLabel);
		root.addChild(settings);
		var controls = row();
		controls.addChildren(button("start_overview", () -> start(Mode.OVERVIEW)),
				button("start_detail", () -> start(Mode.DETAIL)), button("start_gpu", () -> start(Mode.GPU)),
				button("stop", PhotonPerformanceCapture::stop));
		root.addChild(controls);
		var tabs = row();
		for (int index = 0; index < PAGES.size(); index++) {
			if (index == 4) {
				root.addChild(tabs);
				tabs = row();
			}
			String name = PAGES.get(index);
			tabs.addChild(button(name, () -> {
				page = name;
				pageIndex = 0;
				refresh();
			}));
		}
		root.addChild(tabs);
		var filters = row();
		var search = new TextField().setText("").setTextResponder(value -> {
			filter = value.toLowerCase(Locale.ROOT);
			pageIndex = 0;
			refresh();
		});
		search.layout(layout -> layout.flex(1).minWidth(0));
		var searchLabel = new TextElement().setText(key("search"));
		searchLabel.layout(layout -> layout.width(110).height(ROW_HEIGHT));
		filters.addChildren(searchLabel, search, button("all", () -> {
			selected = 0;
			pageIndex = 0;
			refresh();
		}), button("focus", this::focus));
		root.addChild(filters);
		ordering.addChildren(selfOrder, button("sort_max", () -> order(1)), allocationOrder);
		root.addChild(ordering);
		status.layout(layout -> layout.widthPercent(100).height(ROW_HEIGHT).minHeight(ROW_HEIGHT));
		root.addChild(status);
		root.addChild(chart.element());
		heading.setText(key("row_header"));
		heading.layout(layout -> layout.widthPercent(100).height(ROW_HEIGHT).minHeight(ROW_HEIGHT));
		root.addChild(heading);
		var scroller = new ScrollerView();
		scroller.layout(layout -> layout.widthPercent(100).flex(1).minHeight(0));
		for (int index = 0; index < PAGE_SIZE; index++) {
			int row = index;
			var button = new Button().setOnClick(_ -> {
				if (rowNodes[row] != 0) {
					selected = rowNodes[row];
					pageIndex = 0;
					refresh();
				}
			});
			button.layout(layout -> layout.widthPercent(100).height(ROW_HEIGHT).minHeight(ROW_HEIGHT));
			rows.add(button);
			scroller.addScrollViewChild(button);
		}
		root.addChild(scroller);
		details.layout(layout -> layout.widthPercent(100).height(ROW_HEIGHT * 2).minHeight(ROW_HEIGHT * 2));
		pagination.layout(layout -> layout.width(110).height(ROW_HEIGHT));
		root.addChild(details);
		var footer = row();
		footer.addChildren(button("previous", () -> {
					pageIndex = Math.max(0, pageIndex - 1);
					refresh();
				}),
				pagination, button("next", () -> {
					pageIndex++;
					refresh();
				}),
				button("cancel", PhotonPerformanceCapture::cancel), button("close", () -> close.run()));
		root.addChild(footer);
		root.addEventListener(UIEvents.REMOVED, _ -> analysis.close());
		root.addEventListener(UIEvents.TICK, _ -> {
			if (System.nanoTime() - refreshedNs >= PhotonPerformanceCapture.VIEW_REFRESH_NS) {
				refresh();
			}
		});
	}

	private void start(Mode mode) {
		try {
			PhotonPerformanceCapture.start(label, seconds, mode);
			if (editor == null) {
				close.run();
			}
		} catch (RuntimeException failure) {
			details.setText(Component.translatable(key("error"), Component.translatable(String.valueOf(failure.getMessage()))));
		}
	}

	private void order(int sort) {
		this.sort = sort;
		pageIndex = 0;
		refresh();
	}

	private void refresh() {
		refreshedNs = System.nanoTime();
		var state = PhotonPerformanceCapture.status();
		var current = PhotonPerformanceCapture.view();
		if (view != current) {
			paths.clear();
			contexts.clear();
			if (current != null) {
				for (var context : current.contexts()) {
					contexts.put(context.node(), context.owner());
				}
			}
		}
		view = current;
		if (view != null && viewedSession != view.sessionId()) {
			viewedSession = view.sessionId();
			paths.clear();
			selected = 0;
			clearWindow();
		}
		refreshWindow();
		ordering.setDisplay(!page.equals("operations") && !page.equals("resources") && !page.equals("export"));
		selfOrder.setText(key(page.equals("gpu") ? "sort_gpu" : "sort_self"));
		allocationOrder.setDisplay(!page.equals("gpu"));
		chart.element().setDisplay(page.equals("overview") || page.equals("operations"));
		chart.refresh(PhotonPerformanceCapture.frames());
		status.setText(Component.translatable(key("status"),
				view != null && view.quality().limit() != null
						? Component.translatable(key("capacity_short"),
						Component.translatable(key("limit." + view.quality().limit().reason().name().toLowerCase(Locale.ROOT))))
						: state.get("active").getAsBoolean(),
				state.get("exporting").getAsBoolean(), view == null ? 0 : view.nodes().size(), view == null ? 0 : view.frame()));
		entries.clear();
		if (view != null) {
			if (page.equals("resources")) {
				entries.addAll(view.counters());
			} else if (firstFrame > 0) {
				if (window != null) {
					if (page.equals("operations")) {
						entries.addAll(window.marks().stream().sorted(Comparator.comparingLong(Mark::timeNs).reversed()).toList());
					} else if (page.equals("gpu")) {
						entries.addAll(window.gpu());
					} else {
						entries.addAll(window.cpu());
					}
				}
			} else if (page.equals("operations")) {
				entries.addAll(view.marks().stream().sorted(Comparator.comparingLong(Mark::timeNs).reversed()).toList());
			} else if (page.equals("gpu")) {
				for (var aggregate : PhotonPerformanceGpu.view()) {
					entries.add(gpuRow(aggregate));
				}
			} else {
				entries.addAll(view.aggregates());
			}
			entries.removeIf(value -> !included(value));
			if (!page.equals("operations") && !page.equals("resources")) {
				entries.sort(Comparator.comparingLong(this::sortValue).reversed());
			}
		}
		heading.setText(key(page.equals("gpu") ? "gpu_header" : page.equals("resources") ? "resource_header"
				: page.equals("operations") ? "operation_header" : firstFrame > 0 ? "window_header" : "row_header"));
		if (page.equals("export")) {
			heading.setText(key("export"));
			entries.clear();
			if (!state.get("output").isJsonNull()) {
				entries.add(Component.translatable(key("output"), state.get("output").getAsString()));
			}
			if (!state.get("error").isJsonNull()) {
				entries.add(Component.translatable(key("error"), state.get("error").getAsString()));
			}
			entries.add(text("export_hint"));
			if (view != null) {
				var quality = view.quality();
				entries.add(Component.translatable(key("quality_breakdown"), quality.deniedNodeRequests(),
						quality.deniedAggregateRequests(), quality.droppedRawSpans(), quality.deniedCounterRequests()));
				if (quality.limit() != null) {
					entries.add(Component.translatable(key("capacity_detail"),
							Component.translatable(key("limit." + quality.limit().reason().name().toLowerCase(Locale.ROOT))),
							quality.limit().frame()));
				}
			}
			entries.add(Component.literal(state.get("gpu_query_status").toString()));
		}
		int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		pageIndex = Math.min(pageIndex, pages - 1);
		for (int index = 0; index < PAGE_SIZE; index++) {
			int entry = pageIndex * PAGE_SIZE + index;
			boolean visible = entry < entries.size();
			rows.get(index).setDisplay(visible);
			rowNodes[index] = visible ? nodeId(entries.get(entry)) : 0;
			if (visible) {
				rows.get(index).setText(describe(entries.get(entry)));
			}
		}
		pagination.setText(Component.translatable(key("page"), pageIndex + 1, pages, entries.size()));
		if (windowError != null) {
			details.setText(Component.translatable(key("error"), Component.translatable(key(windowError))));
		} else if (view == null) {
			details.setText(text("empty"));
		} else if (firstFrame > 0 && !page.equals("resources")) {
			if (window == null) {
				details.setText(key(PhotonPerformanceCapture.active() ? "window_wait_stop" : "window_working"));
			} else {
				details.setText(Component.translatable(key("window_summary"), firstFrame, lastFrame,
								nullableDecimal(window.frames().intervals().p95()), nullableDecimal(window.frames().intervals().p99()))
						.append("\n").append(Component.translatable(key(window.detailedCpu() ? "window_quality" : "window_overview"),
								window.globalDropped(), window.globalOverwritten(), window.gpuPartial())));
			}
		} else if (selected > 0) {
			details.setText(Component.translatable(key("ownership"), path(selected)));
		} else {
			details.setText(Component.translatable(key("quality"), view.dropped(), view.overwritten(), view.incomplete()));
		}
	}

	private void chooseWindow(int first, int last) {
		var frames = PhotonPerformanceCapture.frames();
		try {
			if (frames == null) {
				throw new IllegalArgumentException("window_invalid");
			}
			PhotonPerformanceWindowUtil.bounds(frames, first, last);
			firstFrame = first;
			lastFrame = last;
			submittedSession = 0;
			window = null;
			windowError = null;
			analysis.clear();
			chart.selected(first, last);
			pageIndex = 0;
		} catch (IllegalArgumentException failure) {
			windowError = "window_invalid";
		}
		refresh();
	}

	private void clearWindow() {
		firstFrame = lastFrame = 0;
		submittedSession = 0;
		window = null;
		windowError = null;
		analysis.clear();
		chart.reset();
	}

	private void refreshWindow() {
		if (firstFrame == 0) {
			return;
		}
		var snapshot = PhotonPerformanceCapture.finished();
		if (snapshot != null && submittedSession != snapshot.capture().view().sessionId()) {
			try {
				analysis.request(snapshot.capture(), snapshot.frames(), snapshot.gpuSamples(), firstFrame, lastFrame);
				submittedSession = snapshot.capture().view().sessionId();
			} catch (IllegalArgumentException failure) {
				windowError = "window_invalid";
			}
		}
		var state = analysis.state();
		window = state.result();
		if (state.error() != null) {
			windowError = state.error();
		}
	}

	private void exportWindow() {
		windowError = window == null || !PhotonPerformanceCapture.exportWindow(window) ? "window_export_unavailable" : null;
		refresh();
	}

	private long sortValue(Object value) {
		if (value instanceof Aggregate aggregate) {
			return switch (sort) {
				case 1 -> aggregate.maximumNs();
				case 2 -> aggregate.selfAllocatedBytes();
				default -> aggregate.selfNs();
			};
		}
		if (value instanceof CpuRow cpu) {
			return switch (sort) {
				case 1 -> cpu.maximumFullNs();
				case 2 -> cpu.knownSelfAllocatedBytes();
				default -> cpu.knownSelfNs();
			};
		}
		if (value instanceof GpuRow gpu) {
			return sort == 1 ? gpu.maximumNs() : gpu.sumNs();
		}
		return 0;
	}

	private boolean included(Object value) {
		int node = nodeId(value);
		if (selected != 0 && !descendant(node, selected)) {
			return false;
		}
		Stage stage = value instanceof Aggregate aggregate ? aggregate.stage() : value instanceof CpuRow cpu ? cpu.stage() : null;
		if (stage != null) {
			if (page.equals("models") && !List.of(Stage.MODEL_PREPARE, Stage.MODEL_SAMPLE, Stage.MODEL_POSE,
					Stage.MODEL_PART, Stage.MATERIAL, Stage.GEOMETRY, Stage.UPLOAD).contains(stage)) {
				return false;
			}
			if (page.equals("animations") && !List.of(Stage.ANIMATION, Stage.ANIMATION_FRAME, Stage.TRACK, Stage.CURVE).contains(stage)) {
				return false;
			}
		}
		return filter.isEmpty() || (path(node) + " " + contextLabel(node) + " " + value).toLowerCase(Locale.ROOT).contains(filter);
	}

	private Component describe(Object value) {
		if (value instanceof CpuRow cpu) {
			return Component.literal((cpu.fullCalls() == 0 ? "?" : decimal(cpu.knownSelfNs())) + " | "
							+ (cpu.fullCalls() == 0 ? "?" : decimal(cpu.maximumFullNs())) + " | " + cpu.fullCalls() + "+"
							+ cpu.partialCalls() + "/" + cpu.incomplete() + " | " + path(cpu.node()) + contextLabel(cpu.node()) + " / ")
					.append(Component.translatable(key("stage." + cpu.stage().name().toLowerCase(Locale.ROOT))))
					.append(Component.literal(" / " + cpu.threadName()));
		}
		if (value instanceof GpuRow gpu) {
			return Component.literal((gpu.measured() == 0 ? "?" : decimal(gpu.sumNs())) + " | "
							+ (gpu.measured() == 0 ? "?" : decimal(gpu.maximumNs())) + " | " + gpu.measured() + "/" + gpu.unavailable()
							+ " | " + path(gpu.node()) + " / " + gpu.pass() + " / ")
					.append(Component.translatable(key("linked_scope".equals(gpu.ownership()) ? "gpu_linked" : "gpu_shared")));
		}
		if (value instanceof Aggregate aggregate) {
			String name = view.nodes().get(aggregate.node()).name();
			return Component.literal(decimal(aggregate.selfNs()) + " | " + decimal(aggregate.maximumNs())
							+ " | " + aggregate.calls() + " | " + name + contextLabel(aggregate.node()) + " / ")
					.append(Component.translatable(key("stage." + aggregate.stage().name().toLowerCase(Locale.ROOT))));
		}
		if (value instanceof Mark mark) {
			return Component.literal(String.format(Locale.ROOT, "%.3f | %s | %s | %s | %.3f / %.3f",
					(mark.timeNs() - view.startedNs()) / NS_PER_MS, path(mark.node()), mark.code(), mark.detail(), mark.value(), mark.secondary()));
		}
		if (value instanceof Counter counter) {
			return Component.literal(path(counter.node()) + " | " + counter.metric() + " | last=" + counter.last()
					+ " | max=" + counter.maximum() + " | samples=" + counter.samples());
		}
		return value instanceof Component component ? component : Component.literal(String.valueOf(value));
	}

	private String contextLabel(int node) {
		int context = contexts.getOrDefault(node, 0);
		return context == 0 || view == null || context >= view.nodes().size() ? ""
				: " [" + Component.translatable(key("execution_context"), view.nodes().get(context).name() + " #" + context).getString() + "]";
	}

	private boolean descendant(int id, int parent) {
		if (view == null) {
			return false;
		}
		int pending = 0;
		int visited = 0;
		ancestorSearch[pending++] = id;
		while (pending > 0 && visited < PhotonPerformanceTraceUtil.MAX_DEPTH) {
			int cursor = ancestorSearch[--pending];
			while (cursor > 0 && cursor < view.nodes().size() && visited++ < PhotonPerformanceTraceUtil.MAX_DEPTH) {
				if (cursor == parent) {
					return true;
				}
				int context = contexts.getOrDefault(cursor, 0);
				if (context > 0 && context != cursor && pending < ancestorSearch.length) {
					ancestorSearch[pending++] = context;
				}
				cursor = view.nodes().get(cursor).parent();
			}
		}
		return false;
	}

	private String path(int id) {
		if (view == null || id < 0 || id >= view.nodes().size()) {
			return "";
		}
		var cached = paths.get(id);
		if (cached != null) {
			return cached;
		}
		var names = new ArrayList<String>();
		int cursor = id;
		for (int depth = 0; cursor > 0 && cursor < view.nodes().size() && depth < PhotonPerformanceTraceUtil.MAX_DEPTH; depth++) {
			var node = view.nodes().get(cursor);
			names.addFirst(node.name());
			cursor = node.parent();
		}
		String path = names.isEmpty() ? view.nodes().get(id).name() : String.join("/", names);
		paths.put(id, path);
		return path;
	}

	private void focus() {
		if (editor == null || editor.runtime == null || view == null || selected <= 0 || selected >= view.nodes().size()) {
			return;
		}
		int targetNode = selected;
		for (int depth = 0; targetNode > 0 && depth < PhotonPerformanceTraceUtil.MAX_DEPTH; depth++) {
			if (PhotonPerformanceOwnerUtil.target(targetNode, view.sessionId()) != null) {
				break;
			}
			targetNode = view.nodes().get(targetNode).parent();
		}
		if (targetNode <= 0) {
			return;
		}
		Node node = view.nodes().get(targetNode);
		try {
			var object = editor.runtime.getSceneObject(UUID.fromString(node.definitionId()));
			if (object != null && object == PhotonPerformanceOwnerUtil.target(targetNode, view.sessionId())) {
				var treeRoot = editor.hierarchyView.getRootNode();
				if (treeRoot == null) {
					return;
				}
				var pending = new ArrayDeque<FXObjectTreeNode>();
				pending.add(treeRoot);
				while (!pending.isEmpty()) {
					var item = pending.removeFirst();
					if (item.getKey() == object) {
						editor.hierarchyView.treeList.setSelected(List.of(item), true);
						return;
					}
					pending.addAll(item.getChildren());
				}
			}
		} catch (IllegalArgumentException ignored) {
			// 非场景对象或已经结束的运行实例只展示捕获路径。
		}
	}

	private static int nodeId(Object value) {
		if (value instanceof Aggregate aggregate) {
			return aggregate.node();
		}
		if (value instanceof Mark mark) {
			return mark.node();
		}
		if (value instanceof CpuRow cpu) {
			return cpu.node();
		}
		if (value instanceof GpuRow gpu) {
			return gpu.node();
		}
		return value instanceof Counter counter ? counter.node() : 0;
	}

	private static GpuRow gpuRow(JsonObject value) {
		return new GpuRow(value.get("pass").getAsString(), value.get("node_id").getAsInt(), value.get("ownership").getAsString(),
				value.get("measured").getAsLong(), value.get("unavailable").getAsLong() + value.get("skipped_budget_or_pending").getAsLong(),
				value.get("duration_ns_sum").isJsonNull() ? 0 : value.get("duration_ns_sum").getAsLong(),
				value.get("duration_ns_max").isJsonNull() ? 0 : value.get("duration_ns_max").getAsLong());
	}

	private static String nullableDecimal(@Nullable Long value) {
		return value == null ? "?" : decimal(value);
	}

	private static String decimal(long ns) {
		return String.format(Locale.ROOT, "%.3f", ns / NS_PER_MS);
	}

	private static UIElement row() {
		return new UIElement().layout(layout -> layout.widthPercent(100).height(ROW_HEIGHT).minHeight(ROW_HEIGHT).flexDirection(FlexDirection.ROW));
	}

	private static Button button(String name, Runnable action) {
		var button = new Button().setText(key(name)).setOnClick(_ -> action.run());
		button.layout(layout -> layout.flex(1).minWidth(0));
		return button;
	}

	private static String key(String name) {
		return PhotonPerformanceTextUtil.key(name);
	}

	private static Component text(String name) {
		return Component.translatable(key(name));
	}
}
