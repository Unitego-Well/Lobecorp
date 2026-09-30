package org.unitego.lobecorp.entity_skill;

import java.util.ArrayList;
import java.util.List;

/// 实体技能的非持久化运行态容器。
///
/// 运行实例只属于当前加载的实体；该附件不序列化、不跨世界迁移，也不保存持久化业务数据。
/// 生命周期由 {@link EntitySkillAccess} 能力统一推进和清理。
public class EntitySkillRuntimeData {
	public List<EntitySkillRuntime<?>> active = new ArrayList<>();
	/// 当前实体内下一个运行实例 ID；仅用于本次加载期间的客户端同步关联。
	public long nextRuntimeId = 1L;

	public long allocateRuntimeId() {
		return nextRuntimeId++;
	}

	public EntitySkillRuntime<?> find(long runtimeId) {
		return active.stream().filter(runtime -> runtime.id() == runtimeId).findFirst().orElse(null);
	}
}
