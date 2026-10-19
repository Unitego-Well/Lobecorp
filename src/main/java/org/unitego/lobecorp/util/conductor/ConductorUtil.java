package org.unitego.lobecorp.util.conductor;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.conductor.ability.IConductorAbility;
import org.unitego.lobecorp.conductor.control.IConductorUnitControl;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.registry.LcCapabilities;
import org.unitego.lobecorp.util.entity.combat.CombatUtil;

import java.util.Collection;
import java.util.List;

/// 指挥控制能力的统一访问入口。
///
/// 业务代码通过这里查询能力并提交操作，避免在调用方直接依赖 NeoForge
/// `EntityCapability` 的实现细节。能力不存在时，所有操作都保持无操作结果。
public class ConductorUtil {

	@Nullable
	public static IConductorUnitControl get(Entity entity) {
		return entity.getCapability(LcCapabilities.CONDUCTOR_CONTROL);
	}

	public static boolean supports(Mob mob, ConductorData.OrderType order) {
		if (!CombatUtil.acceptsControl(mob)) {
			return false;
		}
		IConductorUnitControl control = get(mob);
		return control != null && control.supports(order);
	}

	public static boolean accept(Mob mob, ConductorData.Unit command) {
		if (!CombatUtil.acceptsControl(mob)) {
			return false;
		}
		IConductorUnitControl control = get(mob);
		return control != null && control.accept(command);
	}

	public static void tick(Mob mob) {
		if (!CombatUtil.acceptsControl(mob)) {
			return;
		}
		IConductorUnitControl control = get(mob);

		if (control != null) {
			control.tick();
		}
	}

	public static void stop(Mob mob) {
		IConductorUnitControl control = get(mob);

		if (control != null) {
			control.stop();
		}
	}

	public static void prepareMovement(Mob mob) {
		IConductorUnitControl control = get(mob);

		if (control != null) {
			control.prepareMovement();
		}
	}

	public static void advanceMovement(Mob mob, ConductorData data, ConductorData.Unit command) {
		IConductorUnitControl control = get(mob);

		if (control != null) {
			control.advanceMovement(data, command);
		}
	}

	public static void movementStopped(Mob mob) {
		IConductorUnitControl control = get(mob);

		if (control != null) {
			control.movementStopped();
		}
	}

	public static void movementArrived(Mob mob) {
		IConductorUnitControl control = get(mob);

		if (control != null) {
			control.movementArrived();
		}
	}

	public static Collection<IConductorAbility> abilities(Mob mob) {
		if (!CombatUtil.acceptsControl(mob)) {
			return List.of();
		}
		IConductorUnitControl control = get(mob);
		return control == null ? List.of() : control.abilities();
	}

	@Nullable
	public static IConductorAbility ability(Mob mob, Identifier id) {
		if (!CombatUtil.acceptsControl(mob)) {
			return null;
		}
		IConductorUnitControl control = get(mob);
		return control == null ? null : control.ability(id);
	}

	public static boolean cast(Mob mob, Identifier skill, @Nullable Entity target, @Nullable Vec3 position) {
		if (!CombatUtil.acceptsControl(mob)) {
			return false;
		}
		IConductorUnitControl control = get(mob);
		return control != null && control.cast(skill, target, position);
	}
}
