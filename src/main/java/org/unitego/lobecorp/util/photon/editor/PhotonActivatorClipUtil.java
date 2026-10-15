package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.util.photon.runtime.PhotonActivatorTimelineUtil;

import java.util.function.BooleanSupplier;

/// 激活片段沿用原生信号轨道的线与方块，保留原片段数据和交互。
public class PhotonActivatorClipUtil {
	/// 与原生 SignalTrackEditor 的方块宽高一致，单位为像素。
	private static final float MARKER_SIZE = 5;
	/// 与原生信号标记的上下留白一致，单位为像素。
	private static final float MARKER_INSET = 2;
	/// 与原生片段边缘调整指针的宽高一致，单位为像素。
	private static final float RESIZE_ICON_SIZE = 10;

	public static boolean enabled(TimelineContext ctx, Track track) {
		return track instanceof ActivatorTrack && PhotonEditorSettings.of(ctx.editor()).enabled("control");
	}

	public static boolean isPoint(TimelineContext ctx, Track track) {
		if (!enabled(ctx, track)) {
			return false;
		}

		var options = PhotonActivatorTimelineUtil.options(track);
		return options.startControl() != options.endControl();
	}

	public static boolean showsBoundary(TimelineContext ctx, Track track, boolean start) {
		if (!enabled(ctx, track)) {
			return true;
		}

		var options = PhotonActivatorTimelineUtil.options(track);
		return start ? options.startControl() : options.endControl();
	}

	public static void reposition(TimelineContext ctx, Track track, Clip clip, UIElement element) {
		if (!isPoint(ctx, track)) {
			return;
		}

		var options = PhotonActivatorTimelineUtil.options(track);
		var tick = options.startControl() ? clip.start() : clip.end();
		element.layout(layout -> {
			layout.left(ctx.tickToLocalX(tick) - TimelineContext.KEY_HIT_PX);
			layout.width(TimelineContext.KEY_HIT_PX * 2);
		});
	}

	public static IGuiTexture background(TimelineContext ctx, Track track, IGuiTexture original) {
		if (!(track instanceof ActivatorTrack)) {
			return original;
		}

		return GuiTexture.of((graphics, x, y, width, height) -> {
			if (isPoint(ctx, track)) {
				return;
			}

			if (inactive(ctx, track)) {
				DrawerHelperClient.drawSolidRect(graphics, x + 1, y, Math.max(1, width - 2), height, ColorPattern.T_GRAY.color);
				return;
			}

			graphics.drawTexture(original, x, y, width, height);
		});
	}

	public static IGuiTexture overlay(TimelineContext ctx, Track track, Clip clip, IGuiTexture original,
	                                  ColorPattern color, BooleanSupplier invalid) {
		if (!(track instanceof ActivatorTrack)) {
			return original;
		}

		return GuiTexture.of((graphics, x, y, width, height) -> {
			if (isPoint(ctx, track)) {
				drawMarker(graphics, x + width / 2, y, height, markerColor(ctx, clip, color, invalid.getAsBoolean()));
				return;
			}

			if (inactive(ctx, track)) {
				drawInactive(graphics, ctx, track, clip, x, y, width, height, invalid.getAsBoolean());
				return;
			}

			graphics.drawTexture(original, x, y, width, height);

			if (!enabled(ctx, track)) {
				return;
			}

			var markerColor = markerColor(ctx, clip, color, invalid.getAsBoolean());
			drawMarker(graphics, ctx.originX() + ctx.tickToLocalX(clip.start()), y, height, markerColor);
			drawMarker(graphics, ctx.originX() + ctx.tickToLocalX(clip.end()), y, height, markerColor);
		});
	}

	private static boolean inactive(TimelineContext ctx, Track track) {
		if (!enabled(ctx, track)) {
			return false;
		}

		var options = PhotonActivatorTimelineUtil.options(track);
		return !options.startControl() && !options.endControl();
	}

	private static int markerColor(TimelineContext ctx, Clip clip, ColorPattern color, boolean invalid) {
		if (invalid) {
			return ColorPattern.RED.color;
		}

		return ctx.isClipSelected(clip) ? ColorPattern.WHITE.color : color.color;
	}

	private static void drawMarker(GUIContext graphics, float x, float y, float height, int color) {
		var halfMarker = MARKER_SIZE / 2;
		var centerY = y + height / 2;
		var lineColor = color == ColorPattern.WHITE.color || color == ColorPattern.RED.color ? color : ColorPattern.T_WHITE.color;
		DrawerHelperClient.drawSolidRect(graphics, x - .5f, y + MARKER_INSET, 1, height - MARKER_INSET * 2, lineColor);
		DrawerHelperClient.drawSolidRect(graphics, x - halfMarker, centerY - halfMarker, MARKER_SIZE, MARKER_SIZE, color);
	}

	private static void drawInactive(GUIContext graphics, TimelineContext ctx, Track track, Clip clip,
	                                 float x, float y, float width, float height, boolean invalid) {
		var borderX = x + 1;
		var borderWidth = Math.max(1, width - 2);
		var color = invalid ? ColorPattern.RED : ctx.isClipSelected(clip) ? ColorPattern.WHITE : ColorPattern.GRAY;
		DrawerHelperClient.drawBorder(graphics, borderX, y, borderWidth, height, color.color, 1);

		if (track.lock() || graphics.mouseY < y || graphics.mouseY > y + height
				|| graphics.mouseX < borderX || graphics.mouseX > borderX + borderWidth) {
			return;
		}

		if (graphics.mouseX <= borderX + TimelineContext.EDGE_PX
				|| graphics.mouseX >= borderX + borderWidth - TimelineContext.EDGE_PX) {
			graphics.drawTexture(Icons.ARROW_LEFT_RIGHT, graphics.mouseX - RESIZE_ICON_SIZE / 2,
					graphics.mouseY - RESIZE_ICON_SIZE / 2, RESIZE_ICON_SIZE, RESIZE_ICON_SIZE);
		}
	}
}
