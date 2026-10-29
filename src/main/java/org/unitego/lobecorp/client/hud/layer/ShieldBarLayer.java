package org.unitego.lobecorp.client.hud.layer;

import org.unitego.lobecorp.Lobecorp;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.client.hud.widget.HorizontalStatusBar;
import org.unitego.lobecorp.client.hud.widget.HorizontalStatusBar.TextureLayer;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/// 四色护盾条按伤害类型选择纹理；当前值为零时跳过条形绘制，数值文本沿用基类。
@NullMarked
public class ShieldBarLayer extends StatusBarLayer {
	private final LcDamageType type;

	public ShieldBarLayer(LcDamageType type) {
		super(createBar(type));
		this.type = type;
	}

	public LcDamageType getDamageType() {
		return type;
	}

	@Override
	protected void renderStatusBar(GuiGraphicsExtractor graphics, float delta) {
		if (currentValue != 0) {
			super.renderStatusBar(graphics, delta);
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

	private static HorizontalStatusBar createBar(LcDamageType type) {
		String name = switch (type) {
			case PHYSICS -> "physic";
			case SPIRIT -> "spirit";
			case EROSION -> "erosion";
			case THE_SOUL -> "soul";
		};
		String path = "hud_bar/shield/" + name + "_shield_bar";
		return new HorizontalStatusBar(93, 11,
				new TextureLayer(Lobecorp.id(path + "_bottom"), 0, 0, 93, 11),
				new TextureLayer(Lobecorp.id(path), 14, 2, 78, 7),
				new TextureLayer(Lobecorp.id(path + "_light"), 0, 0, 93, 11));
	}
}
