package org.unitego.lobecorp.client.development;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.MONITORINFOEX;
import org.lwjgl.system.windows.User32;
import org.lwjgl.system.windows.WindowProc;

/// 开发窗口的非激活铺满与本窗口最小化命令保护；不捕获或移动系统鼠标。
public class DevelopmentWindowPolicy {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final long hwnd;
	private final long previousProc;
	private final boolean hadMinimizeBox;
	private final WindowProc callback;
	private boolean destroyed;
	private boolean released;

	public DevelopmentWindowPolicy(long handle, boolean fullscreen) {
		RenderSystem.assertOnRenderThread();
		hwnd = GLFWNativeWin32.glfwGetWin32Window(handle);
		long style = User32.GetWindowLongPtr(hwnd, User32.GWL_STYLE);
		hadMinimizeBox = (style & User32.WS_MINIMIZEBOX) != 0;
		previousProc = User32.GetWindowLongPtr(hwnd, User32.GWL_WNDPROC);
		callback = WindowProc.create(this::windowMessage);
		long replaced;
		try {
			replaced = User32.SetWindowLongPtr(null, hwnd, User32.GWL_WNDPROC, callback.address());
		} catch (RuntimeException | LinkageError failure) {
			callback.free();
			released = true;
			LOGGER.warn("开发窗口消息链安装失败，跳过窗口辅助", failure);
			return;
		}
		if (replaced == 0) {
			callback.free();
			released = true;
			LOGGER.warn("无法安装开发窗口消息链，跳过窗口辅助");
			return;
		}

		try {
			User32.SetWindowLongPtr(null, hwnd, User32.GWL_STYLE, style & ~User32.WS_MINIMIZEBOX);
			if (!fullscreen && !User32.IsIconic(hwnd)) {
				try (var stack = MemoryStack.stackPush()) {
					var info = MONITORINFOEX.calloc(stack).cbSize(MONITORINFOEX.SIZEOF);
					if (!User32.GetMonitorInfo(User32.MonitorFromWindow(hwnd, User32.MONITOR_DEFAULTTONEAREST), info)) {
						LOGGER.warn("无法读取开发窗口工作区，跳过铺满");
						return;
					}

					var work = info.rcWork();
					// 铺满工作区不等于原生最大化；不设置 WS_MAXIMIZE 或伪造 GLFW 状态。
					if (!User32.SetWindowPos(null, hwnd, 0, work.left(), work.top(),
							work.right() - work.left(), work.bottom() - work.top(),
							User32.SWP_NOACTIVATE | User32.SWP_NOZORDER | User32.SWP_FRAMECHANGED)) {
						LOGGER.warn("开发窗口工作区铺满失败；不重试、不激活窗口");
					}
				}
			}
		} catch (RuntimeException | LinkageError failure) {
			// 已安装的回调继续由 Window 生命周期持有，到窗口销毁后统一释放。
			LOGGER.warn("开发窗口辅助初始化未完成，跳过剩余窗口操作", failure);
		}
	}

	public static DevelopmentWindowPolicy install(long handle, boolean fullscreen) {
		if (Platform.get() != Platform.WINDOWS) {
			return null;
		}

		try {
			return new DevelopmentWindowPolicy(handle, fullscreen);
		} catch (RuntimeException | LinkageError failure) {
			LOGGER.warn("开发窗口辅助不可用，跳过窗口操作", failure);
			return null;
		}
	}

	private long windowMessage(long window, int message, long wParam, long lParam) {
		if (message == User32.WM_SYSCOMMAND && (wParam & 0xFFF0L) == User32.SC_MINIMIZE) {
			return 0;
		}

		if (message == User32.WM_NCDESTROY) {
			// 本机回调不调用会断言线程或抛异常的 Java 清理入口。
			destroyed = true;
		}

		// 包含 WM_SIZE 的通知仍交给原链；吞掉通知不能阻止已发生的原生最小化。
		return User32.nCallWindowProc(previousProc, window, message, wParam, lParam);
	}

	public void detach() {
		RenderSystem.assertOnRenderThread();
		if (destroyed || released) {
			return;
		}

		if (User32.GetWindowLongPtr(hwnd, User32.GWL_WNDPROC) == callback.address()) {
			User32.SetWindowLongPtr(null, hwnd, User32.GWL_WNDPROC, previousProc);
		}

		if (hadMinimizeBox) {
			long style = User32.GetWindowLongPtr(hwnd, User32.GWL_STYLE);
			User32.SetWindowLongPtr(null, hwnd, User32.GWL_STYLE, style | User32.WS_MINIMIZEBOX);
		}
	}

	/// 原生窗口销毁后才释放，避免后续子类化链仍引用本回调。
	public void releaseAfterDestroy() {
		RenderSystem.assertOnRenderThread();
		if (!released) {
			destroyed = true;
			callback.free();
			released = true;
		}
	}
}
