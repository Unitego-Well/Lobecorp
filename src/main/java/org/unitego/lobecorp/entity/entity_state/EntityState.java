package org.unitego.lobecorp.entity.entity_state;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/// 可同步的实体状态定义。
///
/// @param id             状态的稳定资源 ID
/// @param exclusiveGroup 互斥组；同组状态不能同时存在，null 表示不互斥
public record EntityState(Identifier id, @Nullable Identifier exclusiveGroup) {
	public EntityState(Identifier id) {
		this(id, null);
	}

	public boolean conflictsWith(EntityState other) {
		return exclusiveGroup != null && exclusiveGroup.equals(other.exclusiveGroup());
	}
}
