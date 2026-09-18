package org.unitego.lobecorp.entity.entity_state;

import java.util.ArrayList;
import java.util.List;

/// 持有同步实体状态的对象。
/// <p>
/// 状态只通过实体同步数据发送给客户端，不参与存档。
public interface EntityStateHolder {
	List<EntityState> getEntityStates();

	void setEntityStates(List<EntityState> states);

	default boolean hasEntityState(EntityState state) {
		return getEntityStates().stream().anyMatch(activeState -> activeState.id().equals(state.id()));
	}

	default void addEntityState(EntityState state) {
		List<EntityState> states = new ArrayList<>(getEntityStates());
		states.removeIf(activeState -> activeState.id().equals(state.id()) || state.conflictsWith(activeState));
		states.add(state);
		setEntityStates(List.copyOf(states));
	}

	default void removeEntityState(EntityState state) {
		List<EntityState> states = new ArrayList<>(getEntityStates());
		if (states.removeIf(activeState -> activeState.id().equals(state.id()))) {
			setEntityStates(List.copyOf(states));
		}
	}
}
