package org.unitego.lobecorp.client.photon.performance;

import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceReportUtil.Frames;
import org.unitego.lobecorp.registry.translation.photon.performance.PhotonPerformanceTranslations;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceWindowUtil;

import java.util.Arrays;
import java.util.function.BiConsumer;

/// 有界时间桶保留最长帧，拖动释放后才申请后台分析；绘制只读所属线程缓存。
public class PhotonPerformanceFrameChart {
	private static final int MAX_BUCKETS = 128;
	private static final int HEIGHT = 40;
	private static final int ROW_HEIGHT = 18;
	private final UIElement root = new UIElement();
	private final UIElement graph = new UIElement();
	private final TextElement legend = new TextElement();
	private final TextField firstField = new TextField().setText("1").setTextRegexValidator("[0-9]{0,6}");
	private final TextField lastField = new TextField().setText("1").setTextRegexValidator("[0-9]{0,6}");
	private @Nullable Frames frames;
	// 有界时间桶中各桶的代表帧索引。
	private int[] points = new int[0];
	private long maximumNs = 1;
	// 选区使用 1-based 帧号；0 表示未选择。
	private int first;
	private int last;
	private int anchor;
	private boolean dragging;

	public PhotonPerformanceFrameChart(BiConsumer<Integer, Integer> select, Runnable clear, Runnable export) {
		root.layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN).minHeight(0));
		graph.layout(layout -> layout.widthPercent(100).height(HEIGHT).minHeight(HEIGHT));
		graph.style(style -> style.backgroundTexture(GuiTexture.of(this::draw)));
		graph.addEventListener(UIEvents.MOUSE_DOWN, event -> {
			if (event.button == 0 && frames != null && frames.count() > 1) {
				anchor = frameAt(event.x);
				first = last = anchor;
				dragging = true;
				fields();
				graph.startDrag(null, null);
				event.stopPropagation();
			}
		});
		graph.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
			if (dragging) {
				int target = frameAt(event.x);
				first = Math.min(anchor, target);
				last = Math.max(anchor, target);
				fields();
				event.stopPropagation();
			}
		});
		graph.addEventListener(UIEvents.DRAG_END, event -> {
			if (dragging) {
				dragging = false;
				select.accept(first, last);
				event.stopPropagation();
			}
		});
		legend.layout(layout -> layout.widthPercent(100).height(ROW_HEIGHT).minHeight(ROW_HEIGHT));
		var controls = new UIElement().layout(layout -> layout.widthPercent(100).height(ROW_HEIGHT)
				.minHeight(ROW_HEIGHT).flexDirection(FlexDirection.ROW));
		firstField.layout(layout -> layout.width(48));
		lastField.layout(layout -> layout.width(48));
		controls.addChildren(new TextElement().setText(key("window_frames")).layout(layout -> layout.width(90)),
				firstField, lastField, button("window_apply", () -> {
					try {
						select.accept(Integer.parseInt(firstField.getText()), Integer.parseInt(lastField.getText()));
					} catch (NumberFormatException ignored) {
						legend.setText(key("window_invalid"));
					}
				}), button("window_clear", () -> {
					reset();
					clear.run();
				}), button("window_export", export));
		root.addChildren(graph, legend, controls);
	}

	public UIElement element() {
		return root;
	}

	public void reset() {
		first = last = anchor = 0;
		dragging = false;
		firstField.setText("1");
		lastField.setText("1");
	}

	public void selected(int first, int last) {
		this.first = first;
		this.last = last;
		fields();
	}

	public void refresh(@Nullable Frames frames) {
		this.frames = frames;
		points = frames == null ? new int[0] : PhotonPerformanceWindowUtil.chartFrames(frames, MAX_BUCKETS);
		maximumNs = 1;
		for (int point : points) {
			maximumNs = Math.max(maximumNs, frames.intervals()[point]);
			maximumNs = Math.max(maximumNs, Math.max(frames.cpu()[point - 1], frames.gpu()[point - 1]));
		}
		legend.setText(Component.translatable(key("chart_legend"), String.format(java.util.Locale.ROOT, "%.2f", maximumNs / 1_000_000d),
				frames == null ? 0 : Math.max(0, frames.count() - 1)));
	}

	private void fields() {
		firstField.setText(Integer.toString(first));
		lastField.setText(Integer.toString(last));
	}

	private int frameAt(float x) {
		if (frames == null || frames.count() < 2) {
			return 1;
		}
		double fraction = Math.clamp((x - graph.getContentX()) / Math.max(1, graph.getContentWidth()), 0, 1);
		long target = frames.heads()[0] + (long) ((frames.heads()[frames.count() - 1] - frames.heads()[0]) * fraction);
		int found = Arrays.binarySearch(frames.heads(), 0, frames.count(), target);
		return Math.clamp(found < 0 ? -found - 1 : found, 1, frames.count() - 1);
	}

	private void draw(GUIContext context, float x, float y, float width, float height) {
		DrawerHelperClient.drawSolidRect(context, x, y, width, height, 0xff151b22);
		if (frames == null || frames.count() < 2) {
			return;
		}
		long start = frames.heads()[0];
		long duration = Math.max(1, frames.heads()[frames.count() - 1] - start);
		if (first > 0 && first <= last && last < frames.count()) {
			float from = (float) ((frames.heads()[first - 1] - start) / (double) duration * width);
			float to = (float) ((frames.heads()[last] - start) / (double) duration * width);
			DrawerHelperClient.drawSolidRect(context, x + from, y, Math.max(1, to - from), height, 0x665e8c50);
		}
		for (int point : points) {
			float px = x + (float) ((frames.heads()[point] - start) / (double) duration * Math.max(0, width - 2));
			long interval = frames.intervals()[point];
			if (interval >= 0) {
				float bar = Math.max(1, interval / (float) maximumNs * (height - 2));
				DrawerHelperClient.drawSolidRect(context, px, y + height - bar, 1, bar, 0xffe4b94e);
			}
			point(context, px, y, height, frames.cpu()[point - 1], 0xff54d6db);
			point(context, px + 1, y, height, frames.gpu()[point - 1], 0xffc08cf5);
		}
	}

	private void point(GUIContext context, float x, float y, float height, long value, int color) {
		if (value >= 0) {
			float bar = value / (float) maximumNs * (height - 2);
			DrawerHelperClient.drawSolidRect(context, x, y + height - Math.max(1, bar), 2, 1, color);
		}
	}

	private static Button button(String name, Runnable action) {
		var button = new Button().setText(key(name)).setOnClick(_ -> action.run());
		button.layout(layout -> layout.flex(1).minWidth(0));
		return button;
	}

	private static String key(String name) {
		return PhotonPerformanceTranslations.key(name);
	}
}
