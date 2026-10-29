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

/// 浪潮调试 Gizmo：线路包围、分割割圆与潮头潮尾采样点，复用原版调试管线。
public class HordeDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	/// 分割割圆：禁入核心本身，半径就是 GPU 让步使用的半径。
	private static final int SPLIT_COLOR = 0xFFFF4040;
	/// 潮头采样点。
	private static final int HEAD_COLOR = 0xFF00FF00;
	/// 潮尾采样点。
	private static final int TAIL_COLOR = 0xFF00A0FF;

	@Override
	public void emitGizmos(double camX, double camY, double camZ, @NonNull DebugValueAccess values,
	                       @NonNull Frustum frustum, float partialTick) {
		var entries = Minecraft.getInstance().debugEntries;
		if (!entries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)
				&& !entries.isCurrentlyEnabled(LcDebugEntries.HORDE_RANGES)) {
			return;
		}

		for (var range : HordeClient.debugRanges()) {
			Gizmos.cuboid(range.bounds(), GizmoStyle.stroke(range.color()));
		}
		for (var split : HordeDebugState.splitGizmos()) {
			Gizmos.circle(split.center(), (float) split.radius(), GizmoStyle.stroke(SPLIT_COLOR));
			Gizmos.point(split.center(), SPLIT_COLOR, 0.8f);
		}
		for (var tide : HordeDebugState.tideGizmos()) {
			Gizmos.point(tide.point(), tide.head() ? HEAD_COLOR : TAIL_COLOR, 1.6f);
		}
	}
}
