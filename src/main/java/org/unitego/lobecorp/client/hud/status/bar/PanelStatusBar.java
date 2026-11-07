package org.unitego.lobecorp.client.hud.status.bar;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.TextHudRenderer;
import org.unitego.lobecorp.client.hud.TextureHudRenderer;
import org.unitego.lobecorp.client.hud.RectangleHudRenderer;
import org.unitego.lobecorp.client.hud.TextureLayer;
import org.unitego.lobecorp.client.hud.status.StatusBarComponent;
import org.unitego.lobecorp.config.client.PlayerHudConfig;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;
import org.unitego.lobecorp.util.entity.damage.LcShieldUtil;

/// NEW 条按基础值、吸收、护盾顺序组合；同一 pose 包含框、内容及数字。
@NullMarked
public class PanelStatusBar extends StatusBarComponent {
	private static final int BAR_HEIGHT = 7;
	private static final Identifier[] HEALTH_VALUES = textures("health",
			"full", "poisioned", "withered", "frozen", "absorbing", "physic_shield_bar", "erosion_shield_bar", "null");
	private static final Identifier[] RATIONALITY_VALUES = textures("rationality",
			"rationality_tall", "rationality_low", "rationality", "spirit_shield_bar");
	private static final int CONTENT_X = 19;
	private static final int CONTENT_Y = 4;
	private static final float MAX_FRAME_SCALE = 3;
	/// 数值减少后的白色闪光持续时间，单位 tick。
	private static final float WHITE_FLASH_TICKS = 2;
	/// 变化提示保持两秒，再以四分之一秒淡出；时间单位为游戏 tick。
	private static final float NUMBER_HOLD_TICKS = 40;
	private static final float NUMBER_FADE_TICKS = 5;
	private final boolean rationality;
	private final int boxWidth;
	private final int valueWidth;
	private float frameWidth;
	private final TextureHudRenderer box;
	private final TextureHudRenderer icon;
	private final Segment[] segments;
	private final TextHudRenderer numbers = new TextHudRenderer();
	private final StatusBarShake shake = new StatusBarShake();
	private float absorptionPeak;
	private final float[] numberAmounts;
	private float numberMaximum;
	private float numberTicks;
	private boolean numbersSampled;

	public PanelStatusBar(boolean rationality, Identifier boxTexture, Identifier iconTexture) {
		super(new StatusGauge(rationality ? 48 : 66, 15, rationality ? 20 : 38),
				new StatusGaugeView(new TextureLayer(boxTexture, 0, 0, rationality ? 48 : 66, 15),
						new TextureLayer(valueTexture(rationality, 0), CONTENT_X, CONTENT_Y, rationality ? 20 : 38, BAR_HEIGHT), null), false);
		this.rationality = rationality;
		boxWidth = rationality ? 48 : 66;
		valueWidth = rationality ? 20 : 38;
		frameWidth = boxWidth;
		box = TextureHudRenderer.sprite(boxTexture);
		box.setSize(boxWidth, 15);
		icon = TextureHudRenderer.sprite(iconTexture);
		icon.setPosition(8, 3);
		icon.setSize(10, 10);
		segments = new Segment[rationality ? 2 : 4];
		numberAmounts = new float[segments.length];
		numbers.setVisible(false);
		numbers.setShadow(true);
		for (int index = 0; index < segments.length; index++) {
			segments[index] = new Segment(index == 0 ? gauge : new StatusGauge(valueWidth, BAR_HEIGHT, valueWidth),
					TextureHudRenderer.sprite(valueTexture(rationality, 0)));
		}
		updateFrameVisibility(true);
	}

	@Override
	protected float getValueFromSource() {
		return player == null ? 0 : rationality ? LcDamageUtil.getRationality(player) : player.getHealth();
	}

	@Override
	protected float getMaximumFromSource() {
		return player == null ? 0 : rationality ? LcDamageUtil.getMaxRationality(player) : player.getMaxHealth();
	}

