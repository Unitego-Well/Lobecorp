package org.unitego.lobecorp.attachment;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/// 独立绑定快照不要求动画存在；世界变换始终是创建侧采样后的结果。
public record LcAttachmentSnapshot(UUID id, LcAnchorReference anchor, LcTransform offset,
                                   LcTransform worldTransform, @Nullable LcRenderAnchorReference renderAnchor,
                                   long revision, boolean preserveWorldTransform, LcTransform renderOffset) {
}
