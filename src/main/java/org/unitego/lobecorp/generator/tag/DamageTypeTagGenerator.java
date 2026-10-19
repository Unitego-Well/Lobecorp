package org.unitego.lobecorp.generator.tag;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.tag.LcDamageTypeTags;

import java.util.concurrent.CompletableFuture;

public class DamageTypeTagGenerator extends DamageTypeTagsProvider {
	public DamageTypeTagGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
		super(output, lookupProvider, Lobecorp.NAMESPACE);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		tag(LcDamageTypeTags.PHYSICS);
		tag(LcDamageTypeTags.SPIRIT);
		tag(LcDamageTypeTags.EROSION);
		tag(LcDamageTypeTags.THE_SOUL);
	}
}
