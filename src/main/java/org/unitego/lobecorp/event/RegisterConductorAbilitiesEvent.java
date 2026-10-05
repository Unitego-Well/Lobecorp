package org.unitego.lobecorp.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import org.unitego.lobecorp.conductor.ability.ConductorAbility;
import org.unitego.lobecorp.conductor.ability.ConductorAbilityRuntime;
import org.unitego.lobecorp.conductor.ability.ConductorAbilityState;
import org.unitego.lobecorp.conductor.control.ConductorUnitRuntime;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.util.conductor.ConductorAttachmentUtil;
import org.unitego.lobecorp.network.tc.ConductorSnapshotPayload;

import java.util.*;
import java.util.function.Supplier;

public class RegisterConductorAbilitiesEvent extends Event implements IModBusEvent {
	public static final Map<EntityType<?>, Map<Identifier, Definition>> DEFINITIONS = new HashMap<>();

	public static ConductorAbility get(Mob mob, Identifier id) {
		ConductorAbilityRuntime runtime = runtime(mob, id);
		return runtime == null ? null : runtime.ability();
	}

	public static boolean add(Mob mob, Identifier id) {
		if (mob.level().isClientSide())
			return false;
		Definition definition = DEFINITIONS.getOrDefault(mob.getType(), Map.of()).get(id);
		if (definition == null || !definition.ability().supports(mob))
			return false;
		ConductorAbilityState state = ConductorAttachmentUtil.abilities(mob);
		ConductorAttachmentUtil.setAbilities(mob, state.add(id, definition.initial()));
		if (mob.level() instanceof ServerLevel level) {
			ConductorSnapshotPayload.sendAll(level.getServer(), ConductorData.get(level.getServer()));
		}
		return true;
	}

	public static boolean remove(Mob mob, Identifier id) {
		if (mob.level().isClientSide())
			return false;
		Definition definition = DEFINITIONS.getOrDefault(mob.getType(), Map.of()).get(id);
		if (definition == null)
			return false;
		ConductorAbilityState state = ConductorAttachmentUtil.abilities(mob);
		ConductorAttachmentUtil.setAbilities(mob, state.remove(id, definition.initial()));
		Map<Identifier, ConductorAbilityRuntime> runtimes = ConductorUnitRuntime.get(mob).abilities;
		if (runtimes != null)
			runtimes.remove(id);
		if (mob.level() instanceof ServerLevel level) {
			ConductorSnapshotPayload.sendAll(level.getServer(), ConductorData.get(level.getServer()));
		}
		return true;
	}

	public static Collection<ConductorAbility> forMob(Mob mob) {
		List<ConductorAbility> available = new ArrayList<>();
		for (Map.Entry<Identifier, Definition> entry : DEFINITIONS.getOrDefault(mob.getType(), Map.of()).entrySet()) {
			if (get(mob, entry.getKey()) != null)
				available.add(entry.getValue().ability());
		}
		return List.copyOf(available);
	}

	public static ConductorAbilityRuntime runtime(Mob mob, Identifier id) {
		Definition definition = DEFINITIONS.getOrDefault(mob.getType(), Map.of()).get(id);
		if (definition == null || !owns(mob, id, definition) || !definition.ability().supports(mob))
			return null;
		return ConductorUnitRuntime.get(mob).abilities.computeIfAbsent(id, ignored -> new ConductorAbilityRuntime(mob.getUUID(), id, definition.ability()));
	}

	private static boolean owns(Mob mob, Identifier id, Definition definition) {
		ConductorAbilityState state = ConductorAttachmentUtil.abilities(mob);
		return state.owns(id, definition.initial());
	}

	private void register(EntityType<?> type, Identifier id, Supplier<ConductorAbility> factory, boolean initial) {
		Map<Identifier, Definition> abilities = DEFINITIONS.computeIfAbsent(type, ignored -> new LinkedHashMap<>());
		if (abilities.containsKey(id)) {
			throw new IllegalArgumentException("Duplicate conductor ability: " + type + " / " + id);
		}
		ConductorAbility ability = factory.get();
		if (ability == null || !id.equals(ability.id())) {
			throw new IllegalArgumentException("Invalid conductor ability: " + type + " / " + id);
		}
		abilities.put(id, new Definition(ability, initial));
	}

	public void register(EntityType<?> type, Identifier id, Supplier<ConductorAbility> factory) {
		register(type, id, factory, true);
	}

	public void registerLearnable(EntityType<?> type, Identifier id, Supplier<ConductorAbility> factory) {
		register(type, id, factory, false);
	}

	public record Definition(ConductorAbility ability, boolean initial) {
	}
}
