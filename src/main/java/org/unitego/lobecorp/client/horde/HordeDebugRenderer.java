package org.unitego.lobecorp.client.horde;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.registry.entity.client.LcDebugEntries;

/// 区域框复用原版调试Gizmo管线，不再向正常场景提交手工线段。
public class HordeDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	@Override
	public void emitGizmos(double camX, double camY, double camZ, @NonNull DebugValueAccess values, @NonNull Frustum frustum, float partialTick) {
		var entries = Minecraft.getInstance().debugEntries;
		if (!entries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)
				&& !entries.isCurrentlyEnabled(LcDebugEntries.HORDE_RANGES)) {
			return;
		}

		for (var range : HordeClient.debugRanges()) {
			Gizmos.cuboid(range.bounds(), GizmoStyle.stroke(range.color()));
		}
	}
}
