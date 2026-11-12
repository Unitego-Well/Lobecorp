package org.unitego.lobecorp.client.hud.status.bar;

import net.minecraft.client.gui.Gui.HeartType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.client.ClientHooks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.TextureLayer;
import org.unitego.lobecorp.client.hud.status.StatusBarComponent;

import java.text.DecimalFormat;

/// 生命条按原版生命类型切换纹理，并叠加吸收生命及其短暂高亮。
@NullMarked
public class HealthBarComponent extends StatusBarComponent {
	private static final Identifier LIGHT = texture("light");
	private static final Identifier ABSORPTION = texture("absorbing");
	private static final Identifier ABSORPTION_LIGHT = texture("absorbing_light");
	private static final int ABSORPTION_TEXT_COLOR = 0xFFE400;
	private final DecimalFormat absorptionNumber = createNumberFormat("#,##0.00");
	private final AbsorptionFlash absorptionFlash = new AbsorptionFlash();
	private @Nullable HeartType heartType;

	public HealthBarComponent() {
		super(new StatusGauge(90, 9, 78),
				new StatusGaugeView(bottom("full"), internal("full"), new TextureLayer(LIGHT, 0, 0, 90, 9)));
	}

	public static HeartType heartType(LocalPlayer player) {
		var vanilla = player.hasEffect(MobEffects.POISON) ? HeartType.POISIONED
				: player.hasEffect(MobEffects.WITHER) ? HeartType.WITHERED
				: player.isFullyFrozen() ? HeartType.FROZEN : HeartType.NORMAL;
		return ClientHooks.firePlayerHeartTypeEvent(player, vanilla);
	}

	private static TextureLayer bottom(String variant) {
		return new TextureLayer(texture(variant + "_bottom"), 0, 0, 90, 9);
	}

	private static TextureLayer internal(String variant) {
		return new TextureLayer(texture(variant), 11, 1, 78, 7);
	}

	private static Identifier texture(String name) {
		return Lobecorp.id("hud/bar/health/" + name);
	}

	@Override
	public void prepare(@Nullable LocalPlayer nextPlayer, float delta) {
		super.prepare(nextPlayer, delta);
		absorptionFlash.prepare(player == null ? 0 : player.getAbsorptionAmount(), frameDelta, visibleForFrame());
	}

	@Override
	protected void renderGauge(GuiGraphicsExtractor graphics, float x, float y) {
		gaugeView.render(graphics, x + 3, y, gauge);
		if (absorptionFlash.absorption() > 0) {
			graphics.lobecorp$blitSprite(RenderPipelines.GUI_TEXTURED, absorptionFlash.lightTicks() > 0 ? ABSORPTION_LIGHT : ABSORPTION,
					x + 13, y, 80, 9);
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
		if (player == null || !visibleForFrame()) {
			return;
		}

		var next = heartType(player);
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
		gaugeView.setLayers(bottom(variant), internal(variant), new TextureLayer(LIGHT, 0, 0, 90, 9));
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
}
