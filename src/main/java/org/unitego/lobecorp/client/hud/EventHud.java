package org.unitego.lobecorp.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.event.EventHudState;
import org.unitego.lobecorp.client.hud.event.EventHudView;
import org.unitego.lobecorp.client.hud.host.HudHost;

import java.util.UUID;
import java.util.function.Supplier;

/// 事件 HUD 的业务门面；注册定义、活动状态与统一宿主生命周期在这里汇合。
@NullMarked
public class EventHud implements GuiLayer {
	public static final Identifier ID = Lobecorp.id("event_hud");
	public static final EventHud INSTANCE = new EventHud();
	private final EventHudState<EventHudComponent> state = new EventHudState<>(EventHudComponent::direction);

	private EventHud() {
	}

	/// 向统一管理器登记事件宿主；由启动期的串行集成入口调用。
	public void registerLayer() {
		HudLayerManager.INSTANCE.register(ID, () -> !state.activeEmpty(), this::createHost, state::clearActive);
	}

	public void register(Identifier id, String englishName, String chineseName,
	                     Supplier<? extends EventHudComponent> factory) {
		state.register(id, englishName, chineseName, factory);
	}

	public EventHudComponent show(Identifier type, UUID instanceId) {
		HudLayerManager.INSTANCE.synchronizeSession();
		return state.show(type, instanceId);
	}

	public void hide(UUID instanceId) {
		state.hide(instanceId);
	}

	/// 清空活动实例与宿主，保留事件定义供重新显示；会话身份由管理器维护。
	public void clear() {
		HudLayerManager.INSTANCE.clear(ID);
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		HudLayerManager.INSTANCE.render(ID, graphics, deltaTracker);
	}

	private HudHost createHost() {
		var view = new EventHudView(state);
		return new HudHost(view, view::prepare, _ -> {
		}, view::clear);
	}
}
