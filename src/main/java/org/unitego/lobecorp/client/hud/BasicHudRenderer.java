package org.unitego.lobecorp.client.hud;

import org.jspecify.annotations.NullMarked;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/// 浮点几何与最终像素对齐；布局和动画数据不因取整丢失精度。
@NullMarked
public abstract class BasicHudRenderer {
	private float x, y, width, height;
	private boolean visible = true;
	private PixelAlignment positionAlignment = PixelAlignment.INHERIT;
	private PixelAlignment sizeAlignment = PixelAlignment.INHERIT;
	private @Nullable ContainerHudRenderer parent;
	private float renderX, renderY;
	private PixelAlignment resolvedPosition = PixelAlignment.NONE;
	private PixelAlignment resolvedSize = PixelAlignment.NONE;

	public float getX() {
		return x;
	}

	public float getY() {
		return y;
	}

	public float getWidth() {
		return width;
	}

	public float getHeight() {
		return height;
	}

	public void setPosition(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public void setSize(float width, float height) {
		this.width = Math.max(0, width);
		this.height = Math.max(0, height);
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public void setPositionAlignment(PixelAlignment alignment) {
		positionAlignment = alignment;
	}

	public void setSizeAlignment(PixelAlignment alignment) {
		sizeAlignment = alignment;
	}

	public void setPixelAlignment(PixelAlignment alignment) {
		setPositionAlignment(alignment);
		setSizeAlignment(alignment);
	}

	public @Nullable ContainerHudRenderer getParent() {
		return parent;
	}

	protected void setParent(@Nullable ContainerHudRenderer parent) {
		this.parent = parent;
	}

	public void layout() {
	}

	public void render(GuiGraphicsExtractor graphics) {
		render(graphics, 0, 0);
	}

	public void render(GuiGraphicsExtractor graphics, float originX, float originY) {
		renderAt(graphics, originX, originY, PixelAlignment.NONE, PixelAlignment.NONE);
	}

	protected void renderAt(GuiGraphicsExtractor graphics, float originX, float originY,
	                        PixelAlignment inheritedPosition, PixelAlignment inheritedSize) {
		if (!visible) {
			return;
		}
		layout();
		renderX = originX + x;
		renderY = originY + y;
		resolvedPosition = positionAlignment == PixelAlignment.INHERIT ? inheritedPosition : positionAlignment;
		resolvedSize = sizeAlignment == PixelAlignment.INHERIT ? inheritedSize : sizeAlignment;
		float left = resolvedPosition.apply(renderX);
		float top = resolvedPosition.apply(renderY);
		float drawnWidth = resolvedSize.apply(renderX + width) - resolvedSize.apply(renderX);
		float drawnHeight = resolvedSize.apply(renderY + height) - resolvedSize.apply(renderY);
		renderContent(graphics, left, top, drawnWidth, drawnHeight);
	}

	protected float renderOriginX() {
		return renderX;
	}

	protected float renderOriginY() {
		return renderY;
	}

	protected PixelAlignment positionAlignment() {
		return resolvedPosition;
	}

	protected PixelAlignment sizeAlignment() {
		return resolvedSize;
	}

	protected abstract void renderContent(GuiGraphicsExtractor graphics, float x, float y, float width, float height);
}
