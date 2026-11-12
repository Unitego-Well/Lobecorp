package org.unitego.lobecorp.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/// 子元素使用父级原始浮点坐标，最终绘制时才应用继承的对齐策略。
@NullMarked
public class ContainerHudRenderer extends BasicHudRenderer {
	private final List<BasicHudRenderer> children = new ArrayList<>();
	private Layout layout = Layout.FREE;
	private float gap;
	private float contentWidth, contentHeight;
	private boolean clip;
	private boolean autoSize;
	private Anchor horizontalAnchor = Anchor.START;
	private Anchor verticalAnchor = Anchor.START;

	private static float offset(Anchor anchor, float available, float size) {
		return switch (anchor) {
			case START -> 0;
			case CENTER -> (available - size) / 2;
			case END -> available - size;
		};
	}

	public void add(BasicHudRenderer child) {
		if (child == this || child.getParent() != null) {
			throw new IllegalArgumentException("元素已有父级或为容器自身");
		}
		for (ContainerHudRenderer ancestor = this; ancestor != null; ancestor = ancestor.getParent()) {
			if (ancestor == child) {
				throw new IllegalArgumentException("容器不能形成循环");
			}
		}
		children.add(child);
		child.setParent(this);
	}

	public void remove(BasicHudRenderer child) {
		if (children.remove(child)) {
			child.setParent(null);
		}
	}

	public void clearChildren() {
		for (var child : children) {
			child.setParent(null);
		}
		children.clear();
	}

	public List<BasicHudRenderer> getChildren() {
		return List.copyOf(children);
	}

	public void setLayout(Layout layout) {
		this.layout = layout;
	}

	public void setGap(float gap) {
		this.gap = gap;
	}

	public void setClip(boolean clip) {
		this.clip = clip;
	}

	public void setAutoSize(boolean autoSize) {
		this.autoSize = autoSize;
	}

	public void setAnchor(Anchor horizontal, Anchor vertical) {
		horizontalAnchor = horizontal;
		verticalAnchor = vertical;
	}

	@Override
	public void layout() {
		float cursor = 0, right = 0, bottom = 0;
		boolean first = true;
		for (var child : children) {
			if (!child.isVisible()) {
				continue;
			}
			child.layout();
			if (layout != Layout.FREE) {
				if (!first) {
					cursor += gap;
				}
				child.setPosition(layout == Layout.HORIZONTAL ? cursor : child.getX(),
						layout == Layout.VERTICAL ? cursor : child.getY());
				cursor += layout == Layout.HORIZONTAL ? child.getWidth() : child.getHeight();
				first = false;
			}
			right = Math.max(right, child.getX() + child.getWidth());
			bottom = Math.max(bottom, child.getY() + child.getHeight());
		}
		contentWidth = right;
		contentHeight = bottom;
		if (autoSize) {
			setSize(right, bottom);
		}
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
		if (clip) {
			graphics.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + width), (int) Math.ceil(y + height));
		}
		try {
			float ox = renderOriginX(), oy = renderOriginY();
			for (var child : children) {
				float ax = offset(horizontalAnchor, getWidth(), contentWidth);
				float ay = offset(verticalAnchor, getHeight(), contentHeight);
				child.renderAt(graphics, ox + ax, oy + ay, positionAlignment(), sizeAlignment());
			}
		} finally {
			if (clip) {
				graphics.disableScissor();
			}
		}
	}

	public enum Layout {FREE, VERTICAL, HORIZONTAL}

	public enum Anchor {START, CENTER, END}
}
