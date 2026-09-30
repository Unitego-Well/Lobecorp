package org.unitego.lobecorp.conductor.control;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import org.unitego.lobecorp.conductor.ability.ConductorAbilityRuntime;
import org.unitego.lobecorp.util.ConductorAttachmentUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// 单个编队单位的非持久化移动、工作和技能运行状态。
public class ConductorUnitRuntime {
	public FlyingPathNavigation flyingNavigation;
	public UUID sharedTarget;
	public Map<Identifier, ConductorAbilityRuntime> abilities = new HashMap<>();
	protected ConductorMovement.Progress movement;
	protected ConductorWork.Work work;
	protected ConductorSonicBoom.Cast sonic;
	protected ConductorPointAttack.State pointAttack;
	protected ConductorController.ManualSonicBoomRequest manualSonic;

	public static ConductorUnitRuntime get(Mob mob) {
		return ConductorAttachmentUtil.runtime(mob);
	}
}
