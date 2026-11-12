package org.unitego.lobecorp.registry.world.encounter;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.LcRegistries;
import org.unitego.lobecorp.registry.world.horde.HordeProfiles;
import org.unitego.lobecorp.registry.translation.LangHandler;
import org.unitego.lobecorp.world.encounter.EncounterDefinition;
import org.unitego.lobecorp.world.encounter.IEncounterDefinition;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;

import java.util.function.Supplier;

public interface LcEncounters {
	DeferredRegister<EncounterDefinition<?>> REGISTER = Lobecorp.register(LcRegistries.ENCOUNTER);
	DeferredHolder<EncounterDefinition<?>, EncounterDefinition<MidnightCleanupEncounter>> MIDNIGHT_CLEANUP = register(
			MidnightCleanupEncounter.ID, "horde.lobecorp.midnight_cleanup", "Midnight Cleanup", "深夜清扫", MidnightCleanupEncounter::new);

	static void init(IEventBus eventBus) {
		REGISTER.register(eventBus);
	}

	static <T extends IEncounterDefinition> DeferredHolder<EncounterDefinition<?>, EncounterDefinition<T>> register(
			Identifier id, String nameKey, String enUs, String zhCn, Supplier<T> factory) {
		LangHandler.creates(REGISTER.getNamespace(), nameKey, enUs, zhCn);
		return REGISTER.register(id.getPath(), () -> {
			var implementation = factory.get();
			HordeProfiles.register(implementation.hordeProfile());
			return new EncounterDefinition<>(id, nameKey, implementation);
		});
	}
}
