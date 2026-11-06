package org.unitego.lobecorp.client.hud.status.bar;

import org.jspecify.annotations.NullMarked;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.client.hud.status.StatusBarComponent;
import org.unitego.lobecorp.client.hud.TextureLayer;
import org.unitego.lobecorp.registry.LcAttachmentTypes;

/// 四色护盾条按伤害类型选择纹理；当前值为零时跳过条形绘制，数值文本沿用基类。
@NullMarked
public class ShieldBarComponent extends StatusBarComponent {
	private final LcDamageType type;

	public ShieldBarComponent(LcDamageType type) {
		super(new StatusGauge(93, 11, 78), createView(type));
		this.type = type;
	}

	@Override
	protected void renderGauge(GuiGraphicsExtractor graphics, float x, float y) {
		if (currentValue == 0) {
			return;
	}

		super.renderGauge(graphics, x, y);
	}

	@Override
	protected float getValueFromSource() {
		return player == null ? 0 : player.getData(LcAttachmentTypes.LC_SHIELDS).get(type).amount();
	}

	@Override
	protected float getMaximumFromSource() {
		return player == null ? 0 : player.getData(LcAttachmentTypes.LC_SHIELDS).get(type).maximum();
	}

	private static StatusGaugeView createView(LcDamageType type) {
		String name = switch (type) {
			case PHYSICS -> "physic";
			case SPIRIT -> "spirit";
			case EROSION -> "erosion";
			case THE_SOUL -> "soul";
		};
		String path = "hud/bar/shield/" + name + "_shield_bar";
		return new StatusGaugeView(
				new TextureLayer(Lobecorp.id(path + "_bottom"), 0, 0, 93, 11),
				new TextureLayer(Lobecorp.id(path), 14, 2, 78, 7),
				new TextureLayer(Lobecorp.id(path + "_light"), 0, 0, 93, 11));
	}
}
