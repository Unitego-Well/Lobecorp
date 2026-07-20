package org.unitego.lobecorp.api;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

public class LcCodecs {
    public static final Codec<Optional<EntityType<?>>> OPTIONAL_ENTITY_TYPE = LcMapCodecs.ENTITY_TYPE.codec();
}
