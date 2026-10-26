package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/// 显式基准的窗口焦点审计：失焦时尝试还原并计数；测量阶段发生失焦的轮次标记为无效，不据此出成绩。
public final class PhotonBenchFocusAudit {
	private boolean focused = true;
	private long losses;
	private long restores;
	private long ticks;
	private long measureTicks;
	private boolean measureInvalid;

	public void audit(boolean duringMeasure) {
		ticks++;

		if (duringMeasure) {
			measureTicks++;
		}

		var window = Minecraft.getInstance().getWindow();
		boolean current = window.isFocused();

		if (!current) {
			if (focused) {
				losses++;
			}

			if (duringMeasure) {
				measureInvalid = true;
			}

			if (window.isIconified()) {
				GLFW.glfwRestoreWindow(window.handle());
			}

			GLFW.glfwShowWindow(window.handle());
			GLFW.glfwFocusWindow(window.handle());
			restores++;
		}

		focused = current;
	}

	public JsonObject snapshot() {
		var result = new JsonObject();
		result.addProperty("ticks", ticks);
		result.addProperty("measure_ticks", measureTicks);
		result.addProperty("focus_losses", losses);
		result.addProperty("focus_restores", restores);
		result.addProperty("measure_focus_valid", !measureInvalid);
		result.addProperty("final_focused", focused);
		return result;
	}
}
