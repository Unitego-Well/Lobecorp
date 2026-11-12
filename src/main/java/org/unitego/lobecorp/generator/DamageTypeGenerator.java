package org.unitego.lobecorp.generator;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.world.damage.LcDamageTypes;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DamageTypeGenerator extends DatapackBuiltinEntriesProvider {
	public DamageTypeGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
		super(output, lookupProvider, new RegistrySetBuilder().add(Registries.DAMAGE_TYPE, LcDamageTypes::bootstrap), Set.of(Lobecorp.NAMESPACE));
	}
}
