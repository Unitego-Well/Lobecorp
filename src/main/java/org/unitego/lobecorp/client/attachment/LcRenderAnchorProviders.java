package org.unitego.lobecorp.client.attachment;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.unitego.lobecorp.attachment.LcRenderAnchorReference;

import java.util.LinkedHashMap;
import java.util.Map;

/// 渲染来源通过显式键注册，第三方适配仍由各自渲染器提供本帧上下文。
public class LcRenderAnchorProviders {
	private static final Map<Identifier, ILcRenderAnchorProvider> PROVIDERS = new LinkedHashMap<>();

	public static void register(Identifier id, ILcRenderAnchorProvider provider) {
		PROVIDERS.put(id, provider);
	}

	public static @Nullable Matrix4f resolve(LcRenderAnchorReference reference, LcRenderAnchorContext context) {
		if (!reference.provider().equals(context.provider()) || !reference.sourceId().equals(context.sourceId())
				|| reference.entityId() != context.entityId()) {
			return null;
		}

		var provider = PROVIDERS.get(reference.provider());
		var matrix = provider == null ? null : provider.resolve(reference, context);
		return matrix == null ? null : new Matrix4f(matrix);
	}
}
