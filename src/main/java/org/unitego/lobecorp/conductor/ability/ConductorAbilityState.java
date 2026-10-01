package org.unitego.lobecorp.conductor.ability;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/// 指挥家包装能力的持久化增删修正，未修正项继承注册定义的默认值。
public record ConductorAbilityState(Map<Identifier, Boolean> overrides) {
	/// true 表示显式添加，false 表示显式移除；不保存默认能力列表。
	public static final Codec<ConductorAbilityState> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.BOOL)
			.xmap(ConductorAbilityState::new, ConductorAbilityState::overrides);

	public ConductorAbilityState {
		overrides = Map.copyOf(overrides);
	}

	public static ConductorAbilityState empty() {
		return new ConductorAbilityState(Map.of());
	}

	public boolean owns(Identifier id, boolean initial) {
		return overrides.getOrDefault(id, initial);
	}

	public ConductorAbilityState add(Identifier id, boolean initial) {
		return with(id, true, initial);
	}

	public ConductorAbilityState remove(Identifier id, boolean initial) {
		return with(id, false, initial);
	}

	private ConductorAbilityState with(Identifier id, boolean owned, boolean initial) {
		Map<Identifier, Boolean> next = new HashMap<>(overrides);
		if (owned == initial) next.remove(id);
		else next.put(id, owned);
		return new ConductorAbilityState(next);
	}
}
