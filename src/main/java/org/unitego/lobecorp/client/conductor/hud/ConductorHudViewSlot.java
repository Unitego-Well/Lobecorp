package org.unitego.lobecorp.client.conductor.hud;

import org.jspecify.annotations.Nullable;

/// 门面当前视图的单一引用槽；释放旧宿主时不得清掉后来创建的新视图。
public final class ConductorHudViewSlot<T> {
	private @Nullable T view;

	public @Nullable T get() {
		return view;
	}

	public void set(T next) {
		view = next;
	}

	/// 仅当槽内仍是同一对象时清空；返回是否发生清理。
	public boolean clearIfSame(T expected) {
		if (view != expected) {
			return false;
		}

		view = null;
		return true;
	}
}
