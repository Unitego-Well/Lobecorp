package org.unitego.lobecorp.world.horde;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public record HordeTargetContext(ServerLevel level, Vec3 origin, String faction) {
}
