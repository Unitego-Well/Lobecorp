package org.unitego.lobecorp.client.hud.status;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.hud.host.HudFrame;
import org.unitego.lobecorp.client.hud.status.bar.StatusGauge;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/// 玩家状态条组件：每帧先在宿主布局前刷新数值，再由自身绘制局部内容。
@NullMarked
public abstract class StatusBarComponent extends UIElement {
	private final DecimalFormat number = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
	private final DecimalFormat integer = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ROOT));
	protected final StatusGauge gauge;
	protected @Nullable LocalPlayer player;
	protected float currentValue;
	protected float maximumValue;
	protected float frameDelta;
	private boolean placed;
	private float placedLeft;
	private float placedTop;
	private boolean placedVisible;

	protected StatusBarComponent(StatusGauge gauge) {
		this.gauge = gauge;
		number.setRoundingMode(RoundingMode.HALF_UP);
		lss("position", "absolute");
		layout(layout -> layout.width(gauge.getWidth()).height(gauge.getHeight()));
		setVisible(false);
	}

	/// 在 ModularUI 布局与提取前刷新当帧状态；同一帧只调用一次。
	public void prepare(HudFrame frame) {
		var nextPlayer = frame.player();
		if (player != nextPlayer) {
			clear();
			player = nextPlayer;
		}

		maximumValue = getMaximumFromSource();
		currentValue = getValueFromSource();
		frameDelta = deltaTicks(frame);
		gauge.advance(currentValue, maximumValue, frameDelta, visibleForFrame());
		updateAppearance();
	}

	protected float deltaTicks(HudFrame frame) {
		return Minecraft.getInstance().isPaused() ? 0 : frame.deltaTracker().getRealtimeDeltaTicks();
	}

	/// 放置到宿主计算出的局部坐标；未变化时不重复标脏布局。
	public void place(float left, float top, boolean visible) {
		if (placed && placedLeft == left && placedTop == top && placedVisible == visible) {
			return;
		}

		placed = true;
		placedLeft = left;
		placedTop = top;
		placedVisible = visible;
		layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(left).top(top));
		setVisible(visible);
		setDisplay(visible);
		markTaffyStyleDirty();
	}

	protected boolean visibleForFrame() {
		return placedVisible;
	}

	@Override
	protected void drawBackgroundAdditional(IGUIContext context) {
		if (!(context instanceof GUIContext gui) || player == null) {
			return;
		}

		renderContents(gui.graphics, getPositionX(), getPositionY());
	}

	protected void renderContents(GuiGraphicsExtractor graphics, float left, float top) {
		renderGauge(graphics, left, top);
		renderValueText(graphics, left, top);
	}

	protected void renderGauge(GuiGraphicsExtractor graphics, float left, float top) {
		gauge.render(graphics, left, top);
	}

	protected void renderValueText(GuiGraphicsExtractor graphics, float left, float top) {
		var font = Minecraft.getInstance().font;
		var text = getRenderText();
		graphics.text(font, text, (int) (left - font.width(text)), (int) (top + font.lineHeight / 2 - 3), 0xFFFFFFFF);
	}

	protected MutableComponent getRenderText() {
		return getValueText().append("/").append(getMaxValueText());
	}

	protected MutableComponent getMaxValueText() {
		return Component.literal(format(maximumValue));
	}

	protected MutableComponent getValueText() {
		return Component.literal(format(currentValue));
	}

	protected String format(float value) {
		return value == Math.rint(value) ? integer.format(value) : number.format(value);
	}

	protected void updateAppearance() {
	}

	protected abstract float getValueFromSource();

	protected abstract float getMaximumFromSource();

	public void clear() {
		currentValue = 0;
		maximumValue = 0;
		frameDelta = 0;
		player = null;
		gauge.clear();
	}
}
