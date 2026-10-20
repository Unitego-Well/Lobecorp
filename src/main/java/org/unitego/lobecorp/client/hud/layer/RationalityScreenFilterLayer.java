package org.unitego.lobecorp.client.hud.layer;

import net.minecraft.client.DeltaTracker;
import org.jspecify.annotations.NullMarked;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.widget.ScreenFilter;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;

@NullMarked
public class RationalityScreenFilterLayer extends ScreenFilterLayer {
	private static final float LOW_RATIO = 1.0F / 3.0F;
	private static final float LERP_SPEED = 0.5F;
	private float intensity;

	public RationalityScreenFilterLayer() {
		super(new ScreenFilter(Lobecorp.id("textures/gui/filter/rationality.png")),
				new ScreenFilter(Lobecorp.id("textures/gui/filter/rationality1.png")),
				new ScreenFilter(Lobecorp.id("textures/gui/filter/rationality2.png")));
	}

	@Override
	public void init(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		super.init(graphics, deltaTracker);
		if (player == null) {
			return;
		}

		float maximum = LcDamageUtil.getMaxRationality(player);
		if (maximum <= 0) {
			intensity = 0;
			resetAlpha();
			return;
		}

		float ratio = LcDamageUtil.getRationality(player) / maximum;
		// 保留当前 0～上限的理智规则，最低三分之一映射到旧项目的三层交叉淡变。
		// 健康区间保留负偏移，平滑值才能像旧实现一样越过零并清除全部滤镜。
		float target = (LOW_RATIO - ratio) / LOW_RATIO;
		float step = Math.clamp(deltaTicks(deltaTracker) * LERP_SPEED, 0, 1);
		intensity += (target - intensity) * step;
		if (step > 0 && target <= 0 && intensity > 0 && intensity <= Math.ulp(1.0F)) {
			intensity = 0;
		}

		if (intensity <= 0) {
			resetAlpha();
			return;
		}

		filters.get(0).setAlpha(1 - Math.abs(intensity - 0.25F) * 4);
		filters.get(1).setAlpha(1 - Math.abs(intensity - 0.666F) * 1.5F);
		filters.get(2).setAlpha((intensity - 0.666F) * 3);
	}

	private void resetAlpha() {
		for (var filter : filters) {
			filter.setAlpha(0);
		}
	}

	@Override
	public void clear() {
		super.clear();
		intensity = 0;
	}
}
