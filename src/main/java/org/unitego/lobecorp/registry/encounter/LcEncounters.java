package org.unitego.lobecorp.registry.encounter;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.LcRegistries;
import org.unitego.lobecorp.registry.horde.HordeProfiles;
import org.unitego.lobecorp.registry.translation.MidnightCleanupTranslationKeys;
import org.unitego.lobecorp.world.encounter.EncounterDefinition;
import org.unitego.lobecorp.world.encounter.IEncounterDefinition;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;

import java.util.function.Supplier;

public class LcEncounters {
	public static final DeferredRegister<EncounterDefinition<?>> REGISTER = Lobecorp.register(LcRegistries.ENCOUNTER);
	public static final DeferredHolder<EncounterDefinition<?>, EncounterDefinition<MidnightCleanupEncounter>> MIDNIGHT_CLEANUP = register(
			MidnightCleanupEncounter.ID, MidnightCleanupTranslationKeys.NAME, MidnightCleanupEncounter::new);

	public static void init(IEventBus eventBus) {
		REGISTER.register(eventBus);
	}

	private static <T extends IEncounterDefinition> DeferredHolder<EncounterDefinition<?>, EncounterDefinition<T>> register(
			Identifier id, String nameKey, Supplier<T> factory) {
		return REGISTER.register(id.getPath(), () -> {
			var implementation = factory.get();
			HordeProfiles.register(implementation.hordeProfile());
			return new EncounterDefinition<>(id, nameKey, implementation);
		});
	}
}
