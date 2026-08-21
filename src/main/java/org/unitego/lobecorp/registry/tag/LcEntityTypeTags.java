package org.unitego.lobecorp.registry.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.ApiStatus;

public interface LcEntityTypeTags {
    TagKey<EntityType<?>> ORDEAL = create("ordeal", "Ordeal", "考验");
    TagKey<EntityType<?>> ORDEAL_AMBER = create("ordeal/amber", "Amber Ordeal", "琥珀色的考验");
    TagKey<EntityType<?>> ORDEAL_CRIMSON = create("ordeal/crimson", "Crimson Ordeal", "血色的考验");
    TagKey<EntityType<?>> ORDEAL_GREEN = create("ordeal/green", "Green Ordeal", "绿色的考验");
    TagKey<EntityType<?>> ORDEAL_INDIGO = create("ordeal/indigo", "Indigo Ordeal", "靛蓝色的考验");
    TagKey<EntityType<?>> ORDEAL_VIOLET = create("ordeal/violet", "Violet Ordeal", "紫罗兰的考验");
    TagKey<EntityType<?>> ORDEAL_WHITE = create("ordeal/white", "White Ordeal", "惨白的考验");

    @ApiStatus.Internal
    static void init() {
    }

    private static TagKey<EntityType<?>> create(String name, String enUs, String zhCn) {
        return LcTags.create(Registries.ENTITY_TYPE, name, enUs, zhCn);
    }
}
