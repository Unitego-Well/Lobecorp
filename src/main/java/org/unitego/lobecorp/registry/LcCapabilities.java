package org.unitego.lobecorp.registry;

import net.neoforged.neoforge.capabilities.EntityCapability;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.conductor.control.IConductorUnitControl;
import org.unitego.lobecorp.world.entity.skill.IEntitySkillAccess;

/// NeoForge 实体能力声明。
public interface LcCapabilities {
	/// 指挥家单位控制能力。
	EntityCapability<IConductorUnitControl, Void> CONDUCTOR_CONTROL =
			EntityCapability.createVoid(Lobecorp.id("unit_control"), IConductorUnitControl.class);

	/// 实体技能操作能力。
	EntityCapability<IEntitySkillAccess, Void> ENTITY_SKILL =
			EntityCapability.createVoid(Lobecorp.id("entity_skill"), IEntitySkillAccess.class);
}
