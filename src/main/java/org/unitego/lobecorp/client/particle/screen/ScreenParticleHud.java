package org.unitego.lobecorp.client.particle.screen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.HudLayerManager;
import org.unitego.lobecorp.client.hud.host.HudFrame;
import org.unitego.lobecorp.client.hud.host.HudHost;

/// 屏幕粒子 HUD 门面：宿主与显隐由统一管理器负责，投影与内容批次仍归粒子系统。
@NullMarked
public class ScreenParticleHud implements GuiLayer {
	public static final Identifier ID = Lobecorp.id("screen_particle_hud");
	public static final ScreenParticleHud INSTANCE = new ScreenParticleHud();

	private ScreenParticleHud() {
	}

	/// 向统一管理器注册粒子宿主；须在首次提取前由启动期串行集成调用。
	public void register() {
		HudLayerManager.INSTANCE.register(ID, this::visible, () -> {
			var content = new UIElement();
			content.layout(layout -> layout.widthPercent(100).heightPercent(100));
			return new HudHost(content, this::draw, () -> {
			});
		});
	}

	/// 保留原图层入口；宿主登记由启动期串行集成完成，此处只提交当帧提取。
	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		HudLayerManager.INSTANCE.render(ID, graphics, deltaTracker);
	}

	public void clear() {
		HudLayerManager.INSTANCE.clear(ID);
	}

	private boolean visible() {
		var minecraft = Minecraft.getInstance();
		return minecraft.level != null && !minecraft.options.hideGui;
	}

	private void draw(HudFrame frame) {
		ScreenParticles.extract(frame.graphics(), frame.deltaTracker());
	}
}
