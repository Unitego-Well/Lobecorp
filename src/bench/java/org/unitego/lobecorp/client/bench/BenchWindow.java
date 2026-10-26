package org.unitego.lobecorp.client.bench;

import org.lwjgl.glfw.GLFW;

/// 基准窗口防失焦：每 tick 检查最小化/焦点，必要时还原并重新聚焦，并记录丢失/恢复次数。
public final class BenchWindow {
	private static int losses;
	private static int restores;

	private BenchWindow() {
	}

	public static void reset() {
		losses = 0;
		restores = 0;
	}

	public static void ensure(long handle) {
		if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) {
			GLFW.glfwRestoreWindow(handle);
			GLFW.glfwMaximizeWindow(handle);
			restores++;
		}
		if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE) {
			losses++;
			GLFW.glfwFocusWindow(handle);
			restores++;
		}
	}

	public static int losses() {
		return losses;
	}

	public static int restores() {
		return restores;
	}

	public static boolean focused(long handle) {
		return GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
	}

	public static boolean iconified(long handle) {
		return GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
	}
}
