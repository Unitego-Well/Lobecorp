package org.unitego.lobecorp.init.tag;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.ApiStatus;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.EnUsLangGenerator;
import org.unitego.lobecorp.generator.lang.ZhCnLangGenerator;

public class LcTags {
    @ApiStatus.Internal
    public static void init() {
        LcEntityTypeTags.init();
        LcDamageTypeTags.init();
    }

    public static <T> TagKey<T> create(ResourceKey<? extends Registry<T>> registry, String name, String enUs, String zhCn) {
        TagKey<T> tagKey = TagKey.create(registry, Lobecorp.id(name));
        EnUsLangGenerator.addI18nTagKey(tagKey, enUs);
        ZhCnLangGenerator.addI18nTagKey(tagKey, zhCn);
        return tagKey;
    }
}
