package org.unitego.lobecorp.client.hud.status.bar;

import net.minecraft.client.gui.Gui.HeartType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.client.ClientHooks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.host.HudFrame;
import org.unitego.lobecorp.client.hud.status.StatusBarComponent;
import org.unitego.lobecorp.client.hud.status.bar.StatusGauge.TextureLayer;
import org.unitego.lobecorp.util.client.gui.GuiUtil;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/// 生命条组件；吸收生命与心形变体继续使用旧状态条纹理和格式。
@NullMarked
public class HealthBarComponent extends StatusBarComponent {
	private static final Identifier LIGHT = texture("light");
	private static final Identifier ABSORPTION = texture("absorbing");
	private static final Identifier ABSORPTION_LIGHT = texture("absorbing_light");
	private static final int ABSORPTION_TEXT_COLOR = 0xFFE400;
	private final DecimalFormat absorptionNumber = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
	private @Nullable HeartType heartType;
	private final AbsorptionFlash absorptionFlash = new AbsorptionFlash();

	public HealthBarComponent() {
		super(new StatusGauge(90, 9, bottom("full"), internal("full"), new TextureLayer(LIGHT, 0, 0, 90, 9)));
		absorptionNumber.setRoundingMode(RoundingMode.HALF_UP);
	}

	@Override
	public void prepare(HudFrame frame) {
		super.prepare(frame);
		absorptionFlash.prepare(player == null ? 0 : player.getAbsorptionAmount(), frameDelta, visibleForFrame());
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
		gauge.setLayers(bottom(variant), internal(variant), new TextureLayer(LIGHT, 0, 0, 90, 9));
	}

	@Override
	protected void renderGauge(GuiGraphicsExtractor graphics, float left, float top) {
		super.renderGauge(graphics, left + 3, top);
		if (absorptionFlash.absorption() > 0) {
			GuiUtil.blitSprite(graphics, absorptionFlash.lightTicks() > 0 ? ABSORPTION_LIGHT : ABSORPTION,
					left + 13, top, 80, 9);
		}
	}

	@Override
	protected MutableComponent getValueText() {
		var value = super.getValueText();
		if (absorptionFlash.absorption() > 0) {
			value.append(Component.literal("+" + absorptionNumber.format(absorptionFlash.absorption())).withColor(ABSORPTION_TEXT_COLOR));
		}

		return value;
	}

	@Override
	public void clear() {
		super.clear();
		heartType = null;
		absorptionFlash.clear();
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
