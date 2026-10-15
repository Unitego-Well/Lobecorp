package org.unitego.lobecorp.client.photon.animation.adapter;

import com.lowdragmc.photon.client.fx.FXRuntime;

import java.util.ArrayDeque;

/// 编辑器回放的同步作用域；内部编排继续运行，仅静默外部回调与音频。
public class PhotonAnimationPreviewScope {
	/// 当前线程的嵌套编辑器回放深度；最外层退出时清除。
	private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

	/// 正在建立制作预览的主实例；后续生成的子实例不关闭默认动画。
	private static final ThreadLocal<ArrayDeque<FXRuntime>> AUTHORING = ThreadLocal.withInitial(ArrayDeque::new);

	public static void enterAuthoring(FXRuntime runtime) {
		enter();
		AUTHORING.get().push(runtime);
	}

	public static void leaveAuthoring() {
		var runtimes = AUTHORING.get();
		runtimes.pop();

		if (runtimes.isEmpty()) {
			AUTHORING.remove();
		}

		leave();
	}

	public static boolean authoring(FXRuntime runtime) {
		return AUTHORING.get().peek() == runtime;
	}

	public static void enter() {
		DEPTH.set(DEPTH.get() + 1);
	}

	public static void leave() {
		var depth = DEPTH.get() - 1;

		if (depth <= 0) {
			DEPTH.remove();
			return;
		}

		DEPTH.set(depth);
	}

	public static boolean active() {
		return DEPTH.get() > 0;
	}
}
