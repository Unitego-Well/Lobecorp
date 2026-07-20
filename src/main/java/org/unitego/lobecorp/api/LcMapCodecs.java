package org.unitego.lobecorp.api;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

public class LcMapCodecs {
    public static final MapCodec<Optional<EntityType<?>>> ENTITY_TYPE = EntityType.CODEC.optionalFieldOf("entity_type");
}
