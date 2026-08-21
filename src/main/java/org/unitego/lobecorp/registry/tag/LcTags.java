package org.unitego.lobecorp.registry.tag;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.ApiStatus;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.EnUsLangGenerator;
import org.unitego.lobecorp.generator.lang.LangHandler;
import org.unitego.lobecorp.generator.lang.ZhCnLangGenerator;

public interface LcTags {
    @ApiStatus.Internal
    static void init() {
        LcEntityTypeTags.init();
        LcDamageTypeTags.init();
    }

    static <T> TagKey<T> create(ResourceKey<? extends Registry<T>> registry, String name, String enUs, String zhCn) {
        TagKey<T> tagKey = TagKey.create(registry, Lobecorp.id(name));
        LangHandler.addLangEnUsAndZhCnTxt(Lobecorp.NAMESPACE, enUs, zhCn, (langSet, s) -> langSet.tagKey(tagKey, s));
        return tagKey;
    }
}
