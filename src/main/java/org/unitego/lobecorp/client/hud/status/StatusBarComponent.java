package org.unitego.lobecorp.client.hud.status;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.hud.status.bar.StatusGauge;
import org.unitego.lobecorp.client.hud.ContainerHudRenderer;
import org.unitego.lobecorp.client.hud.TextHudRenderer;
import org.unitego.lobecorp.client.hud.status.bar.StatusGaugeView;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/// 玩家状态条组件：每帧先刷新数值与动画状态，再由原版绘制入口提交局部内容。
@NullMarked
public abstract class StatusBarComponent extends ContainerHudRenderer {
	private final TextHudRenderer valueText = new TextHudRenderer();
	private final DecimalFormat number = createNumberFormat("0.00");
	private final DecimalFormat integer = createNumberFormat("0");
	protected final StatusGauge gauge;
	protected final StatusGaugeView gaugeView;
	private final boolean valueTextVisible;
	protected @Nullable LocalPlayer player;
	protected float currentValue;
	protected float maximumValue;
	protected float frameDelta;
	private boolean frameVisible;

	protected StatusBarComponent(StatusGauge gauge, StatusGaugeView gaugeView) {
		this(gauge, gaugeView, true);
	}

	protected StatusBarComponent(StatusGauge gauge, StatusGaugeView gaugeView, boolean valueTextVisible) {
		setSize(gauge.getWidth(), gauge.getHeight());
		valueText.setAlignment(TextHudRenderer.Alignment.RIGHT);
		this.gauge = gauge;
		this.gaugeView = gaugeView;
		this.valueTextVisible = valueTextVisible;
	}

	protected static DecimalFormat createNumberFormat(String pattern) {
		var format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT));
		format.setRoundingMode(RoundingMode.HALF_UP);
		return format;
	}

	/// 在绘制前刷新当帧状态；同一帧只调用一次。
	public void prepare(@Nullable LocalPlayer nextPlayer, float delta) {
		if (player != nextPlayer) {
			clear();
			player = nextPlayer;
		}

		float value = getValueFromSource();
		float maximum = getMaximumFromSource();
		prepare(value, maximum, delta);
	}

	/// 使用调用方提供的数值更新动画，不依赖玩家取值方式。
	public void prepare(float value, float maximum, float delta) {
		maximumValue = maximum;
		currentValue = value;
		frameDelta = deltaTicks(delta);
		gauge.advance(currentValue, maximumValue, frameDelta, visibleForFrame());
		updateAppearance();
	}

	protected float deltaTicks(float delta) {
		return Minecraft.getInstance().isPaused() ? 0 : delta;
	}

	/// 更新本帧可见性；隐藏条不参与容器布局与 gap。
	public void updateFrameVisibility(boolean visible) {
		frameVisible = visible;
	}

	protected boolean visibleForFrame() {
		return frameVisible;
	}

	/// 在指定屏幕坐标绘制状态条与数值。
	protected void renderContent(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
		renderGauge(graphics, x, y);
		if (valueTextVisible) {
			renderValueText(graphics, x, y);
		}
		super.renderContent(graphics, x, y, width, height);
	}

	/// 状态条视觉内容的定位扩展点；数值文字始终使用组件基准坐标。
	protected void renderGauge(GuiGraphicsExtractor graphics, float x, float y) {
		gaugeView.render(graphics, x, y, gauge);
	}

	protected void renderValueText(GuiGraphicsExtractor graphics, float x, float y) {
		var font = Minecraft.getInstance().font;
		valueText.setText(getRenderText());
		valueText.setPosition(0, font.lineHeight / 2 - 3);
		valueText.render(graphics, x, y);
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

	protected float getValueFromSource() {
		return currentValue;
	}

	protected float getMaximumFromSource() {
		return maximumValue;
	}

	public void clear() {
		currentValue = 0;
		maximumValue = 0;
		frameDelta = 0;
		player = null;
		gauge.clear();
	}
}