	@Override
	public void prepare(@Nullable LocalPlayer nextPlayer, float delta) {
		super.prepare(nextPlayer, delta);
		shake.advance(frameDelta);
		if (player == null) {
			return;
		}
		int baseRow = rationality ? RationalityBarComponent.textureRow(gauge.getRenderedValue(), maximumValue)
				: switch (HealthBarComponent.heartType(player)) {
			case POISIONED -> 1;
			case WITHERED -> 2;
			case FROZEN -> 3;
			default -> 0;
		};
		float loss = segments[0].update(currentValue, maximumValue, baseRow, false, frameDelta);
		float shieldTotal;
		float absorption = 0;
		if (rationality) {
			loss += updateShield(1, LcDamageType.SPIRIT, 3);
			shieldTotal = segments[1].amount;
		} else {
			absorption = player.getAbsorptionAmount();
			absorptionPeak = segments[1].amount <= 0 && absorption > 0 ? absorption : Math.max(absorptionPeak, absorption);
			loss += segments[1].update(absorption, absorptionPeak, 4, true, frameDelta);
			loss += updateShield(2, LcDamageType.PHYSICS, 5);
			loss += updateShield(3, LcDamageType.EROSION, 6);
			shieldTotal = segments[2].amount + segments[3].amount;
		}
		shake.trigger(loss);
		updateNumbers(absorption, shieldTotal);
		float total = 0;
		for (var segment : segments) {
			total += Math.max(0, segment.animation.getRenderedValue());
		}
		float scale = maximumValue > 0 ? Math.clamp(total / maximumValue, 1, MAX_FRAME_SCALE) : 1;
		frameWidth = boxWidth * scale;
		box.setSize(frameWidth, 15);
		numbers.setPosition(frameWidth + 4, 3);
		setSize(frameWidth + (numbers.isVisible() ? 4 + numbers.getWidth() : 0), 15);
	}

	private void updateNumbers(float absorption, float shieldTotal) {
		numberTicks = Math.max(0, numberTicks - Math.max(0, frameDelta));
		boolean changed = numbersSampled && Float.compare(numberMaximum, maximumValue) != 0;
		for (int index = 0; index < segments.length; index++) {
			changed |= numbersSampled && Float.compare(numberAmounts[index], segments[index].amount) != 0;
			numberAmounts[index] = segments[index].amount;
		}
		numberMaximum = maximumValue;
		numbersSampled = true;
		if (changed) {
			numberTicks = NUMBER_HOLD_TICKS + NUMBER_FADE_TICKS;
		}

		float alpha = Minecraft.getInstance().hasShiftDown() ? 1 : Math.min(1, numberTicks / NUMBER_FADE_TICKS);
		numbers.setVisible(Math.round(alpha * 255) >= 4);
		numbers.setColor(Math.round(alpha * 255) << 24 | 0xFFFFFF);
		ChatFormatting valueColor = rationality ? switch (RationalityBarComponent.textureRow(currentValue, maximumValue)) {
			case 0 -> ChatFormatting.BLUE;
			case 1 -> ChatFormatting.RED;
			default -> ChatFormatting.WHITE;
		} : ChatFormatting.WHITE;
		var text = Component.literal(format(currentValue)).withStyle(valueColor);
		if (absorption > 0) {
			text.append(Component.literal("+").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(format(absorption)).withStyle(ChatFormatting.WHITE));
		}
		if (shieldTotal > 0) {
			text.append(Component.literal("+").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(format(shieldTotal)).withStyle(ChatFormatting.WHITE));
		}
		text.append(Component.literal(" / " + format(maximumValue)).withStyle(ChatFormatting.WHITE));
		numbers.setText(text);
		numbers.layout();
	}

