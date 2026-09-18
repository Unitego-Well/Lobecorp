package org.unitego.lobecorp.generator.tag;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import org.unitego.lobecorp.Lobecorp;

import java.util.concurrent.CompletableFuture;

public class DamageTypeTagGenerator extends DamageTypeTagsProvider {
	public DamageTypeTagGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
		super(output, lookupProvider, Lobecorp.NAMESPACE);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
	}
}
