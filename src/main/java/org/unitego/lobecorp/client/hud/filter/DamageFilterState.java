package org.unitego.lobecorp.client.hud.filter;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/// 四色伤害滤镜的纯状态：保持首次命中顺序、重复命中复位与逐帧衰减。
///
/// 本类不读取客户端会话或绘制上下文；命中可先于 UI 创建保存，业务状态由登记的 clearAction 清理。
@NullMarked
public final class DamageFilterState {
	public static final int CHANNEL_COUNT = 4;
	private static final float FADE_SPEED = 0.5F;
	private final float[] current = new float[CHANNEL_COUNT];
	private final float[] previous = new float[CHANNEL_COUNT];
	private final float[] alpha = new float[CHANNEL_COUNT];
	private final LinkedHashSet<Integer> active = new LinkedHashSet<>();

	/// 按位标记命中通道；重复命中把该通道恢复到完整亮度并保留原首次顺序。
	public void hit(int channels) {
		for (int index = 0; index < CHANNEL_COUNT; index++) {
			if ((channels & 1 << index) != 0) {
				current[index] = 1;
				previous[index] = 1;
				alpha[index] = 1;
				active.add(index);
			}
		}
	}

	/// 按当帧 delta 采样透明度并衰减；绘制读取采样值，`current` 的衰减影响下一帧。
	public void advance(float delta) {
		float step = Math.clamp(delta, 0, 1) * FADE_SPEED;
		var iterator = active.iterator();
		while (iterator.hasNext()) {
			int index = iterator.next();
			previous[index] += (current[index] - previous[index]) * step;
			alpha[index] = Math.clamp(previous[index], 0, 1);
			current[index] -= step;
			if (alpha[index] <= 0 && current[index] <= 0) {
				iterator.remove();
			}
		}
	}

	/// 按首次命中顺序返回当前通道采样值；视图只读取该快照绘制。
	public List<Layer> entries() {
		var result = new ArrayList<Layer>(active.size());
		for (int index : active) {
			result.add(new Layer(index, alpha[index]));
		}

		return result;
	}

	public void clear() {
		active.clear();
		for (int index = 0; index < CHANNEL_COUNT; index++) {
			current[index] = 0;
			previous[index] = 0;
			alpha[index] = 0;
		}
	}

	/// 单个通道在当前帧的透明度。
	public record Layer(int channel, float alpha) {
	}
}
