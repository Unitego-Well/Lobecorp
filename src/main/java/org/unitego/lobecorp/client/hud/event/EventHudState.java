package org.unitego.lobecorp.client.hud.event;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/// 事件 HUD 的纯注册与活动状态；不创建 UI，不依赖客户端会话。
///
/// 活动实例按 `show` 插入顺序保存，按包装归属返回视图顺序；`hide` 只移除目标 UUID。
@NullMarked
public final class EventHudState<C> {
	private final Map<Identifier, Definition<C>> definitions = new LinkedHashMap<>();
	private final Map<UUID, Active<C>> active = new LinkedHashMap<>();
	private final Map<Identifier, WrapperDefinition> wrappers = new LinkedHashMap<>();
	private boolean hostCreated;

	public EventHudState() {
	}

	/// 登记直属根的包装容器；宿主创建后不允许追加。
	public void registerWrapper(Identifier wrapperId) {
		registerWrapper(wrapperId, null);
	}

	/// 登记包装容器与可空父包装；父包装必须先行注册，宿主创建后不允许追加。
	public void registerWrapper(Identifier wrapperId, @Nullable Identifier parentId) {
		Objects.requireNonNull(wrapperId);
		if (hostCreated) {
			throw new IllegalStateException("事件 HUD 宿主已创建，不能追加包装：" + wrapperId);
		}

		if (wrappers.containsKey(wrapperId)) {
			throw new IllegalArgumentException("事件 HUD 包装已注册：" + wrapperId);
		}

		if (Objects.equals(wrapperId, parentId)) {
			throw new IllegalArgumentException("事件 HUD 包装不能以自身为父级：" + wrapperId);
		}

		if (parentId != null && !wrappers.containsKey(parentId)) {
			throw new IllegalArgumentException("事件 HUD 父包装未注册：" + parentId);
		}

		wrappers.put(wrapperId, new WrapperDefinition(parentId));
	}

	/// 按登记顺序返回包装容器 ID。
	public List<Identifier> wrapperIds() {
		return List.copyOf(wrappers.keySet());
	}

	/// 返回包装的父级；未登记包装明确拒绝。
	public @Nullable Identifier wrapperParent(Identifier wrapperId) {
		Objects.requireNonNull(wrapperId);
		var definition = wrappers.get(wrapperId);
		if (definition == null) {
			throw new IllegalArgumentException("事件 HUD 包装未注册：" + wrapperId);
		}

		return definition.parentId();
	}

	/// 按登记顺序返回直接子包装；`null` 表示直接挂在根节点的包装。
	public List<Identifier> childWrapperIds(@Nullable Identifier parentId) {
		var result = new ArrayList<Identifier>();
		for (var entry : wrappers.entrySet()) {
			if (Objects.equals(entry.getValue().parentId(), parentId)) {
				result.add(entry.getKey());
			}
		}

		return List.copyOf(result);
	}

	/// 登记事件定义；包装必须先行登记，同一 `Identifier` 重复登记明确拒绝。
	public void register(Identifier id, String englishName, String chineseName,
	                     @Nullable Identifier wrapperId, Supplier<? extends C> factory) {
		Objects.requireNonNull(id);
		Objects.requireNonNull(englishName);
		Objects.requireNonNull(chineseName);
		Objects.requireNonNull(factory);
		if (wrapperId != null && !wrappers.containsKey(wrapperId)) {
			throw new IllegalArgumentException("事件 HUD 包装未注册：" + wrapperId);
		}

		var definition = new Definition<C>(englishName, chineseName, wrapperId, factory);
		if (definitions.putIfAbsent(id, definition) != null) {
			throw new IllegalArgumentException("事件 HUD 已注册：" + id);
		}
	}

	/// 标记宿主已首次创建；此后的包装与根布局登记均被拒绝。
	public void markHostCreated() {
		hostCreated = true;
	}

	/// 宿主是否已经首次创建。
	public boolean hostCreated() {
		return hostCreated;
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
		active.put(instanceId, new Active<>(type, definition.wrapperId(), component));
		return component;
	}

	/// 移除实例并返回其组件；未找到时返回 `null`。
	public @Nullable C hide(UUID instanceId) {
		var entry = active.remove(instanceId);
		if (entry == null) {
			return null;
		}

		return entry.component();
	}

	/// 清空活动实例与顺序，保留全部注册定义。
	public void clearActive() {
		active.clear();
	}

	/// 当前是否没有活动实例；空活动只停止绘制，不影响定义或已有宿主。
	public boolean activeEmpty() {
		return active.isEmpty();
	}

	/// 按包装归属返回该容器的活动组件顺序；`null` 表示直接挂在根节点。
	public List<C> activeInOrder(@Nullable Identifier wrapperId) {
		var result = new ArrayList<C>();
		for (var entry : active.values()) {
			if (Objects.equals(entry.wrapperId(), wrapperId)) {
				result.add(entry.component());
			}
		}

		return List.copyOf(result);
	}

	private record WrapperDefinition(@Nullable Identifier parentId) {
	}

	private record Definition<C>(String englishName, String chineseName,
	                             @Nullable Identifier wrapperId, Supplier<? extends C> factory) {
	}

	private record Active<C>(Identifier type, @Nullable Identifier wrapperId, C component) {
	}
}
