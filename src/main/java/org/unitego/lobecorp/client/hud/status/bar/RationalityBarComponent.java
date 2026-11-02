package org.unitego.lobecorp.client.hud.status.bar;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.status.StatusBarComponent;
import org.unitego.lobecorp.client.hud.status.bar.StatusGauge.TextureLayer;
import org.unitego.lobecorp.util.client.gui.GuiUtil;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;

/// 理智条组件；低理智动态纹理和档位切换保持旧状态条行为。
@NullMarked
public class RationalityBarComponent extends StatusBarComponent {
	private static final float HIGH_RATIO = 0.7F;
	private static final float LOW_RATIO = 1.0F / 3.0F;
	private static final Identifier DYNAMIC = texture("rationality_low_dynamic");
	private String appearance = "";

	public RationalityBarComponent() {
		super(new StatusGauge(93, 11, bottom("rationality"), internal("rationality"), light("rationality")));
	}

	@Override
	protected float getValueFromSource() {
		return player == null ? 0 : LcDamageUtil.getRationality(player);
	}

	@Override
	protected float getMaximumFromSource() {
		return player == null ? 0 : LcDamageUtil.getMaxRationality(player);
	}

	@Override
	protected void updateAppearance() {
		float ratio = maximumValue > 0 ? gauge.getRenderedValue() / maximumValue : 1;
		String next = ratio >= HIGH_RATIO ? "rationality_tall" : ratio < LOW_RATIO ? "rationality_low" : "rationality";
		if (!next.equals(appearance)) {
			appearance = next;
			gauge.setLayers(bottom(next), internal(next), light(next));
		}
	}

	@Override
	protected void renderGauge(GuiGraphicsExtractor graphics, float left, float top) {
		if (maximumValue > 0 && currentValue <= 0) {
			GuiUtil.blitSprite(graphics, DYNAMIC, left - 3, top - 1, 98, 14);
			return;
		}

		super.renderGauge(graphics, left, top);
	}

	@Override
	public void clear() {
		super.clear();
		appearance = "";
	}

	private static TextureLayer bottom(String name) {
		boolean low = name.equals("rationality_low");
		return new TextureLayer(texture(name + "_bottom"), low ? -1 : 0, low ? -2 : 0, low ? 94 : 93, low ? 14 : 11);
	}

	private static TextureLayer internal(String name) {
		return new TextureLayer(texture(name), 14, 2, 78, 7);
	}

	private static TextureLayer light(String name) {
		return new TextureLayer(texture(name + "_light"), 0, 0, 93, 11);
	}

	private static Identifier texture(String name) {
		return Lobecorp.id("hud_bar/rationality/" + name);
	}
}
