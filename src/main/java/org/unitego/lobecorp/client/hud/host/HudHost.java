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
	private final Consumer<HudFrame> prepareAction;
	private @Nullable SpecialRoot specialRoot;
	private @Nullable Consumer<HudFrame> specialAfterRender;
	private final Runnable releaseAction;
	private final UIElement root;
	private @Nullable ModularUI modularUI;
	private boolean released;

	/// 准备回调先于 LDLib2 的布局与同步提取执行。
	public HudHost(UIElement content, Consumer<HudFrame> prepareAction, Runnable releaseAction) {
		Objects.requireNonNull(content);
		this.prepareAction = Objects.requireNonNull(prepareAction);
		this.releaseAction = Objects.requireNonNull(releaseAction);
		root = new UIElement();
		root.layout(layout -> layout.widthPercent(100).heightPercent(100));
		root.addChild(content);
	}

	/// 仅供需要在 UI 树背景阶段提交特殊绘制的旧入口使用。
	public static HudHost special(UIElement content, Consumer<HudFrame> renderer, Runnable releaseAction) {
		return special(content, _ -> {
		}, renderer, _ -> {
		}, releaseAction);
	}

	/// 保留特殊入口的布局前准备、背景绘制和布局后绘制顺序。
	public static HudHost special(UIElement content, Consumer<HudFrame> prepareAction, Consumer<HudFrame> renderer,
	                              Consumer<HudFrame> afterRender, Runnable releaseAction) {
		var specialRoot = new SpecialRoot(Objects.requireNonNull(renderer));
		specialRoot.layout(layout -> layout.widthPercent(100).heightPercent(100));
		specialRoot.addChild(content);
		var host = new HudHost(specialRoot, prepareAction, releaseAction);
		host.specialRoot = specialRoot;
		host.specialAfterRender = Objects.requireNonNull(afterRender);
		return host;
	}

	@Override
	public void ensureInitialized(int screenWidth, int screenHeight) {
		if (released) {
			return;
		}

		if (modularUI == null) {
			initializeNewUI(screenWidth, screenHeight);
		}
	}

	private void initializeNewUI(int screenWidth, int screenHeight) {
		var candidate = new ModularUI(UI.of(root, size -> size));
		try {
			candidate.init(screenWidth, screenHeight);
			modularUI = candidate;
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

		prepareAction.accept(frame);
		ensureInitialized(frame.screenWidth(), frame.screenHeight());
		var special = specialRoot;
		if (special == null) {
			ModularHudLayer.super.render(frame.graphics(), frame.deltaTracker());
			return;
		}

		var previousFrame = special.frame;
		special.frame = frame;
		try {
			ModularHudLayer.super.render(frame.graphics(), frame.deltaTracker());
			Objects.requireNonNull(specialAfterRender).accept(frame);
		} finally {
			special.frame = previousFrame;
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

	private static final class SpecialRoot extends UIElement {
		private final Consumer<HudFrame> renderer;
		private @Nullable HudFrame frame;

		private SpecialRoot(Consumer<HudFrame> renderer) {
			this.renderer = renderer;
		}

		@Override
		protected void drawBackgroundAdditional(IGUIContext context) {
			if (context instanceof GUIContext && frame != null) {
				renderer.accept(frame);
			}
		}
	}
}
