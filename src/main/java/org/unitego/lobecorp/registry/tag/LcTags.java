package org.unitego.lobecorp.registry.tag;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;

public interface LcTags {
	static void init() {
		LcEntityTypeTags.init();
		LcDamageTypeTags.init();
	}

	static <T> TagKey<T> create(ResourceKey<? extends Registry<T>> registry, String name, String enUs, String zhCn) {
		TagKey<T> tagKey = TagKey.create(registry, Lobecorp.id(name));
		LangHandler.creates(Lobecorp.NAMESPACE, enUs, zhCn, (langSet, s) -> langSet.tagKey(tagKey, s));
		return tagKey;
	}
}
