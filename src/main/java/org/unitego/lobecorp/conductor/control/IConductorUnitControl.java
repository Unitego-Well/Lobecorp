package org.unitego.lobecorp.conductor.control;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
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

	/// 自定义控制器默认按现有能力列表查询，注册表控制器可复用按 ID 的入口。
	default @Nullable IConductorAbility ability(Identifier id) {
		return abilities().stream().filter(ability -> ability.id().equals(id)).findFirst().orElse(null);
	}

	boolean cast(Identifier skill, Entity target, Vec3 position);
}
