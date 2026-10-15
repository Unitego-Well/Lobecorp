package org.unitego.lobecorp.client.attachment;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.unitego.lobecorp.attachment.LcRenderAnchorReference;

/// 从本次实际渲染的完整矩阵解析节点，不读取或修改服务端骨架。
@FunctionalInterface
public interface ILcRenderAnchorProvider {
	@Nullable Matrix4f resolve(LcRenderAnchorReference reference, LcRenderAnchorContext context);
}
