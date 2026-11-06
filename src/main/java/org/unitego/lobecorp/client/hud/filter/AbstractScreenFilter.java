package org.unitego.lobecorp.client.hud.filter;

import org.unitego.lobecorp.client.hud.AbstractHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NullMarked;

/// 滤镜共用的会话、显隐与暂停处理；具体效果由各自实现持有。
@NullMarked
public abstract class AbstractScreenFilter extends AbstractHud {

	protected abstract boolean enabled();

	protected abstract void advance(float delta);

	public abstract void render(GuiGraphicsExtractor graphics);

	public abstract void clear();

	protected final boolean available() {
		tick();
		return enabled() && !session.unavailable();
	}

	public final void tick() {
		synchronizeSession();
		if (!enabled()) {
			clear();
		}
	}

	public final void resetSession() {
		session.clear();
		clear();
	}

	@Override
	protected void onSessionChanged() {
		clear();
	}

	@Override
	protected boolean hudVisible() {
		return available() && super.hudVisible();
	}

	@Override
	protected void prepareFrame(float delta) {
		advance(delta);
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
		var pose = graphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(x, y);
			render(graphics);
		} finally {
			pose.popMatrix();
		}
	}

}
