package org.unitego.lobecorp.generator.tag;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.init.tag.LcEntityTypeTags;

import java.util.concurrent.CompletableFuture;

public class EntityTypeTagGenerator extends EntityTypeTagsProvider {
    public EntityTypeTagGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Lobecorp.NAMESPACE);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(LcEntityTypeTags.ORDEAL).addTags(
                LcEntityTypeTags.ORDEAL_AMBER,
                LcEntityTypeTags.ORDEAL_CRIMSON,
                LcEntityTypeTags.ORDEAL_GREEN,
                LcEntityTypeTags.ORDEAL_INDIGO,
                LcEntityTypeTags.ORDEAL_VIOLET,
                LcEntityTypeTags.ORDEAL_WHITE);
    }
}
