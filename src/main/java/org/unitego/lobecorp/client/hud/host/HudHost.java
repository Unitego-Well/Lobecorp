package org.unitego.lobecorp.client.hud.host;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/// 通过组合持有 [ModularUI] 的 HUD 宿主；视觉内容保持普通 [UIElement]。
@NullMarked
public final class HudHost implements IHudHost, ModularHudLayer {
	private final Consumer<HudFrame> renderer;
	private final Runnable releaseAction;
	private final UIElement root;
	private @Nullable ModularUI modularUI;
	private boolean released;
	private int screenWidth;
	private int screenHeight;

	/// 创建一个由内容节点驱动绘制的宿主。
	///
	/// `renderer` 只在 LDLib2 同步提取期间执行；`releaseAction` 与内容节点的释放
	/// 由该宿主拥有，重复 [release()] 不会重复执行。
	public HudHost(UIElement content, Consumer<HudFrame> renderer, Runnable releaseAction) {
		Objects.requireNonNull(content);
		this.renderer = Objects.requireNonNull(renderer);
		this.releaseAction = Objects.requireNonNull(releaseAction);
		root = new HostRoot();
		root.layout(layout -> layout.widthPercent(100).heightPercent(100));
		root.addChild(content);
	}

	@Override
	public void ensureInitialized(int screenWidth, int screenHeight) {
		if (released) {
			return;
		}

		if (modularUI == null) {
			initializeNewUI(screenWidth, screenHeight);
			return;
		}

		if (this.screenWidth != screenWidth || this.screenHeight != screenHeight) {
			modularUI.init(screenWidth, screenHeight);
			this.screenWidth = screenWidth;
			this.screenHeight = screenHeight;
		}
	}

	private void initializeNewUI(int screenWidth, int screenHeight) {
		var candidate = new ModularUI(UI.of(root, size -> size));
		try {
			candidate.init(screenWidth, screenHeight);
			modularUI = candidate;
			this.screenWidth = screenWidth;
			this.screenHeight = screenHeight;
		} catch (RuntimeException exception) {
			try {
				candidate.onRemoved();
			} catch (RuntimeException cleanupFailure) {
				exception.addSuppressed(cleanupFailure);
			}

			throw exception;
		}
	}

	@Override
	public void render(HudFrame frame) {
		if (released) {
			return;
		}

		ensureInitialized(frame.screenWidth(), frame.screenHeight());
		try (HudFrameScope ignored = HudFrameScope.open(frame)) {
			ModularHudLayer.super.render(frame.graphics(), frame.deltaTracker());
		}
	}

	@Override
	public @Nullable ModularUI getModularUI() {
		return modularUI;
	}

	@Override
	public void release() {
		if (released) {
			return;
		}

		released = true;
		var oldUI = modularUI;
		modularUI = null;
		RuntimeException failure = null;
		try {
			releaseAction.run();
		} catch (RuntimeException exception) {
			failure = exception;
		}

		if (oldUI != null) {
			try {
				oldUI.onRemoved();
			} catch (RuntimeException exception) {
				if (failure == null) {
					failure = exception;
				} else {
					failure.addSuppressed(exception);
				}
			}
		}

		if (failure != null) {
			throw failure;
		}
	}

	private final class HostRoot extends UIElement {
		@Override
		protected void drawBackgroundAdditional(IGUIContext context) {
			if (!(context instanceof GUIContext)) {
				return;
			}

			var frame = HudFrameScope.current();
			if (frame != null) {
				renderer.accept(frame);
			}
		}
	}
}
