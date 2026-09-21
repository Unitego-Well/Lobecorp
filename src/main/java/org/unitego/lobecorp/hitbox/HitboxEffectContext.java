package org.unitego.lobecorp.hitbox;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/// 判断框效果调用时的服务端上下文。
///
/// @param level 所在服务端维度
/// @param instance 产生命中的实例
/// @param source 可选来源实体
/// @param target 当前候选目标
public record HitboxEffectContext(ServerLevel level, HitboxInstance instance, @Nullable Entity source, Entity target) {
}
