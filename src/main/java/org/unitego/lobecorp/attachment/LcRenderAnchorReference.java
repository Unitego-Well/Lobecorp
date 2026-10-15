package org.unitego.lobecorp.attachment;

import net.minecraft.resources.Identifier;

import java.util.UUID;

/// 渲染提供器只替换客户端视觉来源，不能回写权威世界位置。
public record LcRenderAnchorReference(Identifier provider, UUID sourceId, int entityId, String node) {
}

