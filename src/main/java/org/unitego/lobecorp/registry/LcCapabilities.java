package org.unitego.lobecorp.registry;

import net.neoforged.neoforge.capabilities.EntityCapability;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.conductor.control.ConductorUnitControl;
import org.unitego.lobecorp.world.entity.skill.EntitySkillAccess;

/// NeoForge 实体能力声明。
public interface LcCapabilities {
	/// 指挥家单位控制能力。
	EntityCapability<ConductorUnitControl, Void> CONDUCTOR_CONTROL =
			EntityCapability.createVoid(Lobecorp.id("unit_control"), ConductorUnitControl.class);

	/// 实体技能操作能力。
	EntityCapability<EntitySkillAccess, Void> ENTITY_SKILL =
			EntityCapability.createVoid(Lobecorp.id("entity_skill"), EntitySkillAccess.class);
}
