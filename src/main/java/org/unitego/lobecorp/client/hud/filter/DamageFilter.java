package org.unitego.lobecorp.client.hud.filter;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.widget.ScreenFilter;
import org.unitego.lobecorp.config.client.PlayerHudConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/// 四色伤害滤镜：统一维护命中顺序、透明度衰减与绘制。
@NullMarked
public final class DamageFilter extends AbstractScreenFilter {
	public static final Identifier ID = Lobecorp.id("damage_screen_filter");
	public static final int CHANNEL_COUNT = 4;
	private static final String[] TEXTURES = {"physics", "spirit", "erosion", "the_soul"};
	private static final float FADE_SPEED = 0.5F;
	public static final DamageFilter INSTANCE = new DamageFilter();
	private final Channel[] channels = new Channel[CHANNEL_COUNT];
	private final LinkedHashSet<Channel> active = new LinkedHashSet<>();

	public DamageFilter() {
		for (int index = 0; index < channels.length; index++) {
			channels[index] = new Channel(index, new ScreenFilter(ScreenFilter.texture(TEXTURES[index])));
		}
	}

	/// 按位标记命中通道；重复命中把该通道恢复到完整亮度并保留原首次顺序。
	public void hit(int channels) {
		for (int index = 0; index < CHANNEL_COUNT; index++) {
			if ((channels & 1 << index) != 0) {
				var channel = this.channels[index];
				channel.current = 1;
				channel.previous = 1;
				channel.filter.setAlpha(1);
				active.add(channel);
			}
		}
	}

	/// 按当帧 delta 采样透明度并衰减；绘制读取采样值，`current` 的衰减影响下一帧。
	@Override
	public void advance(float delta) {
		float step = Math.clamp(delta, 0, 1) * FADE_SPEED;
		var iterator = active.iterator();
		while (iterator.hasNext()) {
			var channel = iterator.next();
			channel.previous += (channel.current - channel.previous) * step;
			channel.filter.setAlpha(channel.previous);
			channel.current -= step;
			if (channel.filter.getAlpha() <= 0 && channel.current <= 0) {
				iterator.remove();
			}
		}
	}

	/// 按首次命中顺序返回当前通道采样值。
	public List<Layer> entries() {
		var result = new ArrayList<Layer>(active.size());
		for (var channel : active) {
			result.add(new Layer(channel.index, channel.filter.getAlpha()));
		}

		return result;
	}

	@Override
	protected boolean enabled() {
		return PlayerHudConfig.ENABLE_LC_COLOR_DAMAGE_FILTER.get();
	}

	/// 网络命中先同步会话，避免新会话首击被首次绘制清除。
	public void receiveHit(int channels) {
		if (available()) {
			hit(channels);
		}
	}

	/// 按当前首次命中顺序绘制；每层只读取自身透明度。
	@Override
	public void render(GuiGraphicsExtractor graphics) {
		// 旧视图按 entries 逆序加入，LDLib2 同 z 排序后反向遍历等于加入顺序；
		// 迁移到原版直接绘制时，按同样逆序绘制以保持覆盖顺序。
		for (var channel : active.reversed()) {
			channel.filter.render(graphics);
		}
	}

	@Override
	public void clear() {
		active.clear();
		for (var channel : channels) {
			channel.current = 0;
			channel.previous = 0;
			channel.filter.setAlpha(0);
		}
	}

	/// 单个通道在当前帧的透明度。
	public record Layer(int channel, float alpha) {
	}

	private static class Channel {
		private final int index;
		private final ScreenFilter filter;
		private float current;
		private float previous;

		private Channel(int index, ScreenFilter filter) {
			this.index = index;
			this.filter = filter;
		}
	}
}
