package org.unitego.lobecorp.util.entity.skill;

import net.neoforged.fml.loading.FMLEnvironment;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;

/// 技能运行实例的可选调试日志工具，仅在非生产环境输出。
public class EntitySkillDebugUtil {
	/// 设为 true 时启用技能诊断；正式运行保持静默。
	public static volatile boolean ENABLED = false;

	/// 记录一次技能生命周期事件，不改变技能运行状态。
	public static void log(EntitySkillRuntime<?> runtime, String event) {
		if (ENABLED && !FMLEnvironment.isProduction()) {
			Lobecorp.LOGGER.info("[Entity Skill Debug] entity={} skill={} event={} state={} ticksLeft={} elapsedTicks={} activeTicks={} target={}",
					runtime.owner().getUUID(), runtime.skill().id(), event, runtime.state(), runtime.ticksLeft(),
					runtime.elapsedTicks(), runtime.activeTicks(),
					runtime.target() == null ? "<none>" : runtime.target().getUUID());
		}
	}
}
