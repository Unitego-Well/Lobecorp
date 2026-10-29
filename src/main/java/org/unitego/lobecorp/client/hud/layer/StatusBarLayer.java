package org.unitego.lobecorp.client.hud.layer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.unitego.lobecorp.client.hud.widget.HorizontalStatusBar;
import org.jspecify.annotations.NullMarked;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.math.RoundingMode;

/// 状态条共用数值读取、格式化、文本绘制与位置同步，子类只补充数据源和外观。
@NullMarked
public abstract class StatusBarLayer extends HudLayer {
	private final DecimalFormat number = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
	private final DecimalFormat integer = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ROOT));
	protected final HorizontalStatusBar statusBar;
	protected float currentValue;
	protected float maximumValue;

	protected StatusBarLayer(HorizontalStatusBar statusBar) {
		this.statusBar = statusBar;
		number.setRoundingMode(RoundingMode.HALF_UP);
	}

	@Override
	public void init(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		super.init(graphics, deltaTracker);
		maximumValue = getMaximumFromSource();
		currentValue = getValueFromSource();
		statusBar.update(currentValue, maximumValue, deltaTicks(deltaTracker));
		updateAppearance();
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		renderStatusBar(graphics, deltaTicks(deltaTracker));
		renderValueText(graphics);
	}

	protected void renderValueText(GuiGraphicsExtractor graphics) {
		var text = getRenderText();
		graphics.text(font, text, leftPos - font.width(text), topPos + font.lineHeight / 2 - 3, 0xFFFFFFFF);
	}

	protected MutableComponent getRenderText() {
		return getValueText().append("/").append(getMaxValueText());
	}

	protected MutableComponent getMaxValueText() {
		return Component.literal(format(maximumValue));
	}

	protected void renderStatusBar(GuiGraphicsExtractor graphics, float delta) {
		statusBar.render(graphics, delta);
	}

	protected MutableComponent getValueText() {
		return Component.literal(format(currentValue));
	}

	protected String format(float value) {
		return value == Math.rint(value) ? integer.format(value) : number.format(value);
	}

	protected abstract float getValueFromSource();

	protected abstract float getMaximumFromSource();

	protected void updateAppearance() {
	}

	@Override
	public void setPosition(int left, int top) {
		super.setPosition(left, top);
		statusBar.setPosition(left, top);
	}

	@Override
	public int getWidth() {
		return statusBar.getWidth();
	}

	@Override
	public int getHeight() {
		return statusBar.getHeight();
	}

	@Override
	public void clear() {
		super.clear();
		currentValue = 0;
		maximumValue = 0;
		statusBar.clear();
	}
}
