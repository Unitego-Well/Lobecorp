package org.unitego.lobecorp.conductor.control;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.conductor.ability.ConductorAbility;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.util.ConductorAttachmentUtil;
import org.unitego.lobecorp.util.ConductorUtil;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.event.RegisterConductorAbilitiesEvent;
import org.unitego.lobecorp.registry.entity_skill.SweeperSkills;

import java.util.Collection;

/// 提供指挥家实体控制能力及实体适配器运行逻辑。
public class ConductorCapabilities {
	public static Collection<ConductorAbility> abilities(Mob mob) {
		return ConductorUtil.abilities(mob);
	}

	public static ConductorAbility ability(Mob mob, Identifier id) {
		return ConductorUtil.ability(mob, id);
	}

	public static class MobControl implements ConductorUnitControl {
		protected Mob mob;

		public MobControl(Mob mob) {
			this.mob = mob;
		}

		@Override
		public boolean supports(ConductorData.OrderType order) {
			return mob.isAlive() && switch (order) {
				case CLEANUP, REASSEMBLE -> false;
				case ATTACK_POINT -> ConductorPointAttack.supports(mob);
				default -> true;
			};
		}

		@Override
		public boolean accept(ConductorData.Unit command) {
			if (!supports(command.order())) return false;
			ConductorAttachmentUtil.unit(mob).read(command);
			return true;
		}

		@Override
		public void tick() {
			ConductorController.tickControlled(mob);
		}

		@Override
		public void stop() {
			ConductorController.stopControlled(mob);
		}

		@Override
		public void prepareMovement() {
		}

		@Override
		public void advanceMovement(ConductorData data, ConductorData.Unit command) {
			ConductorMovement.advance(mob, mob.getNavigation(), data, command);
		}

		@Override
		public void movementStopped() {
		}

		@Override
		public void movementArrived() {
		}

		@Override
		public Collection<ConductorAbility> abilities() {
			return RegisterConductorAbilitiesEvent.forMob(mob);
		}

		@Override
		public boolean cast(Identifier skill, Entity target, Vec3 position) {
			ConductorAbility ability = RegisterConductorAbilitiesEvent.get(mob, skill);
			return ability != null && ability.isAvailable(mob) && ability.cooldownTicks(mob) == 0
					&& ability.cast(mob, target, position);
		}
	}

	public static class SweeperControl extends MobControl {
		public SweeperControl(Sweeper mob) {
			super(mob);
		}

		@Override
		public boolean supports(ConductorData.OrderType order) {
			if (order == ConductorData.OrderType.CLEANUP || order == ConductorData.OrderType.REASSEMBLE) {
				Identifier skill = order == ConductorData.OrderType.CLEANUP ? SweeperSkills.SWEEP.getId() : SweeperSkills.REASSEMBLE.getId();
				return mob.isAlive() && RegisterConductorAbilitiesEvent.get(mob, skill) != null;
			}
			return super.supports(order);
		}

		@Override
		public void tick() {
			if (mob.level() instanceof ServerLevel level && ConductorWork.active(mob)) {
				ConductorData data = ConductorData.get(level.getServer());
				ConductorWork.tick((Sweeper) mob, level, data, data.unit(mob.getUUID()));
			} else super.tick();
		}
	}

	public static class WardenControl extends MobControl {
		public WardenControl(Warden mob) {
			super(mob);
		}

		@Override
		public void tick() {
			if (mob.level() instanceof ServerLevel level && ConductorSonicBoom.active(mob)) {
				ConductorSonicBoom.tick((Warden) mob, level);
			} else super.tick();
		}
	}
}
