package org.unitego.lobecorp.event.client;

import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.Event;

import java.util.HashSet;
import java.util.Set;

public class EntityCorpseReverseEvent extends Event {
	/// 使用默认尸体翻转渲染的实体类型。
	private static final Set<EntityType<?>> ENTITY_TYPES = new HashSet<>();

	/// 注册使用默认尸体翻转渲染的实体类型。
	///
	/// @param entityType 要注册的实体类型
	public void register(EntityType<?> entityTypeKey) {
		ENTITY_TYPES.add(entityTypeKey);
	}

	/// 获取已注册实体类型的不可变副本。
	///
	/// @return 使用默认尸体翻转渲染的实体类型
	public static Set<EntityType<?>> getEntityTypes() {
		return Set.copyOf(ENTITY_TYPES);
	}

	/// 判断实体类型是否使用默认尸体翻转渲染。
	///
	/// @param entityType 要检查的实体类型
	/// @return 已注册时返回 {@code true}
	public static boolean contains(EntityType<?> entityType) {
		return ENTITY_TYPES.contains(entityType);
	}
}
