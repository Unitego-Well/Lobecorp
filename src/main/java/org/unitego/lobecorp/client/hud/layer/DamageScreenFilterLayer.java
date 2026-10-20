package org.unitego.lobecorp.client.hud.layer;

import net.minecraft.client.DeltaTracker;
import org.jspecify.annotations.NullMarked;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.widget.ScreenFilter;

import java.util.LinkedHashSet;
import java.util.Set;

/// 保留旧滤镜按首次命中顺序叠加、再次命中复位及逐帧衰减的行为。
@NullMarked
public class DamageScreenFilterLayer extends ScreenFilterLayer {
	private static final float FADE_SPEED = 0.5F;
	private final DamageFilter[] damageFilters;
	private final Set<DamageFilter> active = new LinkedHashSet<>();

	public DamageScreenFilterLayer() {
		this(new DamageFilter[]{
				new DamageFilter("physics"), new DamageFilter("spirit"),
				new DamageFilter("erosion"), new DamageFilter("the_soul")
		});
	}

	private DamageScreenFilterLayer(DamageFilter[] filters) {
		super(filters);
		damageFilters = filters;
	}

	public void hit(int channels) {
		for (int index = 0; index < damageFilters.length; index++) {
			if ((channels & 1 << index) != 0) {
				var filter = damageFilters[index];
				filter.current = 1;
				filter.previous = 1;
				filter.setAlpha(1);
				active.add(filter);
			}
		}
	}

	@Override
	protected void renderLayer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		float step = Math.clamp(deltaTicks(deltaTracker), 0, 1) * FADE_SPEED;
		var iterator = active.iterator();
		while (iterator.hasNext()) {
			var filter = iterator.next();
			filter.previous += (filter.current - filter.previous) * step;
			filter.setAlpha(filter.previous);
			filter.render(graphics);
			filter.current -= step;
			if (filter.getAlpha() <= 0 && filter.current <= 0) {
				iterator.remove();
			}
		}
	}

	@Override
	public void clear() {
		super.clear();
		active.clear();
		for (var filter : damageFilters) {
			filter.current = 0;
			filter.previous = 0;
		}
	}

	private static class DamageFilter extends ScreenFilter {
		private float current;
		private float previous;

		private DamageFilter(String name) {
			super(Lobecorp.id("textures/gui/filter/" + name + ".png"));
		}
	}
}
