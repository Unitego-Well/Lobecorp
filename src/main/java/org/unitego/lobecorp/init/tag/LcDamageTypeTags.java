package org.unitego.lobecorp.init.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.ApiStatus;

public class LcDamageTypeTags {
    @ApiStatus.Internal
    static void init() {
    }

    private static TagKey<EntityType<?>> create(String name, String enUs, String zhCn) {
        return LcTags.create(Registries.ENTITY_TYPE, name, enUs, zhCn);
    }
}
