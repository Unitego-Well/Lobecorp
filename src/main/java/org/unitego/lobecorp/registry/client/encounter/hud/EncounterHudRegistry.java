package org.unitego.lobecorp.registry.client.encounter.hud;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/// 事件 HUD 的定义和包装登记；保存工厂与包装关系，不持有活动组件实例。
@NullMarked
public class EncounterHudRegistry<C> {
	private final Map<Identifier, Definition<C>> definitions = new LinkedHashMap<>();
	private final Map<Identifier, WrapperDefinition> wrappers = new LinkedHashMap<>();
	private final BooleanSupplier hostCreated;

	/// 宿主是否创建由运行期状态提供，直接登记也遵守相同的包装冻结边界。
	public EncounterHudRegistry(BooleanSupplier hostCreated) {
		this.hostCreated = Objects.requireNonNull(hostCreated);
	}

	/// 登记直属根的包装容器；宿主创建后不允许追加。
	public void registerWrapper(Identifier wrapperId) {
		registerWrapper(wrapperId, null);
	}

	/// 登记包装容器与可空父包装；父包装必须先行注册，宿主创建后不允许追加。
	public void registerWrapper(Identifier wrapperId, @Nullable Identifier parentId) {
		Objects.requireNonNull(wrapperId);
		if (hostCreated.getAsBoolean()) {
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

	public @Nullable Definition<C> definition(Identifier id) {
		return definitions.get(id);
	}

	private record WrapperDefinition(@Nullable Identifier parentId) {
	}

	public record Definition<C>(String englishName, String chineseName,
	                             @Nullable Identifier wrapperId, Supplier<? extends C> factory) {
	}

}
