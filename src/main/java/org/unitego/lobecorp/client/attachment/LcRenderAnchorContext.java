package org.unitego.lobecorp.client.attachment;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.unitego.lobecorp.Lobecorp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// 节点矩阵已包含模型父链和实际渲染变换；世界换算只用于跨来源的显式连续切换。
public record LcRenderAnchorContext(Identifier provider, UUID sourceId, int entityId, Identifier view,
                                    Matrix4fc worldFromRender, Map<String, Matrix4fc> nodes, Set<String> visibleNodes) {
	public static final Identifier WORLD_VIEW = Lobecorp.id("world");
	public static final Identifier FIRST_PERSON_VIEW = Lobecorp.id("first_person");

	public LcRenderAnchorContext {
		worldFromRender = new Matrix4f(worldFromRender);
		var copy = new LinkedHashMap<String, Matrix4fc>();
		nodes.forEach((name, matrix) -> copy.put(name, new Matrix4f(matrix)));
		nodes = Map.copyOf(copy);
		visibleNodes = Set.copyOf(visibleNodes);
	}

	@Override
	public Matrix4fc worldFromRender() {
		return new Matrix4f(worldFromRender);
	}

	@Override
	public Map<String, Matrix4fc> nodes() {
		var copy = new LinkedHashMap<String, Matrix4fc>();
		nodes.forEach((name, matrix) -> copy.put(name, new Matrix4f(matrix)));
		return Map.copyOf(copy);
	}

	public @Nullable Matrix4f node(String name) {
		var matrix = nodes.get(name);
		return matrix == null ? null : new Matrix4f(matrix);
	}
}
