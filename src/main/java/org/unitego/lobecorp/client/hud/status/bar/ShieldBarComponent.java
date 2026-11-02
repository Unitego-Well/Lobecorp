package org.unitego.lobecorp.client.hud.status.bar;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.client.hud.status.StatusBarComponent;
import org.unitego.lobecorp.client.hud.status.bar.StatusGauge.TextureLayer;
import org.unitego.lobecorp.registry.LcAttachmentTypes;

/// 四色护盾条组件；只绘制非零存量，纹理按伤害类型选择。
@NullMarked
public class ShieldBarComponent extends StatusBarComponent {
	private final LcDamageType type;

	public ShieldBarComponent(LcDamageType type) {
		super(createGauge(type));
		this.type = type;
	}

	@Override
	protected void renderGauge(GuiGraphicsExtractor graphics, float left, float top) {
		if (currentValue != 0) {
			super.renderGauge(graphics, left, top);
		}
	}

	@Override
	protected float getValueFromSource() {
		return player == null ? 0 : player.getData(LcAttachmentTypes.LC_SHIELDS).get(type).amount();
	}

	@Override
	protected float getMaximumFromSource() {
		return player == null ? 0 : player.getData(LcAttachmentTypes.LC_SHIELDS).get(type).maximum();
	}

	private static StatusGauge createGauge(LcDamageType type) {
		String name = switch (type) {
			case PHYSICS -> "physic";
			case SPIRIT -> "spirit";
			case EROSION -> "erosion";
			case THE_SOUL -> "soul";
		};
		String path = "hud_bar/shield/" + name + "_shield_bar";
		return new StatusGauge(93, 11,
				new TextureLayer(Lobecorp.id(path + "_bottom"), 0, 0, 93, 11),
				new TextureLayer(Lobecorp.id(path), 14, 2, 78, 7),
				new TextureLayer(Lobecorp.id(path + "_light"), 0, 0, 93, 11));
	}
}
