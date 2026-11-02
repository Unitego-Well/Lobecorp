package org.unitego.lobecorp.client.hud.filter;

import org.jspecify.annotations.NullMarked;

/// 低理智滤镜的纯状态：把当前理智比例平滑映射到三层交叉透明度。
@NullMarked
public final class RationalityFilterState {
	private static final float LOW_RATIO = 1.0F / 3.0F;
	private static final float LERP_SPEED = 0.5F;
	private final float[] alpha = new float[3];
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

		alpha[0] = Math.clamp(1 - Math.abs(intensity - 0.25F) * 4, 0, 1);
		alpha[1] = Math.clamp(1 - Math.abs(intensity - 0.666F) * 1.5F, 0, 1);
		alpha[2] = Math.clamp((intensity - 0.666F) * 3, 0, 1);
	}

	/// 返回指定层的当前透明度。
	public float alpha(int layer) {
		return alpha[layer];
	}

	public void clear() {
		intensity = 0;
		resetAlpha();
	}

	private void resetAlpha() {
		alpha[0] = 0;
		alpha[1] = 0;
		alpha[2] = 0;
	}
}
