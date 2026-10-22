package org.unitego.lobecorp.world.horde;

import net.minecraft.resources.Identifier;

/// 客户端资源描述，不持有客户端类型。
public record HordeVisualDefinition(Identifier model, Identifier animation, String runAnimation, Identifier texture) {
}
