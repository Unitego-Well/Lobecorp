package org.unitego.lobecorp.client.hud.host;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 将当帧上下文桥接给 LDLib2 同步绘制回调；离开作用域后立即恢复。
@NullMarked
public final class HudFrameScope implements AutoCloseable {
	private static final ThreadLocal<HudFrame> CURRENT = new ThreadLocal<>();
	private final @Nullable HudFrame previous;
	private boolean closed;

	private HudFrameScope(HudFrame frame) {
		previous = CURRENT.get();
		CURRENT.set(frame);
	}

	/// 在同步提取作用域内暴露当前帧。
	public static HudFrameScope open(HudFrame frame) {
		return new HudFrameScope(frame);
	}

	/// 返回当前同步提取帧；不在作用域内时为 `null`。
	public static @Nullable HudFrame current() {
		return CURRENT.get();
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}

		closed = true;
		if (previous == null) {
			CURRENT.remove();
		} else {
			CURRENT.set(previous);
		}
	}
}
