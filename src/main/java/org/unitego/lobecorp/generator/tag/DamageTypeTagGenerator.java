package org.unitego.lobecorp.generator.tag;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import net.minecraft.tags.DamageTypeTags;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.world.damage.LcDamageTypes;
import org.unitego.lobecorp.registry.world.tag.LcDamageTypeTags;

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
		tag(LcDamageTypeTags.CAN_HURT_ALLIES);
		tag(DamageTypeTags.IS_PROJECTILE).add(LcDamageTypes.MAGIC_STAR);
		tag(DamageTypeTags.BYPASSES_COOLDOWN).add(LcDamageTypes.MAGIC_STAR);
	}
}
