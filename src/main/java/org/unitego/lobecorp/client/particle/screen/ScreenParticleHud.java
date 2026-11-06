package org.unitego.lobecorp.client.particle.screen;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;

/// 屏幕粒子 HUD 门面：直接提交粒子系统提取，投影与内容批次仍归粒子系统。
@NullMarked
public class ScreenParticleHud implements GuiLayer {
	public static final Identifier ID = Lobecorp.id("screen_particle_hud");
	public static final ScreenParticleHud INSTANCE = new ScreenParticleHud();

	private ScreenParticleHud() {
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (visible()) {
			ScreenParticles.extract(graphics, deltaTracker);
		}
	}

	public void clear() {
		ScreenParticles.clear();
	}

	private boolean visible() {
		var minecraft = Minecraft.getInstance();
		return minecraft.level != null && !minecraft.options.hideGui;
	}

}
