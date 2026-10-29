package org.unitego.lobecorp.world.horde;

import net.minecraft.world.phys.Vec3;

import java.util.List;

public record HordeStartRequest(Vec3 start, Vec3 end, List<Vec3> waypoints,
                                int durationTicks, double width) {
	public HordeStartRequest {
		waypoints = List.copyOf(waypoints);
	}

	public static HordeStartRequest between(Vec3 start, Vec3 end) {
		return new HordeStartRequest(start, end, List.of(), 0, 0);
	}
}
