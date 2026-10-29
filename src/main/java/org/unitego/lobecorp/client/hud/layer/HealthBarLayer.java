package org.unitego.lobecorp.client.hud.layer;

import net.minecraft.client.DeltaTracker;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.gui.Gui.HeartType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.client.ClientHooks;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.widget.HorizontalStatusBar;
import org.unitego.lobecorp.client.hud.widget.HorizontalStatusBar.TextureLayer;
import org.unitego.lobecorp.util.client.gui.GuiUtil;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.math.RoundingMode;

/// 生命条按原版生命类型切换纹理，并叠加吸收生命及其短暂高亮。
@NullMarked
public class HealthBarLayer extends StatusBarLayer {
	private static final Identifier LIGHT = texture("light");
	private static final Identifier ABSORPTION = texture("absorbing");
	private static final Identifier ABSORPTION_LIGHT = texture("absorbing_light");
	private static final int ABSORPTION_TEXT_COLOR = 0xFFE400;
	private final DecimalFormat absorptionNumber = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
	private @Nullable HeartType heartType;
	private float absorption;
	private float absorptionLightTicks;

	public HealthBarLayer() {
		super(new HorizontalStatusBar(90, 9, bottom("full"), internal("full"), new TextureLayer(LIGHT, 0, 0, 90, 9)));
		absorptionNumber.setRoundingMode(RoundingMode.HALF_UP);
	}

	@Override
	public void init(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		super.init(graphics, deltaTracker);
		float next = player == null ? 0 : player.getAbsorptionAmount();
		if (next != absorption) {
			absorption = next;
			absorptionLightTicks = next > 0 ? 2 : 0;
		}
	}

	@Override
	protected float getValueFromSource() {
		return player == null ? 0 : player.getHealth();
	}

	@Override
	protected float getMaximumFromSource() {
		return player == null ? 0 : player.getMaxHealth();
	}

	@Override
	protected void updateAppearance() {
		if (player == null) {
			return;
		}

		var vanilla = player.hasEffect(MobEffects.POISON) ? HeartType.POISIONED
				: player.hasEffect(MobEffects.WITHER) ? HeartType.WITHERED
				: player.isFullyFrozen() ? HeartType.FROZEN : HeartType.NORMAL;
		var next = ClientHooks.firePlayerHeartTypeEvent(player, vanilla);
		if (next == heartType) {
			return;
		}

		heartType = next;
		String variant = switch (next) {
			case POISIONED -> "poisioned";
			case WITHERED -> "withered";
			case FROZEN -> "frozen";
			default -> "full";
		};
		statusBar.setLayers(bottom(variant), internal(variant), new TextureLayer(LIGHT, 0, 0, 90, 9));
	}

	@Override
	protected void renderStatusBar(GuiGraphicsExtractor graphics, float delta) {
		super.renderStatusBar(graphics, delta);
		if (absorption > 0) {
			GuiUtil.blitSprite(graphics, absorptionLightTicks > 0 ? ABSORPTION_LIGHT : ABSORPTION,
					leftPos + 13, topPos, 80, 9);
			absorptionLightTicks = Math.max(0, absorptionLightTicks - delta);
		}
	}

	@Override
	protected MutableComponent getValueText() {
		var value = super.getValueText();
		if (absorption > 0) {
			value.append(Component.literal("+" + absorptionNumber.format(absorption)).withColor(ABSORPTION_TEXT_COLOR));
		}

		return value;
	}

	@Override
	public void setPosition(int left, int top) {
		super.setPosition(left, top);
		statusBar.setPosition(left + 3, top);
	}

	@Override
	public void clear() {
		super.clear();
		heartType = null;
		absorption = 0;
		absorptionLightTicks = 0;
	}

	private static TextureLayer bottom(String variant) {
		return new TextureLayer(texture(variant + "_bottom"), 0, 0, 90, 9);
	}

	private static TextureLayer internal(String variant) {
		return new TextureLayer(texture(variant), 11, 1, 78, 7);
	}

	private static Identifier texture(String name) {
		return Lobecorp.id("hud_bar/health/" + name);
	}
}