	private float updateShield(int index, LcDamageType type, int row) {
		if (player == null || !PlayerHudConfig.usesNewShield(type)) {
			segments[index].clear();
			return 0;
		}
		var pool = player.getData(LcAttachmentTypes.LC_SHIELDS).get(type);
		float amount = player.hasEffect(LcShieldUtil.effect(type)) ? pool.amount() : 0;
		return segments[index].update(amount, pool.maximum(), row, true, frameDelta);
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
		var pose = graphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(shake.x(), shake.y());
			super.renderContent(graphics, x, y, width, height);
		} finally {
			pose.popMatrix();
		}
	}

	@Override
	protected void renderGauge(GuiGraphicsExtractor graphics, float x, float y) {
		box.render(graphics, x, y);
		icon.render(graphics, x, y);
		float[] values = new float[segments.length];
		for (int index = 0; index < segments.length; index++) {
			var segment = segments[index];
			float rendered = Math.max(0, segment.animation.getRenderedValue());
			values[index] = segment.amount <= 0 && (segment.capacity <= 0
					|| rendered / segment.capacity * valueWidth < 0.5F) ? 0 : rendered;
		}
		float[] widths = SegmentedBarLayout.widths(valueWidth + frameWidth - boxWidth, maximumValue, values);
		float cursor = CONTENT_X;
		for (int index = 0; index < segments.length; index++) {
			var segment = segments[index];
			float width = widths[index];
			if (width <= 0) {
				continue;
			}
			segment.texture.setTexture(valueTexture(rationality, segment.row));
			segment.texture.setPosition(cursor, CONTENT_Y);
			segment.texture.setSize(width, BAR_HEIGHT);
			// 独立精灵由 nine_slice 保留端点并填充中间区域。
			segment.texture.render(graphics, x, y);
			if (segment.whiteTicks > 0) {
				segment.white.setColor(Math.round(Math.min(1, segment.whiteTicks) * 255) << 24 | 0xFFFFFF);
				segment.white.setPosition(cursor, CONTENT_Y);
				segment.white.setSize(width, BAR_HEIGHT);
				segment.white.render(graphics, x, y);
			}
			float trailWidth = Math.min(width, segment.animation.getTrailWidth() * width / valueWidth);
			if (trailWidth > 0) {
				segment.trail.setColor(segment.animation.getTrailColor());
				segment.trail.setPosition(cursor + width - trailWidth, CONTENT_Y);
				segment.trail.setSize(trailWidth, BAR_HEIGHT);
				segment.trail.render(graphics, x, y);
			}
			cursor += width;
		}
		numbers.render(graphics, x, y);
	}

	@Override
	public void clear() {
		super.clear();
		absorptionPeak = 0;
		numbersSampled = false;
		numberMaximum = 0;
		numberTicks = 0;
		numbers.setVisible(false);
		numbers.setShadow(true);
		frameWidth = boxWidth;
		box.setSize(frameWidth, 15);
		for (var segment : segments) {
			segment.clear();
		}
		shake.clear();
	}

	private static Identifier[] textures(String group, String... names) {
		var textures = new Identifier[names.length];
		for (int index = 0; index < names.length; index++) {
			textures[index] = Lobecorp.id("newhud/bar/" + group + "/" + names[index]);
		}
		return textures;
	}

	private static Identifier valueTexture(boolean rationality, int state) {
		return (rationality ? RATIONALITY_VALUES : HEALTH_VALUES)[state];
	}

	private static class Segment {
		private final StatusGauge animation;
		private final TextureHudRenderer texture;
		private final RectangleHudRenderer trail = new RectangleHudRenderer();
		private final RectangleHudRenderer white = new RectangleHudRenderer();
		private float whiteTicks;
		private float amount;
		private float capacity;
		private boolean sampled;
		private int row;

		private Segment(StatusGauge animation, TextureHudRenderer texture) {
			this.animation = animation;
			this.texture = texture;
		}

		private float update(float next, float maximum, int row, boolean advance, float delta) {
			next = Float.isFinite(next) ? Math.max(0, next) : 0;
			float loss = sampled && capacity > 0 ? Math.max(0, amount - next) / capacity : 0;
			whiteTicks = Math.max(0, whiteTicks - Math.max(0, delta));
			if (sampled && next < amount) {
				whiteTicks = WHITE_FLASH_TICKS;
			}
			amount = next;
			if (maximum > 0) {
				capacity = maximum;
			}
			sampled = true;
			this.row = row;
			if (advance) {
				animation.advance(next, capacity, delta, true);
			}
			return loss;
		}

		private void clear() {
			animation.clear();
			amount = 0;
			capacity = 0;
			sampled = false;
			whiteTicks = 0;
		}
	}
}
