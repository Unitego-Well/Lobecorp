package org.unitego.lobecorp.conductor.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.unitego.lobecorp.conductor.control.ConductorUnitRuntime;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.network.tc.ConductorSnapshotPayload;
import org.unitego.lobecorp.registry.conductor.ConductorAbilityRegistry;
import org.unitego.lobecorp.registry.conductor.ConductorAbilityRegistry.Definition;
import org.unitego.lobecorp.util.conductor.ConductorAttachmentUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// 指挥家能力的资格查询、附件修改及运行实例入口。
public class ConductorAbilities {
	public static IConductorAbility get(Mob mob, Identifier id) {
		ConductorAbilityRuntime runtime = runtime(mob, id);
		return runtime == null ? null : runtime.ability();
	}

	public static boolean add(Mob mob, Identifier id) {
		if (mob.level().isClientSide()) {
			return false;
		}
		Definition definition = ConductorAbilityRegistry.DEFINITIONS.getOrDefault(mob.getType(), Map.of()).get(id);

		if (definition == null || !definition.ability().supports(mob)) {
			return false;
		}
		ConductorAbilityState state = ConductorAttachmentUtil.abilities(mob);
		ConductorAttachmentUtil.setAbilities(mob, state.add(id, definition.initial()));

		if (mob.level() instanceof ServerLevel level) {
			ConductorSnapshotPayload.sendAll(level.getServer(), ConductorData.get(level.getServer()));
		}

		return true;
	}

	public static boolean remove(Mob mob, Identifier id) {
		if (mob.level().isClientSide()) {
			return false;
		}
		Definition definition = ConductorAbilityRegistry.DEFINITIONS.getOrDefault(mob.getType(), Map.of()).get(id);

		if (definition == null) {
			return false;
		}
		ConductorAbilityState state = ConductorAttachmentUtil.abilities(mob);
		ConductorAttachmentUtil.setAbilities(mob, state.remove(id, definition.initial()));
		Map<Identifier, ConductorAbilityRuntime> runtimes = ConductorUnitRuntime.get(mob).abilities;

		if (runtimes != null) {
			runtimes.remove(id);
		}

		if (mob.level() instanceof ServerLevel level) {
			ConductorSnapshotPayload.sendAll(level.getServer(), ConductorData.get(level.getServer()));
		}

		return true;
	}

	public static Collection<IConductorAbility> forMob(Mob mob) {
		List<IConductorAbility> available = new ArrayList<>();

		for (Map.Entry<Identifier, Definition> entry : ConductorAbilityRegistry.DEFINITIONS.getOrDefault(mob.getType(), Map.of()).entrySet()) {
			if (get(mob, entry.getKey()) != null) {
				available.add(entry.getValue().ability());
			}
		}

		return List.copyOf(available);
	}

	public static ConductorAbilityRuntime runtime(Mob mob, Identifier id) {
		Definition definition = ConductorAbilityRegistry.DEFINITIONS.getOrDefault(mob.getType(), Map.of()).get(id);

		if (definition == null || !owns(mob, id, definition) || !definition.ability().supports(mob)) {
			return null;
		}
		return ConductorUnitRuntime.get(mob).abilities.computeIfAbsent(id, _ -> new ConductorAbilityRuntime(mob.getUUID(), id, definition.ability()));
	}

	private static boolean owns(Mob mob, Identifier id, Definition definition) {
		ConductorAbilityState state = ConductorAttachmentUtil.abilities(mob);
		return state.owns(id, definition.initial());
	}
}
