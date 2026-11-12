package org.unitego.lobecorp.client.hud.filter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.widget.ScreenFilter;
import org.unitego.lobecorp.config.client.PlayerHudConfig;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;

/// 低理智滤镜：统一维护平滑强度、三层交叉透明度与绘制。
@NullMarked
public final class RationalityFilter extends AbstractScreenFilter {
	public static final Identifier ID = Lobecorp.id("rationality_screen_filter");
	public static final RationalityFilter INSTANCE = new RationalityFilter();
	private static final float LOW_RATIO = 1.0F / 3.0F;
	private static final float LERP_SPEED = 0.5F;
	private final ScreenFilter[] filters = {
			new ScreenFilter(ScreenFilter.texture("rationality")),
			new ScreenFilter(ScreenFilter.texture("rationality1")),
			new ScreenFilter(ScreenFilter.texture("rationality2"))
	};
	private float intensity;

	/// 按当帧理智值和最大值推进平滑强度；最大值异常时清空全部透明度。
	public void advance(float rationality, float maximum, float delta) {
		if (!Float.isFinite(maximum) || maximum <= 0) {
			intensity = 0;
			resetAlpha();
			return;
		}

		float safeRationality = Float.isFinite(rationality) ? rationality : 0;
		float ratio = safeRationality / maximum;
		// 保留当前 0～上限的理智规则，最低三分之一映射到旧项目的三层交叉淡变。
		// 健康区间保留负偏移，平滑值才能像旧实现一样越过零并清除全部滤镜。
		float target = (LOW_RATIO - ratio) / LOW_RATIO;
		float step = Math.clamp(delta * LERP_SPEED, 0, 1);
		intensity += (target - intensity) * step;
		if (step > 0 && target <= 0 && intensity > 0 && intensity <= Math.ulp(1.0F)) {
			intensity = 0;
		}

		if (intensity <= 0) {
			resetAlpha();
			return;
		}

		filters[0].setAlpha(Math.clamp(1 - Math.abs(intensity - 0.25F) * 4, 0, 1));
		filters[1].setAlpha(Math.clamp(1 - Math.abs(intensity - 0.666F) * 1.5F, 0, 1));
		filters[2].setAlpha(Math.clamp((intensity - 0.666F) * 3, 0, 1));
	}

	/// 返回指定层的当前透明度。
	public float alpha(int layer) {
		return filters[layer].getAlpha();
	}

	@Override
	protected boolean enabled() {
		return PlayerHudConfig.ENABLE_LOW_RATIONALITY_FILTER.get();
	}

	@Override
	protected void advance(float delta) {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			clear();

			return;
		}

		advance(LcDamageUtil.getRationality(player), LcDamageUtil.getMaxRationality(player), delta);
	}

	/// 按原有三层从后到前顺序绘制。
	@Override
	public void render(GuiGraphicsExtractor graphics) {
		for (int index = filters.length - 1; index >= 0; index--) {
			filters[index].render(graphics);
		}
	}

	@Override
	public void clear() {
		intensity = 0;
		resetAlpha();
	}

	private void resetAlpha() {
		for (var filter : filters) {
			filter.setAlpha(0);
		}
	}

}
