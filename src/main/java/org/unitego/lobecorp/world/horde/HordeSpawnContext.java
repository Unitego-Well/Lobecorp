package org.unitego.lobecorp.world.horde;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public record HordeSpawnContext(UUID eventId, LivingEntity target, Vec3 position, long seed) {
}
