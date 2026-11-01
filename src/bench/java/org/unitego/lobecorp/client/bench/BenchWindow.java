package org.unitego.lobecorp.client.bench;

import org.lwjgl.glfw.GLFW;

/// 基准窗口辅助：仅记录焦点变化，不操作系统窗口、鼠标或用户设置。
/// 不强行抢占焦点，避免干扰同时使用电脑的用户。
public final class BenchWindow {
	private static int losses;
	private static int restores;
	private static boolean focusLossRecorded;

	private BenchWindow() {
	}

	public static void reset() {
		losses = 0;
		restores = 0;
		focusLossRecorded = false;
	}

	/// 每 tick 仅观察焦点，开发环境节流由公共客户端入口处理。
	public static void ensure(long handle) {
		boolean focused = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
		if (!focused && !focusLossRecorded) {
			// 连续失焦只记一次，避免每 tick 累加把次数刷成噪声。
			focusLossRecorded = true;
			losses++;
		}
		if (focused) {
			focusLossRecorded = false;
		}
	}

	/// 保留场景清理入口；窗口与节流策略不随场景切换。
	public static void restore() {
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
