package org.unitego.lobecorp.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

public interface LcMapCodecs {
	MapCodec<Optional<EntityType<?>>> ENTITY_TYPE = EntityType.CODEC.optionalFieldOf("entity_type");
}
