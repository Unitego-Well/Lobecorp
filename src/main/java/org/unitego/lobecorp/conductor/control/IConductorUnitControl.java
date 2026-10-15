package org.unitego.lobecorp.conductor.control;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.conductor.ability.IConductorAbility;
import org.unitego.lobecorp.conductor.data.ConductorData;

import java.util.Collection;

/// 实体指挥能力的统一操作接口。
public interface IConductorUnitControl {
	boolean supports(ConductorData.OrderType order);

	boolean accept(ConductorData.Unit command);

	void tick();

	void stop();

	void prepareMovement();

	void advanceMovement(ConductorData data, ConductorData.Unit command);

	void movementStopped();

	void movementArrived();

	Collection<IConductorAbility> abilities();

	boolean cast(Identifier skill, Entity target, Vec3 position);
}
