package org.unitego.lobecorp.client.development;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.JNI;
import org.lwjgl.system.windows.WINDOWPLACEMENT;
import net.neoforged.fml.loading.FMLEnvironment;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.MONITORINFOEX;
import org.lwjgl.system.windows.User32;
import org.lwjgl.system.windows.WindowProc;
import org.slf4j.Logger;

/// 显式测试入口的原生最大化与最小化保护；普通启动默认关闭，不激活窗口或操作实体鼠标。
public class DevelopmentWindowPolicy {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String ENABLE_PROPERTY = "lobecorp.developmentWindowTestPolicy";
	private static final String MODE_PROPERTY = "lobecorp.developmentWindowTestMode";
	private static boolean creationHintsApplied;
	private static long foregroundBefore;
	private boolean visibleChecked;
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
			if (nativeMode()) {
				User32.SetWindowPos(null, hwnd, 0, 0, 0, 0, 0,
						User32.SWP_NOMOVE | User32.SWP_NOSIZE | User32.SWP_NOACTIVATE
								| User32.SWP_NOZORDER | User32.SWP_FRAMECHANGED);
			} else if ("workarea".equals(System.getProperty(MODE_PROPERTY))
					&& !fullscreen && !User32.IsIconic(hwnd)) {
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
		if (!enabled()) {
			return null;
		}

		try {
			return new DevelopmentWindowPolicy(handle, fullscreen);
		} catch (RuntimeException | LinkageError failure) {
			LOGGER.warn("开发窗口辅助不可用，跳过窗口操作", failure);
			return null;
		}
	}

	public static boolean enabled() {
		return !FMLEnvironment.isProduction() && Boolean.getBoolean(ENABLE_PROPERTY)
				&& Platform.get() == Platform.WINDOWS;
	}

	public static boolean nativeMode() {
		return "native".equals(System.getProperty(MODE_PROPERTY, "native"));
	}

	public static void prepareCreation(long monitor) {
		creationHintsApplied = false;
		if (!enabled() || !nativeMode() || monitor != 0L) {
			return;
		}
		foregroundBefore = foregroundWindow();
		GLFW.glfwWindowHint(GLFW.GLFW_MAXIMIZED, GLFW.GLFW_TRUE);
		GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_CENTER_CURSOR, GLFW.GLFW_FALSE);
		creationHintsApplied = true;
		LOGGER.info("event=creation-hints mode=native foregroundBefore={} pid={}", foregroundBefore, ProcessHandle.current().pid());
	}

	public static boolean nativeCreated(long handle) {
		return enabled() && nativeMode() && creationHintsApplied
				&& GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
	}

	private static long foregroundWindow() {
		return JNI.callP(User32.getLibrary().getFunctionAddress("GetForegroundWindow"));
	}

	/// 首次可见时只读验收；不恢复前台、不重试最大化。
	public void verifyVisible(long handle) {
		if (visibleChecked || GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE) != GLFW.GLFW_TRUE) {
			return;
		}
		visibleChecked = true;
		try (var stack = MemoryStack.stackPush()) {
			var placement = WINDOWPLACEMENT.calloc(stack).length(WINDOWPLACEMENT.SIZEOF);
			boolean placementRead = User32.GetWindowPlacement(null, hwnd, placement);
			var width = stack.mallocInt(1);
			var height = stack.mallocInt(1);
			var fbWidth = stack.mallocInt(1);
			var fbHeight = stack.mallocInt(1);
			GLFW.glfwGetWindowSize(handle, width, height);
			GLFW.glfwGetFramebufferSize(handle, fbWidth, fbHeight);
			long after = foregroundWindow();
			boolean zoomed = User32.IsZoomed(hwnd);
			int maximized = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED);
			LOGGER.info("event=first-visible mode={} creationHints={} foregroundBefore={} foregroundAfter={} foregroundUnchanged={} max={} placementRead={} placement={} glfwMaximized={} glfwSize={}x{} framebuffer={}x{} pid={} success={}",
					nativeMode() ? "native" : "workarea", creationHintsApplied, foregroundBefore, after,
					foregroundBefore == after, zoomed, placementRead, placement.showCmd(), maximized,
					width.get(0), height.get(0), fbWidth.get(0), fbHeight.get(0), ProcessHandle.current().pid(),
					creationHintsApplied && foregroundBefore == after && zoomed && placementRead
							&& placement.showCmd() == 3 && maximized == GLFW.GLFW_TRUE);
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
