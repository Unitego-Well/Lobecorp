package org.unitego.lobecorp.client.hud.encounter;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.AbstractHud;
import org.unitego.lobecorp.registry.client.encounter.hud.EncounterHudRegistry;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;

/// 事件 HUD 的业务门面；注册定义与活动状态在这里汇合，按原版图层直接绘制。
@NullMarked
public class EncounterHud extends AbstractHud {
	public static final Identifier ID = Lobecorp.id("event_hud");
	public static final EncounterHud INSTANCE = new EncounterHud();
	private static final int EVENT_GAP = 4;
	private final EncounterHudState<EncounterHudComponent> state = new EncounterHudState<>();

	private EncounterHud() {
		setFitToScreen(false);
		setAutoSize(true);
	}

	private static float layoutContainer(EncounterHudState<? extends EncounterHudComponent> state,
	                                     @Nullable Identifier wrapperId, float top, List<Placed> result) {
		float y = top;
		for (var childId : state.childWrapperIds(wrapperId)) {
			y = layoutContainer(state, childId, y, result);
		}

		for (var component : state.activeInOrder(wrapperId)) {
			if (!component.isVisible()) {
				continue;
			}
			result.add(new Placed(component, y));
			y += component.height() + EVENT_GAP;
		}

		return y;
	}

	public EncounterHudRegistry<EncounterHudComponent> registry() {
		return state.registry();
	}

	/// 在首次绘制前登记直属根的包装容器；首次绘制后明确拒绝。
	public void registerWrapper(Identifier wrapperId) {
		registerWrapper(wrapperId, null);
	}

	/// 在首次绘制前登记指定父级的包装容器；父级关系由注册表唯一持有。
	public void registerWrapper(Identifier wrapperId, @Nullable Identifier parentId) {
		registry().registerWrapper(wrapperId, parentId);
	}

	/// 登记直接挂在根节点的事件定义。
	public void register(Identifier id, String englishName, String chineseName,
	                     Supplier<? extends EncounterHudComponent> factory) {
		register(id, englishName, chineseName, null, factory);
	}

	/// 登记归属指定包装的事件定义；包装必须先行登记。
	public void register(Identifier id, String englishName, String chineseName, @Nullable Identifier wrapperId,
	                     Supplier<? extends EncounterHudComponent> factory) {
		registry().register(id, englishName, chineseName, wrapperId, factory);
	}

	public EncounterHudComponent show(Identifier type, UUID instanceId) {
		synchronizeSession();
		return state.show(type, instanceId);
	}

	public void hide(UUID instanceId) {
		state.hide(instanceId);
	}

	/// 清空活动实例，保留事件定义供重新显示。
	protected void onSessionChanged() {
		clearChildren();
		state.clearActive();
	}

	@Override
	protected void prepareFrame(float delta) {
		prepare(delta);
	}

	/// 更新事件区域，与外部状态面板及屏幕边距无关。
	public void prepare(float delta) {
		synchronizeSession();
		state.markHostCreated();
		clearChildren();
		var placedEvents = layout(state);
		for (var placed : placedEvents) {
			placed.component().setPosition(0, placed.top());
			add(placed.component());
		}
		setVisible(!placedEvents.isEmpty());
		layout();
	}

	/// 事件区域的局部布局：先子包装、后所属容器中的活动事件。
	public List<Placed> layout(EncounterHudState<? extends EncounterHudComponent> state) {
		var result = new ArrayList<Placed>();
		layoutContainer(state, null, 0, result);
		return List.copyOf(result);
	}

	@Override
	protected boolean hudVisible() {
		return true;
	}

	public record Placed(EncounterHudComponent component, float top) {
	}

}
