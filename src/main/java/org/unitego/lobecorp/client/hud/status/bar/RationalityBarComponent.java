package org.unitego.lobecorp.client.hud.status.bar;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.status.StatusBarComponent;
import org.unitego.lobecorp.client.hud.TextureLayer;
import net.minecraft.client.renderer.RenderPipelines;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;

/// 理智条按比例切换高低纹理，归零时改用独立动态纹理。
@NullMarked
public class RationalityBarComponent extends StatusBarComponent {
	private static final float HIGH_RATIO = 0.7F;
	private static final float LOW_RATIO = 1.0F / 3.0F;
	private static final Identifier DYNAMIC = texture("rationality_low_dynamic");
	private String appearance = "";

	public RationalityBarComponent() {
		super(new StatusGauge(93, 11, 78),
				new StatusGaugeView(bottom("rationality"), internal("rationality"), light("rationality")));
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
		String next = switch (textureRow(gauge.getRenderedValue(), maximumValue)) {
			case 0 -> "rationality_tall";
			case 1 -> "rationality_low";
			default -> "rationality";
		};
		if (!next.equals(appearance)) {
			appearance = next;
			gaugeView.setLayers(bottom(next), internal(next), light(next));
		}
	}

	@Override
	protected void renderGauge(GuiGraphicsExtractor graphics, float x, float y) {
		boolean dynamic = maximumValue > 0 && currentValue <= 0;
		if (dynamic) {
			graphics.lobecorp$blitSprite(RenderPipelines.GUI_TEXTURED, DYNAMIC, x - 3, y - 1, 98, 14);
			return;
		}

		super.renderGauge(graphics, x, y);
	}

	@Override
	public void clear() {
		super.clear();
		appearance = "";
	}

	public static int textureRow(float value, float maximum) {
		float ratio = maximum > 0 ? value / maximum : 1;
		return ratio >= HIGH_RATIO ? 0 : ratio < LOW_RATIO ? 1 : 2;
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
		return Lobecorp.id("hud/bar/rationality/" + name);
	}
}
