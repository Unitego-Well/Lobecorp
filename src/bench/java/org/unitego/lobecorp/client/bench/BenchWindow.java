package org.unitego.lobecorp.client.bench;

import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/// 基准窗口辅助：取消失焦与不活动限流，最小化时自动还原，并记录丢失/恢复次数供报告分析。
/// 不强行抢占焦点，避免干扰同时使用电脑的用户。
public final class BenchWindow {
	private static int losses;
	private static int restores;
	private static boolean focusLossRecorded;
	private static InactivityFpsLimit originalInactivity;

	private BenchWindow() {
	}

	public static void reset() {
		losses = 0;
		restores = 0;
		focusLossRecorded = false;
	}

	/// 每 tick 调用：解除原版失焦/不活动限流，丢焦点只记录一次，不抢焦点也不判失败。
	public static void ensure(long handle) {
		if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) {
			GLFW.glfwRestoreWindow(handle);
			GLFW.glfwMaximizeWindow(handle);
			restores++;
		}
		boolean focused = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
		if (!focused && !focusLossRecorded) {
			// 连续失焦只记一次，避免每 tick 累加把次数刷成噪声。
			focusLossRecorded = true;
			losses++;
		}
		if (focused) {
			focusLossRecorded = false;
		}
		var minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return;
		}
		// 失焦时原版会降到低频并节流，后台跑基准口径会失真，这里统一关闭。
		if (minecraft.options.inactivityFpsLimit().get() != InactivityFpsLimit.MINIMIZED) {
			if (originalInactivity == null) {
				originalInactivity = minecraft.options.inactivityFpsLimit().get();
			}
			minecraft.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
		}
		minecraft.getFramerateLimitTracker().onInputReceived();
	}

	/// 场景结束时恢复用户原本的不活动限流设置。
	public static void restore() {
		if (originalInactivity == null) {
			return;
		}
		var minecraft = Minecraft.getInstance();
		if (minecraft != null) {
			minecraft.options.inactivityFpsLimit().set(originalInactivity);
		}
		originalInactivity = null;
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
