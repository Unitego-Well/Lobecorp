package xyz.langyo.minecraft.mcp.mod.util.client.control;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static xyz.langyo.minecraft.mcp.mod.util.client.control.ControlRequestUtil.*;

public class GuiControlUtil {
	private static final int MAX_WIDGETS = 2048;
	private static final int MAX_TEXT_LENGTH = 4096;

	public static JsonObject handle(JsonObject request) {
		String action = text(request, "action", "list");
		return client(!action.equals("list"), () -> operate(request, action));
	}

	private static JsonObject operate(JsonObject request, String action) {
		var minecraft = Minecraft.getInstance();
		var screen = minecraft.screen;
		require(screen != null, "NO_SCREEN", "No GUI is open.");
		if (action.equals("list")) {
			return describe(screen);
		}
		require(text(request, "screenId").equals(id(screen)), "STALE_SCREEN", "Read gui_control/list again.");
		String mode = text(request, "mode", "simulate");
		require(mode.equals("direct") || mode.equals("simulate"), "INVALID_REQUEST", "Unknown GUI mode.");
		if (mode.equals("direct")) {
			return direct(screen, request, action);
		}
		return simulate(minecraft, screen, request, action);
	}

	private static JsonObject describe(Screen screen) {
		var data = new JsonObject();
		data.addProperty("screenId", id(screen));
		data.addProperty("screenClass", screen.getClass().getName());
		data.addProperty("width", screen.width);
		data.addProperty("height", screen.height);
		var widgets = new JsonArray();
		for (var widget : widgets(screen)) {
			var entry = new JsonObject();
			entry.addProperty("widgetId", id(widget));
			entry.addProperty("type", widget.getClass().getName());
			entry.addProperty("label", widget.getMessage().getString());
			entry.addProperty("x", widget.getX());
			entry.addProperty("y", widget.getY());
			entry.addProperty("width", widget.getWidth());
			entry.addProperty("height", widget.getHeight());
			entry.addProperty("active", widget.active);
			entry.addProperty("visible", widget.visible);
			entry.addProperty("focused", widget.isFocused());
			if (widget instanceof EditBox edit) {
				entry.addProperty("value", edit.getValue());
			}
			widgets.add(entry);
		}
		data.add("widgets", widgets);
		return data;
	}

	private static List<AbstractWidget> widgets(Screen screen) {
		var found = new ArrayList<AbstractWidget>();
		Set<GuiEventListener> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		collect(screen, found, visited);
		return found;
	}

	private static void collect(GuiEventListener listener, List<AbstractWidget> found, Set<GuiEventListener> visited) {
		if (!visited.add(listener)) {
			return;
		}
		require(visited.size() <= MAX_WIDGETS, "GUI_TOO_LARGE", "GUI tree exceeds the inspection limit.");
		if (listener instanceof AbstractWidget widget) {
			found.add(widget);
		}
		if (listener instanceof AbstractWidget widget && (!widget.visible || !widget.active)) {
			return;
		}
		if (listener instanceof ContainerEventHandler container) {
			for (var child : container.children()) {
				collect(child, found, visited);
			}
		}
	}

	private static boolean focus(ContainerEventHandler container, GuiEventListener target) {
		for (var child : container.children()) {
			if (child == target || child instanceof ContainerEventHandler nested && focus(nested, target)) {
				container.setFocused(child);
				child.setFocused(true);
				return true;
			}
		}
		return false;
	}

