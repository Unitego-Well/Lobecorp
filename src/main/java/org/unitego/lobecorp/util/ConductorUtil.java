package org.unitego.lobecorp.util;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.conductor.ability.ConductorAbility;
import org.unitego.lobecorp.conductor.control.ConductorUnitControl;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.registry.LcCapabilities;

import java.util.Collection;
import java.util.List;

/**
 * 指挥控制能力的统一访问入口。
 *
 * <p>业务代码通过这里查询能力并提交操作，避免在调用方直接依赖 NeoForge
 * {@code EntityCapability} 的实现细节。能力不存在时，所有操作都保持无操作结果。</p>
 */
public class ConductorUtil {

	@Nullable
	public static ConductorUnitControl get(Entity entity) {
		return entity.getCapability(LcCapabilities.CONDUCTOR_CONTROL);
	}

	public static boolean supports(Mob mob, ConductorData.OrderType order) {
		ConductorUnitControl control = get(mob);
		return control != null && control.supports(order);
	}

	public static boolean accept(Mob mob, ConductorData.Unit command) {
		ConductorUnitControl control = get(mob);
		return control != null && control.accept(command);
	}

	public static void tick(Mob mob) {
		ConductorUnitControl control = get(mob);
		if (control != null) control.tick();
	}

	public static void stop(Mob mob) {
		ConductorUnitControl control = get(mob);
		if (control != null) control.stop();
	}

	public static void prepareMovement(Mob mob) {
		ConductorUnitControl control = get(mob);
		if (control != null) control.prepareMovement();
	}

	public static void advanceMovement(Mob mob, ConductorData data, ConductorData.Unit command) {
		ConductorUnitControl control = get(mob);
		if (control != null) control.advanceMovement(data, command);
	}

	public static void movementStopped(Mob mob) {
		ConductorUnitControl control = get(mob);
		if (control != null) control.movementStopped();
	}

	public static void movementArrived(Mob mob) {
		ConductorUnitControl control = get(mob);
		if (control != null) control.movementArrived();
	}

	public static Collection<ConductorAbility> abilities(Mob mob) {
		ConductorUnitControl control = get(mob);
		return control == null ? List.of() : control.abilities();
	}

	@Nullable
	public static ConductorAbility ability(Mob mob, Identifier id) {
		return abilities(mob).stream().filter(ability -> ability.id().equals(id)).findFirst().orElse(null);
	}

	public static boolean cast(Mob mob, Identifier skill, @Nullable Entity target, @Nullable Vec3 position) {
		ConductorUnitControl control = get(mob);
		return control != null && control.cast(skill, target, position);
	}
}
