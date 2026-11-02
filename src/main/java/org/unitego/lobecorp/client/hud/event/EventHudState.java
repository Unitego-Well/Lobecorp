package org.unitego.lobecorp.client.hud.event;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.hud.EventHudDirection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/// 事件 HUD 的纯注册与活动状态；不创建 UI，不依赖客户端会话。
///
/// 每个方向维护独立的活动顺序，`hide` 只移除目标 UUID，剩余组件按原顺序补位。
@NullMarked
public final class EventHudState<C> {
	private final Function<? super C, EventHudDirection> directionOf;
	private final Map<Identifier, Definition<C>> definitions = new LinkedHashMap<>();
	private final Map<UUID, Active<C>> active = new LinkedHashMap<>();
	private final Map<EventHudDirection, List<UUID>> order = new EnumMap<>(EventHudDirection.class);

	public EventHudState(Function<? super C, EventHudDirection> directionOf) {
		this.directionOf = Objects.requireNonNull(directionOf);

		for (var direction : EventHudDirection.values()) {
			order.put(direction, new ArrayList<>());
		}
	}

	/// 登记事件定义；同一 `Identifier` 重复登记明确拒绝。
	public void register(Identifier id, String englishName, String chineseName,
	                     Supplier<? extends C> factory) {
		Objects.requireNonNull(id);
		Objects.requireNonNull(englishName);
		Objects.requireNonNull(chineseName);
		Objects.requireNonNull(factory);
		var definition = new Definition<C>(englishName, chineseName, factory);
		if (definitions.putIfAbsent(id, definition) != null) {
			throw new IllegalArgumentException("事件 HUD 已注册：" + id);
		}
	}

	/// 显示实例；同 UUID 同类型幂等返回原组件，类型冲突或未登记定义时抛出异常。
	public C show(Identifier type, UUID instanceId) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(instanceId);
		var existing = active.get(instanceId);
		if (existing != null) {
			if (!existing.type().equals(type)) {
				throw new IllegalArgumentException("事件 HUD 实例类型不一致：" + instanceId);
			}

			return existing.component();
		}

		var definition = definitions.get(type);
		if (definition == null) {
			throw new IllegalArgumentException("事件 HUD 未注册：" + type);
		}

		var component = Objects.requireNonNull(definition.factory().get(), "事件 HUD 组件工厂返回 null");
		active.put(instanceId, new Active<>(type, component));
		order.get(directionOf.apply(component)).add(instanceId);
		return component;
	}

	/// 移除实例并返回其组件；未找到时返回 `null`。
	public @Nullable C hide(UUID instanceId) {
		var entry = active.remove(instanceId);
		if (entry == null) {
			return null;
		}

		order.get(directionOf.apply(entry.component())).remove(instanceId);
		return entry.component();
	}

	/// 清空活动实例与各方向顺序，保留全部注册定义。
	public void clearActive() {
		active.clear();
		for (var ids : order.values()) {
			ids.clear();
		}
	}

	/// 当前是否没有活动实例；空活动只停止绘制，不影响定义或已有宿主。
	public boolean activeEmpty() {
		return active.isEmpty();
	}

	/// 按某方向的活动顺序返回组件，供视图恢复子节点和纯逻辑测试检查补位。
	public List<C> activeInOrder(EventHudDirection direction) {
		var ids = order.get(direction);
		var result = new ArrayList<C>(ids.size());
		for (var id : ids) {
			var entry = active.get(id);
			if (entry != null) {
				result.add(entry.component());
			}
		}

		return List.copyOf(result);
	}

	private record Definition<C>(String englishName, String chineseName,
	                             Supplier<? extends C> factory) {
	}

	private record Active<C>(Identifier type, C component) {
	}
}