	private static JsonObject direct(Screen screen, JsonObject request, String action) {
		String widgetId = text(request, "widgetId");
		var widget = widgets(screen).stream().filter(value -> id(value).equals(widgetId)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Widget no longer exists; enumerate again."));
		require(widget.visible && widget.active, "WIDGET_UNAVAILABLE", "Widget is hidden or disabled.");
		switch (action) {
			case "click" -> {
				require(widget instanceof AbstractButton, "UNSUPPORTED_WIDGET", "Direct clicks require an AbstractButton.");
				((AbstractButton) widget).onPress(new MouseButtonInfo(0,
						request.has("modifiers") ? integer(request, "modifiers", 0, 15) : 0));
			}
			case "set_text" -> {
				require(widget instanceof EditBox, "UNSUPPORTED_WIDGET", "Direct text assignment requires an EditBox.");
				String value = text(request, "text");
				require(value.length() <= MAX_TEXT_LENGTH, "INVALID_REQUEST", "Text is too long.");
				((EditBox) widget).setValue(value);
				var data = accepted(((EditBox) widget).getValue().equals(value));
				data.addProperty("value", ((EditBox) widget).getValue());
				return data;
			}
			case "focus" ->
					require(focus(screen, widget), "WIDGET_UNAVAILABLE", "Widget is not focusable in this screen.");
			default -> throw new IllegalArgumentException("Unsupported direct GUI action.");
		}
		return accepted(true);
	}

	private static JsonObject simulate(Minecraft minecraft, Screen screen, JsonObject request, String action) {
		return switch (action) {
			case "type" -> typeText(minecraft, screen, request);
			case "key" -> key(minecraft, screen, request);
			case "close" -> {
				screen.onClose();
				yield accepted(minecraft.screen != screen);
			}
			case "click", "drag", "scroll" -> mouse(minecraft, screen, request, action);
			default -> throw new IllegalArgumentException("Unsupported simulated GUI action.");
		};
	}

	private static JsonObject typeText(Minecraft minecraft, Screen screen, JsonObject request) {
		String value = text(request, "text");
		require(value.length() <= MAX_TEXT_LENGTH, "INVALID_REQUEST", "Text is too long.");
		int handled = 0;
		for (int codepoint : value.codePoints().toArray()) {
			require(minecraft.screen == screen, "STALE_SCREEN", "Screen changed while entering text.");
			if (screen.charTyped(new CharacterEvent(codepoint))) {
				handled++;
			}
		}
		var data = new JsonObject();
		data.addProperty("handledCodepoints", handled);
		return data;
	}

	private static JsonObject key(Minecraft minecraft, Screen screen, JsonObject request) {
		int key = integer(request, "key", 0, 348);
		int modifiers = request.has("modifiers") ? integer(request, "modifiers", 0, 15) : 0;
		var event = new KeyEvent(key, 0, modifiers);
		boolean handled = screen.keyPressed(event);
		if (minecraft.screen == screen) {
			screen.keyReleased(event);
		}
		return accepted(handled);
	}

	private static JsonObject mouse(Minecraft minecraft, Screen screen, JsonObject request, String action) {
		var point = point(minecraft, screen, request, "x", "y");
		int button = request.has("button") ? integer(request, "button", 0, 2) : 0;
		var event = new MouseButtonEvent(point[0], point[1], new MouseButtonInfo(button,
				request.has("modifiers") ? integer(request, "modifiers", 0, 15) : 0));
		if (action.equals("scroll")) {
			return accepted(screen.mouseScrolled(point[0], point[1],
					request.has("scrollX") ? number(request, "scrollX", -100, 100) : 0,
					number(request, "scrollY", -100, 100)));
		}
		require(action.equals("click") || action.equals("drag"), "INVALID_REQUEST", "Unsupported simulated GUI action.");
		var end = action.equals("drag") ? point(minecraft, screen, request, "endX", "endY") : point;
		boolean handled = screen.mouseClicked(event, false);
		if (minecraft.screen == screen) {
			try {
				if (handled && action.equals("drag")) {
					handled = screen.mouseDragged(new MouseButtonEvent(end[0], end[1], event.buttonInfo()),
							end[0] - point[0], end[1] - point[1]);
				}
			} finally {
				if (minecraft.screen == screen) {
					screen.mouseReleased(new MouseButtonEvent(end[0], end[1], event.buttonInfo()));
				}
			}
		}
		return accepted(handled);
	}

	private static double[] point(Minecraft minecraft, Screen screen, JsonObject request, String xKey, String yKey) {
		String space = text(request, "coordinateSpace", "gui");
		require(space.equals("gui") || space.equals("framebuffer"), "INVALID_REQUEST", "Unknown coordinate space.");
		double width = space.equals("gui") ? screen.width : minecraft.getWindow().getWidth();
		double height = space.equals("gui") ? screen.height : minecraft.getWindow().getHeight();
		require(width > 0 && height > 0, "SCREEN_UNAVAILABLE", "Screen has no drawable area.");
		double x = number(request, xKey, 0, width);
		double y = number(request, yKey, 0, height);
		return new double[]{x * screen.width / width, y * screen.height / height};
	}

	private static String id(Object value) {
		return value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
	}
}
