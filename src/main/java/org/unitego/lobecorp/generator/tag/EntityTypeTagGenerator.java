package org.unitego.lobecorp.generator.tag;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;
import org.unitego.lobecorp.registry.tag.LcEntityTypeTags;

import java.util.concurrent.CompletableFuture;

public class EntityTypeTagGenerator extends EntityTypeTagsProvider {
	public EntityTypeTagGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
		super(output, lookupProvider, Lobecorp.NAMESPACE);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		tag(LcEntityTypeTags.ORDEAL_AMBER);
		tag(LcEntityTypeTags.ORDEAL_CRIMSON);
		tag(LcEntityTypeTags.ORDEAL_GREEN);
		tag(LcEntityTypeTags.ORDEAL_INDIGO)
				.add(OrdealEntityTypes.SWEEPER.get());
		tag(LcEntityTypeTags.ORDEAL_VIOLET);
		tag(LcEntityTypeTags.ORDEAL_WHITE);
		tag(LcEntityTypeTags.ABNORMALITIE);

		tag(LcEntityTypeTags.ORDEAL).addTags(
				LcEntityTypeTags.ORDEAL_AMBER,
				LcEntityTypeTags.ORDEAL_CRIMSON,
				LcEntityTypeTags.ORDEAL_GREEN,
				LcEntityTypeTags.ORDEAL_INDIGO,
				LcEntityTypeTags.ORDEAL_VIOLET,
				LcEntityTypeTags.ORDEAL_WHITE);
	}
}
