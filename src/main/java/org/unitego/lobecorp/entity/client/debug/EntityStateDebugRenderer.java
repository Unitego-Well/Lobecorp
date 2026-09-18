package org.unitego.lobecorp.entity.client.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.entity.entity_state.EntityState;
import org.unitego.lobecorp.entity.entity_state.EntityStateHolder;

import java.util.stream.Collectors;

public class EntityStateDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final double MAX_RENDER_DISTANCE_SQUARED = 64.0 * 64.0;
	private static final double TEXT_HEIGHT_OFFSET = 0.5;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final String STATE_PREFIX = "states: ";

	private final Minecraft minecraft;

	public EntityStateDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void emitGizmos(double camX, double camY, double camZ, @NonNull DebugValueAccess debugValues,
			@NonNull Frustum frustum, float partialTicks) {
		if (minecraft.level == null) {
			return;
		}

		Vec3 cameraPosition = new Vec3(camX, camY, camZ);
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof EntityStateHolder holder) || holder.getEntityStates().isEmpty()
					|| entity.position().distanceToSqr(cameraPosition) > MAX_RENDER_DISTANCE_SQUARED) {
				continue;
			}

			String states = holder.getEntityStates().stream()
					.map(EntityState::id)
					.map(Identifier::toString)
					.collect(Collectors.joining(", ", STATE_PREFIX, ""));
			Vec3 textPosition = new Vec3(entity.getX(), entity.getBoundingBox().maxY + TEXT_HEIGHT_OFFSET, entity.getZ());
			Gizmos.billboardText(states, textPosition, TextGizmo.Style.forColorAndCentered(TEXT_COLOR));
		}
	}
}
