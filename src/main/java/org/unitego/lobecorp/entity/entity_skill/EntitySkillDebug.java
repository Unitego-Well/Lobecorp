package org.unitego.lobecorp.entity.entity_skill;

import net.neoforged.fml.loading.FMLEnvironment;
import org.unitego.lobecorp.Lobecorp;

public final class EntitySkillDebug {
	/// Set to true to enable skill diagnostics; production runs remain silent.
	public static volatile boolean ENABLED = false;

	private EntitySkillDebug() {
	}

	public static void log(EntitySkillRuntime<?> runtime, String event) {
		if (ENABLED && !FMLEnvironment.isProduction()) {
			Lobecorp.LOGGER.info("[Entity Skill Debug] entity={} skill={} event={} state={} ticksLeft={} elapsedTicks={} activeTicks={} target={}",
					runtime.owner().getUUID(), runtime.skill().id(), event, runtime.state(), runtime.ticksLeft(),
					runtime.elapsedTicks(), runtime.activeTicks(),
					runtime.target() == null ? "<none>" : runtime.target().getUUID());
		}
	}
}
