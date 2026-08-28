package org.unitego.lobecorp.event.client;

import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.Event;

import java.util.HashSet;
import java.util.Set;

public class EntityCorpseReverseEvent extends Event {
    private static final Set<EntityType<?>> ENTITY_TYPES = new HashSet<>();

    /// 注册实体类型 Key。
    public void register(EntityType<?> entityTypeKey) {
        ENTITY_TYPES.add(entityTypeKey);
    }

    /// 获取已注册的实体类型 Key。
    public static Set<EntityType<?>> getEntityTypes() {
        return Set.copyOf(ENTITY_TYPES);
    }

    public static boolean contains(EntityType<?> entityType) {
        return ENTITY_TYPES.contains(entityType);
    }
}
