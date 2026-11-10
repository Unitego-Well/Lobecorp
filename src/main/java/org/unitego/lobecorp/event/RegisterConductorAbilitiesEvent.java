package org.unitego.lobecorp.event;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import org.unitego.lobecorp.conductor.ability.IConductorAbility;
import org.unitego.lobecorp.registry.conductor.ConductorAbilityRegistry;

import java.util.function.Supplier;

public class RegisterConductorAbilitiesEvent extends Event implements IModBusEvent {

	public void register(EntityType<?> type, Identifier id, Supplier<IConductorAbility> factory) {
		ConductorAbilityRegistry.register(type, id, factory, true);
	}

	public void registerLearnable(EntityType<?> type, Identifier id, Supplier<IConductorAbility> factory) {
		ConductorAbilityRegistry.register(type, id, factory, false);
	}

}
